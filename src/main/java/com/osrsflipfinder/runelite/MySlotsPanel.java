package com.osrsflipfinder.runelite;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.LinkBrowser;

/** Live GE flip command center - price alerts, stagnation, and slot status. */
class MySlotsPanel extends SidebarContentPanel
{
	private static final long LOCAL_GE_POLL_MS = 2_000L;

	private final Client client;
	private final ClientThread clientThread;
	private final ItemManager itemManager;
	private final GeSlotTracker slotTracker;
	private final PortfolioClient portfolioClient;
	private final ItemsClient itemsClient;
	private final FlipFinderConfig config;
	private final ScheduledExecutorService executorService;

	private final JLabel summaryLabel = PluginUi.caption(" ");
	private final JPanel rows = PluginUi.listContainer();
	private final JLabel statusLabel = PluginUi.caption("Open the GE or place offers to track slots");
	private final JLabel refreshTimerLabel = PluginUi.caption(" ");
	private final AtomicBoolean priceFetchInFlight = new AtomicBoolean(false);
	private final SlotAnalysisStabilizer stabilizer = new SlotAnalysisStabilizer();
	private volatile ScheduledFuture<?> debouncedRefresh;
	private String lastRenderKey = "";
	private volatile long nextLocalGeRefreshAtMs;

	private ScheduledFuture<?> pollTask;

	@Inject
	MySlotsPanel(
		Client client,
		ClientThread clientThread,
		ItemManager itemManager,
		GeSlotTracker slotTracker,
		PortfolioClient portfolioClient,
		ItemsClient itemsClient,
		FlipFinderConfig config,
		ScheduledExecutorService executorService
	)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.itemManager = itemManager;
		this.slotTracker = slotTracker;
		this.portfolioClient = portfolioClient;
		this.itemsClient = itemsClient;
		this.config = config;
		this.executorService = executorService;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		PluginUi.transparent(this);

		add(PluginUi.sectionHeader("Flip manager"));
		add(PluginUi.gap(PluginUi.SPACING_XS));
		summaryLabel.setAlignmentX(LEFT_ALIGNMENT);
		SidebarContentPanel.lockWidth(summaryLabel);
		add(summaryLabel);
		add(PluginUi.gap(PluginUi.SPACING_SM));
		add(rows);
		add(PluginUi.gap(PluginUi.SPACING_SM));
		statusLabel.setAlignmentX(LEFT_ALIGNMENT);
		SidebarContentPanel.lockWidth(statusLabel);
		add(statusLabel);
		refreshTimerLabel.setAlignmentX(LEFT_ALIGNMENT);
		refreshTimerLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		SidebarContentPanel.lockWidth(refreshTimerLabel);
		add(refreshTimerLabel);

		JButton portfolioLink = PluginUi.externalLinkButton("Open portfolio on web");
		portfolioLink.addActionListener(e ->
			LinkBrowser.browse(FlipXConstants.baseUrl() + "/portfolio")
		);
		PluginUi.fullWidth(portfolioLink);
		add(portfolioLink);

		if (!config.enableUpload())
		{
			JLabel uploadHint = PluginUi.cardHint(
				"GE upload off - alerts use live GE prices + FlipX market data. Enable upload to sync portfolio."
			);
			add(PluginUi.gap(PluginUi.SPACING_XS));
			add(uploadHint);
		}

