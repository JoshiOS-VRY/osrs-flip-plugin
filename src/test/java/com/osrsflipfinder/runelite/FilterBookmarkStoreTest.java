package com.osrsflipfinder.runelite;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import net.runelite.client.config.ConfigManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class FilterBookmarkStoreTest
{
	@Mock
	private ConfigManager configManager;

	private FilterBookmarkStore store;

	@Before
	public void setUp()
	{
		store = new FilterBookmarkStore(configManager, new Gson());
		when(configManager.getConfiguration(eq(FlipFinderConfig.GROUP), anyString())).thenReturn(null);
	}

	@Test
	public void createLocalAssignsIdAndPersists()
	{
		FilterBookmark draft = new FilterBookmark();
		draft.setName("Test view");

		FilterBookmark created = store.createLocal("market", draft);

		assertEquals("Test view", created.getName());
		assertEquals("market", created.getContext());
		assertEquals(true, created.isLocalOnly());
	}

	@Test
	public void mergePrefersCloudOnNameCollision()
	{
		FilterBookmark local = bookmark("local:1", "Alpha", true);
		FilterBookmark cloud = bookmark("uuid-1", "alpha", false);

		List<FilterBookmark> merged = store.merge(List.of(local), List.of(cloud));

		assertEquals(1, merged.size());
		assertEquals("uuid-1", merged.get(0).getId());
		assertEquals(false, merged.get(0).isLocalOnly());
	}

	@Test
	public void createLocalEnforcesLimit()
	{
		List<FilterBookmark> existing = new ArrayList<>();
		for (int i = 0; i < 5; i++)
		{
			existing.add(bookmark("local:" + i, "View " + i, true));
		}
		when(configManager.getConfiguration(FlipFinderConfig.GROUP, "bookmarks.market"))
			.thenReturn(new Gson().toJson(existing));

		FilterBookmark draft = new FilterBookmark();
		draft.setName("Sixth");

		assertThrows(IllegalStateException.class, () -> store.createLocal("market", draft));
	}

	private static FilterBookmark bookmark(String id, String name, boolean localOnly)
	{
		FilterBookmark bookmark = new FilterBookmark();
		bookmark.setId(id);
		bookmark.setName(name);
		bookmark.setLocalOnly(localOnly);
		return bookmark;
	}
}
