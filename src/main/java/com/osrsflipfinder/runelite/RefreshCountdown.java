package com.osrsflipfinder.runelite;

/** Formats countdown text aligned with {@code formatNextRefreshInlineLabel} on web. */
final class RefreshCountdown
{
	private RefreshCountdown()
	{
	}

	static String formatPolling(
		long nextRefreshAtMs,
		boolean pollingActive,
		boolean fetchInProgress
	)
	{
		if (!pollingActive)
		{
			return "Refresh paused (switch to this tab to resume)";
		}
		if (fetchInProgress)
		{
			return "Refreshing…";
		}
		if (nextRefreshAtMs <= 0)
		{
			return "Next refresh in 0s";
		}
		long remainingMs = ClientSchedule.msUntilNextFetch(
			nextRefreshAtMs,
			System.currentTimeMillis()
		);
		long sec = formatNextRefreshCountdownSeconds(remainingMs);
		return String.format("Next refresh in %ds", sec);
	}

	/** Matches {@code formatNextRefreshCountdownSeconds} in client-schedule.ts. */
	static long formatNextRefreshCountdownSeconds(long remainingMs)
	{
		return Math.max(0, (remainingMs + 999) / 1000);
	}

	static String combine(String primary, String secondary)
	{
		boolean hasPrimary = primary != null && !primary.isBlank();
		boolean hasSecondary = secondary != null && !secondary.isBlank();
		if (hasPrimary && hasSecondary)
		{
			return primary + " | " + secondary;
		}
		if (hasPrimary)
		{
			return primary;
		}
		if (hasSecondary)
		{
			return secondary;
		}
		return " ";
	}
}
