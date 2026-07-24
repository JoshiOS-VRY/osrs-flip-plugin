package com.osrsflipfinder.runelite;

import lombok.Data;

/** Single-item copilot payload from {@code /api/plugin/copilot/*}. */
@Data
public class CopilotItem
{
	private int itemId;
	private String name;
	private long estimatedBuyPrice;
	private long estimatedSellPrice;
	private int opportunityScore;
	private double confidenceScore;
	private long netProfitPerItem;
	private double netRoiPercent;
	private long referenceTradingPrice;
	private Double geGuidePrice;
	private long updatedAt;
	private Long instantBuyPrice;
	private Long instantSellPrice;
	private String repriceHint;
	private String priceIssue;
	private Double deltaPercent;
}
