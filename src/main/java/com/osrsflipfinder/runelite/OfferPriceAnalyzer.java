package com.osrsflipfinder.runelite;

/** Display-only reprice hints derived from market vs offer price. */
final class OfferPriceAnalyzer
{
	private OfferPriceAnalyzer()
	{
	}

	static String repriceHint(
		boolean buySide,
		long offerPrice,
		long marketBuy,
		long marketSell,
		double thresholdPercent
	)
	{
		if (buySide)
		{
			if (marketBuy <= 0)
			{
				return null;
			}
			double delta = ((offerPrice - marketBuy) / (double) marketBuy) * 100;
			if (delta > thresholdPercent)
			{
				return "Hint: buy closer to " + MarketFormat.gp(marketBuy) + " (currently overbid)";
			}
			if (delta < -thresholdPercent)
			{
				return "Hint: raise toward " + MarketFormat.gp(marketBuy) + " for faster fill";
			}
			return "Buy price near market";
		}

		if (marketSell <= 0)
		{
			return null;
		}
		double delta = ((offerPrice - marketSell) / (double) marketSell) * 100;
		if (delta < -thresholdPercent)
		{
			return "Hint: sell closer to " + MarketFormat.gp(marketSell) + " (currently undercut)";
		}
		if (delta > thresholdPercent)
		{
			return "Hint: lower toward " + MarketFormat.gp(marketSell) + " for faster fill";
		}
		return "Sell price near market";
	}
}
