package com.osrsflipfinder.runelite;

import lombok.Data;

/** One GE slot recommendation from {@code POST /api/plugin/slots/optimize}. */
@Data
public class SlotRecommendation
{
	private int slotNumber;
	private int itemId;
	private String itemName;
	private long capitalRequired;
	private double profitPerSlotHour;
	private double turnoverHours;
	private int opportunityScore;
	private long netProfitPerItem;
	private long estimatedBuyPrice;
	private long estimatedSellPrice;
	private double netRoiPercent;
	private long estimatedProfitAtQuantity;
	private long estimatedTradableQuantity;
	private Integer buyLimit;
}
