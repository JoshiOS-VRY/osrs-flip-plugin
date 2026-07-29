package com.osrsflipfinder.runelite;

import net.runelite.api.Client;
import net.runelite.api.KeyCode;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;

/**
 * Sets GE offer quantity (varbit 4396) via the Enter-quantity meslayer (mode 7, same as price).
 */
final class GeOfferSetupExactQuantity
{
	static final int CS_MESLAYER_ONKEY = GeOfferSetupExactPrice.CS_MESLAYER_ONKEY;
	static final int MODE_QUANTITY_INPUT = GeOfferSetupExactPrice.MODE_PRICE_INPUT;
	static final int MAX_WAIT_TICKS = GeOfferSetupExactPrice.MAX_WAIT_TICKS;

	enum Phase
	{
		IDLE,
		OPEN_ENTER_QUANTITY,
		WAIT_MESLAYER,
		SUBMIT,
		VERIFY,
		FAILED
	}

	private GeOfferSetupExactQuantity()
	{
	}

	static boolean isExactMatch(Client client, int targetQty)
	{
		return client.getVarbitValue(VarbitID.GE_NEWOFFER_QUANTITY) == targetQty;
	}

	static void startApply(QuantityApplyState state, Client client)
	{
		state.waitTicks = 0;
		state.openedMeslayer = false;
		int mode = client.getVarcIntValue(VarClientID.MESLAYERMODE);
		if (mode == MODE_QUANTITY_INPUT)
		{
			state.phase = Phase.SUBMIT;
		}
		else if (mode == 0)
		{
			state.phase = Phase.OPEN_ENTER_QUANTITY;
		}
		else
		{
			state.phase = Phase.IDLE;
		}
	}

	static void finishSuccess(Client client, QuantityApplyState state)
	{
		GeOfferSetupMeslayer.abortIfOpen(client);
		state.phase = Phase.IDLE;
		state.openedMeslayer = false;
	}

	static void finishFailed(Client client, QuantityApplyState state)
	{
		if (client.getVarcIntValue(VarClientID.MESLAYERMODE) == MODE_QUANTITY_INPUT)
		{
			GeOfferSetupMeslayer.abortIfOpen(client);
		}
		state.phase = Phase.IDLE;
		state.openedMeslayer = false;
	}

	static boolean tick(Client client, Widget setup, int targetQty, QuantityApplyState state)
	{
		if (targetQty <= 0 || setup == null || state == null)
		{
			return true;
		}
		if (state.phase == Phase.IDLE)
		{
			return true;
		}
		if (isExactMatch(client, targetQty))
		{
			finishSuccess(client, state);
			return true;
		}

		switch (state.phase)
		{
			case OPEN_ENTER_QUANTITY:
				int mesMode = client.getVarcIntValue(VarClientID.MESLAYERMODE);
				if (mesMode == MODE_QUANTITY_INPUT)
				{
					state.phase = Phase.SUBMIT;
					return false;
				}
				if (mesMode != 0)
				{
					finishFailed(client, state);
					return true;
				}
				Widget enter = findEnterQuantityButton(setup);
				if (enter == null)
				{
					finishFailed(client, state);
					return true;
				}
				String action = enterQuantityAction(setup, enter);
				GeOfferSetupNative.clickWidget(client, enter, action);
				state.openedMeslayer = true;
				state.waitTicks = 0;
				state.phase = Phase.WAIT_MESLAYER;
				return false;

			case WAIT_MESLAYER:
				if (client.getVarcIntValue(VarClientID.MESLAYERMODE) == MODE_QUANTITY_INPUT)
				{
					state.phase = Phase.SUBMIT;
					return false;
				}
				if (++state.waitTicks > MAX_WAIT_TICKS)
				{
					finishFailed(client, state);
					return true;
				}
				return false;

			case SUBMIT:
				client.setVarcStrValue(VarClientID.MESLAYERINPUT, String.valueOf(targetQty));
				Widget mesText2 = client.getWidget(InterfaceID.Chatbox.MES_TEXT2);
				if (mesText2 != null)
				{
					mesText2.setText(targetQty + "*");
				}
				client.runScript(CS_MESLAYER_ONKEY, KeyCode.KC_ENTER, 0, "");
				state.waitTicks = 0;
				state.phase = Phase.VERIFY;
				return false;

			case VERIFY:
				if (isExactMatch(client, targetQty))
				{
					finishSuccess(client, state);
					return true;
				}
				if (++state.waitTicks > MAX_WAIT_TICKS)
				{
					finishFailed(client, state);
					return true;
				}
				return false;

			case FAILED:
				finishFailed(client, state);
				return true;

			default:
				state.phase = Phase.IDLE;
				return true;
		}
	}

	static Widget findEnterQuantityButton(Widget setup)
	{
		GeSetupWidgetSearch.RowBand row = GeSetupWidgetSearch.quantityRow();
		for (String action : new String[] { "Enter quantity", "Enter amount" })
		{
			Widget hit = GeSetupWidgetSearch.findByAction(setup, action, row);
			if (hit != null)
			{
				return hit;
			}
		}
		return GeSetupWidgetSearch.findByAction(setup, "Enter quantity");
	}

	private static String enterQuantityAction(Widget setup, Widget enter)
	{
		GeSetupWidgetSearch.RowBand row = GeSetupWidgetSearch.quantityRow();
		for (String action : new String[] { "Enter quantity", "Enter amount" })
		{
			if (GeSetupWidgetSearch.findByAction(setup, action, row) == enter)
			{
				return action;
			}
		}
		String[] actions = enter.getActions();
		return actions != null && actions.length > 0 && actions[0] != null
			? actions[0]
			: "Enter quantity";
	}

	static boolean advanceUntilWait(
		Client client,
		Widget setup,
		int targetQty,
		QuantityApplyState state
	)
	{
		while (state.phase != Phase.IDLE)
		{
			boolean done = tick(client, setup, targetQty, state);
			if (done)
			{
				return true;
			}
			if (state.phase == Phase.WAIT_MESLAYER || state.phase == Phase.VERIFY)
			{
				return false;
			}
		}
		return true;
	}

	static boolean needsAsyncContinuation(QuantityApplyState state)
	{
		return state.phase == Phase.WAIT_MESLAYER || state.phase == Phase.VERIFY;
	}

	static final class QuantityApplyState
	{
		Phase phase = Phase.IDLE;
		int waitTicks;
		boolean openedMeslayer;
	}
}
