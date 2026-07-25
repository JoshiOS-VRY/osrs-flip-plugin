package com.osrsflipfinder.runelite;

import net.runelite.api.GrandExchangeOffer;

/**
 * Resolves per-item trade price for P&amp;L. {@link GrandExchangeOffer#getPrice()} is the
 * player's limit price on the offer form; instant fills execute at the opposing book
 * (see RuneLite GE plugin: {@code trade.getSpent() / trade.getQty()}).
 */
final class GeOfferPricing
{
	private GeOfferPricing()
	{
	}

	static int unitPrice(GrandExchangeOffer offer)
	{
		if (offer == null)
		{
			return 0;
		}

		int filled = offer.getQuantitySold();
		if (filled > 0)
		{
			int spent = offer.getSpent();
			if (spent > 0)
			{
				return spent / filled;
			}
		}

		return offer.getPrice();
	}
}
