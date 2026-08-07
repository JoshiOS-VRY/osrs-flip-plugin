package com.osrsflipfinder.runelite;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Single source of truth for per-item opportunity data in the plugin. Market
 * polls, GE setup, copilot overlay, charts, and item detail all read from this
 * cache so buy/sell/score figures never drift between UI surfaces.
 */
@Slf4j
@Singleton
public class ItemsClient
{
	private static final long MIN_TTL_MS = 15_000L;

	private final PluginApiClient apiClient;
	private final FlipFinderConfig config;
	private final LocalCacheStore cacheStore;
	private final ScheduledExecutorService executorService;
	private final Map<Integer, MemoryEntry> memory = new ConcurrentHashMap<>();
	private final Map<Integer, Boolean> historyLoaded = new ConcurrentHashMap<>();
	private final Map<Integer, AtomicBoolean> fetchLocks = new ConcurrentHashMap<>();
	private final Set<Integer> watchedItemIds = ConcurrentHashMap.newKeySet();
	private final CopyOnWriteArrayList<IntConsumer> updateListeners = new CopyOnWriteArrayList<>();

	private volatile long refreshIntervalMs = MIN_TTL_MS;
	private volatile long publishLeadMs = 500L;
	private volatile long nextWikiRefreshAtMs = 0L;

