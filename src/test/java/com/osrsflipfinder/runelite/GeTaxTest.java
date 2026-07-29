package com.osrsflipfinder.runelite;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GeTaxTest
{
	@Test
	public void breakEvenSellPriceMatchesWebExamples()
	{
		assertEquals(9_999L, GeTax.breakEvenSellPrice(9_800, 4151));
		assertEquals(9_795L, GeTax.breakEvenSellPrice(9_600, 4151));
	}

	@Test
	public void breakEvenSellPriceForExemptItem()
	{
		assertEquals(1_000_000L, GeTax.breakEvenSellPrice(1_000_000, 13190));
	}
}
