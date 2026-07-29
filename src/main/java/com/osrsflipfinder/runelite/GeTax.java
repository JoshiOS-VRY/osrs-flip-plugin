package com.osrsflipfinder.runelite;

import java.util.Set;

/** GE sales tax — keep in sync with FlipX web {@code geTaxPerItem}. */
final class GeTax
{
	private static final double GE_TAX_RATE = 0.02;
	static final long GE_TAX_CAP = 5_000_000L;

	private static final Set<Integer> TAX_EXEMPT_ITEM_IDS = Set.of(
		13190,
		1755,
		5325,
		1785,
		2347,
		1733,
		233,
		5341,
		8794,
		5329,
		5343,
		952,
		1735,
		5331
	);

	private GeTax()
	{
	}

	static boolean isTaxExempt(int itemId)
	{
		return TAX_EXEMPT_ITEM_IDS.contains(itemId);
	}

	static long geTaxPerItem(long sellPrice, int itemId)
	{
		if (sellPrice < 0 || isTaxExempt(itemId))
		{
			return 0;
		}
		return Math.min((long) Math.floor(sellPrice * GE_TAX_RATE), GE_TAX_CAP);
	}

	/** Minimum sell list price where net per item is ≥ 0 after tax. */
	static long breakEvenSellPrice(long buyPrice, int itemId)
	{
		if (buyPrice <= 0)
		{
			return -1;
		}
		if (isTaxExempt(itemId))
		{
			return buyPrice;
		}

		long low = buyPrice;
		long high = buyPrice + GE_TAX_CAP + Math.max(buyPrice, 1L);
		while (low < high)
		{
			long mid = (low + high) / 2;
			long net = mid - buyPrice - geTaxPerItem(mid, itemId);
			if (net >= 0)
			{
				high = mid;
			}
			else
			{
				low = mid + 1;
			}
		}
		return low;
	}
}
