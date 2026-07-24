package com.osrsflipfinder.runelite;

import java.io.IOException;
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
	private static final int POLL_TICK_SECONDS = 10;

	private final PluginApiClient apiClient;
	private final FlipFinderConfig config;
	private final LocalCacheStore cacheStore;
	private final ScheduledExecutorService executorService;

	@Getter
	private volatile SlotsLiveResponse latestSlots;
	@Getter
	private volatile LiveSessionStats latestSession;
	@Getter
	private volatile boolean slotsOffline;
	@Getter
	private volatile boolean sessionOffline;
	@Getter
	private volatile java.time.Instant slotsCachedAt;
	@Getter
	private volatile java.time.Instant sessionCachedAt;

	private volatile boolean active = false;
	private volatile long nextRefreshAtMs = 0;
	private final AtomicBoolean fetchInProgress = new AtomicBoolean(false);

	private volatile Consumer<SlotsLiveResponse> slotsListener = slots -> {};
	private volatile Consumer<LiveSessionStats> sessionListener = session -> {};

	private ScheduledFuture<?> pollTask;

	@Inject
	PortfolioClient(
		PluginApiClient apiClient,
		FlipFinderConfig config,
		LocalCacheStore cacheStore,
		ScheduledExecutorService executorService
	)
	{
		this.apiClient = apiClient;
		this.config = config;
		this.cacheStore = cacheStore;
		this.executorService = executorService;
	}

	void setSlotsListener(Consumer<SlotsLiveResponse> listener)
	{
		this.slotsListener = listener != null ? listener : slots -> {};
	}

	void setSessionListener(Consumer<LiveSessionStats> listener)
	{
		this.sessionListener = listener != null ? listener : session -> {};
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
		if (!active || !apiClient.isConfigured() || !config.enableUpload())
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
		long intervalMs = 30_000L;
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
		}

		try
		{
			LiveSessionStats session = apiClient.get("/api/plugin/session?account=all", LiveSessionStats.class);
			latestSession = session;
			sessionOffline = false;
			sessionCachedAt = null;
			cacheStore.write("session", session, config);
			sessionListener.accept(session);
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
			log.debug("Session fetch failed", ex);
		}

		nextRefreshAtMs = System.currentTimeMillis() + intervalMs;
	}
}
