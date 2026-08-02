package com.osrsflipfinder.runelite;

import com.google.gson.Gson;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import net.runelite.client.config.ConfigManager;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CopilotClientTest
{
	private MockWebServer server;
	private CopilotClient copilotClient;
	private ItemsClient itemsClient;

	@Before
	public void setUp() throws IOException
	{
		server = new MockWebServer();
		server.start();
		System.setProperty(
			FlipXConstants.BASE_URL_PROPERTY,
			server.url("/").toString().replaceAll("/$", "")
		);

		FlipFinderConfig config = mock(FlipFinderConfig.class);
		when(config.apiKey()).thenReturn("osrs_test");
		when(config.enableOfflineCache()).thenReturn(false);

		PluginApiClient apiClient = new PluginApiClient(
			new OkHttpClient(),
			config,
			mock(ConfigManager.class),
			new Gson()
		);
		LocalCacheStore cacheStore = new LocalCacheStore(new Gson());
		itemsClient = new ItemsClient(apiClient, config, cacheStore);
		copilotClient = new CopilotClient(itemsClient);
	}

	@After
	public void tearDown() throws IOException
	{
		System.clearProperty(FlipXConstants.BASE_URL_PROPERTY);
		server.shutdown();
	}

	private static String historyBody()
	{
		return "{\"snapshots\":[],\"chartDays\":7,\"meta\":{\"chartDays\":7}}";
	}

	private static String liveBody(int itemId, String name, int score)
	{
		return "{"
			+ "\"opportunity\":{"
			+ "\"id\":" + itemId + ","
			+ "\"name\":\"" + name + "\","
			+ "\"opportunityScore\":" + score + ","
			+ "\"estimatedBuyPrice\":1000000,"
			+ "\"estimatedSellPrice\":1100000,"
			+ "\"netProfitPerItem\":90000,"
			+ "\"netRoiPercent\":9.0,"
			+ "\"confidenceScore\":0.8"
			+ "},"
			+ "\"meta\":{\"lastUpdatedMs\":1700000000000,\"chartDays\":7}"
			+ "}";
	}

	private void enqueueItemResponses(int itemId, String name, int score)
	{
		server.enqueue(new MockResponse()
			.setResponseCode(200)
			.setBody(historyBody()));
		server.enqueue(new MockResponse()
			.setResponseCode(200)
			.setBody(liveBody(itemId, name, score)));
	}

	@Test
	public void fetchCachesByItemId() throws IOException
	{
		enqueueItemResponses(4151, "Abyssal whip", 72);

		CopilotItem first = copilotClient.fetch(4151);
		CopilotItem second = copilotClient.fetch(4151);

		assertEquals("Abyssal whip", first.getName());
		assertEquals(72, second.getOpportunityScore());
		assertEquals(2, server.getRequestCount());
	}

	@Test
	public void fetchBulkPopulatesCache() throws IOException
	{
		enqueueItemResponses(4151, "Abyssal whip", 72);
		enqueueItemResponses(11802, "Armadyl godsword", 65);

		List<CopilotItem> items = copilotClient.fetchBulk(Arrays.asList(4151, 11802));

		assertEquals(2, items.size());
		assertEquals(4, server.getRequestCount());
		assertEquals("Abyssal whip", copilotClient.fetch(4151).getName());
		assertEquals(4, server.getRequestCount());
	}
}
