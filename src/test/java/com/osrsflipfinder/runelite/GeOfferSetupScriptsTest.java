package com.osrsflipfinder.runelite;

import net.runelite.api.Client;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GeOfferSetupScriptsTest
{
	@Test
	public void readOfferPriceGpReturnsSetupVarp()
	{
		Client client = mock(Client.class);
		when(client.getVarpLongValue(GeOfferSetupScripts.GE_SETUP_PRICE_VARP)).thenReturn(12_345L);
		assertEquals(12_345L, GeOfferSetupScripts.readOfferPriceGp(client));
	}

	@Test
	public void readOfferPriceGpTreatsNullAsZero()
	{
		assertEquals(0, GeOfferSetupScripts.readOfferPriceGp(null));
	}
}
