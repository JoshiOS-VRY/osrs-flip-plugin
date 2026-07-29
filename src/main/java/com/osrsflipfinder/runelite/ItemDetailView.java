package com.osrsflipfinder.runelite;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.LinkBrowser;

/** Inline item detail view with compact stats for the narrow sidebar. */
class ItemDetailView extends SidebarContentPanel
{
	private final ItemManager itemManager;
	private final ScheduledExecutorService executorService;
	private final WatchlistClient watchlistClient;
	private final Consumer<String> errorListener;

	private final JLabel iconLabel = new JLabel();
	private final JLabel nameLabel = new JLabel();
	private final JLabel loadingLabel = PluginUi.loadingCaption(" ");
	private final JLabel refreshTimerLabel = PluginUi.caption(" ");
	private final JLabel foundLabel = new JLabel(PluginUi.PLACEHOLDER);
	private final JLabel roiLabel = new JLabel(PluginUi.PLACEHOLDER);
	private final JLabel gpHrLabel = new JLabel(PluginUi.PLACEHOLDER);
	private final JLabel capitalLabel = new JLabel(PluginUi.PLACEHOLDER);
	private final JPanel statsPanel = PluginUi.statBlock();
	private final JPanel networkPanel = PluginUi.statBlock();
	private final JLabel networkHeaderLabel = PluginUi.caption("Network");
	private final JButton watchButton = PluginUi.secondaryButton("Add to watchlist");
	private final JButton openButton = PluginUi.externalLinkButton("Open in web app");

	private FlipOpportunity current;
	private volatile int loadingItemId = -1;
	private String baseUrl = "";

	ItemDetailView(
		ItemManager itemManager,
		ScheduledExecutorService executorService,
		WatchlistClient watchlistClient,
		Runnable onBack,
		Consumer<String> errorListener
	)
	{
		this.itemManager = itemManager;
		this.executorService = executorService;
		this.watchlistClient = watchlistClient;
		this.errorListener = errorListener;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setAlignmentX(LEFT_ALIGNMENT);

		add(PluginUi.backButton(onBack));
		add(PluginUi.gap(PluginUi.SPACING_XS));
		refreshTimerLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		refreshTimerLabel.setAlignmentX(LEFT_ALIGNMENT);
		SidebarContentPanel.lockWidth(refreshTimerLabel);
		add(refreshTimerLabel);
		add(PluginUi.gap(PluginUi.SPACING_XS));
		add(loadingLabel);
		add(PluginUi.gap(PluginUi.SPACING_XS));

		JPanel headerCard = PluginUi.card();
		headerCard.setLayout(new BoxLayout(headerCard, BoxLayout.Y_AXIS));
		iconLabel.setPreferredSize(new Dimension(32, 28));
		iconLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		nameLabel.setForeground(Color.WHITE);
		nameLabel.setFont(FontManager.getRunescapeBoldFont());
		nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		headerCard.add(iconLabel);
		headerCard.add(PluginUi.gap(PluginUi.SPACING_XS));
		headerCard.add(nameLabel);
		add(headerCard);
		add(PluginUi.gap(PluginUi.SPACING_SM));

		JPanel hero = PluginUi.detailHeroGrid(
			PluginUi.statCell(foundLabel, "Net / item"),
			PluginUi.statCell(roiLabel, "Net ROI"),
			PluginUi.statCell(gpHrLabel, "GP / hr"),
			PluginUi.statCell(capitalLabel, "Capital")
		);
		add(hero);
		add(PluginUi.gap(PluginUi.SPACING_SM));
		add(statsPanel);
		add(PluginUi.gap(PluginUi.SPACING_SM));
		networkHeaderLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		networkHeaderLabel.setAlignmentX(LEFT_ALIGNMENT);
		add(networkHeaderLabel);
		add(PluginUi.gap(PluginUi.SPACING_XS));
		add(networkPanel);
		add(PluginUi.gap(PluginUi.SPACING_MD));

		watchButton.addActionListener(e -> addToWatchlist());
		openButton.addActionListener(e -> openInWeb());
		JPanel actions = PluginUi.buttonRow(watchButton, openButton);
		PluginUi.fullWidth(actions);
		add(actions);
		SidebarContentPanel.lockWidth(this);
	}

