package com.osrsflipfinder.runelite;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
class BuyLimitClient
{
	private static final long CACHE_TTL_MS = 20_000L;

	private final PluginApiClient apiClient;
	private final FlipFinderConfig config;

	private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

	@Inject
	BuyLimitClient(PluginApiClient apiClient, FlipFinderConfig config)
	{
		this.apiClient = apiClient;
		this.config = config;
	}

	BuyLimitRemaining peek(String accountHash, int itemId)
	{
		CacheEntry entry = cache.get(cacheKey(accountHash, itemId));
		if (entry == null || entry.isStale())
		{
			return null;
		}
		return entry.payload;
	}

	BuyLimitRemaining fetch(String accountHash, int itemId) throws IOException
	{
		if (config.apiKey() == null || config.apiKey().isBlank())
		{
			return null;
		}
		String encAccount = URLEncoder.encode(accountHash, StandardCharsets.UTF_8.name());
		String path = "/api/plugin/portfolio/buy-limit?account=" + encAccount + "&itemId=" + itemId;
		BuyLimitRemaining response = apiClient.get(path, BuyLimitRemaining.class);
		if (response != null)
		{
			cache.put(cacheKey(accountHash, itemId), new CacheEntry(response));
		}
		return response;
	}

	private static String cacheKey(String accountHash, int itemId)
	{
		return accountHash + ":" + itemId;
	}

	private static final class CacheEntry
	{
		private final BuyLimitRemaining payload;
		private final long fetchedAtMs = System.currentTimeMillis();

		private CacheEntry(BuyLimitRemaining payload)
		{
			this.payload = payload;
		}

		private boolean isStale()
		{
			return System.currentTimeMillis() - fetchedAtMs > CACHE_TTL_MS;
		}
	}
}
