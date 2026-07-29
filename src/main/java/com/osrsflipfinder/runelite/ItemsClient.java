package com.osrsflipfinder.runelite;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
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
	private final Map<Integer, MemoryEntry> memory = new ConcurrentHashMap<>();
	private final CopyOnWriteArrayList<IntConsumer> updateListeners = new CopyOnWriteArrayList<>();

	private volatile long refreshIntervalMs = MIN_TTL_MS;
	private volatile long publishLeadMs = 500L;
	private volatile long nextWikiRefreshAtMs = 0L;
	private final AtomicBoolean itemFetchInProgress = new AtomicBoolean(false);

	@Inject
	ItemsClient(PluginApiClient apiClient, FlipFinderConfig config, LocalCacheStore cacheStore)
	{
		this.apiClient = apiClient;
		this.config = config;
		this.cacheStore = cacheStore;
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

	boolean isItemFetchInProgress()
	{
		return itemFetchInProgress.get();
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
		return System.currentTimeMillis() - entry.fetchedAtMs > refreshIntervalMs;
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

		String cacheKey = "item-" + itemId;
		if (!itemFetchInProgress.compareAndSet(false, true))
		{
			ItemDetailResponse waiting = peek(itemId);
			if (waiting != null)
			{
				return waiting;
			}
			throw new IOException("Item fetch already in progress");
		}
		try
		{
			ItemDetailResponse response = apiClient.get("/api/plugin/items/" + itemId, ItemDetailResponse.class);
			if (response != null)
			{
				long updatedAt = response.getMeta() != null ? response.getMeta().getLastUpdatedMs() : 0L;
				putMemory(itemId, response, updatedAt, true);
				scheduleWikiRefreshFromItemMeta(response.getMeta());
				cacheStore.write(cacheKey, response, config);
			}
			return response;
		}
		catch (IOException ex)
		{
			LocalCacheStore.CachedEntry<ItemDetailResponse> cached = cacheStore.read(cacheKey, ItemDetailResponse.class, config);
			if (cached != null)
			{
				long updatedAt = cached.getPayload().getMeta() != null
					? cached.getPayload().getMeta().getLastUpdatedMs()
					: 0L;
				putMemory(itemId, cached.getPayload(), updatedAt, false);
				return cached.getPayload();
			}
			throw ex;
		}
		finally
		{
			itemFetchInProgress.set(false);
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
			mergeOpportunity(opp, updatedAt);
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

	private void scheduleWikiRefreshFromItemMeta(ItemDetailResponse.ItemDetailMeta meta)
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
		putMemory(opp.getId(), detail, snapshotUpdatedAtMs, true);
	}

	void clearMemory()
	{
		memory.clear();
	}

	private void putMemory(int itemId, ItemDetailResponse detail, long snapshotUpdatedAtMs, boolean notify)
	{
		memory.put(itemId, new MemoryEntry(detail, snapshotUpdatedAtMs));
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

	private static final class MemoryEntry
	{
		private final ItemDetailResponse detail;
		private final long snapshotUpdatedAtMs;
		private final long fetchedAtMs;

		private MemoryEntry(ItemDetailResponse detail, long snapshotUpdatedAtMs)
		{
			this.detail = detail;
			this.snapshotUpdatedAtMs = snapshotUpdatedAtMs;
			this.fetchedAtMs = System.currentTimeMillis();
		}
	}
}
