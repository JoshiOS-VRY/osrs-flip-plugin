package com.osrsflipfinder.runelite;

import java.awt.Color;
import java.awt.Dimension;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.LinkBrowser;

/**
 * Market section embedded in the unified sidebar: opportunities, presets,
 * item detail, slot optimizer and watchlist.
 */
@Slf4j
public class MarketPanel extends SidebarContentPanel
{
	private static final String CARD_LIST = "list";
	private static final String CARD_DETAIL = "detail";
	private static final String CARD_SLOTS = "slots";
	private static final String CARD_WATCHLIST = "watchlist";

	private static final String[] PRESET_IDS = {
		"all", "quick-wins", "big-hitters", "low-capital", "steady", "high-roi", "commodities", "dumped"
	};
	private static final String[] PRESET_LABELS = {
		"All", "Quick Wins", "Big Hitters", "Low Capital", "Steady", "High ROI", "Commodities", "Dumped"
	};

	private static final String[] SORT_IDS = {
		"score", "netProfit", "roi", "totalProfit", "gpPerHour", "gpPerSlotHour",
		"buy", "sell", "qty", "capital", "vol5m", "vol1h", "turnover"
	};
	private static final String[] SORT_LABELS = {
		"Score", "Net / item", "Net ROI", "Total profit", "GP / hr", "GP/slot-hr",
		"Est. buy", "Est. sell", "Qty / limit", "Est. capital", "Vol 5m", "Vol 1h", "Full profit est."
	};

	private final FlipFinderConfig config;
	private final ConfigManager configManager;
	private final OpportunitiesClient opportunitiesClient;
	private final WatchlistClient watchlistClient;
	private final CoinBalanceService coinBalanceService;
	private final ItemManager itemManager;
	private final ItemsClient itemsClient;
	private final ScheduledExecutorService executorService;
	private final BookmarksClient bookmarksClient;

	private final SidebarCardStack cardStack = new SidebarCardStack();
	private final JPanel listCard;

	private Runnable scrollToTop = () -> {};
	private Integer detailItemId;

	private final JLabel foundLabel = new JLabel(PluginUi.PLACEHOLDER);
	private final JLabel profitLabel = new JLabel(PluginUi.PLACEHOLDER);
	private final JLabel roiLabel = new JLabel(PluginUi.PLACEHOLDER);
	private final JLabel statusLabel = PluginUi.caption(" ");
	private final JLabel filterNoticeLabel = PluginUi.hint(" ");
	private final JComboBox<String> presetCombo = new JComboBox<>(new DefaultComboBoxModel<>(PRESET_LABELS));
	private final JComboBox<String> sortCombo = new JComboBox<>(new DefaultComboBoxModel<>(SORT_LABELS));
	private final JCheckBox sortDescCheck = new JCheckBox("High to low", true);
	private final JTextField searchField = PluginUi.textField("");
	private final JPanel listContainer = PluginUi.listContainer();
	private final MarketFiltersPanel filtersPanel;
	private final FilterBookmarksBar marketBookmarksBar;

	private final ItemDetailView detailView;
	private final SlotOptimizerPanel slotsPanel;
	private final WatchlistPanel watchlistPanel;

	private volatile boolean suppressPresetEvent = false;
	private volatile boolean suppressSortEvent = false;
	private String activeCard = CARD_LIST;

	@Inject
	MarketPanel(
		FlipFinderConfig config,
		ConfigManager configManager,
		OpportunitiesClient opportunitiesClient,
		WatchlistClient watchlistClient,
		CoinBalanceService coinBalanceService,
		PluginApiClient apiClient,
		ItemManager itemManager,
		ItemsClient itemsClient,
		ScheduledExecutorService executorService,
		BookmarksClient bookmarksClient
	)
	{
		this.config = config;
		this.configManager = configManager;
		this.opportunitiesClient = opportunitiesClient;
		this.watchlistClient = watchlistClient;
		this.coinBalanceService = coinBalanceService;
		this.itemManager = itemManager;
		this.itemsClient = itemsClient;
		this.executorService = executorService;
		this.bookmarksClient = bookmarksClient;

		this.filtersPanel = new MarketFiltersPanel(
			config,
			configManager,
			coinBalanceService,
			this::onFiltersChanged
		);

		this.marketBookmarksBar = new FilterBookmarksBar(
			"market",
			bookmarksClient,
			this::isCloudSyncEnabled,
			this::buildMarketBookmarkSnapshot,
			this::applyMarketBookmark,
			executorService
		);

		this.detailView = new ItemDetailView(itemManager, executorService, watchlistClient, this::showList, this::onError);
		this.slotsPanel = new SlotOptimizerPanel(
			apiClient,
			opportunitiesClient,
			bookmarksClient,
			coinBalanceService,
			itemManager,
			executorService,
			config,
			configManager,
			this::showList,
			this::onError
		);
		this.watchlistPanel = new WatchlistPanel(watchlistClient, itemManager, executorService, this::showList, this::onError);

		this.listCard = buildListCard();

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setAlignmentX(LEFT_ALIGNMENT);
		SidebarContentPanel.lockWidth(this);

		cardStack.showCard(listCard);
		add(cardStack);

		stylePresetCombo();
		styleSortCombo();
		opportunitiesClient.setDataListener(this::onData);
		opportunitiesClient.setEntitlementsListener(this::onEntitlementsChanged);
		opportunitiesClient.setErrorListener(this::onError);
		itemsClient.addUpdateListener(this::onItemUpdated);
		coinBalanceService.setBalanceListener(coins -> onCoinBalanceChanged());

		showList();
	}

