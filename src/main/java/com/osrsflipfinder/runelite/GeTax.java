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
}
