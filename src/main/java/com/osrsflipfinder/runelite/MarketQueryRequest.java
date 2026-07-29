package com.osrsflipfinder.runelite;

import lombok.Data;

/**
 * Body for {@code POST /api/plugin/market/query}. Null fields are omitted by
 * Gson so the server applies its defaults.
 */
@Data
public class MarketQueryRequest
{
	private String presetId;
	private Integer volumeEmphasis;
	private Integer limit;
	private java.util.List<Sort> sort;
	private MarketFilters filters;

	@Data
	public static class Sort
	{
		private final String id;
		private final boolean desc;
	}

	/** Subset of web {@code UiFilters} the plugin can send. Null = unset. */
	@Data
	public static class MarketFilters
	{
		private String search;
		private String members;
		private Long minEstBuyPrice;
		private Long maxEstBuyPrice;
		private Long minEstSellPrice;
		private Long maxEstSellPrice;
		private Long maxCapital;
		private Long minNetProfit;
		private Double minRoiPercent;
		private Long minTotalProfit;
		private Long minProfit30m;
		private Long minGpPerHour;
		private Double maxFullProfitHours;
		private Long minFiveMinuteVolume;
		private Long minHourlyVolume;
		private Integer minTradableQuantity;
		private Integer minBuyLimit;
		private Integer minScore;
		private Double minConfidenceScore;
		private Boolean hideLowConfidence;
		private Boolean discountOnly;
		private Boolean deepDiscountOnly;
		private Boolean dumpedOnly;
	}
}