	private void onCoinBalanceChanged()
	{
		filtersPanel.refreshCoinsLabel();
		if (filtersPanel.isUsingInventoryCoins())
		{
			requestMarketRefresh();
		}
	}

	private void stylePresetCombo()
	{
		PluginUi.styleCombo(presetCombo);
	}

	private void styleSortCombo()
	{
		PluginUi.styleCombo(sortCombo);
		sortDescCheck.setBackground(ColorScheme.DARK_GRAY_COLOR);
		sortDescCheck.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
	}

	private JPanel buildListCard()
	{
		JPanel card = new SidebarContentPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		PluginUi.transparent(card);
		card.setAlignmentX(LEFT_ALIGNMENT);

		JPanel summary = PluginUi.summaryStrip(
			PluginUi.statCell(foundLabel, "Found"),
			PluginUi.statCell(profitLabel, "Top net"),
			PluginUi.statCell(roiLabel, "Top ROI")
		);
		PluginUi.fullWidth(summary);
		card.add(summary);
		card.add(PluginUi.gap(10));

		card.add(PluginUi.labeledField("Quick preset", presetCombo));
		presetCombo.addActionListener(e -> onPresetChanged());
		card.add(PluginUi.gap(8));

		card.add(marketBookmarksBar);
		card.add(PluginUi.gap(8));

		sortCombo.setToolTipText("Sort the opportunity list (All preset only)");
		sortCombo.addActionListener(e -> onSortChanged());
		sortDescCheck.setToolTipText("Descending shows highest values first");
		sortDescCheck.addActionListener(e -> onSortChanged());
		card.add(PluginUi.labeledField("Sort by", sortCombo));
		card.add(PluginUi.gap(4));
		card.add(sortDescCheck);
		card.add(PluginUi.gap(8));

		searchField.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				onSearchChanged();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				onSearchChanged();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				onSearchChanged();
			}
		});
		searchField.setToolTipText("Filter by item name or ID");
		card.add(PluginUi.labeledField("Search", searchField));
		card.add(PluginUi.gap(8));
		card.add(filtersPanel.wrapper());
		card.add(PluginUi.gap(4));
		filterNoticeLabel.setVisible(false);
		card.add(filterNoticeLabel);
		card.add(PluginUi.gap(6));

		JButton slotsButton = PluginUi.secondaryButton("Slot optimizer");
		slotsButton.addActionListener(e -> showSlots());
		JButton watchlistButton = PluginUi.secondaryButton("Watchlist");
		watchlistButton.addActionListener(e -> showWatchlist());
		JPanel nav = PluginUi.buttonRow(slotsButton, watchlistButton);
		PluginUi.fullWidth(nav);
		card.add(nav);
		card.add(PluginUi.gap(8));
		card.add(statusLabel);
		card.add(PluginUi.gap(8));
		card.add(listContainer);
		SidebarContentPanel.lockWidth(card);

		return card;
	}

	void setScrollToTop(Runnable scrollToTop)
	{
		this.scrollToTop = scrollToTop != null ? scrollToTop : () -> {};
	}

	void setActive(boolean active)
	{
		opportunitiesClient.setActive(active && config.enableMarketPanel() && !config.apiKey().isBlank());
	}

	void refreshUi()
	{
		SwingUtilities.invokeLater(() ->
		{
			filtersPanel.refreshCoinsLabel();
			PluginEntitlements entitlements = opportunitiesClient.getEntitlements();
			filtersPanel.setAdvancedEnabled(entitlements != null && entitlements.isAdvancedFilters());

			restorePersistedControls();
			updateFilterNotice();

			if (config.apiKey().isBlank())
			{
				statusLabel.setText("Pair in Connection to browse the market.");
				clearMarketList();
			}
			else if (!config.enableMarketPanel())
			{
				statusLabel.setText("Enable \"Market panel\" in plugin settings.");
				clearMarketList();
			}
			else if (opportunitiesClient.isOffline() && opportunitiesClient.getCachedAt() != null)
			{
				MarketQueryResponse latest = opportunitiesClient.getLatest();
				if (latest != null)
				{
					renderData(latest);
				}
				statusLabel.setText("Offline · cached "
					+ LocalCacheStore.formatCachedAt(opportunitiesClient.getCachedAt()));
			}
			else
			{
				requestMarketRefresh();
			}
			marketBookmarksBar.refresh();
		});
	}

	private void restorePersistedControls()
	{
		suppressPresetEvent = true;
		String presetId = config.marketPresetId();
		int index = 0;
		for (int i = 0; i < PRESET_IDS.length; i++)
		{
			if (PRESET_IDS[i].equals(presetId))
			{
				index = i;
				break;
			}
		}
		presetCombo.setSelectedIndex(index);
		suppressPresetEvent = false;

		suppressSortEvent = true;
		String sortId = config.marketSortId();
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
		sortDescCheck.setSelected(config.marketSortDesc());
		suppressSortEvent = false;

		updateSortControlsEnabled();
	}

	private void updateSortControlsEnabled()
	{
		boolean customList = presetCombo.getSelectedIndex() <= 0;
		sortCombo.setEnabled(customList);
		sortDescCheck.setEnabled(customList);
	}

	private MarketQueryRequest buildQuery()
	{
		MarketQueryRequest request = new MarketQueryRequest();
		int presetIndex = Math.max(0, presetCombo.getSelectedIndex());
		String presetId = PRESET_IDS[presetIndex];
		request.setPresetId(presetId);
		PluginEntitlements entitlements = opportunitiesClient.getEntitlements();
		request.setLimit(entitlements != null ? entitlements.getMaxOpportunities() : 50);

		// Volume emphasis is intentionally omitted: the plugin has no slider, so an
		// active preset supplies its own emphasis and "All" uses the server default.
		String search = searchField.getText().trim();
		if ("all".equals(presetId))
		{
			MarketQueryRequest.MarketFilters filters = filtersPanel.buildFilters();
			if (!search.isEmpty())
			{
				filters.setSearch(search);
			}
			request.setFilters(filters);
			request.setSort(Collections.singletonList(buildSort()));
		}
		else if (!search.isEmpty())
		{
			// A preset owns filters/sort server-side; only overlay the search term.
			MarketQueryRequest.MarketFilters filters = new MarketQueryRequest.MarketFilters();
			filters.setSearch(search);
			request.setFilters(filters);
		}
		return request;
	}

	private MarketQueryRequest.Sort buildSort()
	{
		int sortIndex = Math.max(0, sortCombo.getSelectedIndex());
		return new MarketQueryRequest.Sort(
			SORT_IDS[sortIndex],
			sortDescCheck.isSelected()
		);
	}

	private void onPresetChanged()
	{
		if (suppressPresetEvent)
		{
			return;
		}
		int index = Math.max(0, presetCombo.getSelectedIndex());
		String presetId = PRESET_IDS[index];
		configManager.setConfiguration(FlipFinderConfig.GROUP, "marketPresetId", presetId);
		if (!"all".equals(presetId))
		{
			filtersPanel.clearAll();
		}
		updateSortControlsEnabled();
		requestMarketRefresh();
	}

	private void onSortChanged()
	{
		if (suppressSortEvent || presetCombo.getSelectedIndex() > 0)
		{
			return;
		}
		int sortIndex = Math.max(0, sortCombo.getSelectedIndex());
		configManager.setConfiguration(FlipFinderConfig.GROUP, "marketSortId", SORT_IDS[sortIndex]);
		configManager.setConfiguration(FlipFinderConfig.GROUP, "marketSortDesc", sortDescCheck.isSelected());
		requestMarketRefresh();
	}

	private void onSearchChanged()
	{
		requestMarketRefresh();
	}

	private void onFiltersChanged()
	{
		filtersPanel.refreshCoinsLabel();
		selectPresetSilently(0);
		updateSortControlsEnabled();
		requestMarketRefresh();
	}

	private void requestMarketRefresh()
	{
		updateFilterNotice();
		showLoadingState();
		opportunitiesClient.setQuery(buildQuery());
	}

	private void showLoadingState()
	{
		foundLabel.setText(PluginUi.PLACEHOLDER);
		profitLabel.setText(PluginUi.PLACEHOLDER);
		roiLabel.setText(PluginUi.PLACEHOLDER);
		statusLabel.setText("Updating market…");
		statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		clearMarketList();
	}

	private void clearMarketList()
	{
		listContainer.removeAll();
		listContainer.revalidate();
		listContainer.repaint();
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

	private void selectPresetSilently(int index)
	{
		if (presetCombo.getSelectedIndex() == index)
		{
			return;
		}
		suppressPresetEvent = true;
		presetCombo.setSelectedIndex(index);
		suppressPresetEvent = false;
		configManager.setConfiguration(FlipFinderConfig.GROUP, "marketPresetId", PRESET_IDS[index]);
	}

	private void onData(MarketQueryResponse response)
	{
		SwingUtilities.invokeLater(() -> renderData(response));
	}

	private void onEntitlementsChanged(PluginEntitlements entitlements)
	{
		SwingUtilities.invokeLater(() ->
		{
			filtersPanel.setAdvancedEnabled(entitlements.isAdvancedFilters());
			slotsPanel.refreshEntitlements();
			slotsPanel.invalidateCache();
			if (CARD_SLOTS.equals(activeCard))
			{
				slotsPanel.load();
			}
			else if (config.apiKey() != null && !config.apiKey().isBlank())
			{
				requestMarketRefresh();
			}
			marketBookmarksBar.refresh();
		});
	}

	private boolean isCloudSyncEnabled()
	{
		PluginEntitlements entitlements = opportunitiesClient.getEntitlements();
		return entitlements != null && entitlements.isCloudSync();
	}

	private FilterBookmark buildMarketBookmarkSnapshot(String name)
	{
		FilterBookmark draft = new FilterBookmark();
		draft.setName(name);
		int presetIndex = Math.max(0, presetCombo.getSelectedIndex());
		draft.setPresetId(PRESET_IDS[presetIndex]);
		draft.setFilters(filtersPanel.buildFilters());
		String search = searchField.getText().trim();
		if (!search.isEmpty())
		{
			draft.getFilters().setSearch(search);
		}
		draft.setSort(Collections.singletonList(buildSort()));
		return draft;
	}

	private void applyMarketBookmark(FilterBookmark bookmark)
	{
		marketBookmarksBar.setActiveBookmarkId(bookmark.getId());

		String presetId = bookmark.getPresetId();
		if (presetId != null && !"all".equals(presetId))
		{
			int index = 0;
			for (int i = 0; i < PRESET_IDS.length; i++)
			{
				if (PRESET_IDS[i].equals(presetId))
				{
					index = i;
					break;
				}
			}
			selectPresetWithoutClear(index);
			if (bookmark.getFilters() != null)
			{
				filtersPanel.applyFromFilters(bookmark.getFilters());
				String search = bookmark.getFilters().getSearch();
				searchField.setText(search != null ? search : "");
			}
		}
		else
		{
			selectPresetWithoutClear(0);
			if (bookmark.getFilters() != null)
			{
				filtersPanel.applyFromFilters(bookmark.getFilters());
				String search = bookmark.getFilters().getSearch();
				searchField.setText(search != null ? search : "");
			}
			else
			{
				filtersPanel.clearAll();
				searchField.setText("");
			}
			if (bookmark.getSort() != null && !bookmark.getSort().isEmpty())
			{
				applySortDescriptor(bookmark.getSort().get(0));
			}
		}
		updateSortControlsEnabled();
		requestMarketRefresh();
	}

	private void applySortDescriptor(MarketQueryRequest.Sort sort)
	{
		if (sort == null || sort.getId() == null)
		{
			return;
		}
		suppressSortEvent = true;
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
		suppressSortEvent = false;
		configManager.setConfiguration(FlipFinderConfig.GROUP, "marketSortId", SORT_IDS[sortIndex]);
		configManager.setConfiguration(FlipFinderConfig.GROUP, "marketSortDesc", sort.isDesc());
	}

	private void selectPresetWithoutClear(int index)
	{
		suppressPresetEvent = true;
		presetCombo.setSelectedIndex(index);
		suppressPresetEvent = false;
		configManager.setConfiguration(FlipFinderConfig.GROUP, "marketPresetId", PRESET_IDS[index]);
	}

	private void renderData(MarketQueryResponse response)
	{
		PluginEntitlements entitlements = opportunitiesClient.getEntitlements();
		filtersPanel.setAdvancedEnabled(entitlements != null && entitlements.isAdvancedFilters());
		updateFilterNotice();

		MarketQueryResponse.Summary summary = response.getSummary();
		if (summary != null)
		{
			foundLabel.setText(String.valueOf(summary.getOpportunitiesFound()));
			profitLabel.setText(MarketFormat.gp(summary.getHighestNetProfit()));
			roiLabel.setText(MarketFormat.percent(summary.getHighestRoiPercent()));
		}

		listContainer.removeAll();
		List<FlipOpportunity> opportunities = response.getOpportunities();
		if (opportunities == null || opportunities.isEmpty())
		{
			statusLabel.setText("No opportunities match your filters.");
		}
		else
		{
			statusLabel.setText(buildResultStatus(opportunities.size(), response));
		}
		statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		if (opportunities != null)
		{
			for (FlipOpportunity opp : opportunities)
			{
				listContainer.add(new OpportunityRow(opp, itemManager, () -> showDetail(opp)));
				listContainer.add(PluginUi.gap(4));
			}
		}
		listContainer.revalidate();
		listContainer.repaint();

		if (CARD_DETAIL.equals(activeCard) && detailItemId != null)
		{
			FlipOpportunity unified = itemsClient.peekOpportunity(detailItemId);
			if (unified != null)
			{
				detailView.show(unified, FlipXConstants.baseUrl());
			}
			else if (opportunities != null)
			{
				for (FlipOpportunity opp : opportunities)
				{
					if (opp.getId() == detailItemId)
					{
						detailView.show(opp, FlipXConstants.baseUrl());
						break;
					}
				}
			}
		}

		if (CARD_SLOTS.equals(activeCard))
		{
			slotsPanel.load();
		}
	}

	private String buildResultStatus(int shown, MarketQueryResponse response)
	{
		StringBuilder status = new StringBuilder();
		status.append(shown).append(" shown");
		if (response.getMeta() != null && response.getMeta().isStale())
		{
			status.append(" · stale data");
		}
		if (opportunitiesClient.isOffline() && opportunitiesClient.getCachedAt() != null)
		{
			status.append(" · offline cached ");
			status.append(LocalCacheStore.formatCachedAt(opportunitiesClient.getCachedAt()));
		}
		return status.toString();
	}

	private void onError(String message)
	{
		SwingUtilities.invokeLater(() ->
		{
			if (message != null && !message.isBlank())
			{
				statusLabel.setText(message);
				if (message.contains("upgrade") || message.contains("Upgrade"))
				{
					statusLabel.setForeground(PluginUi.GOLD);
				}
				else
				{
					statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
				}
			}
		});
	}

	void showList()
	{
		activeCard = CARD_LIST;
		detailItemId = null;
		cardStack.showCard(listCard);
		refreshCardLayout();
	}

	private void showDetail(FlipOpportunity opp)
	{
		activeCard = CARD_DETAIL;
		detailItemId = opp.getId();

		cardStack.showCard(detailView);
		scrollToTop.run();
		detailView.showLoading(opp, FlipXConstants.baseUrl());
		refreshCardLayout();

		SwingUtilities.invokeLater(() ->
		{
			FlipOpportunity unified = itemsClient.peekOpportunity(opp.getId());
			detailView.show(unified != null ? unified : opp, FlipXConstants.baseUrl());
			refreshCardLayout();
		});

		executorService.execute(() ->
		{
			try
			{
				itemsClient.fetch(opp.getId());
			}
			catch (IOException ex)
			{
				log.debug("Item detail refresh failed for {}", opp.getId(), ex);
			}
		});
	}

	private void onItemUpdated(int itemId)
	{
		if (detailItemId == null || detailItemId != itemId || !CARD_DETAIL.equals(activeCard))
		{
			return;
		}
		FlipOpportunity unified = itemsClient.peekOpportunity(itemId);
		if (unified != null)
		{
			SwingUtilities.invokeLater(() -> detailView.show(unified, FlipXConstants.baseUrl()));
		}
	}

	private void showSlots()
	{
		activeCard = CARD_SLOTS;
		slotsPanel.refreshEntitlements();
		slotsPanel.load();
		cardStack.showCard(slotsPanel);
		scrollToTop.run();
		refreshCardLayout();
	}

	private void showWatchlist()
	{
		activeCard = CARD_WATCHLIST;
		watchlistPanel.load();
		cardStack.showCard(watchlistPanel);
		scrollToTop.run();
		refreshCardLayout();
	}

	private void refreshCardLayout()
	{
		cardStack.revalidate();
		cardStack.repaint();
		revalidate();
		repaint();
	}

}
