package com.osrsflipfinder.runelite;

import javax.annotation.Nullable;

/** Wiki-aligned buy/sell resolution for overlay and GE assist. */
final class DisplayPriceResolver
{
	private DisplayPriceResolver()
	{
	}

	static long resolveBuyPrice(@Nullable ItemDetailResponse detail, @Nullable PluginEntitlements entitlements)
	{
		return resolveSide(detail, true);
	}

	static long resolveSellPrice(@Nullable ItemDetailResponse detail, @Nullable PluginEntitlements entitlements)
	{
		return resolveSide(detail, false);
	}

	static GeAssistPricing.PriceSource resolveBuySource(
		@Nullable ItemDetailResponse detail,
		@Nullable PluginEntitlements entitlements
	)
	{
		return resolveSideSource(detail, true);
	}

	static GeAssistPricing.PriceSource resolveSellSource(
		@Nullable ItemDetailResponse detail,
		@Nullable PluginEntitlements entitlements
	)
	{
		return resolveSideSource(detail, false);
	}

	private static long resolveSide(ItemDetailResponse detail, boolean buy)
	{
		if (detail == null || detail.getOpportunity() == null)
		{
			return 0;
		}
		FlipOpportunity opp = detail.getOpportunity();
		DisplayPricesResponse dp = opp.getDisplayPrices();
		if (dp != null && dp.getBuyPrice() > 0 && dp.getSellPrice() > 0)
		{
			return buy ? dp.getBuyPrice() : dp.getSellPrice();
		}
		return buy ? opp.getEstimatedBuyPrice() : opp.getEstimatedSellPrice();
	}

	private static GeAssistPricing.PriceSource resolveSideSource(ItemDetailResponse detail, boolean buy)
	{
		if (detail == null || detail.getOpportunity() == null)
		{
			return GeAssistPricing.PriceSource.WIKI;
		}
		return GeAssistPricing.PriceSource.WIKI;
	}

	static long resolveNetProfit(ItemDetailResponse detail)
	{
		if (detail == null || detail.getOpportunity() == null)
		{
			return 0;
		}
		DisplayPricesResponse dp = detail.getOpportunity().getDisplayPrices();
		if (dp != null)
		{
			return dp.getNetProfitPerItem();
		}
		return detail.getOpportunity().getNetProfitPerItem();
	}

	static double resolveNetRoi(ItemDetailResponse detail)
	{
		if (detail == null || detail.getOpportunity() == null)
		{
			return 0;
		}
		DisplayPricesResponse dp = detail.getOpportunity().getDisplayPrices();
		if (dp != null)
		{
			return dp.getNetRoiPercent();
		}
		return detail.getOpportunity().getNetRoiPercent();
	}

	static String overlayBuyLabel(GeAssistPricing.PriceSource source)
	{
		return "Buy";
	}

	static String overlaySellLabel(GeAssistPricing.PriceSource source)
	{
		return "Sell";
	}
}