	@Inject
	ItemsClient(
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

	void setRefreshIntervalMs(long intervalMs)
	{
		this.refreshIntervalMs = Math.max(intervalMs, MIN_TTL_MS);
	}

	void setPublishLeadMs(long publishLeadMs)
	{
		if (publishLeadMs > 0)
		{
			this.publishLeadMs = publishLeadMs;
		}
	}

	long getNextWikiRefreshAtMs()
	{
		return nextWikiRefreshAtMs;
	}

	long getNextFetchAtMs(int itemId)
	{
		long now = System.currentTimeMillis();
		MemoryEntry entry = memory.get(itemId);
		if (entry != null)
		{
			return computeNextFetchAtMs(entry, now);
		}
		return nextWikiRefreshAtMs;
	}

	boolean isItemFetchInProgress()
	{
		for (AtomicBoolean lock : fetchLocks.values())
		{
			if (lock.get())
			{
				return true;
			}
		}
		return false;
	}

	boolean isItemFetchInProgress(int itemId)
	{
		AtomicBoolean lock = fetchLocks.get(itemId);
		return lock != null && lock.get();
	}

	void watchItem(int itemId)
	{
		if (itemId > 0)
		{
			watchedItemIds.add(itemId);
		}
	}

	void unwatchItem(int itemId)
	{
		if (itemId > 0)
		{
			watchedItemIds.remove(itemId);
		}
	}

	void addUpdateListener(IntConsumer listener)
	{
		if (listener != null)
		{
			updateListeners.add(listener);
		}
	}

	ItemDetailResponse peek(int itemId)
	{
		MemoryEntry entry = memory.get(itemId);
		return entry != null ? entry.detail : null;
	}

	FlipOpportunity peekOpportunity(int itemId)
	{
		ItemDetailResponse detail = peek(itemId);
		return detail != null ? detail.getOpportunity() : null;
	}

	CopilotItem toCopilotItem(int itemId)
	{
		return ItemCopilotMapper.from(peek(itemId));
	}

	boolean isStale(int itemId)
	{
		MemoryEntry entry = memory.get(itemId);
		if (entry == null)
		{
			return true;
		}
		long now = System.currentTimeMillis();
		return now >= computeNextFetchAtMs(entry, now);
	}

	/** Background refresh for items the UI is actively watching (GE overlay, item detail). */
	void tickDueRefreshes()
	{
		for (int itemId : watchedItemIds)
		{
			if (isStale(itemId))
			{
				scheduleFetchAsync(itemId, false);
			}
		}
	}

	ItemDetailResponse fetch(int itemId) throws IOException
	{
		return fetch(itemId, false);
	}

	ItemDetailResponse fetch(int itemId, boolean forceRefresh) throws IOException
	{
		if (!forceRefresh && !isStale(itemId))
		{
			ItemDetailResponse cached = peek(itemId);
			if (cached != null)
			{
				return cached;
			}
		}

		AtomicBoolean lock = fetchLocks.computeIfAbsent(itemId, ignored -> new AtomicBoolean(false));
		if (!lock.compareAndSet(false, true))
		{
			ItemDetailResponse waiting = peek(itemId);
			if (waiting != null)
			{
				return waiting;
			}
			throw new IOException("Item fetch already in progress");
		}

		String cacheKey = "item-" + itemId;
		String historyCacheKey = "item-history-" + itemId;
		try
		{
			ItemDetailResponse detail = peek(itemId);
			if (detail == null)
			{
				detail = loadDiskDetail(cacheKey);
			}
			if (detail == null)
			{
				detail = new ItemDetailResponse();
			}

			boolean needsHistory = forceRefresh || !Boolean.TRUE.equals(historyLoaded.get(itemId));
			if (needsHistory)
			{
				String historyPath = forceRefresh
					? "/api/plugin/items/" + itemId + "/history?refresh=1"
					: "/api/plugin/items/" + itemId + "/history";
				ItemDetailHistoryResponse history = apiClient.get(historyPath, ItemDetailHistoryResponse.class);
				if (history != null)
				{
					applyHistory(detail, history);
					historyLoaded.put(itemId, true);
					cacheStore.write(historyCacheKey, history, config);
				}
			}

			ItemDetailLiveResponse live = apiClient.get(
				"/api/plugin/items/" + itemId + "/live",
				ItemDetailLiveResponse.class
			);
			if (live == null || live.getOpportunity() == null)
			{
				throw new IOException("Item not found");
			}

			applyLive(detail, live);

			long updatedAt = detail.getMeta() != null ? detail.getMeta().getLastUpdatedMs() : 0L;
			putMemory(itemId, detail, updatedAt, true);
			cacheStore.write(cacheKey, detail, config);
			return detail;
		}
		catch (IOException ex)
		{
			ItemDetailResponse cached = loadDiskDetail(cacheKey);
			if (cached != null)
			{
				long updatedAt = cached.getMeta() != null
					? cached.getMeta().getLastUpdatedMs()
					: 0L;
				putMemory(itemId, cached, updatedAt, false);
				return cached;
			}
			throw ex;
		}
		finally
		{
			lock.set(false);
		}
	}

	private void scheduleFetchAsync(int itemId, boolean forceRefresh)
	{
		if (isItemFetchInProgress(itemId))
		{
			return;
		}
		executorService.execute(() ->
		{
			try
			{
				fetch(itemId, forceRefresh);
			}
			catch (IOException ex)
			{
				log.debug("Scheduled item refresh failed for {}", itemId, ex);
			}
		});
	}

	private ItemDetailResponse loadDiskDetail(String cacheKey)
	{
		LocalCacheStore.CachedEntry<ItemDetailResponse> cached = cacheStore.read(
			cacheKey,
			ItemDetailResponse.class,
			config
		);
		return cached != null ? cached.getPayload() : null;
	}

	private static void applyHistory(ItemDetailResponse detail, ItemDetailHistoryResponse history)
	{
		detail.setSnapshots(history.getSnapshots());
		detail.setMarketRegime(history.getMarketRegime());
		if (history.getMeta() != null)
		{
			if (detail.getMeta() == null)
			{
				detail.setMeta(history.getMeta());
			}
			else
			{
				detail.getMeta().setChartDays(history.getMeta().getChartDays());
			}
		}
		else if (history.getChartDays() > 0)
		{
			ItemDetailResponse.ItemDetailMeta meta = detail.getMeta();
			if (meta == null)
			{
				meta = new ItemDetailResponse.ItemDetailMeta();
				detail.setMeta(meta);
			}
			meta.setChartDays(history.getChartDays());
		}
	}

	private static void applyLive(ItemDetailResponse detail, ItemDetailLiveResponse live)
	{
		detail.setOpportunity(live.getOpportunity());
		detail.setDisplayPrices(live.getDisplayPrices());
		detail.setNetworkIntel(live.getNetworkIntel());
		if (live.getMeta() != null)
		{
			ItemDetailResponse.ItemDetailMeta meta = detail.getMeta();
			if (meta == null)
			{
				detail.setMeta(live.getMeta());
			}
			else
			{
				meta.setLastUpdatedMs(live.getMeta().getLastUpdatedMs());
				meta.setNextPublishInMs(live.getMeta().getNextPublishInMs());
				meta.setNextWikiPublishAtMs(live.getMeta().getNextWikiPublishAtMs());
				meta.setPhaseConfidence(live.getMeta().getPhaseConfidence());
			}
		}
	}

	List<ItemSearchResponse.ItemSearchHit> searchItems(String query, int limit) throws IOException
	{
		String trimmed = query != null ? query.trim() : "";
		if (trimmed.length() < 2)
		{
			return Collections.emptyList();
		}
		int capped = Math.min(Math.max(limit, 1), 25);
		String encoded = URLEncoder.encode(trimmed, StandardCharsets.UTF_8);
		String path = "/api/plugin/items/search?q=" + encoded + "&limit=" + capped;
		ItemSearchResponse response = apiClient.get(path, ItemSearchResponse.class);
		if (response == null || response.getItems() == null)
		{
			return Collections.emptyList();
		}
		return response.getItems();
	}

	void mergeFromMarketResponse(MarketQueryResponse response)
	{
		if (response == null || response.getOpportunities() == null)
		{
			return;
		}

		long updatedAt = response.getMeta() != null
			? response.getMeta().getLastUpdatedMs()
			: System.currentTimeMillis();

		for (FlipOpportunity opp : response.getOpportunities())
		{
			mergeOpportunity(opp, updatedAt, response.getMeta());
		}

		if (response.getMeta() != null)
		{
			scheduleWikiRefreshFromMarketMeta(response.getMeta());
		}
	}

	void scheduleWikiRefreshFromMarketMeta(MarketQueryResponse.Meta meta)
	{
		long now = System.currentTimeMillis();
		nextWikiRefreshAtMs = ClientSchedule.computeNextFetchAtMs(
			meta,
			publishLeadMs,
			refreshIntervalMs,
			now
		);
	}

	void mergeOpportunity(FlipOpportunity opp, long snapshotUpdatedAtMs)
	{
		mergeOpportunity(opp, snapshotUpdatedAtMs, null);
	}

	private void mergeOpportunity(
		FlipOpportunity opp,
		long snapshotUpdatedAtMs,
		MarketQueryResponse.Meta marketMeta
	)
	{
		if (opp == null)
		{
			return;
		}

		MemoryEntry existing = memory.get(opp.getId());
		if (existing != null && existing.snapshotUpdatedAtMs > snapshotUpdatedAtMs)
		{
			return;
		}

		ItemDetailResponse detail = existing != null ? existing.detail : new ItemDetailResponse();
		detail.setOpportunity(opp);
		ItemDetailResponse.ItemDetailMeta meta = detail.getMeta();
		if (meta == null)
		{
			meta = new ItemDetailResponse.ItemDetailMeta();
			detail.setMeta(meta);
		}
		meta.setLastUpdatedMs(snapshotUpdatedAtMs);
		if (marketMeta != null)
		{
			meta.setNextPublishInMs(marketMeta.getNextPublishInMs());
			meta.setNextWikiPublishAtMs(marketMeta.getNextWikiPublishAtMs());
			meta.setPhaseConfidence(marketMeta.getPhaseConfidence());
		}
		putMemory(opp.getId(), detail, snapshotUpdatedAtMs, true);
	}

	void clearMemory()
	{
		memory.clear();
		historyLoaded.clear();
		watchedItemIds.clear();
		fetchLocks.clear();
		nextWikiRefreshAtMs = 0L;
	}

	private void putMemory(int itemId, ItemDetailResponse detail, long snapshotUpdatedAtMs, boolean notify)
	{
		long now = System.currentTimeMillis();
		MemoryEntry entry = new MemoryEntry(detail, snapshotUpdatedAtMs, now);
		long nextFetchAtMs = computeNextFetchAtMs(entry, now);
		entry = new MemoryEntry(detail, snapshotUpdatedAtMs, now, nextFetchAtMs);
		memory.put(itemId, entry);
		if (nextFetchAtMs > 0)
		{
			if (nextWikiRefreshAtMs <= 0)
			{
				nextWikiRefreshAtMs = nextFetchAtMs;
			}
			else
			{
				nextWikiRefreshAtMs = Math.min(nextWikiRefreshAtMs, nextFetchAtMs);
			}
		}
		if (notify)
		{
			for (IntConsumer listener : updateListeners)
			{
				try
				{
					listener.accept(itemId);
				}
				catch (RuntimeException ex)
				{
					log.debug("Item update listener failed", ex);
				}
			}
		}
	}

	private long computeNextFetchAtMs(MemoryEntry entry, long nowMs)
	{
		ItemDetailResponse detail = entry.detail;
		ItemDetailResponse.ItemDetailMeta meta = detail != null ? detail.getMeta() : null;
		if (meta != null)
		{
			return ClientSchedule.computeNextFetchAtMs(
				ClientSchedule.decayItemMeta(meta, entry.fetchedAtMs, nowMs),
				publishLeadMs,
				refreshIntervalMs,
				nowMs
			);
		}
		return entry.fetchedAtMs + refreshIntervalMs;
	}

	private static final class MemoryEntry
	{
		private final ItemDetailResponse detail;
		private final long snapshotUpdatedAtMs;
		private final long fetchedAtMs;
		private final long nextFetchAtMs;

		private MemoryEntry(ItemDetailResponse detail, long snapshotUpdatedAtMs, long fetchedAtMs)
		{
			this(detail, snapshotUpdatedAtMs, fetchedAtMs, 0L);
		}

		private MemoryEntry(
			ItemDetailResponse detail,
			long snapshotUpdatedAtMs,
			long fetchedAtMs,
			long nextFetchAtMs
		)
		{
			this.detail = detail;
			this.snapshotUpdatedAtMs = snapshotUpdatedAtMs;
			this.fetchedAtMs = fetchedAtMs;
			this.nextFetchAtMs = nextFetchAtMs;
		}
	}
}
