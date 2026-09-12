package com.osrsflipfinder.runelite;

import net.runelite.api.Client;
import net.runelite.api.VarClientInt;
import net.runelite.api.VarClientStr;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;

/**
 * Prefills the GE Enter-price / Enter-quantity chatbox. Same pattern as Flipping Copilot:
 * set the visible input text; the user still presses Enter and Confirms the offer.
 */
final class GeOfferChatInput
{
	static final int CHAT_INPUT_TYPE_GE = 7;

	enum Step
	{
		NONE,
		PRICE,
		QUANTITY_BUY,
		QUANTITY_SELL
	}

	private GeOfferChatInput()
	{
	}

	static boolean isOpen(Client client)
	{
		return client.getVarcIntValue(VarClientInt.INPUT_TYPE) == CHAT_INPUT_TYPE_GE
			|| client.getVarcIntValue(VarClientID.MESLAYERMODE) == CHAT_INPUT_TYPE_GE;
	}

	/**
	 * True when some other chat/meslayer input is open (not the GE price/qty box).
	 * Assist must not prefill or remember a value through that.
	 */
	static boolean isOtherInputOpen(Client client)
	{
		int inputType = client.getVarcIntValue(VarClientInt.INPUT_TYPE);
		int mesMode = client.getVarcIntValue(VarClientID.MESLAYERMODE);
		if (inputType == 0 && mesMode == 0)
		{
			return false;
		}
		return !isOpen(client);
	}

	static String readPrompt(Client client)
	{
		Widget mesText = client.getWidget(InterfaceID.Chatbox.MES_TEXT);
		return mesText != null ? mesText.getText() : null;
	}

	static Step parseStep(String chatPrompt)
	{
		if (chatPrompt == null)
		{
			return Step.NONE;
		}
		if ("Set a price for each item:".equals(chatPrompt))
		{
			return Step.PRICE;
		}
		String lower = chatPrompt.toLowerCase();
		if (lower.contains("how many do you wish to buy"))
		{
			return Step.QUANTITY_BUY;
		}
		if (lower.contains("how many do you wish to sell"))
		{
			return Step.QUANTITY_SELL;
		}
		return Step.NONE;
	}

	static Step currentStep(Client client)
	{
		if (!isOpen(client))
		{
			return Step.NONE;
		}
		return parseStep(readPrompt(client));
	}

	static boolean isPriceOpen(Client client)
	{
		return currentStep(client) == Step.PRICE;
	}

	static boolean isQuantityOpen(Client client)
	{
		Step step = currentStep(client);
		return step == Step.QUANTITY_BUY || step == Step.QUANTITY_SELL;
	}

	static void prefill(Client client, int value)
	{
		if (client == null || value <= 0)
		{
			return;
		}
		String text = String.valueOf(value);
		Widget mesText2 = client.getWidget(InterfaceID.Chatbox.MES_TEXT2);
		if (mesText2 != null)
		{
			mesText2.setText(value + "*");
		}
		client.setVarcStrValue(VarClientStr.INPUT_TEXT, text);
	}

	/** Prefills only when the open GE chat matches {@code required}. */
	static boolean prefillIfStep(Client client, int value, Step required)
	{
		if (client == null || value <= 0 || required == null || required == Step.NONE)
		{
			return false;
		}
		Step actual = currentStep(client);
		if (required == Step.PRICE && actual != Step.PRICE)
		{
			return false;
		}
		if ((required == Step.QUANTITY_BUY || required == Step.QUANTITY_SELL)
			&& actual != Step.QUANTITY_BUY
			&& actual != Step.QUANTITY_SELL)
		{
			return false;
		}
		prefill(client, value);
		return true;
	}
}
