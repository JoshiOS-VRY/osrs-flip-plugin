package com.osrsflipfinder.runelite;

import net.runelite.api.Client;
import net.runelite.api.KeyCode;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;

/**
 * Sets GE offer price (varbit 4398) via the Enter-price meslayer (mode 7). Only runs when the
 * user clicks FlipX; closes the price meslayer after a successful FlipX apply or cleanup.
 */
final class GeOfferSetupExactPrice
{
	static final int CS_MESLAYER_ONKEY = 112;

	static final int MODE_PRICE_INPUT = 7;
	static final int MAX_WAIT_TICKS = 20;

	enum Phase
	{
		IDLE,
		OPEN_ENTER_PRICE,
		WAIT_MESLAYER,
		SUBMIT,
		VERIFY,
		FAILED
	}

	private GeOfferSetupExactPrice()
	{
	}

	static boolean isExactMatch(Client client, int targetGp)
	{
		return client.getVarbitValue(VarbitID.GE_NEWOFFER_PRICE) == targetGp;
	}

	/**
	 * @return {@code true} when a FlipX price apply cannot start because another meslayer is open
	 */
	static boolean isMeslayerBusyForUser(Client client)
	{
		int mode = client.getVarcIntValue(VarClientID.MESLAYERMODE);
		return mode != 0 && mode != MODE_PRICE_INPUT;
	}

	static void startApply(PriceApplyState state, Client client)
	{
		state.waitTicks = 0;
		state.openedMeslayer = false;
		int mode = client.getVarcIntValue(VarClientID.MESLAYERMODE);
		if (mode == MODE_PRICE_INPUT)
		{
			state.phase = Phase.SUBMIT;
		}
		else if (mode == 0)
		{
			state.phase = Phase.OPEN_ENTER_PRICE;
		}
		else
		{
			state.phase = Phase.IDLE;
		}
	}

	static void finishSuccess(Client client, PriceApplyState state)
	{
		GeOfferSetupMeslayer.abortIfOpen(client);
		state.phase = Phase.IDLE;
		state.openedMeslayer = false;
	}

	static void finishFailed(Client client, PriceApplyState state)
	{
		if (client.getVarcIntValue(VarClientID.MESLAYERMODE) == MODE_PRICE_INPUT)
		{
			GeOfferSetupMeslayer.abortIfOpen(client);
		}
		state.phase = Phase.IDLE;
		state.openedMeslayer = false;
	}

	/**
	 * Advances one step of the Enter-price flow.
	 *
	 * @return {@code true} when finished (success, failure, or already exact)
	 */
	static boolean tick(Client client, Widget setup, int targetGp, PriceApplyState state)
	{
		if (targetGp <= 0 || setup == null || state == null)
		{
			return true;
		}
		if (state.phase == Phase.IDLE)
		{
			return true;
		}
		if (isExactMatch(client, targetGp))
		{
			finishSuccess(client, state);
			return true;
		}

		switch (state.phase)
		{
			case OPEN_ENTER_PRICE:
				int mesMode = client.getVarcIntValue(VarClientID.MESLAYERMODE);
				if (mesMode == MODE_PRICE_INPUT)
				{
					state.phase = Phase.SUBMIT;
					return false;
				}
				if (mesMode != 0)
				{
					finishFailed(client, state);
					return true;
				}
				Widget enter = GeSetupWidgetSearch.findByAction(
					setup,
					"Enter price",
					GeSetupWidgetSearch.priceRow()
				);
				if (enter == null)
				{
					finishFailed(client, state);
					return true;
				}
				GeOfferSetupNative.clickWidget(client, enter, "Enter price");
				state.openedMeslayer = true;
				state.waitTicks = 0;
				state.phase = Phase.WAIT_MESLAYER;
				return false;

			case WAIT_MESLAYER:
				if (client.getVarcIntValue(VarClientID.MESLAYERMODE) == MODE_PRICE_INPUT)
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
				client.setVarcStrValue(VarClientID.MESLAYERINPUT, String.valueOf(targetGp));
				Widget mesText2 = client.getWidget(InterfaceID.Chatbox.MES_TEXT2);
				if (mesText2 != null)
				{
					mesText2.setText(targetGp + "*");
				}
				client.runScript(CS_MESLAYER_ONKEY, KeyCode.KC_ENTER, 0, "");
				state.waitTicks = 0;
				state.phase = Phase.VERIFY;
				return false;

			case VERIFY:
				if (isExactMatch(client, targetGp))
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

	/**
	 * Runs {@link #tick} until a step needs a later game tick or the flow finishes.
	 */
	static boolean advanceUntilWait(Client client, Widget setup, int targetGp, PriceApplyState state)
	{
		while (state.phase != Phase.IDLE)
		{
			boolean done = tick(client, setup, targetGp, state);
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

	static boolean needsAsyncContinuation(PriceApplyState state)
	{
		return state.phase == Phase.WAIT_MESLAYER || state.phase == Phase.VERIFY;
	}

	static final class PriceApplyState
	{
		Phase phase = Phase.IDLE;
		int waitTicks;
		/** {@code true} after this apply clicked GE Enter price (safe to abort on cleanup). */
		boolean openedMeslayer;
	}
}