	/** Lightweight skeleton shown immediately when navigating from the list. */
	void updateRefreshStatus(String pollingLine, long lastUpdatedMs)
	{
		String updated = lastUpdatedMs > 0
			? "Updated " + MarketFormat.updatedClock(lastUpdatedMs)
			: null;
		refreshTimerLabel.setText(RefreshCountdown.combine(pollingLine, updated));
	}

	void showLoading(FlipOpportunity opp, String baseUrl)
	{
		this.current = opp;
		this.baseUrl = baseUrl;
		this.loadingItemId = opp.getId();

		loadingLabel.setText("Loading prices...");
		loadingLabel.setVisible(true);
		iconLabel.setIcon(null);
		setItemName(opp.getName());

		PluginUi.setHeroGridStatValue(foundLabel, PluginUi.PLACEHOLDER, null);
		PluginUi.setHeroGridStatValue(roiLabel, PluginUi.PLACEHOLDER, null);
		PluginUi.setHeroGridStatValue(gpHrLabel, PluginUi.PLACEHOLDER, null);
		PluginUi.setHeroGridStatValue(capitalLabel, PluginUi.PLACEHOLDER, null);

		statsPanel.removeAll();
		PluginUi.addStatLine(statsPanel, "Wiki est. buy", PluginUi.PLACEHOLDER);
		PluginUi.addStatLine(statsPanel, "Wiki est. sell", PluginUi.PLACEHOLDER);
		PluginUi.addStatLine(statsPanel, "Score", PluginUi.PLACEHOLDER);
		PluginUi.finalizeStatBlock(statsPanel);
		networkPanel.removeAll();
		PluginUi.finalizeStatBlock(networkPanel);
		networkHeaderLabel.setVisible(false);
		networkPanel.setVisible(false);

		watchButton.setEnabled(false);
		openButton.setEnabled(true);
	}

	void show(FlipOpportunity opp, String baseUrl)
	{
		show(opp, baseUrl, null);
	}

	void show(FlipOpportunity opp, String baseUrl, NetworkIntelResponse networkIntel)
	{
		if (opp == null)
		{
			return;
		}

		this.current = opp;
		this.baseUrl = baseUrl;
		this.loadingItemId = -1;
		loadingLabel.setVisible(false);

		watchButton.setEnabled(true);
		watchButton.setText("Add to watchlist");
		watchButton.setForeground(Color.WHITE);
		openButton.setEnabled(true);

		populateText(opp, networkIntel);

		final int itemId = opp.getId();
		executorService.execute(() -> SwingUtilities.invokeLater(() ->
		{
			if (current != null && current.getId() == itemId)
			{
				itemManager.getImage(itemId).addTo(iconLabel);
			}
		}));
	}

	void applyDetail(ItemDetailResponse detail)
	{
		if (detail == null || detail.getOpportunity() == null)
		{
			return;
		}
		show(detail.getOpportunity(), baseUrl, detail.getNetworkIntel());
		String networkLine = NetworkIntelUi.networkUpdatedLine(detail.getNetworkIntel());
		if (networkLine != null)
		{
			String existing = refreshTimerLabel.getText();
			refreshTimerLabel.setText(
				existing == null || existing.isBlank() ? networkLine : existing + " · " + networkLine
			);
		}
	}

