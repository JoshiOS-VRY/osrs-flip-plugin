package com.osrsflipfinder.runelite;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GeOfferHistoryCompressorTest
{
	@Test
	public void keepsLatestProgressPerSlotUntilTerminal()
	{
		GeOfferHistoryCompressor compressor = new GeOfferHistoryCompressor();

		IngestGeEvent first = buyProgress("acct", 1, 0, 8, 1);
		IngestGeEvent second = buyProgress("acct", 1, 0, 8, 3);
		IngestGeEvent terminal = bought("acct", 1, 0, 1, 1);

		compressor.compressAndTrack(first);
		compressor.compressAndTrack(second);
		compressor.compressAndTrack(terminal);

		assertTrue(compressor.shouldSkipDuplicateProgress(
			buyProgress("acct", 1, 0, 1, 1)
		));
	}

	@Test
	public void updateForCancelledRemovesStaleCancelRow()
	{
		IngestGeEvent cancelled = IngestGeEvent.builder()
			.accountHash("acct")
			.itemId(1)
			.side("sell")
			.price(100)
			.quantity(5)
			.quantityFilled(1)
			.state("cancelled_sell")
			.slot(2)
			.source("plugin")
			.build();

		IngestGeEvent progress = IngestGeEvent.builder()
			.accountHash("acct")
			.itemId(1)
			.side("sell")
			.price(100)
			.quantity(5)
			.quantityFilled(2)
			.state("selling")
			.slot(2)
			.source("plugin")
			.build();

		assertTrue(GeOfferHistoryCompressor.isUpdateForCancelled(progress, cancelled));
	}

	private static IngestGeEvent buyProgress(
		String accountHash,
		int itemId,
		int slot,
		int total,
		int filled
	)
	{
		return IngestGeEvent.builder()
			.accountHash(accountHash)
			.itemId(itemId)
			.side("buy")
			.price(100)
			.quantity(total)
			.quantityFilled(filled)
			.state("buying")
			.slot(slot)
			.source("plugin")
			.build();
	}

	private static IngestGeEvent bought(
		String accountHash,
		int itemId,
		int slot,
		int total,
		int filled
	)
	{
		return IngestGeEvent.builder()
			.accountHash(accountHash)
			.itemId(itemId)
			.side("buy")
			.price(100)
			.quantity(total)
			.quantityFilled(filled)
			.state("bought")
			.slot(slot)
			.source("plugin")
			.build();
	}
}
