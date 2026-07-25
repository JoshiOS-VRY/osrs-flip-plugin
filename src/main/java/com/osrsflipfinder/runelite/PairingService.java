package com.osrsflipfinder.runelite;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

@Slf4j
@Singleton
public class PairingService
{
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

	private final OkHttpClient httpClient;
	private final Gson gson;

	@Inject
	PairingService(OkHttpClient httpClient, Gson gson)
	{
		this.httpClient = httpClient;
		this.gson = gson;
	}

	@Value
	public static class PairingResult
	{
		String apiKey;
		String message;
		boolean networkIntelligenceOptedIn;
		boolean welcomeProGranted;
	}

	public PairingResult pair(String code, String deviceId, String label) throws IOException
	{
		JsonObject body = new JsonObject();
		body.addProperty("code", code.trim());
		body.addProperty("deviceId", deviceId);
		body.addProperty("label", label == null || label.isBlank() ? "RuneLite" : label.trim());

		Request request = new Request.Builder()
			.url(FlipXConstants.baseUrl() + "/api/devices/pair")
			.post(RequestBody.create(JSON, gson.toJson(body)))
			.build();

		try (Response response = httpClient.newCall(request).execute())
		{
			ResponseBody responseBody = response.body();
			String raw = responseBody != null ? responseBody.string() : "";

			if (!response.isSuccessful())
			{
				String message = parseErrorMessage(raw, "Pairing failed (" + response.code() + ")");
				throw new IOException(message);
			}

			JsonObject parsed = new JsonParser().parse(raw).getAsJsonObject();
			if (!parsed.has("apiKey"))
			{
				throw new IOException("Pairing response missing apiKey");
			}

			boolean networkOptedIn = parsed.has("networkIntelligence")
				&& parsed.getAsJsonObject("networkIntelligence").has("optedIn")
				&& parsed.getAsJsonObject("networkIntelligence").get("optedIn").getAsBoolean();
			boolean welcomePro = parsed.has("networkIntelligence")
				&& parsed.getAsJsonObject("networkIntelligence").has("welcomeProGranted")
				&& parsed.getAsJsonObject("networkIntelligence").get("welcomeProGranted").getAsBoolean();

			return new PairingResult(
				parsed.get("apiKey").getAsString(),
				parsed.has("message") ? parsed.get("message").getAsString() : "Paired successfully",
				networkOptedIn,
				welcomePro
			);
		}
	}

	static String normalizeBaseUrl(String baseUrl)
	{
		return FlipXConstants.normalizeBaseUrl(baseUrl);
	}

	public void revokeSelf(String apiKey) throws IOException
	{
		Request request = new Request.Builder()
			.url(FlipXConstants.baseUrl() + "/api/devices/revoke-self")
			.addHeader("Authorization", "Bearer " + apiKey)
			.post(RequestBody.create(JSON, "{}"))
			.build();

		try (Response response = httpClient.newCall(request).execute())
		{
			if (!response.isSuccessful())
			{
				ResponseBody responseBody = response.body();
				String raw = responseBody != null ? responseBody.string() : "";
				throw new IOException(parseErrorMessage(raw, "Revoke failed (" + response.code() + ")"));
			}
		}
	}

	private static String parseErrorMessage(String raw, String fallback)
	{
		try
		{
			JsonObject parsed = new JsonParser().parse(raw).getAsJsonObject();
			if (parsed.has("error"))
			{
				String error = parsed.get("error").getAsString();
				if ("upgrade_required".equals(error))
				{
					return "FlipX could not verify your subscription. Confirm Pro+ on the web app, generate a new code, and connect again.";
				}
				return error;
			}
		}
		catch (RuntimeException ignored)
		{
			// fall through
		}
		return fallback;
	}
}
