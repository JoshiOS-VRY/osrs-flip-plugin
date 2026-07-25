package com.osrsflipfinder.runelite;

import java.io.IOException;
import java.util.Objects;
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
 * Polls plugin entitlements and the market query on each tier-scoped refresh
 * cycle. Polling pauses when the Market tab is not visible to respect API
 * load, and resumes with an immediate refresh when shown.
 */
@Slf4j
@Singleton
public class OpportunitiesClient
{
	private static final int MIN_REFRESH_SECONDS = 15;
	private static final int POLL_TICK_SECONDS = 5;

	private final PluginApiClient apiClient;
	private final FlipFinderConfig config;
	private final LocalCacheStore cacheStore;
	private final ItemsClient itemsClient;
	private final ScheduledExecutorService executorService;

	@Getter
	private volatile PluginEntitlements entitlements;
	@Getter
	private volatile MarketQueryResponse latest;
	@Getter
	private volatile boolean offline;
	@Getter
	private volatile java.time.Instant cachedAt;

	private volatile MarketQueryRequest query = new MarketQueryRequest();
	private volatile boolean active = false;
	private volatile long nextRefreshAtMs = 0;
	@Getter
	private volatile long refreshIntervalMs = MIN_REFRESH_SECONDS * 1000L;
	private final AtomicBoolean fetchInProgress = new AtomicBoolean(false);

	private volatile Consumer<MarketQueryResponse> dataListener = data -> {};
	private volatile Consumer<PluginEntitlements> entitlementsListener = entitlements -> {};
	private volatile Consumer<PluginState> stateListener = state -> {};
	private volatile Consumer<String> errorListener = message -> {};

	private volatile ScheduledFuture<?> alignedRefreshTask;
	private ScheduledFuture<?> pollTask;

	@Inject
	OpportunitiesClient(
		PluginApiClient apiClient,
		FlipFinderConfig config,
		LocalCacheStore cacheStore,
		ItemsClient itemsClient,
		ScheduledExecutorService executorService
	)
	{
		this.apiClient = apiClient;
		this.config = config;
		this.cacheStore = cacheStore;
		this.itemsClient = itemsClient;
		this.executorService = executorService;
	}

	void setDataListener(Consumer<MarketQueryResponse> listener)
	{
		this.dataListener = listener != null ? listener : data -> {};
	}

	void setEntitlementsListener(Consumer<PluginEntitlements> listener)
	{
		this.entitlementsListener = listener != null ? listener : entitlements -> {};
	}

	void setStateListener(Consumer<PluginState> listener)
	{
		this.stateListener = listener != null ? listener : state -> {};
	}

