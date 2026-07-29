package com.osrsflipfinder.runelite;

import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GeOfferSetupScriptsTest
{
	@Before
	public void clearTail()
	{
		GeSetupScriptTail.clear();
	}

	@Test
	public void applyQuantityNativeBurstClicksPlusTenWhenNeeded()
	{
		Client client = mock(Client.class);
		Widget setup = mock(Widget.class);
		Widget plusTen = mock(Widget.class);

		when(client.getVarbitValue(VarbitID.GE_NEWOFFER_QUANTITY)).thenReturn(1, 11);
		when(plusTen.getActions()).thenReturn(new String[] { "+10" });
		when(plusTen.getOriginalY()).thenReturn(136);
		when(plusTen.getIndex()).thenReturn(3);
		when(plusTen.getId()).thenReturn(0x01d1_001a);
		when(plusTen.getChildren()).thenReturn(null);
		when(plusTen.getDynamicChildren()).thenReturn(null);
		when(setup.getChildren()).thenReturn(new Widget[] { plusTen });
		when(setup.getDynamicChildren()).thenReturn(null);
		when(setup.getActions()).thenReturn(null);

		GeOfferSetupScripts.applyQuantityNativeBurst(client, setup, 11);

		verify(client).menuAction(
			eq(3),
			eq(0x01d1_001a),
			eq(MenuAction.CC_OP),
			eq(1),
			eq(-1),
			eq("+10"),
			eq("")
		);
	}

	@Test
	public void applyPriceOneShotReturnsTrueWhenAlreadyExact()
	{
		Client client = mock(Client.class);
		Widget setup = mock(Widget.class);
		when(client.getVarbitValue(VarbitID.GE_NEWOFFER_PRICE)).thenReturn(12_345);
		when(setup.getChildren()).thenReturn(null);
		when(setup.getDynamicChildren()).thenReturn(null);

		org.junit.Assert.assertTrue(
			GeOfferSetupScripts.applyPriceOneShot(client, setup, 12_345)
		);
	}
}
