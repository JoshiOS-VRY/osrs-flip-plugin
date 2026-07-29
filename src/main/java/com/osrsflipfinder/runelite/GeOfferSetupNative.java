package com.osrsflipfinder.runelite;

import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.widgets.Widget;

/**
 * Native GE widget clicks for quantity/price stepping (no meslayer).
 */
final class GeOfferSetupNative
{
	private GeOfferSetupNative()
	{
	}

	static Click pickQuantityClick(Widget setup, int delta)
	{
		GeSetupWidgetSearch.RowBand row = GeSetupWidgetSearch.quantityRow();
		if (delta > 0)
		{
			if (delta >= 1000)
			{
				Click c = clickForAction(setup, "+1K", row);
				if (c != null)
				{
					return c;
				}
			}
			if (delta >= 100)
			{
				Click c = clickForAction(setup, "+100", row);
				if (c != null)
				{
					return c;
				}
			}
			if (delta >= 10)
			{
				Click c = clickForAction(setup, "+10", row);
				if (c != null)
				{
					return c;
				}
			}
			return clickForAction(setup, "+1", row);
		}
		if (delta <= -100)
		{
			Click c = clickForAction(setup, "-100", row);
			if (c != null)
			{
				return c;
			}
		}
		if (delta <= -10)
		{
			Click c = clickForAction(setup, "-10", row);
			if (c != null)
			{
				return c;
			}
		}
		return clickForAction(setup, "-1", row);
	}

	static void clickWidget(Client client, Widget widget, String option)
	{
		client.menuAction(
			widget.getIndex(),
			widget.getId(),
			MenuAction.CC_OP,
			1,
			-1,
			option,
			""
		);
	}

	private static Click clickForAction(Widget setup, String action, GeSetupWidgetSearch.RowBand row)
	{
		Widget widget = GeSetupWidgetSearch.findByAction(setup, action, row);
		if (widget == null)
		{
			return null;
		}
		return new Click(widget, action);
	}

	static final class Click
	{
		final Widget widget;
		final String option;

		Click(Widget widget, String option)
		{
			this.widget = widget;
			this.option = option;
		}
	}
}
