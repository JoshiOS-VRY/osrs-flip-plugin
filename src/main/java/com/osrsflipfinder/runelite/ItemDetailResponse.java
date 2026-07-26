package com.osrsflipfinder.runelite;

import java.util.List;
import lombok.Data;

/** Response of {@code GET /api/plugin/items/{id}}. */
@Data
public class ItemDetailResponse
{
	private FlipOpportunity opportunity;
	private ItemDetailMeta meta;
	private List<PriceSnapshot> snapshots;
	private MarketRegimeSummary marketRegime;
	private NetworkIntelResponse networkIntel;

	@Data
	public static class MarketRegimeSummary
	{
		private String state;
		private String headline;
		private String warning;
	}

	@Data
	public static class ItemDetailMeta
	{
		private int chartDays;
		private long lastUpdatedMs;
		private Long nextPublishInMs;
		private Long nextWikiPublishAtMs;
		private double phaseConfidence;
	}

	@Data
	public static class PriceSnapshot
	{
		private String capturedAt;
		private long estimatedBuy;
		private long estimatedSell;
		private Integer opportunityScore;
	}
}
