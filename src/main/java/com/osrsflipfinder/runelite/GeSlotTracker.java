package com.osrsflipfinder.runelite;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Singleton;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;

/**
 * Tracks per-slot GE activity timestamps for the My Slots panel and stagnation overlay.
 */
@Singleton
public class GeSlotTracker
{
	private static final int GE_SLOT_COUNT = 8;

	private final Map<Integer, SlotState> slots = new ConcurrentHashMap<>();

	void onOfferChanged(int slot, GrandExchangeOffer offer)
	{
		if (slot < 0 || slot >= GE_SLOT_COUNT || offer == null)
		{
			return;
		}

		GrandExchangeOfferState state = offer.getState();
		if (state == GrandExchangeOfferState.EMPTY)
		{
			slots.remove(slot);
			return;
		}

		Instant now = Instant.now();
		SlotState previous = slots.get(slot);
		int qtyFilled = offer.getQuantitySold();
		boolean activity = previous == null
			|| previous.quantityFilled != qtyFilled
			|| previous.itemId != offer.getItemId()
			|| previous.price != offer.getPrice()
			|| previous.state != state;

		Instant lastActivity = activity ? now : previous != null ? previous.lastActivityAt : now;
		Instant placedAt = previous != null && previous.itemId == offer.getItemId()
			? previous.placedAt
			: now;

		slots.put(slot, new SlotState(
			slot,
			offer.getItemId(),
			state,
			offer.getPrice(),
			offer.getTotalQuantity(),
			qtyFilled,
			placedAt,
			lastActivity
		));
	}

	void clearSlot(int slot)
	{
		slots.remove(slot);
	}

	List<SlotState> snapshot()
	{
		List<SlotState> list = new ArrayList<>(slots.values());
		list.sort((a, b) -> Integer.compare(a.slot, b.slot));
		return Collections.unmodifiableList(list);
	}

	static final class SlotState
	{
		final int slot;
		final int itemId;
		final GrandExchangeOfferState state;
		final int price;
		final int quantity;
		final int quantityFilled;
		final Instant placedAt;
		final Instant lastActivityAt;

		SlotState(
			int slot,
			int itemId,
			GrandExchangeOfferState state,
			int price,
			int quantity,
			int quantityFilled,
			Instant placedAt,
			Instant lastActivityAt
		)
		{
			this.slot = slot;
			this.itemId = itemId;
			this.state = state;
			this.price = price;
			this.quantity = quantity;
			this.quantityFilled = quantityFilled;
			this.placedAt = placedAt;
			this.lastActivityAt = lastActivityAt;
		}

		boolean isBuySide()
		{
			return state == GrandExchangeOfferState.BUYING || state == GrandExchangeOfferState.BOUGHT;
		}

		long inactiveSeconds()
		{
			return Math.max(0, Instant.now().getEpochSecond() - lastActivityAt.getEpochSecond());
		}
	}
}
