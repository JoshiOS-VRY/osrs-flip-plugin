package com.osrsflipfinder.runelite;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RefreshCountdownTest
{
	@Test
	public void formatPollingShowsSecondsUntilNextRefresh()
	{
		long next = System.currentTimeMillis() + 12_500;
		String text = RefreshCountdown.formatPolling(next, true, false);
		assertTrue(text.startsWith("Next refresh in 13s"));
		assertTrue(!text.contains("every"));
	}

	@Test
	public void formatPollingPausedWhenInactive()
	{
		String text = RefreshCountdown.formatPolling(
			System.currentTimeMillis() + 60_000,
			false,
			false
		);
		assertEquals("Refresh paused (switch to this tab to resume)", text);
	}

	@Test
	public void formatPollingRefreshingWhenDue()
	{
		String text = RefreshCountdown.formatPolling(
			System.currentTimeMillis() - 1,
			true,
			false
		);
		assertEquals("Refreshing…", text);
	}

	@Test
	public void combineJoinsNonBlankLines()
	{
		assertEquals("a | b", RefreshCountdown.combine("a", "b"));
		assertEquals("a", RefreshCountdown.combine("a", " "));
		assertEquals(" ", RefreshCountdown.combine(null, null));
	}
}
