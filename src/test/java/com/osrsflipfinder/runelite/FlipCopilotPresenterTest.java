package com.osrsflipfinder.runelite;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class FlipCopilotPresenterTest
{
	@Test
	public void overlayAlertLineUsesShortCopy()
	{
		FlipOpportunity opp = new FlipOpportunity();
		opp.setEstimatedBuyPrice(10_000);
		opp.setEstimatedSellPrice(10_050);
		opp.setNetProfitPerItem(-100);
		assertEquals("Loss after tax", FlipCopilotPresenter.overlayAlertLine(opp, 22));
	}

	@Test
	public void overlayAlertLineNullWhenHealthy()
	{
		FlipOpportunity opp = new FlipOpportunity();
		opp.setEstimatedBuyPrice(10_000);
		opp.setEstimatedSellPrice(10_500);
		opp.setNetProfitPerItem(400);
		opp.setEstimatedTurnoverHours(2);
		assertNull(FlipCopilotPresenter.overlayAlertLine(opp, 22));
	}
}
