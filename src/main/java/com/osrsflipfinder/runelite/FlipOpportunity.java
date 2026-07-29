package com.osrsflipfinder.runelite;

import lombok.Data;

/**
 * Mirrors the web app's {@code FlipOpportunity} (subset used by the plugin UI).
 * Deserialized from {@code /api/plugin/*} responses by Gson.
 */
@Data
public class FlipOpportunity
{
	private int id;
	private String name;
	private boolean members;
	private Integer buyLimit;

	private long estimatedBuyPrice;
	private long estimatedSellPrice;
	private long netProfitPerItem;
	private double netRoiPercent;

	private int opportunityScore;
	private double confidenceScore;

	private long estimatedProfitAtQuantity;
	private long estimatedProfit30m;
	/** API may send fractional GP/hr from turnover math; Gson needs double, not long. */
	private double estimatedProfitPerHour;
	private long estimatedCapitalRequired;
	private double estimatedTurnoverHours;
	private long estimatedTradableQuantity;

	private long fiveMinuteVolume;
	private long oneHourVolume;

	private Double referenceTradingPrice;
	private Double geGuidePrice;
	private boolean isPriceDumped;
	private Double edgeScore;
	private DisplayPricesResponse displayPrices;
}
