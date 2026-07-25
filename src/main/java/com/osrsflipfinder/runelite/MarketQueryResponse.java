package com.osrsflipfinder.runelite;

import java.util.List;
import lombok.Data;

/** Response of {@code POST /api/plugin/market/query}. */
@Data
public class MarketQueryResponse
{
	private List<FlipOpportunity> opportunities;
	private Summary summary;
	private List<FlipOpportunity> movers;
	private Meta meta;

	@Data
	public static class Summary
	{
		private int opportunitiesFound;
		private long highestNetProfit;
		private double highestRoiPercent;
		private int visibleCount;
	}

	@Data
	public static class Meta
	{
		private long lastUpdatedMs;
		private boolean stale;
		private long refreshIntervalMs;
		private String tier;
		private Long publishedAtMs;
		private Long nextPublishInMs;
		private Long publishPeriodMs;
		private double phaseConfidence;
	}
}
