package com.osrsflipfinder.runelite;

import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.widgets.Widget;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GeOfferSetupNativeTest
{
	@Test
	public void clickWidgetSendsCcOpWhenActionMatches()
	{
		Client client = mock(Client.class);
		Widget enter = mock(Widget.class);
		when(enter.isHidden()).thenReturn(false);
		when(enter.getActions()).thenReturn(new String[] { "Enter price" });
		when(enter.getIndex()).thenReturn(4);
		when(enter.getId()).thenReturn(0x01d1_001b);

		assertTrue(GeOfferSetupNative.clickWidget(client, enter, "Enter price"));
		verify(client).menuAction(
			eq(4),
			eq(0x01d1_001b),
			eq(MenuAction.CC_OP),
			eq(1),
			eq(-1),
			eq("Enter price"),
			eq("")
		);
	}

	@Test
	public void clickWidgetRefusesMissingAction()
	{
		Client client = mock(Client.class);
		Widget enter = mock(Widget.class);
		when(enter.isHidden()).thenReturn(false);
		when(enter.getActions()).thenReturn(new String[] { "Guide price" });

		assertFalse(GeOfferSetupNative.clickWidget(client, enter, "Enter price"));
		verify(client, never()).menuAction(
			org.mockito.ArgumentMatchers.anyInt(),
			org.mockito.ArgumentMatchers.anyInt(),
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.anyInt(),
			org.mockito.ArgumentMatchers.anyInt(),
			org.mockito.ArgumentMatchers.anyString(),
			org.mockito.ArgumentMatchers.anyString()
		);
	}

	@Test
	public void clickWidgetRefusesHiddenWidget()
	{
		Client client = mock(Client.class);
		Widget enter = mock(Widget.class);
		when(enter.isHidden()).thenReturn(true);
		when(enter.getActions()).thenReturn(new String[] { "Enter price" });

		assertFalse(GeOfferSetupNative.clickWidget(client, enter, "Enter price"));
	}
}
