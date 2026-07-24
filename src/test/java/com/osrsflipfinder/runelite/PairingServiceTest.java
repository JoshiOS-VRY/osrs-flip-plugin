package com.osrsflipfinder.runelite;

import com.google.gson.Gson;
import java.io.IOException;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class PairingServiceTest
{
	private MockWebServer server;
	private PairingService pairingService;

	@Before
	public void setUp() throws IOException
	{
		server = new MockWebServer();
		server.start();
		System.setProperty(
			FlipXConstants.BASE_URL_PROPERTY,
			server.url("/").toString().replaceAll("/$", "")
		);
		pairingService = new PairingService(new OkHttpClient(), new Gson());
	}

	@After
	public void tearDown() throws IOException
	{
		System.clearProperty(FlipXConstants.BASE_URL_PROPERTY);
		server.shutdown();
	}

	@Test
	public void pairReturnsApiKey() throws IOException
	{
		server.enqueue(new MockResponse()
			.setResponseCode(200)
			.setBody("{\"apiKey\":\"test-key-123\",\"message\":\"ok\"}"));

		PairingService.PairingResult result = pairingService.pair(
			"123456",
			"device-uuid",
			"RuneLite"
		);

		assertEquals("test-key-123", result.getApiKey());
	}

	@Test
	public void pairSurfacesErrorMessage() throws IOException
	{
		server.enqueue(new MockResponse()
			.setResponseCode(400)
			.setBody("{\"error\":\"Invalid or expired code\"}"));

		try
		{
			pairingService.pair("000000", "d", "RuneLite");
			fail("Expected IOException");
		}
		catch (IOException e)
		{
			assertEquals("Invalid or expired code", e.getMessage());
		}
	}

	@Test
	public void revokeSelfSucceeds() throws IOException
	{
		server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"success\":true}"));

		pairingService.revokeSelf("test-key");
	}
}
