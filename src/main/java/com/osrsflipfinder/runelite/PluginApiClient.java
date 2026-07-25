package com.osrsflipfinder.runelite;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Shared Bearer-authenticated JSON client for the {@code /api/plugin/*} routes.
 *
 * All calls are synchronous and MUST be invoked from a background thread (the
 * shared {@code ScheduledExecutorService}), never the client thread. Auth and
 * entitlement failures are mapped to {@link PluginState} via
 * {@link PluginApiException} so panels can render the right prompt.
 */
@Slf4j
@Singleton
public class PluginApiClient
{
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

	private final OkHttpClient httpClient;
	private final FlipFinderConfig config;
	private final ConfigManager configManager;
	private final Gson gson;

	@Inject
	PluginApiClient(OkHttpClient httpClient, FlipFinderConfig config, ConfigManager configManager, Gson gson)
	{
		this.httpClient = httpClient;
		this.config = config;
		this.configManager = configManager;
		this.gson = gson;
	}

	boolean isConfigured()
	{
		return !config.apiKey().isBlank();
	}

	<T> T get(String path, Class<T> type) throws IOException
	{
		String apiKeyUsed = config.apiKey();
		Request request = authorized(new Request.Builder().url(url(path)).get(), apiKeyUsed).build();
		return execute(request, type, apiKeyUsed);
	}

	<T> T post(String path, Object body, Class<T> type) throws IOException
	{
		String apiKeyUsed = config.apiKey();
		String json = body != null ? gson.toJson(body) : "{}";
		Request request = authorized(
			new Request.Builder().url(url(path)).post(RequestBody.create(JSON, json)),
			apiKeyUsed
		).build();
		return execute(request, type, apiKeyUsed);
	}

	void delete(String path) throws IOException
	{
		String apiKeyUsed = config.apiKey();
		Request request = authorized(new Request.Builder().url(url(path)).delete(), apiKeyUsed).build();
		try (Response response = httpClient.newCall(request).execute())
		{
			if (response.code() == 401)
			{
				clearApiKeyAfterAuthFailure(apiKeyUsed);
				throw new PluginApiException(PluginState.REPAIR_REQUIRED, "Invalid API key - re-pair required");
			}
			if (response.code() == 403)
			{
				ResponseBody responseBody = response.body();
				String raw = responseBody != null ? responseBody.string() : "";
				throw new PluginApiException(PluginState.UPGRADE_REQUIRED, parseErrorMessage(raw, "Subscription required"));
			}
			if (!response.isSuccessful())
			{
				ResponseBody responseBody = response.body();
				String raw = responseBody != null ? responseBody.string() : "";
				throw new IOException(parseErrorMessage(raw, "Request failed (" + response.code() + ")"));
			}
		}
	}

	private Request.Builder authorized(Request.Builder builder, String apiKey)
	{
		return builder.addHeader("Authorization", "Bearer " + apiKey);
	}

	private String url(String path)
	{
		return FlipXConstants.baseUrl() + path;
	}

	private <T> T execute(Request request, Class<T> type, String apiKeyUsed) throws IOException
	{
		try (Response response = httpClient.newCall(request).execute())
		{
			ResponseBody responseBody = response.body();
			String raw = responseBody != null ? responseBody.string() : "";

			if (response.code() == 401)
			{
				clearApiKeyAfterAuthFailure(apiKeyUsed);
				throw new PluginApiException(PluginState.REPAIR_REQUIRED, "Invalid API key - re-pair required");
			}

			if (response.code() == 403)
			{
				String message = parseErrorMessage(raw, "Subscription required");
				throw new PluginApiException(PluginState.UPGRADE_REQUIRED, message);
			}

			if (!response.isSuccessful())
			{
				String message = parseErrorMessage(raw, "Request failed (" + response.code() + ")");
				throw new IOException(message);
			}

			return gson.fromJson(raw, type);
		}
	}

	private void clearApiKeyAfterAuthFailure(String apiKeyUsed)
	{
		if (apiKeyUsed == null || apiKeyUsed.isBlank())
		{
			return;
		}
		if (!apiKeyUsed.equals(config.apiKey()))
		{
			log.debug("Ignoring 401 for a superseded API key");
			return;
		}
		if (PairingCredentials.isWithinPairingGracePeriod())
		{
			log.debug("Ignoring 401 during pairing grace period");
			return;
		}
		PairingCredentials.clear(configManager);
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
			// fall through to fallback
		}
		return fallback;
	}
}
