package com.osrsflipfinder.runelite;

import net.runelite.api.Client;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GeOfferSetupScriptsTest
{
	@Test
	public void readOfferPriceGpReturnsVarbit()
	{
		Client client = mock(Client.class);
		when(client.getVarbitValue(VarbitID.GE_NEWOFFER_PRICE)).thenReturn(12_345);
		assertEquals(12_345, GeOfferSetupScripts.readOfferPriceGp(client));
	}

	@Test
	public void readOfferPriceGpTreatsNullAsZero()
	{
		assertEquals(0, GeOfferSetupScripts.readOfferPriceGp(null));
	}
}
