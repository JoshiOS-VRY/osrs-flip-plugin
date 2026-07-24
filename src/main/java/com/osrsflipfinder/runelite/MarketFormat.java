package com.osrsflipfinder.runelite;

/** GP and percent formatting for the sidebar — full integers with thousands separators. */
final class MarketFormat
{
	private MarketFormat()
	{
	}

	static String gp(long value)
	{
		return gpExact(value);
	}

	static String gpExact(long value)
	{
		return String.format("%,d", value);
	}

	static String signedGp(long value)
	{
		return (value > 0 ? "+" : "") + gp(value);
	}

	static String percent(double value)
	{
		return String.format("%.1f%%", value);
	}

	/** Web-style quantity vs GE buy limit, e.g. {@code 1 / 8} or {@code 2 / —}. */
	static String qtyLimit(long quantity, Integer buyLimit)
	{
		if (buyLimit == null)
		{
			return quantity + " / —";
		}
		return quantity + " / " + buyLimit;
	}
}
