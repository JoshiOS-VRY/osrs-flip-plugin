package com.osrsflipfinder.runelite;

import org.junit.Assert;
import org.junit.Test;

public class DisplayPriceResolverTest
{
	@Test
	public void prefersDisplayPricesOnOpportunity()
	{
		FlipOpportunity opp = new FlipOpportunity();
		opp.setEstimatedBuyPrice(100);
		opp.setEstimatedSellPrice(110);
		DisplayPricesResponse dp = new DisplayPricesResponse();
		dp.setBuyPrice(99);
		dp.setSellPrice(112);
		dp.setSource("network");
		opp.setDisplayPrices(dp);

		ItemDetailResponse detail = new ItemDetailResponse();
		detail.setOpportunity(opp);

		Assert.assertEquals(99, DisplayPriceResolver.resolveBuyPrice(detail, null));
		Assert.assertEquals(112, DisplayPriceResolver.resolveSellPrice(detail, null));
		Assert.assertEquals(
			GeAssistPricing.PriceSource.NETWORK,
			DisplayPriceResolver.resolveBuySource(detail, null)
		);
	}
}
