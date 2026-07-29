package com.osrsflipfinder.runelite;

import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

/**
 * Display-only FlipX tooltip when hovering a GE slot on the main offer grid.
 */
class GeSlotTooltipOverlay extends Overlay
{
	private final Client client;
	private final FlipFinderConfig config;
	private final ItemsClient itemsClient;
	private final GeSlotTracker slotTracker;
	private final ItemManager itemManager;
	private final TooltipManager tooltipManager;

	@Inject
	GeSlotTooltipOverlay(
		Client client,
		FlipFinderConfig config,
		ItemsClient itemsClient,
		GeSlotTracker slotTracker,
		ItemManager itemManager,
		TooltipManager tooltipManager
	)
	{
		this.client = client;
		this.config = config;
		this.itemsClient = itemsClient;
		this.slotTracker = slotTracker;
		this.itemManager = itemManager;
		this.tooltipManager = tooltipManager;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.enableGeSlotTooltips())
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

		net.runelite.api.Point mouse = client.getMouseCanvasPosition();
		if (mouse == null)
		{
			return null;
		}
		int slot = GeSlotBounds.hitTest(client, mouse.getX(), mouse.getY());
		if (slot < 0)
		{
			return null;
		}

		GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
		if (offers == null || slot >= offers.length)
		{
			return null;
		}

		GrandExchangeOffer offer = offers[slot];
		if (offer == null || offer.getState() == GrandExchangeOfferState.EMPTY)
		{
			return null;
		}

		String tooltip = buildTooltip(slot, offer);
		if (tooltip == null || tooltip.isBlank())
		{
			return null;
		}

		tooltipManager.add(new Tooltip(GeSlotTooltipBuilder.formatForRuneliteOverlay(tooltip)));
		return null;
	}

	private String buildTooltip(int slot, GrandExchangeOffer offer)
	{
		boolean isBuy = offer.getState() == GrandExchangeOfferState.BUYING
			|| offer.getState() == GrandExchangeOfferState.BOUGHT;
		int itemId = offer.getItemId();
		String name = itemManager.getItemComposition(itemId).getName();
		int limitPrice = GeOfferPricing.limitPrice(offer);
		int unitFillPrice = GeOfferPricing.unitPrice(offer);
		int filled = offer.getQuantitySold();
		int totalQty = offer.getTotalQuantity();
		FlipOpportunity opp = itemsClient.peekOpportunity(itemId);

		long stagnationSec = config.tradeStagnationMinutes() * 60L;
		GeSlotTracker.SlotState tracked = slotTracker.snapshot().stream()
			.filter(s -> s.slot == slot)
			.findFirst()
			.orElse(null);
		long inactive = tracked != null ? tracked.inactiveSeconds() : 0;
		boolean stagnant = inactive >= stagnationSec
			&& (offer.getState() == GrandExchangeOfferState.BUYING
			|| offer.getState() == GrandExchangeOfferState.SELLING);

		OfferPriceAnalyzer.Analysis analysis = analyze(offer, isBuy, limitPrice, unitFillPrice, opp, stagnant, inactive, filled, totalQty, stagnationSec);

		long breakEvenSell = 0;
		if (opp != null && !isBuy && offer.getState() == GrandExchangeOfferState.SELLING)
		{
			long buyBasis = opp.getEstimatedBuyPrice();
			if (buyBasis > 0)
			{
				breakEvenSell = GeTax.breakEvenSellPrice(buyBasis, itemId);
			}
		}
		else if (opp != null && isBuy)
		{
			long buyForNet = GeOfferPricing.effectiveBuyForNet(
				limitPrice,
				opp.getEstimatedBuyPrice(),
				filled,
				unitFillPrice
			);
			breakEvenSell = GeTax.breakEvenSellPrice(buyForNet, itemId);
		}

		GeSlotTooltipBuilder.GeSlotTooltipContext ctx = new GeSlotTooltipBuilder.GeSlotTooltipContext(
			name,
			offer.getState(),
			isBuy,
			limitPrice,
			unitFillPrice,
			filled,
			totalQty,
			breakEvenSell,
			opp,
			analysis,
			stagnant,
			inactive
		);
		return GeSlotTooltipBuilder.buildGeSlotHoverTooltip(ctx);
	}

	private static OfferPriceAnalyzer.Analysis analyze(
		GrandExchangeOffer offer,
		boolean isBuy,
		int limitPrice,
		int unitFillPrice,
		FlipOpportunity opp,
		boolean stagnant,
		long inactive,
		int qtyFilled,
		int totalQty,
		long stagnationSec
	)
	{
		if (offer.getState() == GrandExchangeOfferState.BUYING)
		{
			return OfferPriceAnalyzer.analyze(
				true,
				limitPrice,
				opp,
				OfferPriceAnalyzer.DEFAULT_THRESHOLD_PERCENT,
				stagnant,
				inactive,
				qtyFilled,
				totalQty,
				stagnationSec,
				unitFillPrice
			);
		}
		if (offer.getState() == GrandExchangeOfferState.SELLING)
		{
			return OfferPriceAnalyzer.analyze(
				false,
				limitPrice,
				opp,
				OfferPriceAnalyzer.DEFAULT_THRESHOLD_PERCENT,
				stagnant,
				inactive,
				qtyFilled,
				totalQty,
				stagnationSec,
				unitFillPrice
			);
		}
		return OfferPriceAnalyzer.Analysis.none(null, null, opp, stagnant, inactive);
	}
}
