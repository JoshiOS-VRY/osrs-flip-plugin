package com.osrsflipfinder.runelite;

import java.util.List;
import lombok.Data;

/** Response of {@code GET /api/plugin/items/search}. */
@Data
public class ItemSearchResponse
{
	private List<ItemSearchHit> items;

	@Data
	public static class ItemSearchHit
	{
		private int id;
		private String name;
		private String icon;
		private boolean members;
		private Long instantBuy;
		private Long instantSell;
		private Long margin;
		private int volume1h;
	}
}
