package com.osrsflipfinder.runelite;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/** GE slot optimizer with filters, sort, and actionable buy/sell/qty metrics. */
class SlotOptimizerPanel extends SidebarContentPanel
{
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
	private final ScheduledExecutorService executorService;
	private final FlipFinderConfig config;
	private final ConfigManager configManager;
	private final Consumer<String> errorListener;
	private final SlotOptimizerFiltersPanel filtersPanel;
	private final FilterBookmarksBar slotBookmarksBar;

	private final JLabel statusLabel = PluginUi.caption("Loading slots…");
	private final JLabel filterNoticeLabel = PluginUi.hint(" ");
	private final JTextField maxCapitalField = PluginUi.textField("");
	private final JComboBox<String> sortCombo = new JComboBox<>(new DefaultComboBoxModel<>(SORT_LABELS));
	private final JCheckBox sortDescCheck = new JCheckBox("High to low", true);
	private final JPanel summaryPanel = new JPanel();
	private final JPanel listContainer = PluginUi.listContainer();

	private SlotsResponse lastResponse;
	private long lastMetaUpdatedMs;
	private Long lastMaxCapital;
	private String lastEntitlementsKey;
	private String lastQueryKey;

	SlotOptimizerPanel(
		PluginApiClient apiClient,
		OpportunitiesClient opportunitiesClient,
		BookmarksClient bookmarksClient,
		CoinBalanceService coinBalanceService,
		ItemManager itemManager,
		ScheduledExecutorService executorService,
		FlipFinderConfig config,
		ConfigManager configManager,
		Runnable onBack,
		Consumer<String> errorListener
	)
	{
		this.apiClient = apiClient;
		this.opportunitiesClient = opportunitiesClient;
		this.bookmarksClient = bookmarksClient;
		this.coinBalanceService = coinBalanceService;
		this.itemManager = itemManager;
		this.executorService = executorService;
		this.config = config;
		this.configManager = configManager;
		this.errorListener = errorListener;
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

		if (coinBalanceService.hasCoins())
		{
			maxCapitalField.setText(String.valueOf(coinBalanceService.getCoins()));
		}
		add(PluginUi.labeledField("Max capital", maxCapitalField));
		add(PluginUi.gap(8));

		add(slotBookmarksBar);
		add(PluginUi.gap(8));

		sortCombo.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		sortCombo.setForeground(Color.WHITE);
		sortDescCheck.setBackground(ColorScheme.DARK_GRAY_COLOR);
		sortDescCheck.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		sortCombo.setToolTipText("Rank candidates before filling GE slots");
		sortCombo.addActionListener(e -> onSortChanged());
		sortDescCheck.addActionListener(e -> onSortChanged());
		add(PluginUi.labeledField("Rank by", sortCombo));
		add(PluginUi.gap(4));
		add(sortDescCheck);
		add(PluginUi.gap(8));

		add(filtersPanel.wrapper());
		add(PluginUi.gap(4));
		filterNoticeLabel.setVisible(false);
		add(filterNoticeLabel);
		add(PluginUi.gap(6));

		summaryPanel.setLayout(new BoxLayout(summaryPanel, BoxLayout.Y_AXIS));
		summaryPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		summaryPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		SidebarContentPanel.lockWidth(summaryPanel);
		add(summaryPanel);
		add(PluginUi.gap(6));

		PluginUi.fullWidthGrow(listContainer);
		add(listContainer);

		maxCapitalField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener()
		{
			@Override
			public void insertUpdate(javax.swing.event.DocumentEvent e)
			{
				load();
			}

			@Override
			public void removeUpdate(javax.swing.event.DocumentEvent e)
			{
				load();
			}

			@Override
			public void changedUpdate(javax.swing.event.DocumentEvent e)
			{
				load();
			}
		});

