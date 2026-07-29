package com.osrsflipfinder.runelite;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;

/**
 * OSRS GE buy setup shows rolling 4h usage, e.g.
 * "You have bought a total of 5 so far for a total price of … coins."
 */
final class GeOfferSetupBuyProgress
{
	private static final Pattern BOUGHT_SO_FAR = Pattern.compile(
		"(?i)you have bought a total of ([\\d,]+) so far"
	);

	private GeOfferSetupBuyProgress()
	{
	}

	/**
	 * @return items already bought toward the 4h limit for the current setup item, or {@code -1} if unknown
	 */
	static int parseBoughtSoFar(@Nullable Widget setup)
	{
		if (setup == null)
		{
			return -1;
		}
		int fromDynamic = scanChildren(setup.getDynamicChildren());
		if (fromDynamic >= 0)
		{
			return fromDynamic;
		}
		return scanChildren(setup.getChildren());
	}

	private static int scanChildren(@Nullable Widget[] children)
	{
		if (children == null)
		{
			return -1;
		}
		for (Widget widget : children)
		{
			if (widget == null || widget.getType() != WidgetType.TEXT)
			{
				continue;
			}
			String text = widget.getText();
			if (text == null || text.isEmpty())
			{
				continue;
			}
			Matcher matcher = BOUGHT_SO_FAR.matcher(text);
			if (matcher.find())
			{
				return parseIntGroup(matcher.group(1));
			}
		}
		return -1;
	}

	private static int parseIntGroup(@Nullable String raw)
	{
		if (raw == null || raw.isEmpty())
		{
			return -1;
		}
		try
		{
			return Integer.parseInt(raw.replace(",", ""));
		}
		catch (NumberFormatException e)
		{
			return -1;
		}
	}
}
