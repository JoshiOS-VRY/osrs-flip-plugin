package com.osrsflipfinder.runelite;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class GeFlipxPriceAssistTest
{
	@Test
	public void parseBuyOfferSide()
	{
		assertEquals(Boolean.TRUE, GeFlipxPriceAssist.parseBuyOfferSide("Buy offer"));
		assertEquals(Boolean.FALSE, GeFlipxPriceAssist.parseBuyOfferSide("Sell offer"));
		assertNull(GeFlipxPriceAssist.parseBuyOfferSide(""));
		assertNull(GeFlipxPriceAssist.parseBuyOfferSide(null));
	}

	@Test
	public void parseGeOfferChatStep()
	{
		assertEquals(
			GeFlipxPriceAssist.GeOfferChatStep.PRICE,
			GeFlipxPriceAssist.parseGeOfferChatStep("Set a price for each item:")
		);
		assertEquals(
			GeFlipxPriceAssist.GeOfferChatStep.QUANTITY_BUY,
			GeFlipxPriceAssist.parseGeOfferChatStep("How many do you wish to buy?")
		);
		assertEquals(
			GeFlipxPriceAssist.GeOfferChatStep.QUANTITY_SELL,
			GeFlipxPriceAssist.parseGeOfferChatStep("How many do you wish to sell?")
		);
		assertEquals(
			GeFlipxPriceAssist.GeOfferChatStep.NONE,
			GeFlipxPriceAssist.parseGeOfferChatStep("Something else")
		);
	}
}
