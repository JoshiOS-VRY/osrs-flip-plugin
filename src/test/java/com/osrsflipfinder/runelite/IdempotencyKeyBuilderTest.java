package com.osrsflipfinder.runelite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class IdempotencyKeyBuilderTest
{
	@Test
	public void joinsStablePartsWithColonSeparator()
	{
		String key = IdempotencyKeyBuilder.build(
			"abc123",
			4151,
			"buy",
			1_000_000,
			1,
			1,
			"bought",
			"2026-01-15T12:00:00.000Z",
			0
		);

		assertEquals(
			"abc123:4151:buy:1000000:1:1:bought:2026-01-15T12:00:00.000Z:0",
			key
		);
	}

	@Test
	public void usesNegativeOneSlotWhenOmitted()
	{
		String key = IdempotencyKeyBuilder.build(
			"x",
			1,
			"sell",
			100,
			10,
			5,
			"selling",
			"2026-01-15T12:00:00.000Z",
			null
		);

		assertEquals(true, key.endsWith(":-1"));
	}
}
