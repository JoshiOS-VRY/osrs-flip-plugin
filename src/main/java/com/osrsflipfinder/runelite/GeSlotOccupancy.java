package com.osrsflipfinder.runelite;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Value;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;

/** Live GE slot occupancy from the game client (must run on client thread). */
final class GeSlotOccupancy
{
	static final int GE_SLOT_COUNT = 8;

	@Value
	static class Snapshot
	{
		int totalSlots;
		int occupiedSlots;
		int emptySlots;
		List<Integer> occupiedItemIds;
	}

	static Snapshot read(Client client)
	{
		GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
		int occupied = 0;
		List<Integer> itemIds = new ArrayList<>();
		if (offers != null)
		{
			for (int slot = 0; slot < Math.min(GE_SLOT_COUNT, offers.length); slot++)
			{
				GeSlotSnapshot snap = GeSlotSnapshot.from(slot, offers[slot]);
				if (snap.isEmpty())
				{
					continue;
				}
				occupied++;
				GrandExchangeOffer offer = offers[slot];
				if (offer != null && offer.getItemId() > 0)
				{
					itemIds.add(offer.getItemId());
				}
			}
		}
		int empty = Math.max(0, GE_SLOT_COUNT - occupied);
		return new Snapshot(GE_SLOT_COUNT, occupied, empty, Collections.unmodifiableList(itemIds));
	}

	private GeSlotOccupancy()
	{
	}
}
