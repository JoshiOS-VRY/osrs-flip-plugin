package com.osrsflipfinder.runelite;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class OfferPriceAnalyzerTest
{
	private static final long STAGNATION_SEC = 15 * 60;

	private static FlipOpportunity opp(long buy, long sell)
	{
		FlipOpportunity o = new FlipOpportunity();
		o.setEstimatedBuyPrice(buy);
		o.setEstimatedSellPrice(sell);
		o.setNetProfitPerItem(OfferPriceAnalyzer.estimateNetPerItem(buy, sell));
		return o;
	}

	@Test
	public void waitsBeforeRepriceWhenOverbidAndNotStagnant()
	{
		OfferPriceAnalyzer.Analysis analysis = OfferPriceAnalyzer.analyze(
			true,
			1100,
			opp(1000, 1200),
			1,
			false,
			60,
			0,
			8,
			STAGNATION_SEC
		);
		assertEquals(OfferPriceAnalyzer.Issue.BUY_OVERBID, analysis.issue);
		assertEquals(OfferPriceAnalyzer.Action.WAIT, analysis.action);
		assertEquals(1000L, analysis.recommendedPrice);
	}

	@Test
	public void repricesWhenOverbidAndStagnant()
	{
		OfferPriceAnalyzer.Analysis analysis = OfferPriceAnalyzer.analyze(
			true,
			1100,
			opp(1000, 1200),
			1,
			true,
			1200,
			0,
			8,
			STAGNATION_SEC
		);
		assertEquals(OfferPriceAnalyzer.Action.REPRICE_BUY, analysis.action);
		assertEquals(1000L, analysis.recommendedPrice);
	}

	@Test
	public void waitsWhenUndercutAndNotStagnant()
	{
		OfferPriceAnalyzer.Analysis analysis = OfferPriceAnalyzer.analyze(
			false,
			900,
			opp(800, 1000),
			1,
			false,
			30,
			0,
			8,
			STAGNATION_SEC
		);
		assertEquals(OfferPriceAnalyzer.Issue.SELL_UNDERCUT, analysis.issue);
		assertEquals(OfferPriceAnalyzer.Action.WAIT, analysis.action);
		assertEquals(1000L, analysis.recommendedPrice);
	}

	@Test
	public void repricesWhenUndercutAndStagnant()
	{
		OfferPriceAnalyzer.Analysis analysis = OfferPriceAnalyzer.analyze(
			false,
			900,
			opp(800, 1000),
			1,
			true,
			1000,
			0,
			8,
			STAGNATION_SEC
		);
		assertEquals(OfferPriceAnalyzer.Action.REPRICE_SELL, analysis.action);
	}

	@Test
	public void waitsOnBadMarginUntilLongIdle()
	{
		OfferPriceAnalyzer.Analysis analysis = OfferPriceAnalyzer.analyze(
			true,
			1500,
			opp(1000, 1100),
			1,
			false,
			120,
			0,
			8,
			STAGNATION_SEC
		);
		assertEquals(OfferPriceAnalyzer.Action.WAIT, analysis.action);
		assertTrue(analysis.actionLine.toLowerCase().contains("wait"));
	}

	@Test
	public void suggestsExitOnlyAfterExtendedIdleWithLockedLoss()
	{
		OfferPriceAnalyzer.Analysis analysis = OfferPriceAnalyzer.analyze(
			true,
			1500,
			opp(1000, 1100),
			1,
			true,
			STAGNATION_SEC * 2 + 60,
			0,
			8,
			STAGNATION_SEC
		);
		assertEquals(OfferPriceAnalyzer.Action.ABORT_FLIP, analysis.action);
		assertTrue(analysis.actionLine.toLowerCase().contains("consider"));
	}

	@Test
	public void noIssueWhenWithinThreshold()
	{
		OfferPriceAnalyzer.Analysis analysis = OfferPriceAnalyzer.analyze(
			true,
			1005,
			opp(1000, 1050),
			1
		);
		assertNull(analysis.issue);
		assertEquals(OfferPriceAnalyzer.Action.HOLD, analysis.action);
	}
}
