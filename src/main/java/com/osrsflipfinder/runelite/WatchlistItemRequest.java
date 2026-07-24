package com.osrsflipfinder.runelite;

import lombok.Data;

/** Body for {@code POST /api/plugin/watchlists/items}. */
@Data
public class WatchlistItemRequest
{
	private final String action;
	private final int itemId;
	private String itemName;
	private String watchlistId;

	static WatchlistItemRequest add(int itemId, String itemName)
	{
		WatchlistItemRequest request = new WatchlistItemRequest("add", itemId);
		request.itemName = itemName;
		return request;
	}

	static WatchlistItemRequest remove(int itemId)
	{
		return new WatchlistItemRequest("remove", itemId);
	}
}
