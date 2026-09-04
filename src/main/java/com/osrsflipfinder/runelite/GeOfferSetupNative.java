package com.osrsflipfinder.runelite;

import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.widgets.Widget;

/** Activates a visible GE setup widget op (same CC_OP the user can click). */
final class GeOfferSetupNative
{
	private GeOfferSetupNative()
	{
	}

	/**
	 * @return {@code true} when the widget has {@code option} and the click was sent
	 */
	static boolean clickWidget(Client client, Widget widget, String option)
	{
		if (client == null || widget == null || option == null || option.isBlank())
		{
			return false;
		}
		if (widget.isHidden())
		{
			return false;
		}
		if (!hasAction(widget, option))
		{
			return false;
		}
		client.menuAction(
			widget.getIndex(),
			widget.getId(),
			MenuAction.CC_OP,
			1,
			-1,
			option,
			""
		);
		return true;
	}

	static boolean hasAction(Widget widget, String option)
	{
		if (widget == null || option == null)
		{
			return false;
		}
		String[] actions = widget.getActions();
		if (actions == null)
		{
			return false;
		}
		for (String action : actions)
		{
			if (option.equals(action))
			{
				return true;
			}
		}
		return false;
	}
}
