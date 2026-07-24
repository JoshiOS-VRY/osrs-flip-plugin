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

	@Data
	public static class ItemDetailMeta
	{
		private int chartDays;
		private long lastUpdatedMs;
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