	void setErrorListener(Consumer<String> listener)
	{
		this.errorListener = listener != null ? listener : message -> {};
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
		cancelAlignedRefresh();
		if (pollTask != null)
		{
			pollTask.cancel(false);
			pollTask = null;
		}
		entitlements = null;
		latest = null;
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

	/** Called when the Market tab becomes visible/hidden. */
	void setActive(boolean active)
	{
		boolean wasActive = this.active;
		this.active = active;
		if (active && !wasActive)
		{
			requestImmediateRefresh();
		}
	}

	/** Update the market query and refresh immediately if the tab is visible. */
	void setQuery(MarketQueryRequest query)
	{
		MarketQueryRequest next = query != null ? query : new MarketQueryRequest();
		if (queriesEqual(this.query, next))
		{
			return;
		}
		this.query = next;
		if (active)
		{
			requestImmediateRefresh();
		}
	}

	private static boolean queriesEqual(MarketQueryRequest a, MarketQueryRequest b)
	{
		if (a == b)
		{
			return true;
		}
		if (a == null || b == null)
		{
			return false;
		}
		return java.util.Objects.equals(a.getPresetId(), b.getPresetId())
			&& java.util.Objects.equals(a.getFilters(), b.getFilters())
			&& java.util.Objects.equals(a.getSort(), b.getSort());
	}

	void requestImmediateRefresh()
	{
		nextRefreshAtMs = 0;
		executorService.execute(this::tick);
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
		try
		{
			PluginEntitlements freshEntitlements = apiClient.get(
				"/api/plugin/entitlements",
				PluginEntitlements.class
			);
			boolean entitlementsChanged = entitlementsChanged(entitlements, freshEntitlements);
			entitlements = freshEntitlements;
			if (entitlementsChanged)
			{
				entitlementsListener.accept(freshEntitlements);
			}

			MarketQueryResponse response = apiClient.post(
				"/api/plugin/market/query",
				query,
				MarketQueryResponse.class
			);
			latest = response;
			offline = false;
			cachedAt = null;
			cacheStore.write("market", response, config);

			long fallbackMs = tierFallbackIntervalMs();
			refreshIntervalMs = fallbackMs;
			long publishLeadMs = entitlements != null && entitlements.getPublishLeadMs() > 0
				? entitlements.getPublishLeadMs()
				: 500L;
			nextRefreshAtMs = ClientSchedule.computeNextFetchAtMs(
				response.getMeta(),
				publishLeadMs,
				fallbackMs,
				System.currentTimeMillis()
			);
			scheduleAlignedRefresh(nextRefreshAtMs);
			itemsClient.setRefreshIntervalMs(fallbackMs);
			itemsClient.mergeFromMarketResponse(response);

			stateListener.accept(PluginState.CONNECTED);
			errorListener.accept(null);
			dataListener.accept(response);
		}
		catch (PluginApiException e)
		{
			nextRefreshAtMs = System.currentTimeMillis() + tierFallbackIntervalMs();
			if (e.getState() == PluginState.REPAIR_REQUIRED)
			{
				entitlements = null;
			}
			stateListener.accept(e.getState());
			errorListener.accept(e.getMessage());
		}
		catch (IOException e)
		{
			nextRefreshAtMs = System.currentTimeMillis() + tierFallbackIntervalMs();
			offline = true;
			LocalCacheStore.CachedEntry<MarketQueryResponse> cached = cacheStore.read("market", MarketQueryResponse.class, config);
			if (cached != null)
			{
				latest = cached.getPayload();
				cachedAt = cached.getCachedAt();
				dataListener.accept(latest);
			}
			errorListener.accept(e.getMessage());
			log.debug("Market query failed", e);
		}
	}

	private long tierFallbackIntervalMs()
	{
		if (entitlements != null && entitlements.getRefreshIntervalMs() > 0)
		{
			return entitlements.getRefreshIntervalMs();
		}
		return MIN_REFRESH_SECONDS * 1000L;
	}

	static long computeNextRefreshAtMs(
		MarketQueryResponse.Meta meta,
		long publishLeadMs,
		long fallbackIntervalMs,
		long nowMs
	)
	{
		return ClientSchedule.computeNextFetchAtMs(meta, publishLeadMs, fallbackIntervalMs, nowMs);
	}

	private void scheduleAlignedRefresh(long targetAtMs)
	{
		cancelAlignedRefresh();
		long delayMs = Math.max(0, targetAtMs - System.currentTimeMillis());
		alignedRefreshTask = executorService.schedule(
			() -> executorService.execute(this::tick),
			delayMs,
			TimeUnit.MILLISECONDS
		);
	}

	private void cancelAlignedRefresh()
	{
		if (alignedRefreshTask != null)
		{
			alignedRefreshTask.cancel(false);
			alignedRefreshTask = null;
		}
	}

	static boolean entitlementsChanged(PluginEntitlements before, PluginEntitlements after)
	{
		if (before == null || after == null)
		{
			return before != after;
		}
		return !Objects.equals(before.getTier(), after.getTier())
			|| before.isPaid() != after.isPaid()
			|| before.isUltra() != after.isUltra()
			|| before.getMaxOpportunities() != after.getMaxOpportunities()
			|| before.getRefreshIntervalMs() != after.getRefreshIntervalMs()
			|| before.getPublishLeadMs() != after.getPublishLeadMs()
			|| before.getSlotOptimizerSlots() != after.getSlotOptimizerSlots()
			|| before.isQuickPresets() != after.isQuickPresets()
			|| before.isAdvancedFilters() != after.isAdvancedFilters();
	}
}
