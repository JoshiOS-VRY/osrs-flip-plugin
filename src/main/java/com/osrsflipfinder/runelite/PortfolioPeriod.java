package com.osrsflipfinder.runelite;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Portfolio time filters aligned with FlipX web ({@code portfolio-periods.ts}). */
enum PortfolioPeriod
{
	SESSION("session", "Current session"),
	DAY_1("1d", "Last 24 hours"),
	WEEK_1("1w", "Last 7 days"),
	MONTH_1("1m", "Last 30 days"),
	YEAR_1("1y", "Last 365 days"),
	ALL("all", "All time");

	static final PortfolioPeriod DEFAULT = SESSION;

	private static final PortfolioPeriod[] VALUES = values();
	static final String[] LABELS = buildLabels();

	private final String id;
	private final String label;

	PortfolioPeriod(String id, String label)
	{
		this.id = id;
		this.label = label;
	}

	String getId()
	{
		return id;
	}

	String getLabel()
	{
		return label;
	}

	boolean isLiveSession()
	{
		return this == SESSION;
	}

	String heroProfitTitle()
	{
		return isLiveSession() ? "Session P&L" : "Net profit";
	}

	String breakdownSectionTitle()
	{
		if (isLiveSession())
		{
			return "Session breakdown";
		}
		return label + " breakdown";
	}

	String topItemsSectionTitle()
	{
		if (isLiveSession())
		{
			return "Top items this session";
		}
		return "Top items · " + label.toLowerCase();
	}

	String heroTimeLabel()
	{
		return isLiveSession() ? "Session time" : "Flip time";
	}

	static PortfolioPeriod fromId(String raw)
	{
		if (raw == null || raw.isBlank())
		{
			return DEFAULT;
		}
		for (PortfolioPeriod period : VALUES)
		{
			if (period.id.equals(raw))
			{
				return period;
			}
		}
		return DEFAULT;
	}

	static int indexOf(PortfolioPeriod period)
	{
		for (int i = 0; i < VALUES.length; i++)
		{
			if (VALUES[i] == period)
			{
				return i;
			}
		}
		return 0;
	}

	static PortfolioPeriod fromIndex(int index)
	{
		if (index < 0 || index >= VALUES.length)
		{
			return DEFAULT;
		}
		return VALUES[index];
	}

	/** Query string for {@code /api/plugin/session} and analytics (without leading {@code ?}). */
	String toApiQuery()
	{
		StringBuilder q = new StringBuilder("account=all&period=").append(urlEncode(id));
		if (isLiveSession())
		{
			q.append("&days=30&liveSession=1");
			return q.toString();
		}

		java.time.Instant to = java.time.Instant.now();
		java.time.Instant from = null;
		int days = 30;
		if (this == DAY_1)
		{
			from = to.minus(java.time.Duration.ofDays(1));
			days = 1;
		}
		else if (this == WEEK_1)
		{
			from = to.minus(java.time.Duration.ofDays(7));
			days = 7;
		}
		else if (this == MONTH_1)
		{
			from = to.minus(java.time.Duration.ofDays(30));
			days = 30;
		}
		else if (this == YEAR_1)
		{
			from = to.minus(java.time.Duration.ofDays(365));
			days = 365;
		}

		if (this == ALL)
		{
			q.append("&days=3650");
		}
		else if (from != null)
		{
			q.append("&days=").append(days);
			q.append("&from=").append(urlEncode(from.toString()));
			q.append("&to=").append(urlEncode(to.toString()));
		}
		return q.toString();
	}

	String sessionCacheKey()
	{
		return "session-" + id;
	}

	String sessionItemsCacheKey()
	{
		return "session-items-" + id;
	}

	private static String[] buildLabels()
	{
		String[] labels = new String[VALUES.length];
		for (int i = 0; i < VALUES.length; i++)
		{
			labels[i] = VALUES[i].label;
		}
		return labels;
	}

	private static String urlEncode(String value)
	{
		try
		{
			return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
		}
		catch (UnsupportedEncodingException ex)
		{
			return value;
		}
	}
}
