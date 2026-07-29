package com.osrsflipfinder.runelite;

import lombok.Data;

/** Elite display buy/sell overlay from {@code GET /api/plugin/items/{id}}. */
@Data
public class DisplayPricesResponse
{
	private long buyPrice;
	private long sellPrice;
	private String source;
	private long wikiBuy;
	private long wikiSell;
	private Long networkUpdatedAtMs;
	private long netProfitPerItem;
	private double netRoiPercent;
}
