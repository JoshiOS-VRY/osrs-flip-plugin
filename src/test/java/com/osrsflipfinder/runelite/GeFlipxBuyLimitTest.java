package com.osrsflipfinder.runelite;

import net.runelite.client.game.ItemStats;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GeFlipxBuyLimitTest
{
	@Test
	public void usesRemainingWhenSynced()
	{
		BuyLimitRemaining synced = new BuyLimitRemaining();
		synced.setBuyLimit(8);
		synced.setQuantityBoughtInWindow(5);
		synced.setRemainingQuantity(3);
		ItemStats stats = mock(ItemStats.class);
		when(stats.getGeLimit()).thenReturn(8);
		assertEquals(3, GeFlipxBuyLimit.quantityToApply(synced, 22324, stats, -1));
	}

	@Test
	public void returnsZeroWhenLimitExhausted()
	{
		BuyLimitRemaining synced = new BuyLimitRemaining();
		synced.setBuyLimit(8);
		synced.setQuantityBoughtInWindow(8);
		synced.setRemainingQuantity(0);
		ItemStats stats = mock(ItemStats.class);
		when(stats.getGeLimit()).thenReturn(8);
		assertEquals(0, GeFlipxBuyLimit.quantityToApply(synced, 22324, stats, -1));
	}

	@Test
	public void prefersHigherBoughtCountFromClientPanel()
	{
		BuyLimitRemaining synced = new BuyLimitRemaining();
		synced.setBuyLimit(8);
		synced.setQuantityBoughtInWindow(0);
		synced.setRemainingQuantity(8);
		ItemStats stats = mock(ItemStats.class);
		when(stats.getGeLimit()).thenReturn(8);
		assertEquals(3, GeFlipxBuyLimit.quantityToApply(synced, 22324, stats, 5));
	}

	@Test
	public void usesFullBuyLimitWhenNothingBoughtInWindow()
	{
		BuyLimitRemaining synced = new BuyLimitRemaining();
		synced.setBuyLimit(8);
		synced.setRemainingQuantity(8);
		assertEquals(8, GeFlipxBuyLimit.quantityToApply(synced, 22324, null, 0));
	}

	@Test
	public void usesClientBoughtWhenApiMissing()
	{
		ItemStats stats = mock(ItemStats.class);
		when(stats.getGeLimit()).thenReturn(8);
		assertEquals(3, GeFlipxBuyLimit.quantityToApply(null, 22324, stats, 5));
	}

	@Test
	public void capsByInventoryCoinsAtOfferPrice()
	{
		ItemStats stats = mock(ItemStats.class);
		when(stats.getGeLimit()).thenReturn(8);
		BuyLimitRemaining synced = new BuyLimitRemaining();
		synced.setBuyLimit(8);
		synced.setRemainingQuantity(8);
		int price = 2_870_885;
		long coins = 22_967_080L;
		assertEquals(8, GeFlipxBuyLimit.quantityToApply(synced, 6733, stats, 0, price, coins));

		assertEquals(2, GeFlipxBuyLimit.quantityToApply(synced, 6733, stats, 0, price, 6_000_000L));

		BuyLimitRemaining partial = new BuyLimitRemaining();
		partial.setBuyLimit(8);
		partial.setQuantityBoughtInWindow(5);
		partial.setRemainingQuantity(3);
		assertEquals(2, GeFlipxBuyLimit.quantityToApply(partial, 6733, stats, 5, price, 6_000_000L));
	}

	@Test
	public void returnsZeroWhenCoinsCannotAffordOneItem()
	{
		ItemStats stats = mock(ItemStats.class);
		when(stats.getGeLimit()).thenReturn(8);
		assertEquals(
			0,
			GeFlipxBuyLimit.quantityToApply(null, 6733, stats, 0, 1_000_000, 500_000)
		);
	}

	@Test
	public void fallsBackToGeLimitWhenApiMissingAndClientUnknown()
	{
		ItemStats stats = mock(ItemStats.class);
		when(stats.getGeLimit()).thenReturn(8);
		assertEquals(8, GeFlipxBuyLimit.quantityToApply(null, 22324, stats, -1));
	}
}
