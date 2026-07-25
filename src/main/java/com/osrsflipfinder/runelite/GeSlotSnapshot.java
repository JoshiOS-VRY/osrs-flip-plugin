package com.osrsflipfinder.runelite;

import lombok.Value;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;

@Value
class GeSlotSnapshot
{
	int slot;
	boolean empty;

	static GeSlotSnapshot from(int slot, GrandExchangeOffer offer)
	{
		if (offer == null)
		{
			return new GeSlotSnapshot(slot, true);
		}
		GrandExchangeOfferState state = offer.getState();
		// BOUGHT/SOLD slots still occupy the GE until collected - not empty.
		boolean occupied = state == GrandExchangeOfferState.BUYING
			|| state == GrandExchangeOfferState.SELLING
			|| state == GrandExchangeOfferState.BOUGHT
			|| state == GrandExchangeOfferState.SOLD;
		return new GeSlotSnapshot(slot, !occupied);
	}
}