		portfolioClient.setSlotsListener(slots -> refreshLocal());
	}

	void setActive(boolean active)
	{
		if (active && pollTask == null)
		{
			nextLocalGeRefreshAtMs = System.currentTimeMillis() + LOCAL_GE_POLL_MS;
			pollTask = executorService.scheduleAtFixedRate(
				() ->
				{
					nextLocalGeRefreshAtMs = System.currentTimeMillis() + LOCAL_GE_POLL_MS;
					refreshLocal();
				},
				0,
				LOCAL_GE_POLL_MS,
				TimeUnit.MILLISECONDS
			);
			refreshLocal();
			portfolioClient.refreshSlotsNow();
		}
		else if (!active && pollTask != null)
		{
			pollTask.cancel(false);
			pollTask = null;
			nextLocalGeRefreshAtMs = 0;
		}
	}

	void updateRefreshTimer(boolean paired)
	{
		if (!paired)
		{
			refreshTimerLabel.setText(" ");
			return;
		}

		String portfolio = RefreshCountdown.formatPolling(
			portfolioClient.getNextRefreshAtMs(),
			portfolioClient.isActive(),
			portfolioClient.isFetchInProgress()
		);
		String localGe = pollTask != null
			? RefreshCountdown.formatPolling(nextLocalGeRefreshAtMs, true, false)
			: " ";
		refreshTimerLabel.setText(RefreshCountdown.combine(portfolio, localGe));
	}

	void refreshLocal()
	{
		if (debouncedRefresh != null)
		{
			debouncedRefresh.cancel(false);
		}
		debouncedRefresh = executorService.schedule(
			() -> clientThread.invokeLater(this::collectSlotViewsOnClientThread),
			350,
			TimeUnit.MILLISECONDS
		);
	}

	private void collectSlotViewsOnClientThread()
	{
		Map<Integer, EnrichedOpenGeOffer> enrichedBySlot = enrichedBySlot();
		long stagnationSec = config.tradeStagnationMinutes() * 60L;

		List<SlotView> views = new ArrayList<>();
		boolean[] slotActive = new boolean[8];
		GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
		if (offers != null)
		{
			for (int slot = 0; slot < Math.min(8, offers.length); slot++)
			{
				GrandExchangeOffer offer = offers[slot];
				if (offer == null || offer.getState() == GrandExchangeOfferState.EMPTY)
				{
					continue;
				}
				slotActive[slot] = true;
				views.add(buildView(slot, offer, enrichedBySlot.get(slot), stagnationSec));
			}
		}
		for (int slot = 0; slot < slotActive.length; slot++)
		{
			if (!slotActive[slot])
			{
				stabilizer.clearSlot(slot);
			}
		}

		views.sort(SlotView.alertFirst());
		String renderKey = SlotView.displayKey(views);
		SwingUtilities.invokeLater(() -> renderSlotViews(views, renderKey));
	}

	private void renderSlotViews(List<SlotView> views, String renderKey)
	{
		if (renderKey.equals(lastRenderKey))
		{
			return;
		}
		lastRenderKey = renderKey;

		rows.removeAll();
		int alertCount = 0;
		int watchCount = 0;
		int stagnantCount = 0;
		for (SlotView view : views)
		{
			if (SlotView.needsReprice(view.analysis))
			{
				alertCount++;
			}
			else if (SlotView.isWatch(view.analysis))
			{
				watchCount++;
			}
			if (view.stagnant)
			{
				stagnantCount++;
			}
			rows.add(new GeSlotRow(
				view.slot,
				view.state,
				view.itemId,
				view.itemName,
				view.isBuy,
				view.offerPrice,
				view.filled,
				view.totalQty,
				view.inactiveSeconds,
				view.stagnant,
				view.analysis,
				itemManager
			));
			rows.add(PluginUi.gap(PluginUi.SPACING_XS));
		}

		updateSummary(views.size(), alertCount, watchCount, stagnantCount);
		updateStatus(views.size());
		prefetchMarketPrices(views);

		revalidate();
		repaint();
	}

	private void updateSummary(int active, int alerts, int watches, int stagnant)
	{
		if (active == 0)
		{
			PluginUi.setMultilineCaption(summaryLabel, "No active GE offers", ColorScheme.LIGHT_GRAY_COLOR);
			return;
		}
		StringBuilder sb = new StringBuilder();
		sb.append(active).append(" active slot").append(active == 1 ? "" : "s");
		Color summaryColor = PluginUi.POSITIVE;
		if (alerts > 0)
		{
			sb.append(" | ").append(alerts).append(" reprice").append(alerts == 1 ? "" : "s");
			summaryColor = PluginUi.NEGATIVE;
		}
		else if (watches > 0)
		{
			summaryColor = PluginUi.WARNING;
		}
		if (watches > 0)
		{
			sb.append(" | ").append(watches).append(" watch").append(watches == 1 ? "" : "es");
		}
		if (stagnant > 0)
		{
			sb.append(" | ").append(stagnant).append(" stagnant");
			if (alerts == 0 && watches == 0)
			{
				summaryColor = PluginUi.WARNING;
			}
		}
		PluginUi.setMultilineCaption(summaryLabel, sb.toString(), summaryColor);
	}

	private void updateStatus(int active)
	{
		if (active == 0)
		{
			PluginUi.setMultilineCaption(statusLabel, "Place offers in-game - updates every few seconds");
			return;
		}
		if (portfolioClient.isSlotsOffline() && portfolioClient.getSlotsCachedAt() != null)
		{
			PluginUi.setMultilineCaption(statusLabel,
				"Portfolio sync offline | cached "
					+ LocalCacheStore.formatCachedAt(portfolioClient.getSlotsCachedAt())
					+ " | local alerts still active"
			);
		}
		else if (!config.enableUpload())
		{
			PluginUi.setMultilineCaption(statusLabel, "Local GE tracking | enable upload for web portfolio sync");
		}
		else
		{
			PluginUi.setMultilineCaption(statusLabel, "Live GE + synced portfolio");
		}
	}

	private SlotView buildView(
		int slot,
		GrandExchangeOffer offer,
		EnrichedOpenGeOffer enriched,
		long stagnationSec
	)
	{
		boolean isBuy = offer.getState() == GrandExchangeOfferState.BUYING
			|| offer.getState() == GrandExchangeOfferState.BOUGHT;
		int itemId = offer.getItemId();
		String name = itemManager.getItemComposition(itemId).getName();
		FlipOpportunity opp = itemsClient.peekOpportunity(itemId);

		GeSlotTracker.SlotState tracked = slotTracker.snapshot().stream()
			.filter(s -> s.slot == slot)
			.findFirst()
			.orElse(null);
		long inactive = tracked != null ? tracked.inactiveSeconds() : 0;
		boolean rawStagnant = inactive >= stagnationSec
			&& (offer.getState() == GrandExchangeOfferState.BUYING
			|| offer.getState() == GrandExchangeOfferState.SELLING);
		boolean stagnant = stabilizer.stagnantForUi(slot, inactive, stagnationSec, rawStagnant);

		OfferPriceAnalyzer.Analysis local;
		if (offer.getState() == GrandExchangeOfferState.BUYING)
		{
			local = OfferPriceAnalyzer.analyze(
				true,
				offer.getPrice(),
				opp,
				OfferPriceAnalyzer.DEFAULT_THRESHOLD_PERCENT,
				stagnant,
				inactive,
				offer.getQuantitySold(),
				offer.getTotalQuantity(),
				stagnationSec
			);
		}
		else if (offer.getState() == GrandExchangeOfferState.SELLING)
		{
			local = OfferPriceAnalyzer.analyze(
				false,
				offer.getPrice(),
				opp,
				OfferPriceAnalyzer.DEFAULT_THRESHOLD_PERCENT,
				stagnant,
				inactive,
				offer.getQuantitySold(),
				offer.getTotalQuantity(),
				stagnationSec
			);
		}
		else
		{
			local = OfferPriceAnalyzer.Analysis.none(null, null, opp, stagnant, inactive);
		}

		OfferPriceAnalyzer.Analysis analysis = stabilizer.stabilize(
			slot,
			OfferPriceAnalyzer.mergePreferLocal(local, enriched)
		);

		return new SlotView(
			slot,
			offer.getState(),
			itemId,
			name,
			isBuy,
			offer.getPrice(),
			offer.getQuantitySold(),
			offer.getTotalQuantity(),
			inactive,
			stagnant,
			analysis
		);
	}

	private void prefetchMarketPrices(List<SlotView> views)
	{
		if (views.isEmpty() || config.apiKey().isBlank())
		{
			return;
		}
		if (!priceFetchInFlight.compareAndSet(false, true))
		{
			return;
		}
		executorService.execute(() ->
		{
			try
			{
				for (SlotView view : views)
				{
					if (itemsClient.isStale(view.itemId))
					{
						try
						{
							itemsClient.fetch(view.itemId);
						}
						catch (Exception ignored)
						{
							// keep local-only row
						}
					}
				}
			}
			finally
			{
				priceFetchInFlight.set(false);
				refreshLocal();
			}
		});
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

	private static final class SlotView
	{
		final int slot;
		final GrandExchangeOfferState state;
		final int itemId;
		final String itemName;
		final boolean isBuy;
		final long offerPrice;
		final int filled;
		final int totalQty;
		final long inactiveSeconds;
		final boolean stagnant;
		final OfferPriceAnalyzer.Analysis analysis;

		SlotView(
			int slot,
			GrandExchangeOfferState state,
			int itemId,
			String itemName,
			boolean isBuy,
			long offerPrice,
			int filled,
			int totalQty,
			long inactiveSeconds,
			boolean stagnant,
			OfferPriceAnalyzer.Analysis analysis
		)
		{
			this.slot = slot;
			this.state = state;
			this.itemId = itemId;
			this.itemName = itemName;
			this.isBuy = isBuy;
			this.offerPrice = offerPrice;
			this.filled = filled;
			this.totalQty = totalQty;
			this.inactiveSeconds = inactiveSeconds;
			this.stagnant = stagnant;
			this.analysis = analysis;
		}

		static Comparator<SlotView> alertFirst()
		{
			return Comparator
				.<SlotView>comparingInt(v -> v.analysis != null && needsReprice(v.analysis) ? 0 : 1)
				.thenComparingInt(v -> v.analysis != null && isWatch(v.analysis) ? 0 : 1)
				.thenComparingInt(v -> v.stagnant ? 0 : 1)
				.thenComparingInt(v -> v.slot);
		}

		static String displayKey(List<SlotView> views)
		{
			StringBuilder sb = new StringBuilder();
			for (SlotView v : views)
			{
				sb.append(v.slot).append(':')
					.append(v.state).append(':')
					.append(v.offerPrice).append(':')
					.append(v.filled).append('/').append(v.totalQty).append(':')
					.append(v.stagnant).append(':');
				if (v.analysis != null)
				{
					sb.append(v.analysis.action).append(':')
						.append(v.analysis.issue).append(':')
						.append(v.analysis.recommendedPrice).append(':')
						.append(v.analysis.marketPrice).append(':')
						.append(v.analysis.actionLine).append(':');
				}
				if (v.stagnant)
				{
					sb.append(v.inactiveSeconds / 60).append(':');
				}
				sb.append('|');
			}
			return sb.toString();
		}

		static boolean needsReprice(OfferPriceAnalyzer.Analysis analysis)
		{
			return analysis != null
				&& (analysis.action == OfferPriceAnalyzer.Action.REPRICE_BUY
				|| analysis.action == OfferPriceAnalyzer.Action.REPRICE_SELL
				|| analysis.action == OfferPriceAnalyzer.Action.ABORT_FLIP);
		}

		static boolean isWatch(OfferPriceAnalyzer.Analysis analysis)
		{
			return analysis != null
				&& analysis.action == OfferPriceAnalyzer.Action.WAIT
				&& analysis.issue != null;
		}
	}
}
