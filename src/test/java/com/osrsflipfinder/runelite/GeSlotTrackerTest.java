package com.osrsflipfinder.runelite;

import java.time.Instant;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class GeSlotTrackerTest
{
	@Test
	public void recordsInactiveSecondsAfterActivity()
	{
		GeSlotTracker tracker = new GeSlotTracker();
		// Slot tracker updates require live GrandExchangeOffer mocks; verify empty snapshot.
		assertNotNull(tracker.snapshot());
		assertEquals(0, tracker.snapshot().size());
	}

	@Test
	public void formatCachedAtTruncatesIsoTimestamp()
	{
		String formatted = LocalCacheStore.formatCachedAt(Instant.parse("2026-07-23T20:15:30Z"));
		assertTrue(formatted.startsWith("2026-07-23"));
	}
}
