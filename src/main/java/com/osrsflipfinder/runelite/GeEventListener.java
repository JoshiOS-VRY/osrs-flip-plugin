package com.osrsflipfinder.runelite;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;

@Slf4j
@Singleton
public class GeEventListener
{
	private static final int GE_SLOT_COUNT = 8;

	private final Client client;
	private final ItemManager itemManager;
	private final IngestClient ingestClient;
	private final GeSlotTracker slotTracker;

	private final Map<Integer, OfferSnapshot> lastKnownBySlot = new HashMap<>();
	private final Map<Integer, GrandExchangeOffer> pendingBySlot = new HashMap<>();
	private final List<Runnable> offerChangeListeners = new CopyOnWriteArrayList<>();

	@Inject
	GeEventListener(Client client, ItemManager itemManager, IngestClient ingestClient, GeSlotTracker slotTracker)
	{
		this.client = client;
		this.itemManager = itemManager;
		this.ingestClient = ingestClient;
		this.slotTracker = slotTracker;
	}

	void addOfferChangeListener(Runnable listener)
	{
		if (listener != null)
		{
			offerChangeListeners.add(listener);
		}
	}

	void removeOfferChangeListener(Runnable listener)
	{
		offerChangeListeners.remove(listener);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() != GameState.LOGGED_IN || !ingestClient.isConfigured())
		{
			return;
		}

		backfillAllSlots();
		flushPendingOffers();
	}

	@Subscribe
	public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event)
	{
		GrandExchangeOffer offer = event.getOffer();
		if (offer == null)
		{
			return;
		}

		slotTracker.onOfferChanged(event.getSlot(), offer);

		if (ingestClient.isConfigured())
		{
			processOffer(event.getSlot(), offer, false);
		}

		notifyOfferChangeListeners();
	}

	void backfillAllSlots()
	{
		GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
		if (offers == null)
		{
			return;
		}

		long accountHash = client.getAccountHash();
		java.util.List<GeSlotSnapshot> snapshots = new java.util.ArrayList<>();

		for (int slot = 0; slot < Math.min(GE_SLOT_COUNT, offers.length); slot++)
		{
			GrandExchangeOffer offer = offers[slot];
			snapshots.add(GeSlotSnapshot.from(slot, offer));
			if (offer != null)
			{
				processOffer(slot, offer, true);
			}
			else if (accountHash != -1)
			{
				OfferSnapshot previous = lastKnownBySlot.get(slot);
				if (previous != null)
				{
					emitSlotCleared(slot, previous, accountHash);
					lastKnownBySlot.remove(slot);
					pendingBySlot.remove(slot);
				}
			}
		}

		if (accountHash != -1 && ingestClient.isConfigured())
		{
			String displayName = client.getLocalPlayer() != null
				? client.getLocalPlayer().getName()
				: null;
			ingestClient.reconcileSlots(String.valueOf(accountHash), displayName, snapshots);
		}

		notifyOfferChangeListeners();
	}

	private void processOffer(int slot, GrandExchangeOffer offer, boolean forceEmit)
	{
		GrandExchangeOfferState state = offer.getState();
		if (state == GrandExchangeOfferState.EMPTY
			&& client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		if (state == GrandExchangeOfferState.EMPTY)
		{
			OfferSnapshot previous = lastKnownBySlot.get(slot);
			if (previous != null)
			{
				long accountHash = client.getAccountHash();
				if (accountHash != -1)
				{
					emitSlotCleared(slot, previous, accountHash);
				}
			}
			lastKnownBySlot.remove(slot);
			pendingBySlot.remove(slot);
			slotTracker.clearSlot(slot);
			return;
		}

		OfferSnapshot previous = lastKnownBySlot.get(slot);
		if (!forceEmit && previous != null && previous.equalsOffer(offer))
		{
			return;
		}

		lastKnownBySlot.put(slot, OfferSnapshot.from(offer));

		long accountHash = client.getAccountHash();
		if (accountHash == -1)
		{
			pendingBySlot.put(slot, offer);
			log.debug("Deferring GE slot {} until account hash is available", slot);
			return;
		}

		pendingBySlot.remove(slot);
		emitOffer(slot, offer, accountHash);
	}

	private void flushPendingOffers()
	{
		long accountHash = client.getAccountHash();
		if (accountHash == -1 || pendingBySlot.isEmpty())
		{
			return;
		}

		Map<Integer, GrandExchangeOffer> pending = new HashMap<>(pendingBySlot);
		pendingBySlot.clear();

		for (Map.Entry<Integer, GrandExchangeOffer> entry : pending.entrySet())
		{
			emitOffer(entry.getKey(), entry.getValue(), accountHash);
		}
	}

	private void emitOffer(int slot, GrandExchangeOffer offer, long accountHash)
	{
		String displayName = client.getLocalPlayer() != null
			? client.getLocalPlayer().getName()
			: null;

		String itemName = null;
		if (offer.getItemId() > 0)
		{
			itemName = itemManager.getItemComposition(offer.getItemId()).getName();
		}

		IngestGeEvent mapped = EventMapper.mapOffer(
			offer,
			slot,
			accountHash,
			displayName,
			itemName,
			Instant.now()
		);

		if (mapped != null)
		{
			ingestClient.enqueue(mapped);
		}
	}

	private void emitSlotCleared(int slot, OfferSnapshot previous, long accountHash)
	{
		String displayName = client.getLocalPlayer() != null
			? client.getLocalPlayer().getName()
			: null;

		String itemName = null;
		if (previous.getItemId() > 0)
		{
			itemName = itemManager.getItemComposition(previous.getItemId()).getName();
		}

		IngestGeEvent mapped = EventMapper.mapSlotCleared(
			previous,
			slot,
			accountHash,
			displayName,
			itemName,
			Instant.now()
		);

		if (mapped != null)
		{
			ingestClient.enqueue(mapped);
		}
	}

	private void notifyOfferChangeListeners()
	{
		for (Runnable listener : offerChangeListeners)
		{
			try
			{
				listener.run();
			}
			catch (RuntimeException e)
			{
				log.debug("GE offer listener failed", e);
			}
		}
	}
}
