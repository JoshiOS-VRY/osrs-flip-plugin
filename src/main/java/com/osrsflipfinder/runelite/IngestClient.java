package com.osrsflipfinder.runelite;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

@Slf4j
@Singleton
public class IngestClient
{
	static final int MAX_QUEUE_SIZE = 1000;
	static final int FLUSH_THRESHOLD = 50;
	static final int FLUSH_INTERVAL_SECONDS = 8;
	static final int MAX_BATCH_SIZE = 500;
	static final int MAX_BACKOFF_SECONDS = 120;

	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

	private final OkHttpClient httpClient;
	private final FlipFinderConfig config;
	private final ConfigManager configManager;
	private final ScheduledExecutorService executorService;
	private final Gson gson;
	private final ConcurrentLinkedQueue<IngestGeEvent> queue = new ConcurrentLinkedQueue<>();
	private final AtomicBoolean flushInProgress = new AtomicBoolean(false);

	private volatile Consumer<SyncStats> syncStatsListener = stats -> {};
	private volatile Consumer<PluginState> stateListener = state -> {};
	private volatile Consumer<String> errorListener = message -> {};

	private ScheduledFuture<?> flushTask;
	private volatile boolean shuttingDown = false;
	private volatile int consecutiveFailures = 0;
	private volatile long retryAfterMs = 0;

	@Inject
	IngestClient(
		OkHttpClient httpClient,
		FlipFinderConfig config,
		ConfigManager configManager,
		ScheduledExecutorService executorService,
		Gson gson
	)
	{
		this.httpClient = httpClient;
		this.config = config;
		this.configManager = configManager;
		this.executorService = executorService;
		this.gson = gson;
	}

	void start()
	{
		shuttingDown = false;
		if (flushTask != null)
		{
			return;
		}
		flushTask = executorService.scheduleWithFixedDelay(
			this::flushAsync,
			FLUSH_INTERVAL_SECONDS,
			FLUSH_INTERVAL_SECONDS,
			TimeUnit.SECONDS
		);
	}

	void shutdown()
	{
		shuttingDown = true;
		if (flushTask != null)
		{
			flushTask.cancel(false);
			flushTask = null;
		}

		for (int attempt = 0; attempt < 3 && !queue.isEmpty() && isConfigured(); attempt++)
		{
			try
			{
				flushNow();
			}
			catch (RuntimeException e)
			{
				log.warn("Shutdown flush attempt {} failed", attempt + 1, e);
			}
		}
	}

	void setSyncStatsListener(Consumer<SyncStats> listener)
	{
		this.syncStatsListener = listener != null ? listener : stats -> {};
	}

	void setStateListener(Consumer<PluginState> listener)
	{
		this.stateListener = listener != null ? listener : state -> {};
	}

	void setErrorListener(Consumer<String> listener)
	{
		this.errorListener = listener != null ? listener : message -> {};
	}

	boolean isConfigured()
	{
		return config.enableUpload() && !config.apiKey().isBlank();
	}

	int getQueueSize()
	{
		return queue.size();
	}

	void enqueue(IngestGeEvent event)
	{
		if (!isConfigured() || shuttingDown)
		{
			return;
		}

		if (queue.size() >= MAX_QUEUE_SIZE)
		{
			queue.poll();
			log.warn("Ingest queue full; dropped oldest event");
		}

		queue.add(event);
		if (queue.size() >= FLUSH_THRESHOLD)
		{
			flushAsync();
		}
	}

	void reconcileSlots(String accountHash, String accountDisplayName, java.util.List<GeSlotSnapshot> slots)
	{
		if (!isConfigured() || shuttingDown || slots == null || slots.isEmpty())
		{
			return;
		}

		executorService.execute(() ->
		{
			try
			{
				postReconcile(accountHash, accountDisplayName, slots);
			}
			catch (IOException e)
			{
				log.debug("GE reconcile failed", e);
			}
		});
	}

