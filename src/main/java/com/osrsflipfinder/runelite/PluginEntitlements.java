package com.osrsflipfinder.runelite;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

/** Response of {@code GET /api/plugin/entitlements}. */
@Data
public class PluginEntitlements
{
	private String tier;
	@SerializedName("isPaid")
	private boolean isPaid;
	@SerializedName("isUltra")
	private boolean isUltra;
	@SerializedName("isElite")
	private boolean isElite;
	@SerializedName("isTrialing")
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
	private boolean pluginSync;
	private int watchlistMaxItems;
	private int itemChartDays;
	private String crowdSaturation;
	private String fillProbability;
	private boolean portfolio;
	private boolean recipeFlips;
	private Integer historyRetentionDays;
	private Integer maxLifetimeImports;
	private boolean portfolioAdvancedTabs;
	private long portfolioRefreshIntervalMs;
	private boolean cloudSync;

	/** Pro subscription or active trial — hide upgrade CTAs. */
	public boolean hasProAccess()
	{
		if (isPaid || isTrialing)
		{
			return true;
		}
		if (tier == null || tier.isBlank())
		{
			return false;
		}
		String normalized = tier.toLowerCase();
		return "pro".equals(normalized)
			|| "ultra".equals(normalized)
			|| "elite".equals(normalized);
	}
}
