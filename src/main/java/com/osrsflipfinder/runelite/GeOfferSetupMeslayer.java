package com.osrsflipfinder.runelite;

import net.runelite.api.Client;
import net.runelite.api.gameval.VarClientID;

/** Closes GE count/price meslayer without submitting (script 138). Used only to finish FlipX price apply. */
final class GeOfferSetupMeslayer
{
	static final int CS_MESLAYER_ABORT = 138;

	private GeOfferSetupMeslayer()
	{
	}

	static void abortIfOpen(Client client)
	{
		if (client.getVarcIntValue(VarClientID.MESLAYERMODE) == 0)
		{
			return;
		}
		client.runScript(CS_MESLAYER_ABORT);
	}
}
