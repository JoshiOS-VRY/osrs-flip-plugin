package com.osrsflipfinder.runelite;

import com.google.gson.Gson;
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
