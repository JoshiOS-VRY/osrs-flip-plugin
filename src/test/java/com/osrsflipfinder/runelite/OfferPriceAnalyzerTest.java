package com.osrsflipfinder.runelite;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class OfferPriceAnalyzerTest
{
	@Test
	public void suggestsOverbidHintForBuySide()
	{
		String hint = OfferPriceAnalyzer.repriceHint(true, 1100, 1000, 1050, 1);
		assertNotNull(hint);
		assertTrue(hint.contains("overbid"));
	}

	@Test
	public void suggestsUndercutHintForSellSide()
	{
		String hint = OfferPriceAnalyzer.repriceHint(false, 900, 1000, 1000, 1);
		assertNotNull(hint);
		assertTrue(hint.contains("undercut"));
	}

	@Test
	public void returnsNearMarketWhenWithinThreshold()
	{
		String hint = OfferPriceAnalyzer.repriceHint(true, 1005, 1000, 1050, 1);
		assertEquals("Buy price near market", hint);
	}
}
