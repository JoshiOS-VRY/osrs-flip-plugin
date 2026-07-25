package com.osrsflipfinder.runelite;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Polls portfolio endpoints ({@code /api/plugin/slots/live}, {@code /api/plugin/session})
 * when the sidebar is active and GE upload is enabled.
 */
@Slf4j
@Singleton
public class PortfolioClient
{
	static final long REFRESH_INTERVAL_MS = 30_000L;
	private static final int POLL_TICK_SECONDS = 10;

	private final PluginApiClient apiClient;
	private final FlipFinderConfig config;
	private final LocalCacheStore cacheStore;
	private final ScheduledExecutorService executorService;
	private final OpportunitiesClient opportunitiesClient;

	@Getter
	private volatile SlotsLiveResponse latestSlots;
	@Getter
	private volatile LiveSessionStats latestSession;
	@Getter
	private volatile List<ItemPerformanceRow> latestSessionItems = Collections.emptyList();
	@Getter
	private volatile boolean slotsOffline;
	@Getter
	private volatile boolean sessionOffline;
	@Getter
	private volatile boolean sessionItemsOffline;
	@Getter
	private volatile java.time.Instant slotsCachedAt;
	@Getter
	private volatile java.time.Instant sessionCachedAt;
	@Getter
	private volatile java.time.Instant sessionItemsCachedAt;

	private volatile boolean active = false;
	private volatile long nextRefreshAtMs = 0;
	@Getter
	private volatile long refreshIntervalMs = REFRESH_INTERVAL_MS;
	private final AtomicBoolean fetchInProgress = new AtomicBoolean(false);

	private volatile Consumer<SlotsLiveResponse> slotsListener = slots -> {};
	private volatile Consumer<LiveSessionStats> sessionListener = session -> {};
	private volatile Consumer<List<ItemPerformanceRow>> sessionItemsListener = items -> {};

	private volatile Consumer<PluginState> authFailureListener = state -> {};

	void setAuthFailureListener(Consumer<PluginState> listener)
	{
		this.authFailureListener = listener != null ? listener : state -> {};
	}

	private void notifyAuthFailure(IOException ex)
	{
		if (ex instanceof PluginApiException)
		{
			authFailureListener.accept(((PluginApiException) ex).getState());
		}
	}

	private ScheduledFuture<?> pollTask;

	@Inject
	PortfolioClient(
		PluginApiClient apiClient,
		FlipFinderConfig config,
		LocalCacheStore cacheStore,
		ScheduledExecutorService executorService,
		OpportunitiesClient opportunitiesClient
	)
	{
		this.apiClient = apiClient;
		this.config = config;
		this.cacheStore = cacheStore;
		this.executorService = executorService;
		this.opportunitiesClient = opportunitiesClient;
	}

	void setSlotsListener(Consumer<SlotsLiveResponse> listener)
	{
		this.slotsListener = listener != null ? listener : slots -> {};
	}

	void setSessionListener(Consumer<LiveSessionStats> listener)
	{
		this.sessionListener = listener != null ? listener : session -> {};
	}

	void setSessionItemsListener(Consumer<List<ItemPerformanceRow>> listener)
	{
		this.sessionItemsListener = listener != null ? listener : items -> {};
	}

	void start()
	{
		if (pollTask == null)
		{
			pollTask = executorService.scheduleWithFixedDelay(
				this::tick,
				POLL_TICK_SECONDS,
				POLL_TICK_SECONDS,
				TimeUnit.SECONDS
			);
		}
	}

	void shutdown()
	{
		active = false;
		if (pollTask != null)
		{
			pollTask.cancel(false);
			pollTask = null;
		}
	}

	long getNextRefreshAtMs()
	{
		return nextRefreshAtMs;
	}

	boolean isActive()
	{
		return active;
	}

	boolean isFetchInProgress()
	{
		return fetchInProgress.get();
	}

	void setActive(boolean active)
	{
		boolean wasActive = this.active;
		this.active = active;
		if (active && !wasActive)
		{
			nextRefreshAtMs = 0;
			executorService.execute(this::tick);
		}
	}

	private void tick()
	{
		if (!active || !apiClient.isConfigured())
		{
			return;
		}
		if (System.currentTimeMillis() < nextRefreshAtMs)
		{
			return;
		}
		if (!fetchInProgress.compareAndSet(false, true))
		{
			return;
		}
		try
		{
			refreshNow();
		}
		finally
		{
			fetchInProgress.set(false);
		}
	}