		restoreSortControls();
	}

	void refreshEntitlements()
	{
		PluginEntitlements entitlements = opportunitiesClient.getEntitlements();
		filtersPanel.setAdvancedEnabled(entitlements != null && entitlements.isAdvancedFilters());
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
		String queryKey = buildQueryKey(maxCapital);
		if (lastResponse != null
			&& lastMetaUpdatedMs == metaUpdatedMs
			&& Objects.equals(lastMaxCapital, maxCapital)
			&& Objects.equals(lastEntitlementsKey, entitlementsKey)
			&& Objects.equals(lastQueryKey, queryKey))
		{
			render(lastResponse);
			return;
		}

		statusLabel.setText("Loading slots…");
		summaryPanel.removeAll();
		listContainer.removeAll();
		listContainer.revalidate();
		listContainer.repaint();

		SlotsOptimizeRequest request = buildRequest(maxCapital);

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

	private SlotsOptimizeRequest buildRequest(Long maxCapital)
	{
		SlotsOptimizeRequest request = new SlotsOptimizeRequest();
		request.setMaxCapital(maxCapital);
		request.setFilters(filtersPanel.buildFilters());
		int sortIndex = Math.max(0, sortCombo.getSelectedIndex());
		request.setRankSort(SORT_IDS[sortIndex], sortDescCheck.isSelected());
		return request;
	}

	private String buildQueryKey(Long maxCapital)
	{
		int sortIndex = Math.max(0, sortCombo.getSelectedIndex());
		return filtersPanel.filtersFingerprint()
			+ ":" + SORT_IDS[sortIndex]
			+ ":" + sortDescCheck.isSelected()
			+ ":" + maxCapital;
	}

	private void restoreSortControls()
	{
		String sortId = config.slotOptSortId();
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
		sortDescCheck.setSelected(config.slotOptSortDesc());
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
			statusLabel.setText("No slots match your filters. Try widening filters or raising capital.");
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

			statusLabel.setText(slots.size() + " slots · fill GE in order shown");
			summaryPanel.add(buildTotalsStrip(totalCapital, totalGpPerSlotHr));
			summaryPanel.add(PluginUi.gap(6));
			summaryPanel.add(PluginUi.hint("Buy at est. buy · sell at est. sell · qty = recommended stack"));

			for (SlotRecommendation slot : slots)
			{
				listContainer.add(buildRow(slot));
				listContainer.add(PluginUi.gap(4));
			}
		}
		summaryPanel.revalidate();
		summaryPanel.repaint();
		listContainer.revalidate();
		listContainer.repaint();
	}

	private JPanel buildTotalsStrip(long totalCapital, long totalGpPerSlotHr)
	{
		JLabel capitalLabel = new JLabel(MarketFormat.gp(totalCapital));
		capitalLabel.setForeground(Color.WHITE);
		JLabel gpHrLabel = new JLabel(MarketFormat.gp(totalGpPerSlotHr));
		gpHrLabel.setForeground(PluginUi.POSITIVE);
		JPanel strip = PluginUi.summaryStrip(
			PluginUi.statCell(capitalLabel, "Capital"),
			PluginUi.statCell(gpHrLabel, "GP/slot-hr")
		);
		PluginUi.fullWidth(strip);
		return strip;
	}

	private JPanel buildRow(SlotRecommendation slot)
	{
		JPanel card = new SidebarContentPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(javax.swing.BorderFactory.createCompoundBorder(
			javax.swing.BorderFactory.createMatteBorder(0, 3, 0, 0, scoreAccent(slot.getOpportunityScore())),
			javax.swing.BorderFactory.createEmptyBorder(6, 8, 6, 8)
		));
		card.setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel header = new JPanel(new BorderLayout(6, 0));
		PluginUi.transparent(header);
		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(32, 28));
		icon.setHorizontalAlignment(JLabel.CENTER);
		itemManager.getImage(slot.getItemId()).addTo(icon);

		JPanel titleCol = new JPanel();
		titleCol.setLayout(new BoxLayout(titleCol, BoxLayout.Y_AXIS));
		PluginUi.transparent(titleCol);

		JLabel name = new JLabel("Slot " + slot.getSlotNumber() + " · " + slot.getItemName());
		name.setForeground(Color.WHITE);
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setToolTipText(slot.getItemName() + " (#" + slot.getItemId() + ")");

		JLabel scoreLine = PluginUi.caption("Score " + slot.getOpportunityScore());
		titleCol.add(name);
		titleCol.add(scoreLine);
		header.add(icon, BorderLayout.WEST);
		header.add(titleCol, BorderLayout.CENTER);
		PluginUi.fullWidth(header);

		JLabel buyLabel = new JLabel(MarketFormat.gp(slot.getEstimatedBuyPrice()));
		JLabel sellLabel = new JLabel(MarketFormat.gp(slot.getEstimatedSellPrice()));
		JLabel netLabel = new JLabel(MarketFormat.signedGp(slot.getNetProfitPerItem()));
		netLabel.setForeground(slot.getNetProfitPerItem() >= 0 ? PluginUi.POSITIVE : PluginUi.NEGATIVE);
		JLabel roiLabel = new JLabel(MarketFormat.percent(slot.getNetRoiPercent()));

		JPanel metrics = PluginUi.summaryStrip(
			PluginUi.statCell(buyLabel, "Est. buy"),
			PluginUi.statCell(sellLabel, "Est. sell"),
			PluginUi.statCell(netLabel, "Net / item"),
			PluginUi.statCell(roiLabel, "Net ROI")
		);
		PluginUi.fullWidth(metrics);

		JLabel plan = new JLabel(String.format(
			"<html><span style='color:#cccccc;'>Qty </span>"
				+ "<span style='color:#ffffff;'>%s</span>"
				+ "<span style='color:#888888;'> · </span>"
				+ "<span style='color:#cccccc;'>Total </span>"
				+ "<span style='color:#86e589;'>%s</span>"
				+ "<span style='color:#888888;'> · </span>"
				+ "<span style='color:#cccccc;'>%s/slot-hr</span>"
				+ "<span style='color:#888888;'> · </span>"
				+ "<span style='color:#cccccc;'>%.1fh</span></html>",
			MarketFormat.qtyLimit(slot.getEstimatedTradableQuantity(), slot.getBuyLimit()),
			MarketFormat.gp(slot.getEstimatedProfitAtQuantity()),
			MarketFormat.gp(Math.round(slot.getProfitPerSlotHour())),
			slot.getTurnoverHours()
		));
		plan.setFont(FontManager.getRunescapeSmallFont());
		plan.setToolTipText(String.format(
			"Buy %s × %s at %s each, sell at %s · %s capital",
			MarketFormat.qtyLimit(slot.getEstimatedTradableQuantity(), slot.getBuyLimit()),
			slot.getItemName(),
			MarketFormat.gp(slot.getEstimatedBuyPrice()),
			MarketFormat.gp(slot.getEstimatedSellPrice()),
			MarketFormat.gp(slot.getCapitalRequired())
		));

		card.add(header);
		card.add(PluginUi.gap(4));
		card.add(metrics);
		card.add(PluginUi.gap(2));
		card.add(plan);
		SidebarContentPanel.lockWidth(card);
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
}