	private void populateText(FlipOpportunity opp, NetworkIntelResponse networkIntel)
	{
		setItemName(opp.getName());

		long net = opp.getNetProfitPerItem();
		PluginUi.setHeroGridStatValue(
			foundLabel,
			MarketFormat.gpCompactSigned(net),
			MarketFormat.signedGp(net)
		);
		foundLabel.setForeground(net >= 0 ? PluginUi.POSITIVE : PluginUi.NEGATIVE);

		double roi = opp.getNetRoiPercent();
		PluginUi.setHeroGridStatValue(
			roiLabel,
			MarketFormat.percent(roi),
			MarketFormat.percent(roi)
		);

		long gpHr = FlipCopilotPresenter.estimatedGpPerHour(opp);
		PluginUi.setHeroGridStatValue(
			gpHrLabel,
			MarketFormat.gpCompact(gpHr),
			MarketFormat.gp(gpHr)
		);

		long capital = opp.getEstimatedCapitalRequired();
		PluginUi.setHeroGridStatValue(
			capitalLabel,
			MarketFormat.gpCompact(capital),
			MarketFormat.gp(capital)
		);

		statsPanel.removeAll();
		PluginUi.addStatLine(statsPanel, "Wiki est. buy", MarketFormat.gp(opp.getEstimatedBuyPrice()));
		PluginUi.addStatLine(statsPanel, "Wiki est. sell", MarketFormat.gp(opp.getEstimatedSellPrice()));
		long breakEven = GeTax.breakEvenSellPrice(opp.getEstimatedBuyPrice(), opp.getId());
		if (breakEven > 0)
		{
			PluginUi.addStatLine(statsPanel, "Break-even sell", MarketFormat.gp(breakEven));
		}
		PluginUi.addStatLine(statsPanel, "Total profit", MarketFormat.gp(opp.getEstimatedProfitAtQuantity()));
		PluginUi.addStatLine(statsPanel, "Turnover", String.format("%.1fh", opp.getEstimatedTurnoverHours()));
		PluginUi.addStatLine(statsPanel, "Score", opp.getOpportunityScore() + "/100");
		PluginUi.addStatLine(statsPanel, "Confidence", String.format("%.0f%%", opp.getConfidenceScore() * 100));
		PluginUi.addStatLine(
			statsPanel,
			"Buy limit",
			opp.getBuyLimit() != null ? String.valueOf(opp.getBuyLimit()) : PluginUi.PLACEHOLDER
		);
		PluginUi.addStatLine(statsPanel, "1h volume", MarketFormat.gp(opp.getOneHourVolume()));
		PluginUi.finalizeStatBlock(statsPanel);

		boolean hasNetwork = networkIntel != null
			&& (networkIntel.getSignal() != null
				|| networkIntel.getDivergence() != null
				|| networkIntel.getCoopFlipBand() != null
				|| networkIntel.getNetworkPrices() != null
				|| networkIntel.getEdgeScore() != null);
		networkHeaderLabel.setVisible(hasNetwork);
		networkPanel.setVisible(hasNetwork);
		if (hasNetwork)
		{
			NetworkIntelUi.render(networkPanel, networkIntel, opp);
		}
		else
		{
			networkPanel.removeAll();
			PluginUi.finalizeStatBlock(networkPanel);
		}

		statsPanel.revalidate();
		statsPanel.repaint();
		networkPanel.revalidate();
		networkPanel.repaint();
		revalidate();
		repaint();
	}

	private void setItemName(String name)
	{
		String safe = name != null ? name : "Item";
		int wrap = PluginUi.cardBodyWrapWidth();
		nameLabel.setText(
			"<html><div style='width:" + wrap + "px;'>" + escapeHtml(safe) + "</div></html>"
		);
		nameLabel.setToolTipText(safe.length() > 40 ? safe : null);
	}

	private static String escapeHtml(String text)
	{
		return text
			.replace("&", "&amp;")
			.replace("<", "&lt;")
			.replace(">", "&gt;");
	}

	private void addToWatchlist()
	{
		if (current == null || loadingItemId >= 0)
		{
			return;
		}
		FlipOpportunity opp = current;
		watchButton.setEnabled(false);
		executorService.execute(() ->
		{
			try
			{
				watchlistClient.add(opp.getId(), opp.getName());
				SwingUtilities.invokeLater(() ->
				{
					watchButton.setText("Added");
					watchButton.setForeground(PluginUi.POSITIVE);
				});
			}
			catch (IOException e)
			{
				errorListener.accept(e.getMessage());
				SwingUtilities.invokeLater(() -> watchButton.setEnabled(true));
			}
		});
	}

	private void openInWeb()
	{
		if (current != null && !baseUrl.isEmpty())
		{
			LinkBrowser.browse(baseUrl + "/items/" + current.getId());
		}
	}
}
