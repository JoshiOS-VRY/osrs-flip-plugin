package com.osrsflipfinder.runelite;

import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.VarPlayer;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

/**
 * Resolves the GE item the player is configuring or viewing.
 * {@link VarPlayer#CURRENT_GE_ITEM} is only set while creating a new offer, not on offer status.
 */
final class GeItemResolver
{
	private static final int SLOT_COUNT = 8;

	private GeItemResolver()
	{
	}

	static int resolve(Client client)
	{
		int setupItem = client.getVarpValue(VarPlayer.CURRENT_GE_ITEM);
		if (setupItem > 0)
		{
			return setupItem;
		}

		if (!isGeOpen(client))
		{
			return -1;
		}

		if (isDetailsVisible(client))
		{
			int fromDetails = findItemIdInTree(client.getWidget(InterfaceID.GeOffers.DETAILS));
			if (fromDetails > 0)
			{
				return fromDetails;
			}

			int fromSlot = itemFromHighlightedSlot(client);
			if (fromSlot > 0)
			{
				return fromSlot;
			}
		}

		if (isSetupVisible(client))
		{
			int fromSetup = findItemIdInTree(client.getWidget(InterfaceID.GeOffers.SETUP));
			if (fromSetup > 0)
			{
				return fromSetup;
			}
		}

		return soleActiveOfferItem(client);
	}

	private static boolean isGeOpen(Client client)
	{
		Widget universe = client.getWidget(InterfaceID.GeOffers.UNIVERSE);
		return universe != null && !universe.isSelfHidden();
	}

	private static boolean isDetailsVisible(Client client)
	{
		Widget details = client.getWidget(InterfaceID.GeOffers.DETAILS);
		return details != null && !details.isSelfHidden();
	}

	private static boolean isSetupVisible(Client client)
	{
		Widget setup = client.getWidget(InterfaceID.GeOffers.SETUP);
		return setup != null && !setup.isSelfHidden();
	}

	private static int findItemIdInTree(Widget widget)
	{
		if (widget == null)
		{
			return -1;
		}

		int itemId = widget.getItemId();
		if (itemId > 0)
		{
			return itemId;
		}

		Widget[] children = widget.getChildren();
		if (children == null)
		{
			return -1;
		}

		for (Widget child : children)
		{
			int found = findItemIdInTree(child);
			if (found > 0)
			{
				return found;
			}
		}

		return -1;
	}

	private static int itemFromHighlightedSlot(Client client)
	{
		GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
		if (offers == null)
		{
			return -1;
		}

		int highlightedSlot = -1;
		int maxOpacity = -1;
		for (int slot = 0; slot < SLOT_COUNT; slot++)
		{
			Widget index = client.getWidget(InterfaceID.GeOffers.INDEX_0 + slot);
			if (index == null || index.isSelfHidden())
			{
				continue;
			}

			int opacity = index.getOpacity();
			if (opacity > maxOpacity)
			{
				maxOpacity = opacity;
				highlightedSlot = slot;
			}
		}

		if (highlightedSlot < 0 || highlightedSlot >= offers.length)
		{
			return -1;
		}

		GrandExchangeOffer offer = offers[highlightedSlot];
		if (offer == null || offer.getState() == GrandExchangeOfferState.EMPTY)
		{
			return -1;
		}

		return offer.getItemId();
	}

	private static int soleActiveOfferItem(Client client)
	{
		GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
		if (offers == null)
		{
			return -1;
		}

		int activeItem = -1;
		int activeCount = 0;
		for (GrandExchangeOffer offer : offers)
		{
			if (offer == null || offer.getState() == GrandExchangeOfferState.EMPTY)
			{
				continue;
			}

			activeCount++;
			activeItem = offer.getItemId();
		}

		return activeCount == 1 ? activeItem : -1;
	}
}
