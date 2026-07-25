package com.osrsflipfinder.runelite;

import lombok.Data;

/** Response of {@code GET /api/plugin/entitlements}. */
@Data
public class PluginEntitlements
{
	private String tier;
	private boolean isPaid;
	private boolean isUltra;
	private boolean isElite;
	private boolean isTrialing;
	private String trialEndsAt;
	private Integer trialDaysRemaining;
	private int maxOpportunities;
	private long refreshIntervalMs;
	private long publishLeadMs;
	private int slotOptimizerSlots;
	private boolean quickPresets;
	private boolean advancedFilters;
	private boolean volumeEmphasis;
	private boolean copilotApi;
	private int watchlistMaxItems;
	private int itemChartDays;
	private boolean portfolio;
	private boolean recipeFlips;
	private long portfolioRefreshIntervalMs;
	private boolean cloudSync;
}
