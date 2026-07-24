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
}
