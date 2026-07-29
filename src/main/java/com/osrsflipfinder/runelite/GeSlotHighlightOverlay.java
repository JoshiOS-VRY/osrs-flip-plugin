package com.osrsflipfinder.runelite;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Display-only borders on GE offer slots when FlipX detects a pricing issue.
 * Only drawn on the slot grid — hidden in offer details/setup.
 */
class GeSlotHighlightOverlay extends Overlay
{
	private static final int SLOT_COUNT = 8;

	private final Client client;
	private final FlipFinderConfig config;
	private final ItemsClient itemsClient;
	private final GeSlotTracker slotTracker;

	@Inject
	GeSlotHighlightOverlay(
		Client client,
		FlipFinderConfig config,
		ItemsClient itemsClient,
		GeSlotTracker slotTracker
	)
	{
		this.client = client;
		this.config = config;
		this.itemsClient = itemsClient;
		this.slotTracker = slotTracker;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.enableGeSlotHighlights())
		{
			return null;
		}

		if (!GeItemResolver.isSlotGridVisible(client))
		{
			return null;
		}

		Widget universe = client.getWidget(InterfaceID.GeOffers.UNIVERSE);
		if (universe == null || universe.isSelfHidden())
		{
			return null;
		}

		Rectangle geWindow = universe.getBounds();
		if (geWindow == null || geWindow.width <= 0 || geWindow.height <= 0)
		{
			return null;
		}

		GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
		if (offers == null)
		{
			return null;
		}

		long stagnationSec = config.tradeStagnationMinutes() * 60L;
		List<ColoredRect> highlights = new ArrayList<>();

		for (int slot = 0; slot < Math.min(SLOT_COUNT, offers.length); slot++)
		{
			GrandExchangeOffer offer = offers[slot];
			if (offer == null || offer.getState() == GrandExchangeOfferState.EMPTY)
			{
				continue;
			}

			Color border = borderForSlot(slot, offer, stagnationSec);
			if (border == null)
			{
				continue;
			}

			Rectangle canvas = GeSlotBounds.boundsForSlot(client, slot);
			if (canvas == null || !geWindow.intersects(canvas))
			{
				continue;
			}

			highlights.add(new ColoredRect(canvas, border));
		}

		if (highlights.isEmpty())
		{
			return null;
		}

		Rectangle union = highlights.get(0).bounds;
		for (int i = 1; i < highlights.size(); i++)
		{
			union = union.union(highlights.get(i).bounds);
		}

		setPreferredLocation(new java.awt.Point(union.x, union.y));

		for (ColoredRect highlight : highlights)
		{
			Rectangle r = highlight.bounds;
			int x = r.x - union.x;
			int y = r.y - union.y;
			graphics.setColor(highlight.color);
			graphics.setStroke(new BasicStroke(2f));
			graphics.drawRoundRect(x, y, r.width, r.height, 6, 6);
		}

		return new Dimension(union.width, union.height);
	}

	private Color borderForSlot(int slot, GrandExchangeOffer offer, long stagnationSec)
	{
		if (offer.getState() != GrandExchangeOfferState.BUYING
			&& offer.getState() != GrandExchangeOfferState.SELLING)
		{
			return null;
		}

		boolean isBuy = offer.getState() == GrandExchangeOfferState.BUYING;
		int limitPrice = GeOfferPricing.limitPrice(offer);
		int unitFillPrice = GeOfferPricing.unitPrice(offer);
		FlipOpportunity opp = itemsClient.peekOpportunity(offer.getItemId());

		Color lockedLoss = lockedLossBorder(
			isBuy,
			limitPrice,
			unitFillPrice,
			offer.getItemId(),
			offer.getQuantitySold(),
			opp
		);
		if (lockedLoss != null)
		{
			return lockedLoss;
		}

		GeSlotTracker.SlotState tracked = slotTracker.snapshot().stream()
			.filter(s -> s.slot == slot)
			.findFirst()
			.orElse(null);
		long inactive = tracked != null ? tracked.inactiveSeconds() : 0;
		boolean stagnant = inactive >= stagnationSec;

		OfferPriceAnalyzer.Analysis analysis = OfferPriceAnalyzer.analyze(
			isBuy,
			limitPrice,
			opp,
			OfferPriceAnalyzer.DEFAULT_THRESHOLD_PERCENT,
			stagnant,
			inactive,
			offer.getQuantitySold(),
			offer.getTotalQuantity(),
			stagnationSec,
			unitFillPrice
		);

		if (analysis.action == OfferPriceAnalyzer.Action.ABORT_FLIP)
		{
			return PluginUi.NEGATIVE;
		}
		if (analysis.action == OfferPriceAnalyzer.Action.WAIT && analysis.issue != null)
		{
			return PluginUi.WARNING;
		}
		if (analysis.issue != null
			&& (analysis.action == OfferPriceAnalyzer.Action.REPRICE_BUY
			|| analysis.action == OfferPriceAnalyzer.Action.REPRICE_SELL))
		{
			return issueBorderColor(analysis.issue);
		}
		if (stagnant)
		{
			return PluginUi.WARNING.darker();
		}
		return null;
	}

	/**
	 * Red when the current offer price implies zero or negative net after GE tax
	 * (same economics as the copilot), even if the offer is within 1% of FlipX market.
	 */
	private static Color lockedLossBorder(
		boolean isBuy,
		long limitPrice,
		long unitFillPrice,
		int itemId,
		int quantityFilled,
		FlipOpportunity opp
	)
	{
		if (opp == null || limitPrice <= 0)
		{
			return null;
		}

		long netAtOffer;
		if (isBuy)
		{
			long buyForNet = GeOfferPricing.effectiveBuyForNet(
				limitPrice,
				opp.getEstimatedBuyPrice(),
				quantityFilled,
				unitFillPrice
			);
			long sellBasis = opp.getEstimatedSellPrice();
			if (sellBasis <= 0)
			{
				return null;
			}
			netAtOffer = OfferPriceAnalyzer.estimateNetPerItem(buyForNet, sellBasis, itemId);
		}
		else
		{
			long sellForNet = GeOfferPricing.effectiveSellForNet(
				limitPrice,
				opp.getEstimatedSellPrice(),
				quantityFilled,
				unitFillPrice
			);
			long buyBasis = opp.getEstimatedBuyPrice();
			if (buyBasis <= 0)
			{
				return null;
			}
			netAtOffer = OfferPriceAnalyzer.estimateNetPerItem(buyBasis, sellForNet, itemId);
			long breakEven = GeTax.breakEvenSellPrice(buyBasis, itemId);
			if (breakEven > 0 && limitPrice < breakEven)
			{
				return PluginUi.NEGATIVE;
			}
		}

		if (netAtOffer <= 0 && netAtOffer > Long.MIN_VALUE / 4)
		{
			return PluginUi.NEGATIVE;
		}
		return null;
	}

	private static Color issueBorderColor(OfferPriceAnalyzer.Issue issue)
	{
		switch (issue)
		{
			case BUY_OVERBID:
			case SELL_OVERCUT:
				return PluginUi.NEGATIVE;
			case BUY_UNDERBID:
			case SELL_UNDERCUT:
			default:
				return PluginUi.WARNING;
		}
	}

	private static final class ColoredRect
	{
		final Rectangle bounds;
		final Color color;

		ColoredRect(Rectangle bounds, Color color)
		{
			this.bounds = bounds;
			this.color = color;
		}
	}
}
