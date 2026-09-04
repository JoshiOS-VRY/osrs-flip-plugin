package com.osrsflipfinder.runelite;

import net.runelite.api.Client;
import net.runelite.api.gameval.VarbitID;

/** Reads the GE new-offer setup panel. Does not write offer values. */
final class GeOfferSetupScripts
{
	private GeOfferSetupScripts()
	{
	}

	/** Per-item gp on the GE new-offer setup panel (varbit {@link VarbitID#GE_NEWOFFER_PRICE}). */
	static int readOfferPriceGp(Client client)
	{
		if (client == null)
		{
			return 0;
		}
		return Math.max(0, client.getVarbitValue(VarbitID.GE_NEWOFFER_PRICE));
	}
}
