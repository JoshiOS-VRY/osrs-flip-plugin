package com.osrsflipfinder.runelite;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Read/sync the user's watchlists via the plugin API. */
@Singleton
public class WatchlistClient
{
	private final PluginApiClient apiClient;
	private final FlipFinderConfig config;
	private final LocalCacheStore cacheStore;

	private volatile List<WatchlistResponse.WatchlistItem> cachedItems = Collections.emptyList();

	@Inject
	WatchlistClient(PluginApiClient apiClient, FlipFinderConfig config, LocalCacheStore cacheStore)
	{
		this.apiClient = apiClient;
		this.config = config;
		this.cacheStore = cacheStore;
	}

	WatchlistResponse fetch() throws IOException
	{
		try
		{
			WatchlistResponse response = apiClient.get("/api/plugin/watchlists", WatchlistResponse.class);
			if (response != null && response.getItems() != null)
			{
				cachedItems = response.getItems();
				cacheStore.write("watchlist", response, config);
			}
			return response;
		}
		catch (IOException ex)
		{
			LocalCacheStore.CachedEntry<WatchlistResponse> cached = cacheStore.read("watchlist", WatchlistResponse.class, config);
			if (cached != null && cached.getPayload().getItems() != null)
			{
				cachedItems = cached.getPayload().getItems();
				return cached.getPayload();
			}
			throw ex;
		}
	}

	List<WatchlistResponse.WatchlistItem> getCachedItems()
	{
		return cachedItems;
	}

	void add(int itemId, String itemName) throws IOException
	{
		apiClient.post(
			"/api/plugin/watchlists/items",
			WatchlistItemRequest.add(itemId, itemName),
			Object.class
		);
	}

	void remove(int itemId) throws IOException
	{
		apiClient.post(
			"/api/plugin/watchlists/items",
			WatchlistItemRequest.remove(itemId),
			Object.class
		);
	}
}
