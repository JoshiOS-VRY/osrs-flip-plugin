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

	@Test
	public void decayItemMetaUsesAbsoluteWikiTime()
	{
		long storedAt = 1_000_000L;
		long now = storedAt + 10_000L;
		ItemDetailResponse.ItemDetailMeta meta = new ItemDetailResponse.ItemDetailMeta();
		meta.setNextWikiPublishAtMs(now + 30_000L);
		meta.setNextPublishInMs(40_000L);
		meta.setPhaseConfidence(0.9);

		ItemDetailResponse.ItemDetailMeta adjusted =
			ClientSchedule.decayItemMeta(meta, storedAt, now);

		assertEquals(30_000L, (long) adjusted.getNextPublishInMs());
		assertEquals(40_000L, (long) meta.getNextPublishInMs());
	}

	@Test
	public void decayItemMetaSubtractsCacheAge()
	{
		long storedAt = 1_000_000L;
		long now = storedAt + 12_000L;
		ItemDetailResponse.ItemDetailMeta meta = new ItemDetailResponse.ItemDetailMeta();
		meta.setNextPublishInMs(45_000L);
		meta.setPhaseConfidence(0.9);

		ItemDetailResponse.ItemDetailMeta adjusted =
			ClientSchedule.decayItemMeta(meta, storedAt, now);

		assertEquals(33_000L, (long) adjusted.getNextPublishInMs());
	}
}
