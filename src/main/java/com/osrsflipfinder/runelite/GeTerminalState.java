package com.osrsflipfinder.runelite;

import net.runelite.api.GrandExchangeOfferState;

final class GeTerminalState
{
	private GeTerminalState()
	{
	}

	static String resolveOnSlotClear(GrandExchangeOfferState previousState, int quantityFilled)
	{
		boolean buySide = previousState == GrandExchangeOfferState.BUYING
			|| previousState == GrandExchangeOfferState.CANCELLED_BUY
			|| previousState == GrandExchangeOfferState.BOUGHT;

		if (quantityFilled > 0)
		{
			return buySide ? "bought" : "sold";
		}

		return buySide ? "cancelled_buy" : "cancelled_sell";
	}

	static int terminalQuantity(int orderQuantity, int quantityFilled)
	{
		return quantityFilled > 0 ? quantityFilled : orderQuantity;
	}
}
