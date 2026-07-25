package com.osrsflipfinder.runelite;

public final class IdempotencyKeyBuilder
{
	private IdempotencyKeyBuilder()
	{
	}

	public static String build(
		String accountHash,
		int itemId,
		String side,
		int price,
		int quantity,
		int quantityFilled,
		String state,
		String occurredAt,
		Integer slot
	)
	{
		int slotValue = slot != null ? slot : -1;
		return accountHash + ':'
			+ itemId + ':'
			+ side + ':'
			+ price + ':'
			+ quantity + ':'
			+ quantityFilled + ':'
			+ state + ':'
			+ occurredAt + ':'
			+ slotValue;
	}

	/** Stable key for RuneLite GE tradeHistory backfill (no timestamp in key). */
	public static String importHistory(
		String accountHash,
		int itemId,
		String side,
		int price,
		int quantity,
		String state,
		long epochSecond
	)
	{
		return "import:runelite-ge:"
			+ accountHash + ':'
			+ itemId + ':'
			+ side + ':'
			+ price + ':'
			+ quantity + ':'
			+ state + ':'
			+ epochSecond;
	}
}
