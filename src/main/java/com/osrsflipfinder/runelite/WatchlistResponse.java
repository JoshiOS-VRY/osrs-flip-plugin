package com.osrsflipfinder.runelite;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import lombok.Data;

/** Response of {@code GET /api/plugin/watchlists}. */
@Data
public class WatchlistResponse
{
	private List<Watchlist> watchlists;
	private List<WatchlistItem> items;
	private int maxItems;

	@Data
	public static class Watchlist
	{
		private String id;
		private String name;
	}

	@Data
	public static class WatchlistItem
	{
		private String id;
		@SerializedName("watchlist_id")
		private String watchlistId;
		@SerializedName("item_id")
		private int itemId;
		@SerializedName("item_name")
		private String itemName;
	}
}