	private void refreshNow()
	{
		long intervalMs = portfolioFallbackIntervalMs();
		refreshIntervalMs = intervalMs;
		try
		{
			SlotsLiveResponse slots = apiClient.get("/api/plugin/slots/live?account=all", SlotsLiveResponse.class);
			latestSlots = slots;
			slotsOffline = false;
			slotsCachedAt = null;
			cacheStore.write("slots-live", slots, config);
			slotsListener.accept(slots);
		}
		catch (IOException ex)
		{
			slotsOffline = true;
			LocalCacheStore.CachedEntry<SlotsLiveResponse> cached = cacheStore.read("slots-live", SlotsLiveResponse.class, config);
			if (cached != null)
			{
				latestSlots = cached.getPayload();
				slotsCachedAt = cached.getCachedAt();
				slotsListener.accept(latestSlots);
			}
			log.debug("Slots live fetch failed", ex);
			notifyAuthFailure(ex);
		}

		try
		{
			LiveSessionStats session = apiClient.get("/api/plugin/session?account=all", LiveSessionStats.class);
			latestSession = session;
			sessionOffline = false;
			sessionCachedAt = null;
			cacheStore.write("session", session, config);
			sessionListener.accept(session);
			refreshSessionItems(session);
		}
		catch (IOException ex)
		{
			sessionOffline = true;
			LocalCacheStore.CachedEntry<LiveSessionStats> cached = cacheStore.read("session", LiveSessionStats.class, config);
			if (cached != null)
			{
				latestSession = cached.getPayload();
				sessionCachedAt = cached.getCachedAt();
				sessionListener.accept(latestSession);
			}
			LocalCacheStore.CachedEntry<ItemPerformanceResponse> itemsCached =
				cacheStore.read("session-items", ItemPerformanceResponse.class, config);
			if (itemsCached != null)
			{
				latestSessionItems = safeItems(itemsCached.getPayload());
				sessionItemsCachedAt = itemsCached.getCachedAt();
				sessionItemsListener.accept(latestSessionItems);
			}
			log.debug("Session fetch failed", ex);
			notifyAuthFailure(ex);
		}

		nextRefreshAtMs = System.currentTimeMillis() + intervalMs;
	}

	private long portfolioFallbackIntervalMs()
	{
		PluginEntitlements entitlements = opportunitiesClient.getEntitlements();
		if (entitlements != null && entitlements.getRefreshIntervalMs() > 0)
		{
			return entitlements.getRefreshIntervalMs();
		}
		return REFRESH_INTERVAL_MS;
	}

	/** Immediate slots fetch when opening flip manager. */
	void refreshSlotsNow()
	{
		if (!apiClient.isConfigured())
		{
			return;
		}
		executorService.execute(() ->
		{
			try
			{
				SlotsLiveResponse slots = apiClient.get("/api/plugin/slots/live?account=all", SlotsLiveResponse.class);
				latestSlots = slots;
				slotsOffline = false;
				slotsCachedAt = null;
				cacheStore.write("slots-live", slots, config);
				slotsListener.accept(slots);
			}
			catch (IOException ex)
			{
				slotsOffline = true;
				LocalCacheStore.CachedEntry<SlotsLiveResponse> cached =
					cacheStore.read("slots-live", SlotsLiveResponse.class, config);
				if (cached != null)
				{
					latestSlots = cached.getPayload();
					slotsCachedAt = cached.getCachedAt();
					slotsListener.accept(latestSlots);
				}
				log.debug("Slots live fetch failed", ex);
				notifyAuthFailure(ex);
			}
		});
	}

	private void refreshSessionItems(LiveSessionStats session)
	{
		String startedAt = session != null ? session.getStartedAt() : null;
		if (startedAt == null || startedAt.isBlank())
		{
			latestSessionItems = Collections.emptyList();
			sessionItemsOffline = false;
			sessionItemsCachedAt = null;
			sessionItemsListener.accept(latestSessionItems);
			return;
		}

		try
		{
			String from = URLEncoder.encode(startedAt, StandardCharsets.UTF_8.name());
			ItemPerformanceResponse response = apiClient.get(
				"/api/plugin/analytics/items?account=all&limit=15&from=" + from,
				ItemPerformanceResponse.class
			);
			latestSessionItems = safeItems(response);
			sessionItemsOffline = false;
			sessionItemsCachedAt = null;
			cacheStore.write("session-items", response, config);
			sessionItemsListener.accept(latestSessionItems);
		}
		catch (IOException ex)
		{
			sessionItemsOffline = true;
			LocalCacheStore.CachedEntry<ItemPerformanceResponse> cached =
				cacheStore.read("session-items", ItemPerformanceResponse.class, config);
			if (cached != null)
			{
				latestSessionItems = safeItems(cached.getPayload());
				sessionItemsCachedAt = cached.getCachedAt();
				sessionItemsListener.accept(latestSessionItems);
			}
			log.debug("Session items fetch failed", ex);
		}
	}

	private static List<ItemPerformanceRow> safeItems(ItemPerformanceResponse response)
	{
		if (response == null || response.getItems() == null)
		{
			return Collections.emptyList();
		}
		return response.getItems();
	}
}
