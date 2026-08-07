package com.osrsflipfinder.runelite;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.concurrent.Executors;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ItemsClientTest
{
	@Before
	public void setUp()
	{
		System.setProperty(FlipXConstants.BASE_URL_PROPERTY, "https://example.test");
	}

	@After
	public void tearDown()
	{
		System.clearProperty(FlipXConstants.BASE_URL_PROPERTY);
	}

	@Test
	public void perItemNextFetchIsIndependent()
	{
		FlipFinderConfig config = mock(FlipFinderConfig.class);
		when(config.apiKey()).thenReturn("osrs_test");
		when(config.enableOfflineCache()).thenReturn(false);

		ItemsClient client = new ItemsClient(
			mock(PluginApiClient.class),
			config,
			new LocalCacheStore(new Gson()),
			Executors.newSingleThreadScheduledExecutor()
		);
		client.setRefreshIntervalMs(60_000L);
		client.setPublishLeadMs(500L);

		long now = System.currentTimeMillis();

		FlipOpportunity soon = new FlipOpportunity();
		soon.setId(22324);
		soon.setName("Ghrazi rapier");
		MergePayload payloadSoon = mergePayload(soon, now, now + 30_000L);
		client.mergeFromMarketResponse(payloadSoon.response);

		FlipOpportunity later = new FlipOpportunity();
		later.setId(4151);
		later.setName("Abyssal whip");
		MergePayload payloadLater = mergePayload(later, now, now + 90_000L);
		client.mergeFromMarketResponse(payloadLater.response);

		long soonAt = client.getNextFetchAtMs(22324);
		long laterAt = client.getNextFetchAtMs(4151);
		assertTrue(soonAt > now);
		assertTrue(laterAt > soonAt);
		assertNotEquals(soonAt, laterAt);
		assertFalse(client.isStale(22324));
		assertFalse(client.isStale(4151));
	}

	@Test
	public void watchItemRegistersForBackgroundRefresh()
	{
		FlipFinderConfig config = mock(FlipFinderConfig.class);
		when(config.apiKey()).thenReturn("osrs_test");
		when(config.enableOfflineCache()).thenReturn(false);

		ItemsClient client = new ItemsClient(
			mock(PluginApiClient.class),
			config,
			new LocalCacheStore(new Gson()),
			Executors.newSingleThreadScheduledExecutor()
		);

		client.watchItem(4151);
		client.watchItem(4151);
		client.unwatchItem(4151);
		client.tickDueRefreshes();
	}

	private static MergePayload mergePayload(FlipOpportunity opp, long updatedAt, long wikiAt)
	{
		MarketQueryResponse response = new MarketQueryResponse();
		response.setOpportunities(Collections.singletonList(opp));
		MarketQueryResponse.Meta meta = new MarketQueryResponse.Meta();
		meta.setLastUpdatedMs(updatedAt);
		meta.setNextWikiPublishAtMs(wikiAt);
		meta.setNextPublishInMs(Math.max(0L, wikiAt - updatedAt));
		meta.setPhaseConfidence(0.9);
		response.setMeta(meta);
		return new MergePayload(response);
	}

	private static final class MergePayload
	{
		private final MarketQueryResponse response;

		private MergePayload(MarketQueryResponse response)
		{
			this.response = response;
		}
	}
}
