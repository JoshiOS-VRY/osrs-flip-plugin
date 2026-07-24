package com.osrsflipfinder.runelite;

import lombok.Data;

/** One row from {@code GET /api/plugin/analytics/items}. */
@Data
class ItemPerformanceRow
{
	private int itemId;
	private String itemName;
	private int flipCount;
	private long totalProfit;
	private int totalQuantity;
	private double avgRoiPercent;
}
