package com.osrsflipfinder.runelite;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Singleton;

/**
 * Mirrors Flipping Utilities {@code HistoryManager.deletePreviousOffersForTrade} so we
 * store one row per GE trade instead of every progress tick.
 */
@Singleton
public class GeOfferHistoryCompressor
{
	private final Map<String, List<IngestGeEvent>> compressedByAccountItem = new HashMap<>();

	void resetForAccount(String accountHash)
	{
		compressedByAccountItem.keySet().removeIf(key -> key.startsWith(accountHash + ":"));
	}

	IngestGeEvent compressAndTrack(IngestGeEvent incoming)
	{
		if (incoming == null || incoming.getSlot() == null)
		{
			return incoming;
		}

		String key = incoming.getAccountHash() + ":" + incoming.getItemId();
		List<IngestGeEvent> compressed = compressedByAccountItem.computeIfAbsent(
			key,
			k -> new ArrayList<>()
		);

		deletePreviousOffersForTrade(compressed, incoming);
		compressed.add(copy(incoming));
		return incoming;
	}

	private static void deletePreviousOffersForTrade(
		List<IngestGeEvent> compressed,
		IngestGeEvent incoming
	)
	{
		Integer incomingSlot = incoming.getSlot();
		if (incomingSlot == null)
		{
			return;
		}

		for (int i = compressed.size() - 1; i >= 0; i--)
		{
			IngestGeEvent previous = compressed.get(i);

			if (isUpdateForCancelled(incoming, previous))
			{
				compressed.remove(i);
				continue;
			}

			Integer previousSlot = previous.getSlot();
			if (previousSlot != null
				&& previousSlot.equals(incomingSlot)
				&& previous.getSide().equals(incoming.getSide()))
			{
				if (isComplete(previous.getState()))
				{
					return;
				}
				compressed.remove(i);
			}
		}
	}

	private static boolean isComplete(String state)
	{
		return "bought".equals(state)
			|| "sold".equals(state)
			|| "cancelled_buy".equals(state)
			|| "cancelled_sell".equals(state);
	}

	private static boolean isRedundantProgressBeforeCompletion(IngestGeEvent event)
	{
		String state = event.getState();
		if (!"buying".equals(state) && !"selling".equals(state))
		{
			return false;
		}
		return event.getQuantityFilled() > 0 && event.getQuantityFilled() == event.getQuantity();
	}

	static boolean isUpdateForCancelled(IngestGeEvent newer, IngestGeEvent older)
	{
		boolean newerProgress = "buying".equals(newer.getState()) || "selling".equals(newer.getState());
		boolean olderCancelled = "cancelled_buy".equals(older.getState())
			|| "cancelled_sell".equals(older.getState());
		if (!newerProgress || !olderCancelled)
		{
			return false;
		}
		if (newer.getSlot() == null || older.getSlot() == null || !newer.getSlot().equals(older.getSlot()))
		{
			return false;
		}
		if (!newer.getSide().equals(older.getSide()))
		{
			return false;
		}
		if (newer.getQuantity() != older.getQuantity())
		{
			return false;
		}
		if (newer.getPrice() != older.getPrice())
		{
			return false;
		}
		return newer.getQuantityFilled() != older.getQuantityFilled();
	}

	static IngestGeEvent copy(IngestGeEvent source)
	{
		return IngestGeEvent.builder()
			.idempotencyKey(source.getIdempotencyKey())
			.accountHash(source.getAccountHash())
			.accountDisplayName(source.getAccountDisplayName())
			.itemId(source.getItemId())
			.itemName(source.getItemName())
			.side(source.getSide())
			.price(source.getPrice())
			.quantity(source.getQuantity())
			.quantityFilled(source.getQuantityFilled())
			.state(source.getState())
			.occurredAt(source.getOccurredAt())
			.slot(source.getSlot())
			.source(source.getSource())
			.build();
	}

	boolean shouldSkipDuplicateProgress(IngestGeEvent event)
	{
		return isRedundantProgressBeforeCompletion(event);
	}
}
