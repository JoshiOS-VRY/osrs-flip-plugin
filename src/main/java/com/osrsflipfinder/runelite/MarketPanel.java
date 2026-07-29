package com.osrsflipfinder.runelite;

import java.awt.Color;
import java.awt.Dimension;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
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
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;

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

	private final JLabel statusLabel = PluginUi.caption(" ");
	private final JLabel refreshTimerLabel = PluginUi.caption(" ");
	private final JLabel filterNoticeLabel = PluginUi.hint(" ");
	private final JComboBox<String> presetCombo = new JComboBox<>(new DefaultComboBoxModel<>(PRESET_LABELS));
	private final JComboBox<String> sortCombo = new JComboBox<>(new DefaultComboBoxModel<>(SORT_LABELS));
	private final JCheckBox sortDescCheck = PluginUi.checkBox("High to low", true);
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
	private String detailReturnCard = CARD_LIST;

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
		Client client,
		ClientThread clientThread,
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

		this.detailView = new ItemDetailView(
			itemManager,
			executorService,
			watchlistClient,
			this::onDetailBack,
			this::onError
		);
		this.slotsPanel = new SlotOptimizerPanel(
			apiClient,
			opportunitiesClient,
			bookmarksClient,
			coinBalanceService,
			itemManager,
			client,
			clientThread,
			executorService,
			config,
			configManager,
			this::showList,
			this::onError,
			this::showDetailFromSlot
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
		restorePersistedControls();
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
		sortDescCheck.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
	}

	private JPanel buildListCard()
	{
		JPanel card = new SidebarContentPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		PluginUi.transparent(card);
		card.setAlignmentX(LEFT_ALIGNMENT);

		presetCombo.addActionListener(e -> onPresetChanged());

		sortCombo.setToolTipText("Sort the opportunity list (All preset only)");
		sortCombo.addActionListener(e -> onSortChanged());
		sortDescCheck.setToolTipText("Descending shows highest values first");
		sortDescCheck.addActionListener(e -> onSortChanged());

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
		searchField.setToolTipText("Filter the flip list by name or ID");

		JPanel browse = PluginUi.verticalStack(
			PluginUi.labeledField("Quick preset", presetCombo),
			marketBookmarksBar,
			PluginUi.labeledField("Sort by", sortCombo),
			PluginUi.indented(sortDescCheck),
			PluginUi.labeledField("Filter list", searchField)
		);
		card.add(PluginUi.formCard(browse));
		card.add(PluginUi.gap(PluginUi.SPACING_MD));

		card.add(filtersPanel.wrapper());
		filterNoticeLabel.setVisible(false);
		card.add(filterNoticeLabel);
		card.add(PluginUi.gap(PluginUi.SPACING_MD));

		card.add(statusLabel);
		refreshTimerLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		card.add(refreshTimerLabel);
		card.add(PluginUi.gap(PluginUi.SPACING_XS));

		JButton slotsButton = PluginUi.linkButton("Slot optimizer");
		slotsButton.addActionListener(e -> showSlots());
		JButton watchlistButton = PluginUi.linkButton("Watchlist");
		watchlistButton.addActionListener(e -> showWatchlist());
		JPanel nav = PluginUi.buttonRow(slotsButton, watchlistButton);
		PluginUi.fullWidth(nav);
		card.add(nav);
		card.add(PluginUi.gap(PluginUi.SPACING_SM));
		card.add(listContainer);
		SidebarContentPanel.lockWidth(card);

		return card;
	}

	void setScrollToTop(Runnable scrollToTop)
	{
		this.scrollToTop = scrollToTop != null ? scrollToTop : () -> {};
	}

	/** Open item detail from catalog search (any tradable item). */
	void openItemFromCatalog(int itemId, String itemName)
	{
		FlipOpportunity stub = new FlipOpportunity();
		stub.setId(itemId);
		stub.setName(itemName != null && !itemName.isBlank() ? itemName : ("Item " + itemId));
		showDetail(stub, CARD_LIST);
	}

	void setActive(boolean active)
	{
		opportunitiesClient.setActive(
			active && config.enableMarketPanel() && PairingCredentials.isPairedForCurrentApi(config));
	}

	void updateRefreshTimer(boolean paired)
	{
		String marketTimer = formatMarketRefreshTimer(paired);
		refreshTimerLabel.setText(marketTimer);
		slotsPanel.updateMarketRefreshTimer(marketTimer);
		watchlistPanel.updateMarketRefreshTimer(marketTimer);
		if (CARD_DETAIL.equals(activeCard))
		{
			detailView.updateRefreshStatus(marketTimer, readDetailLastUpdatedMs());
		}
	}

	private String formatMarketRefreshTimer(boolean paired)
	{
		if (!config.enableMarketPanel() || !paired)
		{
			return " ";
		}
		return RefreshCountdown.formatPolling(
			opportunitiesClient.getNextRefreshAtMs(),
			opportunitiesClient.isActive(),
			opportunitiesClient.isFetchInProgress()
		);
	}

	private long readDetailLastUpdatedMs()
	{
		if (detailItemId == null)
		{
			return 0L;
		}
		ItemDetailResponse detail = itemsClient.peek(detailItemId);
		if (detail != null && detail.getMeta() != null && detail.getMeta().getLastUpdatedMs() > 0)
		{
			return detail.getMeta().getLastUpdatedMs();
		}
		MarketQueryResponse latest = opportunitiesClient.getLatest();
		if (latest != null && latest.getMeta() != null)
		{
			return latest.getMeta().getLastUpdatedMs();
		}
		return 0L;
	}

	private void syncDetailRefreshUi()
	{
		if (!CARD_DETAIL.equals(activeCard))
		{
			return;
		}
		boolean paired = PairingCredentials.isPairedForCurrentApi(config);
		String marketTimer = formatMarketRefreshTimer(paired);
		detailView.updateRefreshStatus(marketTimer, readDetailLastUpdatedMs());
	}

	private void scheduleDetailItemFetch()
	{
		scheduleDetailItemFetch(false);
	}

	private void scheduleDetailItemFetch(boolean forceRefresh)
	{
		if (!CARD_DETAIL.equals(activeCard) || detailItemId == null)
		{
			return;
		}
		int itemId = detailItemId;
		executorService.execute(() ->
		{
			try
			{
				itemsClient.fetch(itemId, forceRefresh);
			}
			catch (IOException ex)
			{
				log.debug("Item detail poll refresh failed for {}", itemId, ex);
			}
		});
	}

	void refreshUi()
	{
		SwingUtilities.invokeLater(() ->
		{
			filtersPanel.refreshCoinsLabel();
			applyMarketEntitlements(opportunitiesClient.getEntitlements());

			updateSortControlsEnabled();
			updateFilterNotice();

			if (!PairingCredentials.isPairedForCurrentApi(config))
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
				statusLabel.setText("Offline | cached "
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
		String presetId = FlipFinderConfigIO.getString(configManager, "marketPresetId", "all");
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
		String sortId = FlipFinderConfigIO.getString(configManager, "marketSortId", "score");
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
			FlipFinderConfigIO.getBoolean(configManager, "marketSortDesc", true)
		);
		suppressSortEvent = false;

		updateSortControlsEnabled();
	}

	private void applyMarketEntitlements(PluginEntitlements entitlements)
	{
		if (entitlements == null)
		{
			return;
		}
		filtersPanel.setAdvancedEnabled(entitlements.isAdvancedFilters());
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
		marketBookmarksBar.setActiveBookmarkId(null);
		selectPresetSilently(0);
		updateSortControlsEnabled();
		requestMarketRefresh();
	}

	private void requestMarketRefresh()
	{
		updateFilterNotice();
		MarketQueryRequest next = buildQuery();
		boolean sameQuery = OpportunitiesClient.sameQuery(opportunitiesClient.getQuery(), next);

		if (sameQuery)
		{
			MarketQueryResponse cached = opportunitiesClient.getLatest();
			if (cached != null && !opportunitiesClient.isFetchInProgress())
			{
				renderData(cached);
			}
			else
			{
				showLoadingState();
			}
		}
		else
		{
			showLoadingState();
		}

		opportunitiesClient.setQuery(next);
		opportunitiesClient.requestImmediateRefresh();
	}

	private void showLoadingState()
	{
		statusLabel.setText("Updating market...");
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
			applyMarketEntitlements(entitlements);
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

		int presetIndex = 0;
		String presetId = bookmark.getPresetId();
		if (presetId != null)
		{
			for (int i = 0; i < PRESET_IDS.length; i++)
			{
				if (PRESET_IDS[i].equals(presetId))
				{
					presetIndex = i;
					break;
				}
			}
		}
		selectPresetWithoutClear(presetIndex);

		if (bookmark.getFilters() != null)
		{
			filtersPanel.applyFromFilters(bookmark.getFilters());
			String search = bookmark.getFilters().getSearch();
			searchField.setText(search != null ? search : "");
		}
		else if (presetIndex == 0)
		{
			filtersPanel.clearAll();
			searchField.setText("");
		}

		if (presetIndex == 0 && bookmark.getSort() != null && !bookmark.getSort().isEmpty())
		{
			applySortDescriptor(bookmark.getSort().get(0));
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
		if (response == null)
		{
			statusLabel.setText("Market data unavailable");
			statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			clearMarketList();
			return;
		}

		updateFilterNotice();

		listContainer.removeAll();
		List<FlipOpportunity> opportunities = response.getOpportunities();
		statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		if (opportunities == null || opportunities.isEmpty())
		{
			statusLabel.setText("No matches");
			listContainer.add(PluginUi.emptyState(
				"No opportunities match your filters. Widen filters or try another preset."));
		}
		else
		{
			statusLabel.setText(buildResultStatus(opportunities.size(), response));
			for (FlipOpportunity opp : opportunities)
			{
				listContainer.add(new OpportunityRow(opp, itemManager, () -> showDetail(opp)));
				listContainer.add(PluginUi.gap(PluginUi.SPACING_XS));
			}
		}
		listContainer.revalidate();
		listContainer.repaint();

		if (CARD_DETAIL.equals(activeCard) && detailItemId != null)
		{
			ItemDetailResponse cached = itemsClient.peek(detailItemId);
			FlipOpportunity unified = cached != null ? cached.getOpportunity() : itemsClient.peekOpportunity(detailItemId);
			if (unified != null)
			{
				if (cached != null)
				{
					detailView.applyDetail(cached);
				}
				else
				{
					detailView.show(unified, FlipXConstants.baseUrl());
				}
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
			syncDetailRefreshUi();
			scheduleDetailItemFetch();
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
			status.append(" | stale data");
		}
		if (opportunitiesClient.isOffline() && opportunitiesClient.getCachedAt() != null)
		{
			status.append(" | offline cached ");
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
		detailReturnCard = CARD_LIST;
		cardStack.showCard(listCard);
		refreshCardLayout();
	}

	private void onDetailBack()
	{
		if (CARD_SLOTS.equals(detailReturnCard))
		{
			detailReturnCard = CARD_LIST;
			showSlots();
			return;
		}
		showList();
	}

	private void showDetailFromSlot(SlotRecommendation slot)
	{
		showDetail(toOpportunity(slot), CARD_SLOTS);
	}

	private void showDetail(FlipOpportunity opp)
	{
		showDetail(opp, CARD_LIST);
	}

	private void showDetail(FlipOpportunity opp, String returnCard)
	{
		activeCard = CARD_DETAIL;
		detailReturnCard = returnCard;
		detailItemId = opp.getId();

		cardStack.showCard(detailView);
		scrollToTop.run();
		detailView.showLoading(opp, FlipXConstants.baseUrl());
		syncDetailRefreshUi();
		refreshCardLayout();

		SwingUtilities.invokeLater(() ->
		{
			FlipOpportunity unified = itemsClient.peekOpportunity(opp.getId());
			detailView.show(unified != null ? unified : opp, FlipXConstants.baseUrl());
			syncDetailRefreshUi();
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

	private static FlipOpportunity toOpportunity(SlotRecommendation slot)
	{
		FlipOpportunity opp = new FlipOpportunity();
		opp.setId(slot.getItemId());
		opp.setName(slot.getItemName());
		opp.setBuyLimit(slot.getBuyLimit());
		opp.setEstimatedBuyPrice(slot.getEstimatedBuyPrice());
		opp.setEstimatedSellPrice(slot.getEstimatedSellPrice());
		opp.setNetProfitPerItem(slot.getNetProfitPerItem());
		opp.setNetRoiPercent(slot.getNetRoiPercent());
		opp.setOpportunityScore(slot.getOpportunityScore());
		opp.setEstimatedProfitAtQuantity(slot.getEstimatedProfitAtQuantity());
		opp.setEstimatedProfitPerHour(Math.round(slot.getProfitPerSlotHour()));
		opp.setEstimatedCapitalRequired(slot.getCapitalRequired());
		opp.setEstimatedTurnoverHours(slot.getTurnoverHours());
		opp.setEstimatedTradableQuantity(slot.getEstimatedTradableQuantity());
		return opp;
	}

	private void onItemUpdated(int itemId)
	{
		if (detailItemId == null || detailItemId != itemId || !CARD_DETAIL.equals(activeCard))
		{
			return;
		}
		ItemDetailResponse detail = itemsClient.peek(itemId);
		if (detail != null && detail.getOpportunity() != null)
		{
			SwingUtilities.invokeLater(() ->
			{
				detailView.applyDetail(detail);
				syncDetailRefreshUi();
			});
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
