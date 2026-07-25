package com.osrsflipfinder.runelite;

import static org.junit.Assert.assertEquals;

import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
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
		when(offer.getPrice()).thenReturn(1);

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
		when(offer.getPrice()).thenReturn(7_507);

		assertEquals(6_881, GeOfferPricing.unitPrice(offer));
	}
}