	private void postReconcile(
		String accountHash,
		String accountDisplayName,
		java.util.List<GeSlotSnapshot> slots
	) throws IOException
	{
		JsonObject root = new JsonObject();
		root.addProperty("accountHash", accountHash);
		if (accountDisplayName != null && !accountDisplayName.isBlank())
		{
			root.addProperty("accountDisplayName", accountDisplayName);
		}

		JsonArray slotArray = new JsonArray();
		for (GeSlotSnapshot snapshot : slots)
		{
			JsonObject entry = new JsonObject();
			entry.addProperty("slot", snapshot.getSlot());
			entry.addProperty("empty", snapshot.isEmpty());
			slotArray.add(entry);
		}
		root.add("slots", slotArray);

		Request request = new Request.Builder()
			.url(FlipXConstants.baseUrl() + "/api/ingest/ge-reconcile")
			.addHeader("Authorization", "Bearer " + config.apiKey())
			.post(RequestBody.create(JSON, gson.toJson(root)))
			.build();

		try (Response response = httpClient.newCall(request).execute())
		{
			if (!response.isSuccessful())
			{
				ResponseBody responseBody = response.body();
				String raw = responseBody != null ? responseBody.string() : "";
				log.debug("GE reconcile returned {}: {}", response.code(), raw);
			}
		}
	}

	void flushAsync()
	{
		if (!isConfigured() || queue.isEmpty() || shuttingDown)
		{
			return;
		}

		if (System.currentTimeMillis() < retryAfterMs)
		{
			return;
		}

		if (!flushInProgress.compareAndSet(false, true))
		{
			return;
		}

		executorService.execute(() ->
		{
			try
			{
				flushNow();
			}
			finally
			{
				flushInProgress.set(false);
			}
		});
	}

	private void flushNow()
	{
		List<IngestGeEvent> batch = new ArrayList<>();
		while (batch.size() < MAX_BATCH_SIZE)
		{
			IngestGeEvent event = queue.poll();
			if (event == null)
			{
				break;
			}
			batch.add(event);
		}

		if (batch.isEmpty())
		{
			return;
		}

		stateListener.accept(PluginState.SYNCING);

		try
		{
			IngestResult result = postBatch(batch);
			consecutiveFailures = 0;
			retryAfterMs = 0;
			syncStatsListener.accept(new SyncStats(
				Instant.now(),
				result.inserted,
				result.skipped,
				result.total
			));
			stateListener.accept(PluginState.CONNECTED);
			errorListener.accept(null);
		}
		catch (IngestAuthException e)
		{
			requeueFront(batch);
			consecutiveFailures = 0;
			retryAfterMs = 0;
			PairingCredentials.clearIfAuthFailedForKey(configManager, config, e.getApiKeyUsed());
			stateListener.accept(e.getState());
			errorListener.accept(e.getMessage());
		}
		catch (IngestPermanentException e)
		{
			consecutiveFailures = 0;
			retryAfterMs = 0;
			stateListener.accept(PluginState.ERROR);
			errorListener.accept(e.getMessage());
			log.error("Permanent ingest failure; dropped {} events", batch.size(), e);
		}
		catch (IOException e)
		{
			requeueFront(batch);
			consecutiveFailures++;
			int backoffSeconds = Math.min(
				FLUSH_INTERVAL_SECONDS * (1 << Math.min(consecutiveFailures, 4)),
				MAX_BACKOFF_SECONDS
			);
			retryAfterMs = System.currentTimeMillis() + (backoffSeconds * 1000L);
			stateListener.accept(PluginState.ERROR);
			errorListener.accept(e.getMessage() + " (retry in " + backoffSeconds + "s)");
			log.warn("Ingest failed; will retry in {}s", backoffSeconds, e);
		}
	}

