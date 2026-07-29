package com.osrsflipfinder.runelite;

/**
 * Margin-aware GE list prices: aggress enough to fill while keeping a floor on net/item.
 *
 * <p>Replaces flat wiki {@code +1}/{@code -1} when the flip has room to pay up on buys or
 * undercut on sells without destroying the trade.
 */
final class GeOfferPriceStrategy
{
	/** Minimum fraction of reference net to preserve after price aggression. */
	static final double MIN_NET_RETAIN_FRACTION = 0.88;
	/** Max GP we will sacrifice from reference net to improve fill odds. */
	static final double MAX_NET_GIVEUP_FRACTION = 0.40;
	static final long MAX_SLIPPAGE_GP = 50_000_000L;

	private GeOfferPriceStrategy()
	{
	}

	static long suggestedBuyOfferGp(FlipOpportunity opp)
	{
		if (opp == null)
		{
			return 0;
		}
		return suggestedBuyOfferGp(
			opp.getEstimatedBuyPrice(),
			opp.getEstimatedSellPrice(),
			opp.getId()
		);
	}

	static long suggestedSellOfferGp(FlipOpportunity opp)
	{
		if (opp == null)
		{
			return 0;
		}
		return suggestedSellOfferGp(
			opp.getEstimatedBuyPrice(),
			opp.getEstimatedSellPrice(),
			opp.getId()
		);
	}

	static long suggestedBuyOfferGp(long estBuy, long estSell, int itemId)
	{
		if (estBuy <= 0 || estSell <= estBuy)
		{
			return estBuy > 0 ? estBuy + 1 : 0;
		}
		long maxBuy = maxAggressiveBuyGp(estBuy, estSell, itemId);
		return Math.max(estBuy + 1, maxBuy);
	}

	static long suggestedSellOfferGp(long estBuy, long estSell, int itemId)
	{
		if (estBuy <= 0 || estSell <= estBuy)
		{
			return estSell > 0 ? Math.max(1, estSell - 1) : 0;
		}
		long minSell = minAggressiveSellGp(estBuy, estSell, itemId);
		return Math.min(estSell - 1, minSell);
	}

	/** Highest buy price that still meets the minimum acceptable net. */
	static long maxAggressiveBuyGp(long estBuy, long estSell, int itemId)
	{
		if (estBuy <= 0 || estSell <= estBuy)
		{
			return estBuy;
		}
		long referenceNet = OfferPriceAnalyzer.estimateNetPerItem(estBuy, estSell, itemId);
		if (referenceNet <= 0)
		{
			return estBuy;
		}
		long minNet = minimumAcceptableNet(referenceNet);
		long slippage = slippageBudgetGp(referenceNet);
		long ceiling = safeAddCap(estBuy, slippage);
		return highestBuyWithMinNet(estBuy, estSell, itemId, minNet, ceiling);
	}

	/** Lowest sell price that still meets the minimum acceptable net. */
	static long minAggressiveSellGp(long estBuy, long estSell, int itemId)
	{
		if (estBuy <= 0 || estSell <= estBuy)
		{
			return estSell;
		}
		long referenceNet = OfferPriceAnalyzer.estimateNetPerItem(estBuy, estSell, itemId);
		if (referenceNet <= 0)
		{
			return estSell;
		}
		long minNet = minimumAcceptableNet(referenceNet);
		long slippage = slippageBudgetGp(referenceNet);
		long floor = Math.max(1, estSell - slippage);
		return lowestSellWithMinNet(estBuy, estSell, itemId, minNet, floor);
	}

	static long minimumAcceptableNet(long referenceNet)
	{
		if (referenceNet <= 0)
		{
			return 1;
		}
		return Math.max(1, Math.round(referenceNet * MIN_NET_RETAIN_FRACTION));
	}

	static long slippageBudgetGp(long referenceNet)
	{
		if (referenceNet <= 0)
		{
			return 1;
		}
		long fromMargin = Math.round(referenceNet * MAX_NET_GIVEUP_FRACTION);
		return Math.min(MAX_SLIPPAGE_GP, Math.max(1, fromMargin));
	}

	private static long highestBuyWithMinNet(
		long estBuy,
		long estSell,
		int itemId,
		long minNet,
		long ceiling
	)
	{
		long lo = estBuy;
		long hi = Math.max(estBuy, ceiling);
		long best = estBuy;
		while (lo <= hi)
		{
			long mid = lo + (hi - lo) / 2;
			long net = OfferPriceAnalyzer.estimateNetPerItem(mid, estSell, itemId);
			if (net >= minNet)
			{
				best = mid;
				lo = mid + 1;
			}
			else
			{
				hi = mid - 1;
			}
		}
		return best;
	}

	private static long lowestSellWithMinNet(
		long estBuy,
		long estSell,
		int itemId,
		long minNet,
		long floor
	)
	{
		long lo = Math.min(floor, estSell);
		long hi = estSell;
		long best = estSell;
		while (lo <= hi)
		{
			long mid = lo + (hi - lo) / 2;
			long net = OfferPriceAnalyzer.estimateNetPerItem(estBuy, mid, itemId);
			if (net >= minNet)
			{
				best = mid;
				hi = mid - 1;
			}
			else
			{
				lo = mid + 1;
			}
		}
		return Math.max(1, best);
	}

	private static long safeAddCap(long base, long delta)
	{
		if (delta <= 0)
		{
			return base;
		}
		if (base > Long.MAX_VALUE - delta)
		{
			return Long.MAX_VALUE;
		}
		return base + delta;
	}
}
