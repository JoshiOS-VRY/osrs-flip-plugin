package com.osrsflipfinder.runelite;

import java.awt.BorderLayout;
import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.LinkBrowser;

/** Live GE slot dashboard with optional server-side price issue badges. */
class MySlotsPanel extends SidebarContentPanel
{
	private final Client client;
	private final ItemManager itemManager;
	private final GeSlotTracker slotTracker;
	private final PortfolioClient portfolioClient;
	private final FlipFinderConfig config;
	private final ScheduledExecutorService executorService;

	private final JPanel rows = PluginUi.listContainer();
	private final JLabel statusLabel = PluginUi.caption("No active offers");
	private ScheduledFuture<?> pollTask;

	@Inject
	MySlotsPanel(
		Client client,
		ItemManager itemManager,
		GeSlotTracker slotTracker,
		PortfolioClient portfolioClient,
		FlipFinderConfig config,
		ScheduledExecutorService executorService
	)
	{
		this.client = client;
		this.itemManager = itemManager;
		this.slotTracker = slotTracker;
		this.portfolioClient = portfolioClient;
		this.config = config;
		this.executorService = executorService;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		PluginUi.transparent(this);
		add(rows);
		add(statusLabel);

		portfolioClient.setSlotsListener(slots -> SwingUtilities.invokeLater(this::refreshLocal));
	}

	void setActive(boolean active)
	{
		if (active && pollTask == null)
		{
			pollTask = executorService.scheduleAtFixedRate(
				() -> SwingUtilities.invokeLater(this::refreshLocal),
				0,
				1,
				TimeUnit.SECONDS
			);
		}
		else if (!active && pollTask != null)
		{
			pollTask.cancel(false);
			pollTask = null;
		}
	}

	void refreshLocal()
	{
		rows.removeAll();
		Map<Integer, EnrichedOpenGeOffer> enrichedBySlot = enrichedBySlot();

		GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
		int active = 0;
		if (offers != null)
		{
			for (int slot = 0; slot < Math.min(8, offers.length); slot++)
			{
				GrandExchangeOffer offer = offers[slot];
				if (offer == null || offer.getState() == GrandExchangeOfferState.EMPTY)
				{
					continue;
				}
				active++;
				rows.add(buildRow(slot, offer, enrichedBySlot.get(slot)));
			}
		}

		if (portfolioClient.isSlotsOffline() && portfolioClient.getSlotsCachedAt() != null)
		{
			statusLabel.setText("Offline · cached " + LocalCacheStore.formatCachedAt(portfolioClient.getSlotsCachedAt()));
		}
		else if (active == 0)
		{
			statusLabel.setText("No active GE offers");
		}
		else
		{
			statusLabel.setText(active + " active slot" + (active == 1 ? "" : "s"));
		}

		revalidate();
		repaint();
	}

	private Map<Integer, EnrichedOpenGeOffer> enrichedBySlot()
	{
		Map<Integer, EnrichedOpenGeOffer> map = new HashMap<>();
		SlotsLiveResponse response = portfolioClient.getLatestSlots();
		if (response == null || response.getOffers() == null)
		{
			return map;
		}
		for (EnrichedOpenGeOffer offer : response.getOffers())
		{
			if (offer.getSlot() != null)
			{
				map.put(offer.getSlot(), offer);
			}
		}
		return map;
	}

	private JPanel buildRow(int slot, GrandExchangeOffer offer, EnrichedOpenGeOffer enriched)
	{
		JPanel card = PluginUi.card();
		String side = offer.getState() == GrandExchangeOfferState.BUYING
			|| offer.getState() == GrandExchangeOfferState.BOUGHT
			? "BUY" : "SELL";
		String name = itemManager.getItemComposition(offer.getItemId()).getName();

		JLabel title = new JLabel("Slot " + (slot + 1) + " · " + side + " · " + name);
		title.setForeground(Color.WHITE);
		title.setFont(title.getFont().deriveFont(11f));

		JLabel price = PluginUi.caption(MarketFormat.gp(offer.getPrice())
			+ " · " + offer.getQuantitySold() + "/" + offer.getTotalQuantity());

		GeSlotTracker.SlotState tracked = slotTracker.snapshot().stream()
			.filter(s -> s.slot == slot)
			.findFirst()
			.orElse(null);
		String timerText = tracked != null
			? formatInactive(tracked.inactiveSeconds())
			: "—";

		JLabel timer = PluginUi.caption("⏱ " + timerText + " since activity");
		card.add(title);
		card.add(PluginUi.gap(2));
		card.add(price);
		card.add(timer);

		if (enriched != null && enriched.getPriceIssue() != null)
		{
			JLabel issue = PluginUi.caption(formatIssue(enriched.getPriceIssue(), enriched.getDeltaPercent()));
			issue.setForeground(PluginUi.WARNING);
			card.add(issue);
		}

		return card;
	}

	private static String formatInactive(long seconds)
	{
		if (seconds < 60)
		{
			return seconds + "s";
		}
		long minutes = seconds / 60;
		if (minutes < 60)
		{
			return minutes + "m";
		}
		return (minutes / 60) + "h " + (minutes % 60) + "m";
	}

	private static String formatIssue(String issue, Double delta)
	{
		String label = issue.replace('_', ' ');
		if (delta != null)
		{
			return "⚠ " + label + " (" + String.format("%.1f", Math.abs(delta)) + "%)";
		}
		return "⚠ " + label;
	}
}
