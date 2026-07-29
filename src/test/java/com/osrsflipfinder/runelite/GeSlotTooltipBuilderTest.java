package com.osrsflipfinder.runelite;

import net.runelite.api.GrandExchangeOfferState;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class GeSlotTooltipBuilderTest
{
	@Test
	public void geHoverTooltipIncludesFlipXAndScore()
	{
		FlipOpportunity opp = new FlipOpportunity();
		opp.setId(4151);
		opp.setName("Abyssal whip");
		opp.setNetProfitPerItem(5_000);
		opp.setNetRoiPercent(0.5);
		opp.setOpportunityScore(72);
		opp.setConfidenceScore(0.82);
		opp.setEstimatedBuyPrice(1_000_000);
		opp.setEstimatedSellPrice(1_050_000);
		opp.setEstimatedProfitPerHour(120_000);

		OfferPriceAnalyzer.Analysis analysis = OfferPriceAnalyzer.analyze(
			false,
			1_040_000,
			opp,
			OfferPriceAnalyzer.DEFAULT_THRESHOLD_PERCENT,
			false,
			0,
			0,
			3,
			600,
			0
		);

		GeSlotTooltipBuilder.GeSlotTooltipContext ctx = new GeSlotTooltipBuilder.GeSlotTooltipContext(
			"Abyssal whip",
			GrandExchangeOfferState.SELLING,
			false,
			1_040_000,
			0,
			0,
			3,
			1_020_000,
			opp,
			analysis,
			false,
			0
		);

		String html = GeSlotTooltipBuilder.buildGeSlotHoverTooltip(ctx);
		assertNotNull(html);
		assertTrue(html.contains("FlipX"));
		assertTrue(html.contains("72/100"));
		assertTrue(html.contains("GP/hr"));

		String overlay = GeSlotTooltipBuilder.formatForRuneliteOverlay(html);
		assertNotNull(overlay);
		assertTrue(!overlay.contains("<html>"));
		assertTrue(!overlay.contains("</html>"));
		assertTrue(overlay.startsWith("FlipX"));
	}
}
