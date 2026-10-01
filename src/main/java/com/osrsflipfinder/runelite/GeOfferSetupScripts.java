package com.osrsflipfinder.runelite;

import net.runelite.api.Client;
import net.runelite.api.gameval.VarPlayerID;

/** Reads the GE new-offer setup panel. Does not write offer values. */
final class GeOfferSetupScripts
{
	/**
	 * Unnamed 64-bit varp for the price on the GE setup screen.
	 * RuneLite 1.13 removed {@code VarbitID.GE_NEWOFFER_PRICE} when offers
	 * moved past max cash. This varp is the id immediately before
	 * {@link VarPlayerID#GE_TAX_SLOT_LONG_0}.
	 */
	static final int GE_SETUP_PRICE_VARP = VarPlayerID.GE_TAX_SLOT_LONG_0 - 1;

	private GeOfferSetupScripts()
	{
	}

	/** Per-item gp on the GE new-offer setup panel. */
	static long readOfferPriceGp(Client client)
	{
		if (client == null)
		{
			return 0;
		}
		return Math.max(0L, client.getVarpLongValue(GE_SETUP_PRICE_VARP));
	}
}
