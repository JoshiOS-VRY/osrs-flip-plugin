package com.osrsflipfinder.runelite;

import static org.junit.Assert.assertEquals;

import net.runelite.api.GrandExchangeOffer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class GeOfferPricingTest
{
	@Test
	public void usesAverageFillWhenQuantitySoldPositive()
	{
		GrandExchangeOffer offer = mock(GrandExchangeOffer.class);
		when(offer.getQuantitySold()).thenReturn(1);
		when(offer.getSpent()).thenReturn(6_709);

		assertEquals(6_709, GeOfferPricing.unitPrice(offer));
	}

	@Test
	public void usesLimitPriceWhenNothingFilledYet()
	{
		GrandExchangeOffer offer = mock(GrandExchangeOffer.class);
		when(offer.getQuantitySold()).thenReturn(0);
		when(offer.getPrice()).thenReturn(7_507);

		assertEquals(7_507, GeOfferPricing.unitPrice(offer));
	}

	@Test
	public void instantBuyUsesSpentNotHighLimit()
	{
		GrandExchangeOffer offer = mock(GrandExchangeOffer.class);
		when(offer.getQuantitySold()).thenReturn(1);
		when(offer.getSpent()).thenReturn(6_881);

		assertEquals(6_881, GeOfferPricing.unitPrice(offer));
	}

	@Test
	public void effectiveBuyUsesFillAverageWhenPartiallyFilled()
	{
		assertEquals(
			6_810,
			GeOfferPricing.effectiveBuyForNet(7_507, 6_800, 1, 6_810)
		);
	}

	@Test
	public void effectiveBuyUsesMarketWhenHighLimitUnfilled()
	{
		assertEquals(
			6_800,
			GeOfferPricing.effectiveBuyForNet(7_507, 6_800, 0, 0)
		);
	}

	@Test
	public void effectiveSellUsesFillAverageNotLowLimit()
	{
		assertEquals(
			6_710,
			GeOfferPricing.effectiveSellForNet(1, 6_700, 1, 6_710)
		);
	}
}
