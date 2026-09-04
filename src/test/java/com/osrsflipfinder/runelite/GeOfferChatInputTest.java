package com.osrsflipfinder.runelite;

import net.runelite.api.Client;
import net.runelite.api.VarClientInt;
import net.runelite.api.VarClientStr;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GeOfferChatInputTest
{
	@Test
	public void isOpenWhenInputTypeIsGe()
	{
		Client client = mock(Client.class);
		when(client.getVarcIntValue(VarClientInt.INPUT_TYPE)).thenReturn(GeOfferChatInput.CHAT_INPUT_TYPE_GE);
		assertTrue(GeOfferChatInput.isOpen(client));
	}

	@Test
	public void isClosedWhenNoGeChat()
	{
		Client client = mock(Client.class);
		when(client.getVarcIntValue(VarClientInt.INPUT_TYPE)).thenReturn(0);
		when(client.getVarcIntValue(VarClientID.MESLAYERMODE)).thenReturn(0);
		assertFalse(GeOfferChatInput.isOpen(client));
	}

	@Test
	public void isOtherInputOpenWhenBankPinStyleDialog()
	{
		Client client = mock(Client.class);
		when(client.getVarcIntValue(VarClientInt.INPUT_TYPE)).thenReturn(3);
		when(client.getVarcIntValue(VarClientID.MESLAYERMODE)).thenReturn(3);
		assertTrue(GeOfferChatInput.isOtherInputOpen(client));
	}

	@Test
	public void parseStepRecognizesGePrompts()
	{
		assertEquals(GeOfferChatInput.Step.PRICE, GeOfferChatInput.parseStep("Set a price for each item:"));
		assertEquals(
			GeOfferChatInput.Step.QUANTITY_BUY,
			GeOfferChatInput.parseStep("How many do you wish to buy?")
		);
		assertEquals(
			GeOfferChatInput.Step.QUANTITY_SELL,
			GeOfferChatInput.parseStep("How many do you wish to sell?")
		);
		assertEquals(GeOfferChatInput.Step.NONE, GeOfferChatInput.parseStep("Something else"));
	}

	@Test
	public void prefillWritesChatTextAndVarcs()
	{
		Client client = mock(Client.class);
		Widget mesText2 = mock(Widget.class);
		when(client.getWidget(InterfaceID.Chatbox.MES_TEXT2)).thenReturn(mesText2);

		GeOfferChatInput.prefill(client, 4_200);

		verify(mesText2).setText("4200*");
		verify(client).setVarcStrValue(VarClientStr.INPUT_TEXT, "4200");
	}

	@Test
	public void prefillIfStepRefusesWrongDialog()
	{
		Client client = geChat(GeOfferChatInput.CHAT_INPUT_TYPE_GE, "Set a price for each item:");
		Widget mesText2 = mock(Widget.class);
		when(client.getWidget(InterfaceID.Chatbox.MES_TEXT2)).thenReturn(mesText2);

		assertFalse(GeOfferChatInput.prefillIfStep(client, 50, GeOfferChatInput.Step.QUANTITY_BUY));
		verify(client, never()).setVarcStrValue(VarClientStr.INPUT_TEXT, "50");
	}

	@Test
	public void prefillIfStepAcceptsMatchingPriceDialog()
	{
		Client client = geChat(GeOfferChatInput.CHAT_INPUT_TYPE_GE, "Set a price for each item:");
		Widget mesText2 = mock(Widget.class);
		when(client.getWidget(InterfaceID.Chatbox.MES_TEXT2)).thenReturn(mesText2);

		assertTrue(GeOfferChatInput.prefillIfStep(client, 4_200, GeOfferChatInput.Step.PRICE));
		verify(client).setVarcStrValue(VarClientStr.INPUT_TEXT, "4200");
	}

	private static Client geChat(int inputType, String prompt)
	{
		Client client = mock(Client.class);
		when(client.getVarcIntValue(VarClientInt.INPUT_TYPE)).thenReturn(inputType);
		when(client.getVarcIntValue(VarClientID.MESLAYERMODE)).thenReturn(inputType);
		Widget mesText = mock(Widget.class);
		when(mesText.getText()).thenReturn(prompt);
		when(client.getWidget(InterfaceID.Chatbox.MES_TEXT)).thenReturn(mesText);
		return client;
	}
}
