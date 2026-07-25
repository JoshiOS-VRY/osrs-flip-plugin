package com.osrsflipfinder.runelite;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** GP and percent formatting for the sidebar - full integers with thousands separators. */
final class MarketFormat
{
	private static final DateTimeFormatter UPDATED_CLOCK =
		DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
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

	/** Compact GP for in-game overlays (e.g. 43.0M). */
	static String gpCompact(long value)
	{
		return gpCompactSigned(value, false);
	}

	static String gpCompactSigned(long value)
	{
		return gpCompactSigned(value, true);
	}

	private static String gpCompactSigned(long value, boolean showPlus)
	{
		long abs = Math.abs(value);
		String prefix = value < 0 ? "-" : (showPlus && value > 0 ? "+" : "");
		if (abs >= 1_000_000_000)
		{
			return prefix + String.format("%.2fB", abs / 1_000_000_000.0);
		}
		if (abs >= 1_000_000)
		{
			return prefix + String.format("%.1fM", abs / 1_000_000.0);
		}
		if (abs >= 10_000)
		{
			return prefix + String.format("%.0fK", abs / 1_000.0);
		}
		return signedGp(value);
	}

	static String signedGp(long value)
	{
		return (value > 0 ? "+" : "") + gp(value);
	}

	static String percent(double value)
	{
		return String.format("%.1f%%", value);
	}

	/** Web-style quantity vs GE buy limit, e.g. {@code 1 / 8} or {@code 2 / -}. */
	static String qtyLimit(long quantity, Integer buyLimit)
	{
		if (buyLimit == null)
		{
			return quantity + " / -";
		}
		return quantity + " / " + buyLimit;
	}

	/** Local clock for price snapshot freshness (aligned with web item detail bar). */
	static String updatedClock(long lastUpdatedMs)
	{
		if (lastUpdatedMs <= 0)
		{
			return "—";
		}
		return UPDATED_CLOCK.format(Instant.ofEpochMilli(lastUpdatedMs));
	}
}