	private IngestResult postBatch(List<IngestGeEvent> batch) throws IOException
	{
		String apiKeyUsed = config.apiKey();
		JsonObject root = new JsonObject();
		JsonArray events = new JsonArray();
		for (IngestGeEvent event : batch)
		{
			events.add(gson.toJsonTree(event));
		}
		root.add("events", events);

		Request request = new Request.Builder()
			.url(FlipXConstants.baseUrl() + "/api/ingest/ge-events")
			.addHeader("Authorization", "Bearer " + apiKeyUsed)
			.post(RequestBody.create(JSON, gson.toJson(root)))
			.build();

		try (Response response = httpClient.newCall(request).execute())
		{
			ResponseBody responseBody = response.body();
			String raw = responseBody != null ? responseBody.string() : "";

			if (response.code() == 401)
			{
				throw new IngestAuthException(
					PluginState.REPAIR_REQUIRED,
					"Invalid API key - re-pair required",
					apiKeyUsed
				);
			}

			if (response.code() == 403)
			{
				String code = parseErrorCode(raw);
				if ("linked_account_limit".equals(code))
				{
					String message = parseErrorMessage(raw,
						"Free plan supports one OSRS account. Upgrade at flipx.app/pricing or log into your linked account.");
					throw new IngestPermanentException(message);
				}
				String message = humanizeUpgradeMessage(parseErrorMessage(raw, "Pro subscription required for plugin sync"));
				throw new IngestAuthException(PluginState.UPGRADE_REQUIRED, message, apiKeyUsed);
			}

			if (response.code() == 400)
			{
				String message = parseErrorMessage(raw, "Invalid event data - sync paused");
				throw new IngestPermanentException(message);
			}

			if (response.code() == 429)
			{
				String message = parseErrorMessage(raw, "Rate limited - backing off");
				throw new IOException(message);
			}

			if (response.code() >= 500)
			{
				String message = parseErrorMessage(raw, "Server error (" + response.code() + ")");
				throw new IOException(message);
			}

			if (!response.isSuccessful())
			{
				String message = parseErrorMessage(raw, "Ingest failed (" + response.code() + ")");
				throw new IOException(message);
			}

			JsonObject parsed = new JsonParser().parse(raw).getAsJsonObject();
			return new IngestResult(
				parsed.has("inserted") ? parsed.get("inserted").getAsInt() : 0,
				parsed.has("skipped") ? parsed.get("skipped").getAsInt() : 0,
				parsed.has("total") ? parsed.get("total").getAsInt() : batch.size()
			);
		}
	}

	private void requeueFront(List<IngestGeEvent> batch)
	{
		for (int i = batch.size() - 1; i >= 0; i--)
		{
			queue.add(batch.get(i));
		}
	}

	private static String parseErrorCode(String raw)
	{
		try
		{
			JsonObject parsed = new JsonParser().parse(raw).getAsJsonObject();
			if (parsed.has("code"))
			{
				return parsed.get("code").getAsString();
			}
		}
		catch (RuntimeException ignored)
		{
			// fall through
		}
		return "";
	}

	private static String parseErrorMessage(String raw, String fallback)
	{
		try
		{
			JsonObject parsed = new JsonParser().parse(raw).getAsJsonObject();
			if (parsed.has("error"))
			{
				return parsed.get("error").getAsString();
			}
		}
		catch (RuntimeException ignored)
		{
			// fall through
		}
		return fallback;
	}

	private static String humanizeUpgradeMessage(String message)
	{
		if (message == null || message.isBlank() || "upgrade_required".equals(message))
		{
			return "FlipX could not verify your subscription. Refresh billing on the web app, then try Connect again.";
		}
		return message;
	}

	@Value
	static class IngestResult
	{
		int inserted;
		int skipped;
		int total;
	}

	@Value
	public static class SyncStats
	{
		Instant syncedAt;
		int inserted;
		int skipped;
		int total;
	}

	private static final class IngestAuthException extends IOException
	{
		private final PluginState state;
		private final String apiKeyUsed;

		private IngestAuthException(PluginState state, String message, String apiKeyUsed)
		{
			super(message);
			this.state = state;
			this.apiKeyUsed = apiKeyUsed;
		}

		private PluginState getState()
		{
			return state;
		}

		private String getApiKeyUsed()
		{
			return apiKeyUsed;
		}
	}

	private static final class IngestPermanentException extends IOException
	{
		private IngestPermanentException(String message)
		{
			super(message);
		}
	}
}
