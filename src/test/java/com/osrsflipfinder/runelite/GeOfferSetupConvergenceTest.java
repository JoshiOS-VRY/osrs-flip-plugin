package com.osrsflipfinder.runelite;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GeOfferSetupConvergenceTest
{
	@Test
	public void priceRequiresExactMatch()
	{
		assertTrue(GeOfferSetupConvergence.isPriceDone(5, 5));
		assertFalse(GeOfferSetupConvergence.isPriceDone(5, 6));
	}

	@Test
	public void detectsOscillation()
	{
		assertTrue(GeOfferSetupConvergence.isPriceOscillating(80, 85, 80));
		assertFalse(GeOfferSetupConvergence.isPriceOscillating(80, 85, 86));
	}
}
