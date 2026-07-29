package com.osrsflipfinder.runelite;

import javax.annotation.Nullable;

/** Resolves GE chatbox assist prices from wiki-aligned FlipX estimates. */
final class GeAssistPricing
{
	enum PriceSource
	{
		NETWORK,
		WIKI
	}

	static final class ResolvedPrice
	{
		final long priceGp;
		final PriceSource source;

		ResolvedPrice(long priceGp, PriceSource source)
		{
			this.priceGp = priceGp;
			this.source = source;
		}
	}

	private GeAssistPricing()
	{
	}

	@Nullable
	static ResolvedPrice resolve(
		ItemDetailResponse detail,
		boolean buyOffer,
		@Nullable PluginEntitlements entitlements
	)
	{
		if (detail == null || detail.getOpportunity() == null)
		{
			return null;
		}

		FlipOpportunity opp = detail.getOpportunity();
		DisplayPricesResponse dp = opp.getDisplayPrices();
		if (dp != null && dp.getBuyPrice() > 0 && dp.getSellPrice() > 0)
		{
			long price = buyOffer ? dp.getBuyPrice() : dp.getSellPrice();
			return new ResolvedPrice(price, PriceSource.WIKI);
		}

		long wiki = buyOffer ? opp.getEstimatedBuyPrice() : opp.getEstimatedSellPrice();
		if (wiki > 0)
		{
			return new ResolvedPrice(wiki, PriceSource.WIKI);
		}
		return null;
	}

	static long geOfferPriceGp(ResolvedPrice resolved, boolean buyOffer, @Nullable FlipOpportunity opp)
	{
		if (resolved.source == PriceSource.NETWORK)
		{
			return resolved.priceGp;
		}
		if (opp != null)
		{
			long estBuy = opp.getEstimatedBuyPrice();
			long estSell = opp.getEstimatedSellPrice();
			if (estBuy > 0 && estSell > estBuy)
			{
				if (buyOffer)
				{
					long suggested = GeOfferPriceStrategy.suggestedBuyOfferGp(opp);
					if (suggested > 0)
					{
						return suggested;
					}
				}
				else
				{
					long suggested = GeOfferPriceStrategy.suggestedSellOfferGp(opp);
					if (suggested > 0)
					{
						return suggested;
					}
				}
			}
		}
		if (buyOffer)
		{
			return resolved.priceGp + 1;
		}
		return Math.max(1, resolved.priceGp - 1);
	}

	static long geOfferPriceGp(ResolvedPrice resolved, boolean buyOffer)
	{
		return geOfferPriceGp(resolved, buyOffer, null);
	}

	static String priceLineLabel(boolean buyOffer, ResolvedPrice resolved)
	{
		return priceLineLabel(buyOffer, resolved, null);
	}

	static String priceLineLabel(boolean buyOffer, ResolvedPrice resolved, @Nullable FlipOpportunity opp)
	{
		long offerGp = geOfferPriceGp(resolved, buyOffer, opp);
		String side = buyOffer ? "buy" : "sell";
		if (resolved.source == PriceSource.NETWORK)
		{
			return String.format("FlipX %s (network): %,d gp", side, offerGp);
		}
		return String.format("FlipX %s: %,d gp", side, offerGp);
	}
}
