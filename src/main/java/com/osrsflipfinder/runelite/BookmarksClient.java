package com.osrsflipfinder.runelite;

import com.google.gson.annotations.SerializedName;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Data;

@Singleton
public class BookmarksClient
{
	private final PluginApiClient apiClient;
	private final FilterBookmarkStore store;

	@Inject
	BookmarksClient(PluginApiClient apiClient, FilterBookmarkStore store)
	{
		this.apiClient = apiClient;
		this.store = store;
	}

	List<FilterBookmark> listMerged(String context, boolean cloudSync)
	{
		List<FilterBookmark> local = store.listLocal(context);
		if (!apiClient.isConfigured() || !cloudSync)
		{
			return local;
		}
		try
		{
			BookmarksResponse response = apiClient.get(
				"/api/plugin/bookmarks?context=" + context,
				BookmarksResponse.class
			);
			if (response == null || response.bookmarks == null || !response.cloudSync)
			{
				return local;
			}
			return store.merge(local, response.bookmarks);
		}
		catch (IOException e)
		{
			return local;
		}
	}

	FilterBookmark save(String context, FilterBookmark draft, boolean cloudSync)
		throws IOException, PluginApiException
	{
		if (apiClient.isConfigured() && cloudSync)
		{
			CreateBookmarkRequest body = CreateBookmarkRequest.from(draft, context);
			CreateBookmarkResponse response = apiClient.post(
				"/api/plugin/bookmarks",
				body,
				CreateBookmarkResponse.class
			);
			if (response != null && response.bookmark != null)
			{
				return response.bookmark;
			}
		}
		return store.createLocal(context, draft);
	}

	void delete(String context, FilterBookmark bookmark, boolean cloudSync)
		throws IOException, PluginApiException
	{
		if (bookmark.getId() != null && bookmark.getId().startsWith("local:"))
		{
			store.deleteLocal(context, bookmark.getId());
			return;
		}
		if (apiClient.isConfigured() && cloudSync && bookmark.getId() != null)
		{
			apiClient.delete("/api/plugin/bookmarks/" + bookmark.getId());
		}
	}

	@Data
	static class BookmarksResponse
	{
		private List<FilterBookmark> bookmarks;
		private boolean cloudSync;
	}

	@Data
	static class CreateBookmarkRequest
	{
		private String context;
		private String name;
		private String presetId;
		private MarketQueryRequest.MarketFilters filters;
		private List<MarketQueryRequest.Sort> sort;
		private Integer volumeEmphasis;

		static CreateBookmarkRequest from(FilterBookmark draft, String context)
		{
			CreateBookmarkRequest req = new CreateBookmarkRequest();
			req.context = context;
			req.name = draft.getName();
			req.presetId = draft.getPresetId();
			req.filters = draft.getFilters();
			req.sort = draft.getSort() != null ? draft.getSort() : Collections.emptyList();
			req.volumeEmphasis = draft.getVolumeEmphasis();
			return req;
		}
	}

	@Data
	static class CreateBookmarkResponse
	{
		private FilterBookmark bookmark;
	}
}
