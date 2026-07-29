package com.osrsflipfinder.runelite;

import java.awt.Rectangle;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

/** Canvas geometry for the eight GE offer slot tabs on the main picker grid. */
final class GeSlotBounds
{
	static final int SLOT_COUNT = 8;
	private static final int BORDER_PAD = 2;

	private GeSlotBounds()
	{
	}

	static Rectangle boundsForSlot(Client client, int slot)
	{
		Widget tab = client.getWidget(InterfaceID.GeOffers.INDEX_0 + slot);
		if (tab == null || tab.isSelfHidden())
		{
			return null;
		}

		Rectangle bounds = tab.getBounds();
		if (bounds == null || bounds.width <= 0 || bounds.height <= 0)
		{
			return null;
		}

		return new Rectangle(
			bounds.x - BORDER_PAD,
			bounds.y - BORDER_PAD,
			bounds.width + BORDER_PAD * 2,
			bounds.height + BORDER_PAD * 2
		);
	}

	/** Slot index under the canvas mouse position, or {@code -1}. */
	static int hitTest(Client client, int canvasX, int canvasY)
	{
		for (int slot = 0; slot < SLOT_COUNT; slot++)
		{
			Rectangle bounds = boundsForSlot(client, slot);
			if (bounds != null && bounds.contains(canvasX, canvasY))
			{
				return slot;
			}
		}
		return -1;
	}
}
