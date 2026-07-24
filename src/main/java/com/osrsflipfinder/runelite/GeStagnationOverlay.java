package com.osrsflipfinder.runelite;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.VarPlayer;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Display-only stagnation timers on active GE slots.
 */
@Slf4j
public class GeStagnationOverlay extends Overlay
{
	private final Client client;
	private final FlipFinderConfig config;
	private final GeSlotTracker slotTracker;

	@Inject
	GeStagnationOverlay(Client client, FlipFinderConfig config, GeSlotTracker slotTracker)
	{
		this.client = client;
		this.config = config;
		this.slotTracker = slotTracker;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.enableStagnationOverlay())
		{
			return null;
		}

		GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
		if (offers == null)
		{
			return null;
		}

		long thresholdSec = config.tradeStagnationMinutes() * 60L;
		int drawn = 0;

		for (GeSlotTracker.SlotState state : slotTracker.snapshot())
		{
			if (state.slot < 0 || state.slot >= offers.length)
			{
				continue;
			}
			GrandExchangeOffer offer = offers[state.slot];
			if (offer == null || offer.getState() == GrandExchangeOfferState.EMPTY)
			{
				continue;
			}

			long inactive = state.inactiveSeconds();
			String text = formatInactive(inactive);
			boolean stagnant = inactive >= thresholdSec;

			// Fallback position: stack timers top-left when widget anchoring unavailable
			int x = 10;
			int y = 120 + state.slot * 18;
			graphics.setColor(stagnant ? PluginUi.WARNING : PluginUi.GOLD);
			graphics.drawString("S" + (state.slot + 1) + " " + text, x, y);
			drawn++;
		}

		return drawn > 0 ? new Dimension(80, 160) : null;
	}

	private static String formatInactive(long seconds)
	{
		if (seconds < 60)
		{
			return seconds + "s";
		}
		return (seconds / 60) + "m";
	}
}
