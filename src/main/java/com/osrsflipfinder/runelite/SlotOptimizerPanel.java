package com.osrsflipfinder.runelite;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;

/** GE slot optimizer with filters, sort, and actionable buy/sell/qty metrics. */
class SlotOptimizerPanel extends SidebarContentPanel
{
	private static final int ROW_HEIGHT = 52;
	private static final int CAPITAL_DEBOUNCE_MS = 350;

	private static final String[] SORT_IDS = {
		"gpPerSlotHour", "score", "netProfit", "roi", "totalProfit", "gpPerHour",
		"buy", "sell", "qty", "capital", "vol5m", "vol1h", "turnover"
	};
	private static final String[] SORT_LABELS = {
		"GP/slot-hr", "Score", "Net / item", "Net ROI", "Total profit", "GP / hr",
		"Est. buy", "Est. sell", "Qty / limit", "Est. capital", "Vol 5m", "Vol 1h", "Full profit est."
	};

	private final PluginApiClient apiClient;
	private final OpportunitiesClient opportunitiesClient;
	private final BookmarksClient bookmarksClient;
	private final CoinBalanceService coinBalanceService;
	private final ItemManager itemManager;
	private final Client client;
	private final ClientThread clientThread;
	private final ScheduledExecutorService executorService;
	private final FlipFinderConfig config;
	private final ConfigManager configManager;
	private final Consumer<String> errorListener;
	private final Consumer<SlotRecommendation> onItemSelected;
	private final SlotOptimizerFiltersPanel filtersPanel;
	private final FilterBookmarksBar slotBookmarksBar;

	private final JLabel statusLabel = PluginUi.caption("Loading slots...");
	private final JLabel marketRefreshTimerLabel = PluginUi.caption(" ");
	private final JLabel filterNoticeLabel = PluginUi.hint(" ");
	private final JTextField maxCapitalField = PluginUi.textField("");
	private final JComboBox<String> sortCombo = new JComboBox<>(new DefaultComboBoxModel<>(SORT_LABELS));
	private final JCheckBox sortDescCheck = PluginUi.checkBox("High to low", true);
	private final JPanel summaryPanel = new JPanel();
	private final JPanel listContainer = PluginUi.listContainer();

	private SlotsResponse lastResponse;
	private long lastMetaUpdatedMs;
	private Long lastMaxCapital;
	private String lastEntitlementsKey;
	private String lastQueryKey;
	private GeSlotOccupancy.Snapshot lastOccupancy;
	private volatile ScheduledFuture<?> debouncedCapitalLoad;

