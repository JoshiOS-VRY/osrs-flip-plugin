package com.osrsflipfinder.runelite;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import net.runelite.client.config.ConfigManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PluginApiClientTest
{
	private MockWebServer server;
	private ConfigManager configManager;
	private PluginApiClient apiClient;

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

		configManager = mock(ConfigManager.class);
		apiClient = new PluginApiClient(new OkHttpClient(), config, configManager, new Gson());
	}

	@After
	public void tearDown() throws IOException
	{
		System.clearProperty(FlipXConstants.BASE_URL_PROPERTY);
		server.shutdown();
	}

	@Test
	public void getParsesJson() throws IOException, InterruptedException
	{
		server.enqueue(new MockResponse()
			.setResponseCode(200)
			.setBody("{\"tier\":\"ultra\",\"isUltra\":true,\"maxOpportunities\":500}"));

		PluginEntitlements entitlements = apiClient.get("/api/plugin/entitlements", PluginEntitlements.class);

		assertEquals("ultra", entitlements.getTier());
		assertEquals(500, entitlements.getMaxOpportunities());
		assertEquals("Bearer osrs_test", server.takeRequest().getHeader("Authorization"));
	}

	@Test
	public void marketOpportunityRequiresWholeGpForLongFields() throws IOException
	{
		Gson gson = new Gson();
		String fractional30m =
			"{\"opportunities\":[{\"id\":1,\"name\":\"Test\",\"members\":true,"
				+ "\"estimatedBuyPrice\":1000,\"estimatedSellPrice\":1100,\"netProfitPerItem\":100,"
				+ "\"netRoiPercent\":10,\"opportunityScore\":50,\"confidenceScore\":0.9,"
				+ "\"estimatedProfitAtQuantity\":1000,\"estimatedProfit30m\":103.5,"
				+ "\"estimatedProfitPerHour\":207.4,\"estimatedCapitalRequired\":10000,"
				+ "\"estimatedTurnoverHours\":1.2,\"estimatedTradableQuantity\":10,"
				+ "\"fiveMinuteVolume\":20,\"oneHourVolume\":100}]}";
		try
		{
			gson.fromJson(fractional30m, MarketQueryResponse.class);
			fail("Expected JsonSyntaxException for fractional estimatedProfit30m");
		}
		catch (JsonSyntaxException expected)
		{
			// Gson maps long fields strictly — API must send whole GP.
		}

		String wholeGp =
			"{\"opportunities\":[{\"id\":1,\"name\":\"Test\",\"members\":true,"
				+ "\"estimatedBuyPrice\":1000,\"estimatedSellPrice\":1100,\"netProfitPerItem\":100,"
				+ "\"netRoiPercent\":10,\"opportunityScore\":50,\"confidenceScore\":0.9,"
				+ "\"estimatedProfitAtQuantity\":1000,\"estimatedProfit30m\":104,"
				+ "\"estimatedProfitPerHour\":207.4,\"estimatedCapitalRequired\":10000,"
				+ "\"estimatedTurnoverHours\":1.2,\"estimatedTradableQuantity\":10,"
				+ "\"fiveMinuteVolume\":20,\"oneHourVolume\":100}]}";
		MarketQueryResponse parsed = gson.fromJson(wholeGp, MarketQueryResponse.class);
		assertEquals(104L, parsed.getOpportunities().get(0).getEstimatedProfit30m());
	}

	@Test
	public void unauthorizedClearsKeyAndSignalsRepair()
	{
		server.enqueue(new MockResponse().setResponseCode(401).setBody("{\"error\":\"Invalid API key\"}"));

		try
		{
			apiClient.get("/api/plugin/entitlements", PluginEntitlements.class);
			fail("Expected PluginApiException");
		}
		catch (PluginApiException e)
		{
			assertEquals(PluginState.REPAIR_REQUIRED, e.getState());
			verify(configManager).unsetConfiguration(FlipFinderConfig.GROUP, "apiKey");
		}
		catch (IOException e)
		{
			fail("Expected PluginApiException, got " + e);
		}
	}

	@Test
	public void forbiddenSignalsUpgradeWithMessage()
	{
		server.enqueue(new MockResponse()
			.setResponseCode(403)
			.setBody("{\"error\":\"upgrade_required\"}"));

		try
		{
			apiClient.get("/api/plugin/market/query", MarketQueryResponse.class);
			fail("Expected PluginApiException");
		}
		catch (PluginApiException e)
		{
			assertEquals(PluginState.UPGRADE_REQUIRED, e.getState());
			assertEquals("upgrade_required", e.getMessage());
		}
		catch (IOException e)
		{
			fail("Expected PluginApiException, got " + e);
		}
	}
}
