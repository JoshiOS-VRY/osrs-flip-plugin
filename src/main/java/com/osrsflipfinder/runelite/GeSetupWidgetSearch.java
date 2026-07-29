package com.osrsflipfinder.runelite;

import net.runelite.api.widgets.Widget;

/** Finds GE setup widgets whether they live in static or dynamic child lists. */
final class GeSetupWidgetSearch
{
	private GeSetupWidgetSearch()
	{
	}

	static Widget findByAction(Widget root, String action)
	{
		return findByAction(root, action, null);
	}

	static Widget findByAction(Widget root, String action, RowBand band)
	{
		if (root == null || action == null)
		{
			return null;
		}
		return findByActionRecursive(root, action, band, 0);
	}

	private static Widget findByActionRecursive(
		Widget widget,
		String action,
		RowBand band,
		int depth
	)
	{
		if (widget == null || depth > 8)
		{
			return null;
		}
		if (matchesAction(widget, action, band))
		{
			return widget;
		}
		Widget direct = scanArray(widget.getChildren(), action, band, depth);
		if (direct != null)
		{
			return direct;
		}
		return scanArray(widget.getDynamicChildren(), action, band, depth);
	}

	private static boolean matchesAction(Widget widget, String action, RowBand band)
	{
		if (band != null && !band.containsY(widget.getOriginalY()))
		{
			return false;
		}
		String[] actions = widget.getActions();
		if (actions == null)
		{
			return false;
		}
		for (String op : actions)
		{
			if (action.equals(op))
			{
				return true;
			}
		}
		return false;
	}

	private static Widget scanArray(Widget[] children, String action, RowBand band, int depth)
	{
		if (children == null)
		{
			return null;
		}
		for (Widget child : children)
		{
			Widget hit = findByActionRecursive(child, action, band, depth + 1);
			if (hit != null)
			{
				return hit;
			}
		}
		return null;
	}

	/** Quantity row (small +/- and +1K band). */
	static RowBand quantityRow()
	{
		return new RowBand(105, 155);
	}

	/** Price row (+5%, Guide price, Enter price band — same Y as big qty buttons). */
	static RowBand priceRow()
	{
		return new RowBand(125, 175);
	}

	static final class RowBand
	{
		private final int minY;
		private final int maxY;

		RowBand(int minY, int maxY)
		{
			this.minY = minY;
			this.maxY = maxY;
		}

		boolean containsY(int y)
		{
			return y >= minY && y <= maxY;
		}
	}
}