	SlotOptimizerPanel(
		PluginApiClient apiClient,
		OpportunitiesClient opportunitiesClient,
		BookmarksClient bookmarksClient,
		CoinBalanceService coinBalanceService,
		ItemManager itemManager,
		Client client,
		ClientThread clientThread,
		ScheduledExecutorService executorService,
		FlipFinderConfig config,
		ConfigManager configManager,
		Runnable onBack,
		Consumer<String> errorListener,
		Consumer<SlotRecommendation> onItemSelected
	)
	{
		this.apiClient = apiClient;
		this.opportunitiesClient = opportunitiesClient;
		this.bookmarksClient = bookmarksClient;
		this.coinBalanceService = coinBalanceService;
		this.itemManager = itemManager;
		this.client = client;
		this.clientThread = clientThread;
		this.executorService = executorService;
		this.config = config;
		this.configManager = configManager;
		this.errorListener = errorListener;
		this.onItemSelected = onItemSelected;
		this.filtersPanel = new SlotOptimizerFiltersPanel(config, configManager, this::load);

		this.slotBookmarksBar = new FilterBookmarksBar(
			"slot_optimizer",
			bookmarksClient,
			this::isCloudSyncEnabled,
			this::buildSlotBookmarkSnapshot,
			this::applySlotBookmark,
			executorService
		);

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setAlignmentX(LEFT_ALIGNMENT);

		add(PluginUi.subViewHeader("Slot optimizer", onBack, statusLabel));
		marketRefreshTimerLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		marketRefreshTimerLabel.setAlignmentX(LEFT_ALIGNMENT);
		add(marketRefreshTimerLabel);

		if (coinBalanceService.hasCoins())
		{
			maxCapitalField.setText(String.valueOf(coinBalanceService.getCoins()));
		}

		sortCombo.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		sortCombo.setForeground(Color.WHITE);
		sortDescCheck.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		sortCombo.setToolTipText("Rank candidates before filling GE slots");
		sortCombo.addActionListener(e -> onSortChanged());
		sortDescCheck.addActionListener(e -> onSortChanged());
		PluginUi.styleCombo(sortCombo);

		JPanel setup = PluginUi.verticalStack(
			PluginUi.labeledField("Max capital", maxCapitalField),
			slotBookmarksBar,
			PluginUi.labeledField("Rank by", sortCombo),
			PluginUi.indented(sortDescCheck)
		);
		add(PluginUi.formCard(setup));
		add(PluginUi.gap(PluginUi.SPACING_MD));

		add(filtersPanel.wrapper());
		filterNoticeLabel.setVisible(false);
		add(filterNoticeLabel);
		add(PluginUi.gap(PluginUi.SPACING_MD));

		summaryPanel.setLayout(new BoxLayout(summaryPanel, BoxLayout.Y_AXIS));
		summaryPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		summaryPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		SidebarContentPanel.lockWidth(summaryPanel);
		add(summaryPanel);
		add(PluginUi.gap(PluginUi.SPACING_SM));

		PluginUi.fullWidthGrow(listContainer);
		add(listContainer);

		maxCapitalField.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				scheduleCapitalLoad();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				scheduleCapitalLoad();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				scheduleCapitalLoad();
			}
		});

		restoreSortControls();
	}

	private void scheduleCapitalLoad()
	{
		if (debouncedCapitalLoad != null)
		{
			debouncedCapitalLoad.cancel(false);
		}
		debouncedCapitalLoad = executorService.schedule(
			() -> SwingUtilities.invokeLater(this::load),
			CAPITAL_DEBOUNCE_MS,
			TimeUnit.MILLISECONDS
		);
	}

	void refreshEntitlements()
	{
		PluginEntitlements entitlements = opportunitiesClient.getEntitlements();
		if (entitlements != null)
		{
			filtersPanel.setAdvancedEnabled(entitlements.isAdvancedFilters());
		}
		updateFilterNotice();
		slotBookmarksBar.refresh();
	}

	private boolean isCloudSyncEnabled()
	{
		PluginEntitlements entitlements = opportunitiesClient.getEntitlements();
		return entitlements != null && entitlements.isCloudSync();
	}

	private FilterBookmark buildSlotBookmarkSnapshot(String name)
	{
		FilterBookmark draft = new FilterBookmark();
		draft.setName(name);
		draft.setPresetId(null);
		MarketQueryRequest.MarketFilters filters = filtersPanel.buildFilters();
		Long maxCapital = parseLong(maxCapitalField.getText());
		if (maxCapital != null)
		{
			filters.setMaxCapital(maxCapital);
		}
		draft.setFilters(filters);
		int sortIndex = Math.max(0, sortCombo.getSelectedIndex());
		draft.setSort(Collections.singletonList(
			new MarketQueryRequest.Sort(SORT_IDS[sortIndex], sortDescCheck.isSelected())
		));
		return draft;
	}

	private void applySlotBookmark(FilterBookmark bookmark)
	{
		slotBookmarksBar.setActiveBookmarkId(bookmark.getId());
		if (bookmark.getFilters() != null)
		{
			filtersPanel.applyFromFilters(bookmark.getFilters());
			Long maxCapital = bookmark.getFilters().getMaxCapital();
			maxCapitalField.setText(maxCapital != null ? String.valueOf(maxCapital) : "");
		}
		if (bookmark.getSort() != null && !bookmark.getSort().isEmpty())
		{
			MarketQueryRequest.Sort sort = bookmark.getSort().get(0);
			int sortIndex = 0;
			for (int i = 0; i < SORT_IDS.length; i++)
			{
				if (SORT_IDS[i].equals(sort.getId()))
				{
					sortIndex = i;
					break;
				}
			}
			sortCombo.setSelectedIndex(sortIndex);
			sortDescCheck.setSelected(sort.isDesc());
			configManager.setConfiguration(FlipFinderConfig.GROUP, "slotOptSortId", SORT_IDS[sortIndex]);
			configManager.setConfiguration(FlipFinderConfig.GROUP, "slotOptSortDesc", sort.isDesc());
		}
		invalidateCache();
		load();
	}

	private void updateFilterNotice()
	{
		String notice = filtersPanel.getEntitlementNotice();
		if (notice == null || notice.isBlank())
		{
			filterNoticeLabel.setText(" ");
			filterNoticeLabel.setVisible(false);
		}
		else
		{
			filterNoticeLabel.setText(notice);
			filterNoticeLabel.setVisible(true);
		}
	}

	void invalidateCache()
	{
		lastResponse = null;
		lastMetaUpdatedMs = 0;
		lastMaxCapital = null;
		lastEntitlementsKey = null;
		lastQueryKey = null;
	}

	void load()
	{
		refreshEntitlements();

		Long maxCapital = resolveMaxCapital();
		long metaUpdatedMs = readLatestMetaUpdatedMs();
		String entitlementsKey = readEntitlementsKey();

		statusLabel.setText("Loading slots...");
		summaryPanel.removeAll();
		listContainer.removeAll();
		listContainer.revalidate();
		listContainer.repaint();

		clientThread.invokeLater(() ->
		{
			GeSlotOccupancy.Snapshot occupancy = GeSlotOccupancy.read(client);
			SwingUtilities.invokeLater(() -> loadWithOccupancy(
				occupancy,
				maxCapital,
				metaUpdatedMs,
				entitlementsKey
			));
		});
	}

	private void loadWithOccupancy(
		GeSlotOccupancy.Snapshot occupancy,
		Long maxCapital,
		long metaUpdatedMs,
		String entitlementsKey
	)
	{
		String queryKey = buildQueryKey(maxCapital, occupancy);
		if (lastResponse != null
			&& lastMetaUpdatedMs == metaUpdatedMs
			&& Objects.equals(lastMaxCapital, maxCapital)
			&& Objects.equals(lastEntitlementsKey, entitlementsKey)
			&& Objects.equals(lastQueryKey, queryKey))
		{
			lastOccupancy = occupancy;
			render(lastResponse);
			return;
		}

		lastOccupancy = occupancy;

		if (occupancy.getEmptySlots() == 0)
		{
			lastResponse = null;
			lastMetaUpdatedMs = metaUpdatedMs;
			lastMaxCapital = maxCapital;
			lastEntitlementsKey = entitlementsKey;
			lastQueryKey = queryKey;
			renderAllSlotsOccupied();
			return;
		}

		SlotsOptimizeRequest request = buildRequest(maxCapital, occupancy);

		executorService.execute(() ->
		{
			try
			{
				SlotsResponse response = apiClient.post(
					"/api/plugin/slots/optimize",
					request,
					SlotsResponse.class
				);
				SwingUtilities.invokeLater(() ->
				{
					lastResponse = response;
					lastMetaUpdatedMs = metaUpdatedMs;
					lastMaxCapital = maxCapital;
					lastEntitlementsKey = entitlementsKey;
					lastQueryKey = queryKey;
					render(response);
				});
			}
			catch (IOException e)
			{
				errorListener.accept(e.getMessage());
				SwingUtilities.invokeLater(() -> statusLabel.setText(e.getMessage()));
			}
		});
	}

	private SlotsOptimizeRequest buildRequest(Long maxCapital, GeSlotOccupancy.Snapshot occupancy)
	{
		SlotsOptimizeRequest request = new SlotsOptimizeRequest();
		request.setMaxCapital(maxCapital);
		request.setFilters(filtersPanel.buildFilters());
		request.setAvailableSlots(occupancy.getEmptySlots());
		if (!occupancy.getOccupiedItemIds().isEmpty())
		{
			request.setExcludeItemIds(occupancy.getOccupiedItemIds());
		}
		if (!occupancy.getEmptySlotIndices().isEmpty())
		{
			request.setEmptySlotIndices(occupancy.getEmptySlotIndices());
		}
		int sortIndex = Math.max(0, sortCombo.getSelectedIndex());
		request.setRankSort(SORT_IDS[sortIndex], sortDescCheck.isSelected());
		return request;
	}

	private String buildQueryKey(Long maxCapital, GeSlotOccupancy.Snapshot occupancy)
	{
		int sortIndex = Math.max(0, sortCombo.getSelectedIndex());
		return filtersPanel.filtersFingerprint()
			+ ":" + SORT_IDS[sortIndex]
			+ ":" + sortDescCheck.isSelected()
			+ ":" + maxCapital
			+ ":ge:" + occupancy.getOccupiedSlots()
			+ ":" + occupancy.getOccupiedItemIds()
			+ ":" + occupancy.getEmptySlotIndices();
	}

	private void restoreSortControls()
	{
		String sortId = FlipFinderConfigIO.getString(configManager, "slotOptSortId", "gpPerSlotHour");
		int sortIndex = 0;
		for (int i = 0; i < SORT_IDS.length; i++)
		{
			if (SORT_IDS[i].equals(sortId))
			{
				sortIndex = i;
				break;
			}
		}
		sortCombo.setSelectedIndex(sortIndex);
		sortDescCheck.setSelected(
			FlipFinderConfigIO.getBoolean(configManager, "slotOptSortDesc", true)
		);
	}

	private void onSortChanged()
	{
		int sortIndex = Math.max(0, sortCombo.getSelectedIndex());
		configManager.setConfiguration(FlipFinderConfig.GROUP, "slotOptSortId", SORT_IDS[sortIndex]);
		configManager.setConfiguration(FlipFinderConfig.GROUP, "slotOptSortDesc", sortDescCheck.isSelected());
		load();
	}

	private Long resolveMaxCapital()
	{
		Long maxCapital = parseLong(maxCapitalField.getText());
		if (maxCapital == null && coinBalanceService.hasCoins())
		{
			maxCapital = coinBalanceService.getCoins();
		}
		return maxCapital;
	}

	private long readLatestMetaUpdatedMs()
	{
		MarketQueryResponse latest = opportunitiesClient.getLatest();
		if (latest == null || latest.getMeta() == null)
		{
			return 0;
		}
		return latest.getMeta().getLastUpdatedMs();
	}

	private String readEntitlementsKey()
	{
		PluginEntitlements entitlements = opportunitiesClient.getEntitlements();
		MarketQueryResponse latest = opportunitiesClient.getLatest();
		String tier = latest != null && latest.getMeta() != null
			? String.valueOf(latest.getMeta().getTier())
			: "unknown";
		long refreshMs = latest != null && latest.getMeta() != null
			? latest.getMeta().getRefreshIntervalMs()
			: 0;
		if (entitlements == null)
		{
			return tier + ":" + refreshMs;
		}
		return tier
			+ ":" + refreshMs
			+ ":" + entitlements.getSlotOptimizerSlots()
			+ ":" + entitlements.getMaxOpportunities()
			+ ":" + entitlements.getRefreshIntervalMs()
			+ ":" + entitlements.isPaid();
	}

	private void render(SlotsResponse response)
	{
		summaryPanel.removeAll();
		listContainer.removeAll();
		List<SlotRecommendation> slots = response != null ? response.getSlots() : null;
		if (slots == null || slots.isEmpty())
		{
			if (lastOccupancy != null && lastOccupancy.getEmptySlots() > 0)
			{
				statusLabel.setText("No picks match filters");
				summaryPanel.add(PluginUi.emptyState(
					"No new flips fit your filters for "
						+ lastOccupancy.getEmptySlots()
						+ " empty slot(s). Widen filters or raise capital."));
			}
			else
			{
				statusLabel.setText("No matches");
				summaryPanel.add(PluginUi.emptyState(
					"No slots match your filters. Widen filters or raise capital."));
			}
		}
		else
		{
			long totalCapital = 0;
			long totalGpPerSlotHr = 0;
			for (SlotRecommendation slot : slots)
			{
				totalCapital += slot.getCapitalRequired();
				totalGpPerSlotHr += Math.round(slot.getProfitPerSlotHour());
			}

			int empty = lastOccupancy != null ? lastOccupancy.getEmptySlots() : slots.size();
			statusLabel.setText(slots.size() + " picks | " + empty + " empty GE slot" + (empty == 1 ? "" : "s"));
			summaryPanel.add(buildTotalsStrip(totalCapital, totalGpPerSlotHr));
			summaryPanel.add(PluginUi.gap(PluginUi.SPACING_SM));
			if (lastOccupancy != null && !lastOccupancy.getOccupiedItemIds().isEmpty())
			{
				summaryPanel.add(PluginUi.hint(
					lastOccupancy.getOccupiedSlots()
						+ " GE slot(s) in use. In-GE items excluded."));
				summaryPanel.add(PluginUi.gap(PluginUi.SPACING_XS));
			}
			summaryPanel.add(PluginUi.hint("Tap a row for item detail."));

			for (SlotRecommendation slot : slots)
			{
				listContainer.add(buildRow(slot));
				listContainer.add(PluginUi.gap(PluginUi.SPACING_XS));
			}
		}
		summaryPanel.revalidate();
		summaryPanel.repaint();
		listContainer.revalidate();
		listContainer.repaint();
	}

	private void renderAllSlotsOccupied()
	{
		summaryPanel.removeAll();
		listContainer.removeAll();
		statusLabel.setText("All 8 GE slots in use");
		summaryPanel.add(PluginUi.emptyState(
			"No empty slots to fill. Collect completed offers or cancel one, then reopen this view. "
				+ "Active offers and reprice guidance -> Flip manager."));
		summaryPanel.revalidate();
		summaryPanel.repaint();
		listContainer.revalidate();
		listContainer.repaint();
	}

	private JPanel buildTotalsStrip(long totalCapital, long totalGpPerSlotHr)
	{
		JLabel capitalLabel = new JLabel();
		capitalLabel.setForeground(Color.WHITE);
		PluginUi.setHeroGridStatValue(
			capitalLabel,
			MarketFormat.gpCompact(totalCapital),
			MarketFormat.gp(totalCapital) + " gp total capital"
		);

		JLabel gpHrLabel = new JLabel();
		gpHrLabel.setForeground(PluginUi.POSITIVE);
		PluginUi.setHeroGridStatValue(
			gpHrLabel,
			MarketFormat.gpCompact(totalGpPerSlotHr),
			MarketFormat.gp(totalGpPerSlotHr) + " gp/slot-hr combined"
		);

		int cellW = PluginUi.heroGridCellWidth(2);
		JPanel strip = PluginUi.summaryStrip(
			PluginUi.statCell(capitalLabel, "Capital", cellW),
			PluginUi.statCell(gpHrLabel, "GP/slot-hr", cellW)
		);
		PluginUi.fullWidthGrow(strip);
		return strip;
	}

	private JPanel buildRow(SlotRecommendation slot)
	{
		JPanel card = new SidebarContentPanel();
		card.setLayout(new BorderLayout(PluginUi.SPACING_SM, 0));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, scoreAccent(slot.getOpportunityScore())),
			BorderFactory.createEmptyBorder(
				PluginUi.SPACING_SM, PluginUi.SPACING_SM, PluginUi.SPACING_SM, PluginUi.SPACING_SM
			)
		));
		card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		card.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(32, 28));
		icon.setHorizontalAlignment(JLabel.CENTER);
		itemManager.getImage(slot.getItemId()).addTo(icon);

		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		text.setOpaque(false);

		String title = "GE slot " + slot.getSlotNumber() + " - " + slot.getItemName();
		JLabel name = PluginUi.truncatedLabel(title, 24);
		name.setForeground(Color.WHITE);
		name.setFont(FontManager.getRunescapeSmallFont());

		JLabel stats = new JLabel("<html>"
			+ PluginUi.htmlSpan(PluginUi.TEXT_SOFT, MarketFormat.gp(slot.getEstimatedBuyPrice()))
			+ PluginUi.htmlSpan(PluginUi.TEXT_DIM, " -> ")
			+ PluginUi.htmlSpan(PluginUi.TEXT_SOFT, MarketFormat.gp(slot.getEstimatedSellPrice()))
			+ PluginUi.htmlSep()
			+ PluginUi.htmlSpan(Color.WHITE, MarketFormat.qtyLimit(slot.getEstimatedTradableQuantity(), slot.getBuyLimit()))
			+ PluginUi.htmlSep()
			+ PluginUi.htmlSpan(PluginUi.POSITIVE, MarketFormat.gp(Math.round(slot.getProfitPerSlotHour())) + "/hr")
			+ "</html>");
		stats.setFont(FontManager.getRunescapeSmallFont());

		String tooltip = String.format(
			"<html>Score %d | Net %s (%s)<br>"
				+ "Buy %s x %s at %s, sell at %s<br>"
				+ "Total %s | Capital %s | %.1fh turnover</html>",
			slot.getOpportunityScore(),
			MarketFormat.signedGp(slot.getNetProfitPerItem()),
			MarketFormat.percent(slot.getNetRoiPercent()),
			MarketFormat.qtyLimit(slot.getEstimatedTradableQuantity(), slot.getBuyLimit()),
			slot.getItemName(),
			MarketFormat.gp(slot.getEstimatedBuyPrice()),
			MarketFormat.gp(slot.getEstimatedSellPrice()),
			MarketFormat.gp(slot.getEstimatedProfitAtQuantity()),
			MarketFormat.gp(slot.getCapitalRequired()),
			slot.getTurnoverHours()
		);
		card.setToolTipText(tooltip);
		name.setToolTipText(tooltip);
		stats.setToolTipText(tooltip);

		text.add(name);
		text.add(stats);
		card.add(icon, BorderLayout.WEST);
		card.add(text, BorderLayout.CENTER);
		PluginUi.lockRowHeight(card, ROW_HEIGHT);
		SidebarContentPanel.lockWidth(card);

		MouseAdapter hover = new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				SwingUtilities.invokeLater(() -> onItemSelected.accept(slot));
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				card.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
				text.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
				text.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			}
		};
		card.addMouseListener(hover);
		return card;
	}

	private static Color scoreAccent(int score)
	{
		if (score >= 75)
		{
			return PluginUi.POSITIVE;
		}
		if (score >= 50)
		{
			return PluginUi.GOLD;
		}
		return ColorScheme.MEDIUM_GRAY_COLOR;
	}

	private static Long parseLong(String raw)
	{
		if (raw == null)
		{
			return null;
		}
		String trimmed = raw.trim().replace(",", "");
		if (trimmed.isEmpty())
		{
			return null;
		}
		try
		{
			return Long.parseLong(trimmed);
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	void updateMarketRefreshTimer(String text)
	{
		marketRefreshTimerLabel.setText(text != null ? text : " ");
	}
}
