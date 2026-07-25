package com.osrsflipfinder.runelite;

import java.util.Objects;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;

final class OfferSnapshot
{
	private final GrandExchangeOfferState state;
	private final int itemId;
	private final int price;
	private final int quantity;
	private final int quantityFilled;

	private OfferSnapshot(
		GrandExchangeOfferState state,
		int itemId,
		int price,
		int quantity,
		int quantityFilled
	)
	{
		this.state = state;
		this.itemId = itemId;
		this.price = price;
		this.quantity = quantity;
		this.quantityFilled = quantityFilled;
	}

	static OfferSnapshot from(GrandExchangeOffer offer)
	{
		return new OfferSnapshot(
			offer.getState(),
			offer.getItemId(),
			GeOfferPricing.unitPrice(offer),
			offer.getTotalQuantity(),
			offer.getQuantitySold()
		);
	}

	boolean equalsOffer(GrandExchangeOffer offer)
	{
		return state == offer.getState()
			&& itemId == offer.getItemId()
			&& quantity == offer.getTotalQuantity()
			&& quantityFilled == offer.getQuantitySold()
			&& price == GeOfferPricing.unitPrice(offer);
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
		{
			return true;
		}
		if (!(o instanceof OfferSnapshot))
		{
			return false;
		}
		OfferSnapshot that = (OfferSnapshot) o;
		return itemId == that.itemId
			&& price == that.price
			&& quantity == that.quantity
			&& quantityFilled == that.quantityFilled
			&& state == that.state;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(state, itemId, price, quantity, quantityFilled);
	}

	GrandExchangeOfferState getState()
	{
		return state;
	}

	int getItemId()
	{
		return itemId;
	}

	int getPrice()
	{
		return price;
	}

	int getQuantity()
	{
		return quantity;
	}

	int getQuantityFilled()
	{
		return quantityFilled;
	}
}
