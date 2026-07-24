package com.osrsflipfinder.runelite;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Data;

/** Upload Flipping Utilities CSV or JSON via the plugin API. */
@Singleton
public class ImportClient
{
	private final PluginApiClient apiClient;

	@Inject
	ImportClient(PluginApiClient apiClient)
	{
		this.apiClient = apiClient;
	}

	ImportResult importFlippingUtilitiesJson(String json) throws IOException
	{
		Object body = new JsonParser().parse(json);
		return apiClient.post("/api/plugin/import/flipping-utilities", body, ImportResult.class);
	}

	ImportResult importFlippingUtilitiesCsv(String csv) throws IOException
	{
		JsonObject body = new JsonObject();
		body.addProperty("csv", csv);
		return apiClient.post("/api/plugin/import/flipping-utilities", body, ImportResult.class);
	}

	ImportResult importFile(java.nio.file.Path path) throws IOException
	{
		String content = Files.readString(path, StandardCharsets.UTF_8);
		if (looksLikeJson(content))
		{
			return importFlippingUtilitiesJson(content);
		}
		return importFlippingUtilitiesCsv(content);
	}

	private static boolean looksLikeJson(String content)
	{
		if (content == null)
		{
			return false;
		}
		String trimmed = content.stripLeading();
		return trimmed.startsWith("{") || trimmed.startsWith("[");
	}

	@Data
	public static class ImportResult
	{
		private String batchId;
		private int inserted;
		private int skipped;
		private int skippedRows;
	}
}
