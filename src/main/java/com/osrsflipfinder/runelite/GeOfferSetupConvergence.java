package com.osrsflipfinder.runelite;

/** When GE quantity/price targets are reached. */
final class GeOfferSetupConvergence
{
	private GeOfferSetupConvergence()
	{
	}

	static boolean isQuantityDone(int current, int target)
	{
		return current == target;
	}

	static boolean isPriceDone(int current, int target)
	{
		return current == target;
	}

	/**
	 * @deprecated exact price uses meslayer; kept for unit tests
	 */
	@Deprecated
	static boolean isPriceOscillating(int prev, int last, int now)
	{
		return prev > 0 && prev == now && last != now;
	}
}
