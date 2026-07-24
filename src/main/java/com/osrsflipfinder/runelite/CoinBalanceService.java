package com.osrsflipfinder.runelite;

import java.util.function.LongConsumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;

/** Reads the coin stack from the local inventory — no network access. */
@Singleton
class CoinBalanceService
{
	private final Client client;
	private final ClientThread clientThread;
	private volatile long coins = -1;
	private volatile LongConsumer balanceListener = value -> {};

	@Inject
	CoinBalanceService(Client client, ClientThread clientThread)
	{
		this.client = client;
		this.clientThread = clientThread;
	}

	void start()
	{
		clientThread.invokeLater(this::refresh);
	}

	/** Notified on the Swing thread whenever the detected coin balance changes. */
	void setBalanceListener(LongConsumer listener)
	{
		this.balanceListener = listener != null ? listener : value -> {};
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			clientThread.invokeLater(this::refresh);
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() == InventoryID.INVENTORY.getId())
		{
			clientThread.invokeLater(this::refresh);
		}
	}

	void refresh()
	{
		long previous = coins;
		ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
		coins = inventory == null ? -1 : inventory.count(ItemID.COINS);
		if (coins != previous)
		{
			long snapshot = coins;
			javax.swing.SwingUtilities.invokeLater(() -> balanceListener.accept(snapshot));
		}
	}

	long getCoins()
	{
		return coins;
	}

	boolean hasCoins()
	{
		return coins >= 0;
	}
}
