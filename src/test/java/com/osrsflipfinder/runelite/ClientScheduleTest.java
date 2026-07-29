package com.osrsflipfinder.runelite;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ClientScheduleTest
{
	@Test
	public void usesFutureWikiAlignedTime()
	{
		long now = 1_000_000L;
		long wikiAt = now + 20_000L;
		MarketQueryResponse.Meta meta = new MarketQueryResponse.Meta();
		meta.setNextWikiPublishAtMs(wikiAt);
		meta.setNextPublishInMs(20_000L);
		meta.setPhaseConfidence(0.9);

		long at = ClientSchedule.computeNextFetchAtMs(meta, 500L, 60_000L, now);
		assertEquals(wikiAt + 500L, at);
	}

	@Test
	public void fallsBackWhenPublishImminent()
	{
		long now = 1_000_000L;
		MarketQueryResponse.Meta meta = new MarketQueryResponse.Meta();
		meta.setNextPublishInMs(0L);
		meta.setPhaseConfidence(ClientSchedule.PHASE_CONFIDENCE_THRESHOLD);

		long at = ClientSchedule.computeNextFetchAtMs(meta, 500L, 15_000L, now);
		assertEquals(now + 15_000L, at);
	}

	@Test
	public void fallsBackWhenWikiTimeInPast()
	{
		long now = 1_000_000L;
		MarketQueryResponse.Meta meta = new MarketQueryResponse.Meta();
		meta.setNextWikiPublishAtMs(now - 2_000L);
		meta.setNextPublishInMs(0L);
		meta.setPhaseConfidence(0.9);

		long at = ClientSchedule.computeNextFetchAtMs(meta, 500L, 60_000L, now);
		assertEquals(now + 60_000L, at);
	}
}
