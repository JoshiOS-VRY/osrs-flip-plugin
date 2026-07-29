package com.osrsflipfinder.runelite;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class GeOfferPriceStrategyTest
{
	@Test
	public void wideMarginAllowsMeaningfulBuySlippage()
	{
		long buy = 500_000L;
		long sell = 980_000L;
		long maxBuy = GeOfferPriceStrategy.maxAggressiveBuyGp(buy, sell, 0);
		assertTrue(maxBuy >= 520_000L);
		long suggested = GeOfferPriceStrategy.suggestedBuyOfferGp(buy, sell, 0);
		assertEquals(maxBuy, suggested);
	}

	@Test
	public void wideMarginBuyWithinBandIsNotOverbid()
	{
		FlipOpportunity opp = new FlipOpportunity();
		opp.setEstimatedBuyPrice(500_000L);
		opp.setEstimatedSellPrice(980_000L);
		opp.setNetProfitPerItem(OfferPriceAnalyzer.estimateNetPerItem(500_000L, 980_000L));

		OfferPriceAnalyzer.Analysis analysis = OfferPriceAnalyzer.analyze(true, 520_000L, opp, 1.0);
		assertNull(analysis.issue);
	}

	@Test
	public void thinMarginStaysNearPlusOne()
	{
		long buy = 100L;
		long sell = 120L;
		long suggested = GeOfferPriceStrategy.suggestedBuyOfferGp(buy, sell, 0);
		assertTrue(suggested >= 101L);
		assertTrue(suggested <= 120L);
	}
}
