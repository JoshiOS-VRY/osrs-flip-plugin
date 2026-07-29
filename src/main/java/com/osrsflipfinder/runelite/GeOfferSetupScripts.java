package com.osrsflipfinder.runelite;

import net.runelite.api.Client;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;

/** Applies GE setup quantity (777 / native qty widgets). Price uses {@link GeOfferSetupExactPrice}. */
final class GeOfferSetupScripts
{
	/** Max native +/- clicks in one client pass when script 777 tail is unavailable. */
	private static final int MAX_NATIVE_QTY_BURST = 128;

	private GeOfferSetupScripts()
	{
	}

	/**
	 * Sets quantity via Jagex script 777 when the GE widget tail is available (instant delta).
	 *
	 * @return {@code true} when the target is reached
	 */
	static boolean applyQuantityViaScript(Client client, Widget setup, int targetQuantity)
	{
		if (targetQuantity <= 0 || setup == null)
		{
			return true;
		}
		GeSetupScriptTail.ensureForSetup(setup);

		int current = client.getVarbitValue(VarbitID.GE_NEWOFFER_QUANTITY);
		if (current <= 0)
		{
			current = 1;
		}
		if (GeOfferSetupConvergence.isQuantityDone(current, targetQuantity))
		{
			return true;
		}

		if (!GeSetupScriptTail.hasTail())
		{
			return false;
		}

		int delta = targetQuantity - current;
		GeSetupScriptTail.runChangeQuantity(client, delta);
		int after = client.getVarbitValue(VarbitID.GE_NEWOFFER_QUANTITY);
		return GeOfferSetupConvergence.isQuantityDone(after, targetQuantity);
	}

	/**
	 * Last-resort +/- stepping when script tail and Enter-quantity meslayer are unavailable.
	 *
	 * @return {@code true} when target reached or cannot progress further this pass
	 */
	static boolean applyQuantityNativeBurst(Client client, Widget setup, int targetQuantity)
	{
		if (targetQuantity <= 0 || setup == null)
		{
			return true;
		}

		int bursts = 0;
		while (bursts++ < MAX_NATIVE_QTY_BURST)
		{
			int current = client.getVarbitValue(VarbitID.GE_NEWOFFER_QUANTITY);
			if (current <= 0)
			{
				current = 1;
			}
			if (GeOfferSetupConvergence.isQuantityDone(current, targetQuantity))
			{
				return true;
			}
			int delta = targetQuantity - current;
			GeOfferSetupNative.Click click = GeOfferSetupNative.pickQuantityClick(setup, delta);
			if (click == null)
			{
				return false;
			}
			GeOfferSetupNative.clickWidget(client, click.widget, click.option);
		}
		int after = client.getVarbitValue(VarbitID.GE_NEWOFFER_QUANTITY);
		return GeOfferSetupConvergence.isQuantityDone(after, targetQuantity);
	}

	/** @deprecated prefer script + {@link GeOfferSetupExactQuantity}; kept for tests */
	@Deprecated
	static boolean applyQuantityOneShot(Client client, Widget setup, int targetQuantity)
	{
		if (applyQuantityViaScript(client, setup, targetQuantity))
		{
			return true;
		}
		return applyQuantityNativeBurst(client, setup, targetQuantity);
	}

	/**
	 * Sets price (varbit 4398) via script 778 when the GE tail is available — same idea as quantity.
	 *
	 * @return {@code true} when the target is reached or cannot progress without the meslayer
	 */
	static boolean applyPriceOneShot(Client client, Widget setup, int targetPriceGp)
	{
		if (targetPriceGp <= 0 || setup == null)
		{
			return true;
		}
		GeSetupScriptTail.ensureForSetup(setup);

		int current = client.getVarbitValue(VarbitID.GE_NEWOFFER_PRICE);
		if (GeOfferSetupConvergence.isPriceDone(current, targetPriceGp))
		{
			return true;
		}

		if (GeSetupScriptTail.hasTail())
		{
			int delta = targetPriceGp - current;
			GeSetupScriptTail.runChangePrice(client, delta);
			int after = client.getVarbitValue(VarbitID.GE_NEWOFFER_PRICE);
			return GeOfferSetupConvergence.isPriceDone(after, targetPriceGp);
		}

		return false;
	}

	static Widget findWidgetByAction(Widget setup, String action)
	{
		return GeSetupWidgetSearch.findByAction(setup, action);
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
