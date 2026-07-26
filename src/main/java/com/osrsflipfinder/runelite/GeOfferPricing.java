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

	static int limitPrice(GrandExchangeOffer offer)
	{
		return offer == null ? 0 : offer.getPrice();
	}

	/**
	 * Gross sell price for net P&amp;L when the offer may instant-fill above a low limit.
	 */
	static long effectiveSellForNet(
		long limitPrice,
		long marketSellEstimate,
		int quantityFilled,
		int unitFillPrice
	)
	{
		if (quantityFilled > 0 && unitFillPrice > 0)
		{
			return unitFillPrice;
		}
		if (marketSellEstimate > 0 && limitPrice > 0 && limitPrice < marketSellEstimate)
		{
			return marketSellEstimate;
		}
		return limitPrice;
	}

	/**
	 * Buy price for net P&amp;L when the offer may instant-fill below a high limit.
	 */
	static long effectiveBuyForNet(
		long limitPrice,
		long marketBuyEstimate,
		int quantityFilled,
		int unitFillPrice
	)
	{
		if (quantityFilled > 0 && unitFillPrice > 0)
		{
			return unitFillPrice;
		}
		if (marketBuyEstimate > 0 && limitPrice > marketBuyEstimate)
		{
			return marketBuyEstimate;
		}
		return limitPrice;
	}
}
