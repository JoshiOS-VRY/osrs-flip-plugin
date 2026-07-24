package com.osrsflipfinder.runelite;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;

@Singleton
public class FilterBookmarkStore
{
	private static final int LOCAL_LIMIT = 5;
	private static final Type LIST_TYPE = new TypeToken<List<FilterBookmark>>() {}.getType();

	private final ConfigManager configManager;
	private final Gson gson;

	@Inject
	FilterBookmarkStore(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson;
	}

	List<FilterBookmark> listLocal(String context)
	{
		String raw = configManager.getConfiguration(FlipFinderConfig.GROUP, configKey(context));
		if (raw == null || raw.isBlank())
		{
			return new ArrayList<>();
		}
		try
		{
			List<FilterBookmark> parsed = gson.fromJson(raw, LIST_TYPE);
			return parsed != null ? parsed : new ArrayList<>();
		}
		catch (RuntimeException e)
		{
			return new ArrayList<>();
		}
	}

	void saveLocal(String context, List<FilterBookmark> bookmarks)
	{
		configManager.setConfiguration(
			FlipFinderConfig.GROUP,
			configKey(context),
			gson.toJson(bookmarks)
		);
	}

	FilterBookmark createLocal(String context, FilterBookmark draft)
	{
		List<FilterBookmark> existing = listLocal(context);
		if (existing.size() >= LOCAL_LIMIT)
		{
			throw new IllegalStateException("bookmark_limit");
		}
		for (FilterBookmark bookmark : existing)
		{
			if (bookmark.getName().equalsIgnoreCase(draft.getName()))
			{
				throw new IllegalStateException("bookmark_name_taken");
			}
		}
		draft.setId("local:" + UUID.randomUUID());
		draft.setContext(context);
		draft.setLocalOnly(true);
		existing.add(draft);
		saveLocal(context, existing);
		return draft;
	}

	void deleteLocal(String context, String id)
	{
		List<FilterBookmark> next = new ArrayList<>();
		for (FilterBookmark bookmark : listLocal(context))
		{
			if (!id.equals(bookmark.getId()))
			{
				next.add(bookmark);
			}
		}
		saveLocal(context, next);
	}

	List<FilterBookmark> merge(List<FilterBookmark> local, List<FilterBookmark> cloud)
	{
		Map<String, FilterBookmark> byName = new LinkedHashMap<>();
		for (FilterBookmark bookmark : local)
		{
			byName.put(bookmark.getName().toLowerCase(), bookmark);
		}
		for (FilterBookmark bookmark : cloud)
		{
			bookmark.setLocalOnly(false);
			byName.put(bookmark.getName().toLowerCase(), bookmark);
		}
		return new ArrayList<>(byName.values());
	}

	private static String configKey(String context)
	{
		return "bookmarks." + context;
	}
}
