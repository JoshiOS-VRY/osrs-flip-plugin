package com.osrsflipfinder.runelite;

import java.awt.Color;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.LinkBrowser;

/** Live session dashboard - profit hero, metrics, ranked item breakdown. */
class SessionStatsPanel extends SidebarContentPanel
{
	private static final String CONFIG_PORTFOLIO_PERIOD = "portfolioPeriodId";

	private final PortfolioClient portfolioClient;
	private final ConfigManager configManager;
	private final ItemManager itemManager;

	private final JComboBox<String> periodCombo = new JComboBox<>(new DefaultComboBoxModel<>(PortfolioPeriod.LABELS));
	private final JLabel profitHeroCaption = new JLabel("Session P&L", JLabel.CENTER);
	private final JLabel profitLabel = new JLabel(PluginUi.PLACEHOLDER);
	private final JLabel profitSubLabel = PluginUi.caption("After GE tax | completed flips");
	private final JLabel heroGpHr = new JLabel(PluginUi.PLACEHOLDER, JLabel.CENTER);
	private final JLabel heroRoi = new JLabel(PluginUi.PLACEHOLDER, JLabel.CENTER);
	private final JLabel heroFlips = new JLabel(PluginUi.PLACEHOLDER, JLabel.CENTER);
	private final JLabel heroTime = new JLabel(PluginUi.PLACEHOLDER, JLabel.CENTER);
	private final JLabel heroTimeCaption = new JLabel("Session time", JLabel.CENTER);
	private final JLabel breakdownTitleLabel = PluginUi.sectionTitle("Session breakdown");
	private final JLabel topItemsTitleLabel = PluginUi.sectionTitle("Top items this session");
	private final JPanel metricsBlock = PluginUi.statBlock();
	private final JLabel sessionMetaLabel = PluginUi.caption(" ");
	private final JPanel itemList = PluginUi.listContainer();
	private final JLabel itemsEmptyLabel = PluginUi.caption("Completed flips appear here");
	private final JLabel statusLabel = PluginUi.caption("Pair and enable GE upload");
	private final JLabel refreshTimerLabel = PluginUi.caption(" ");

	@Inject
	SessionStatsPanel(
		PortfolioClient portfolioClient,
		ConfigManager configManager,
		ItemManager itemManager
	)
	{
		this.portfolioClient = portfolioClient;
		this.configManager = configManager;
		this.itemManager = itemManager;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		PluginUi.transparent(this);

		PluginUi.styleCombo(periodCombo);
		periodCombo.addActionListener(e -> onPeriodSelected());

		add(PluginUi.labeledField("Time period", periodCombo));
		add(PluginUi.gap(PluginUi.SPACING_SM));
		add(PluginUi.sessionProfitHero(profitLabel, profitHeroCaption, profitSubLabel));
		add(PluginUi.gap(PluginUi.SPACING_SM));
		add(PluginUi.detailHeroGrid(
			PluginUi.statCell(heroGpHr, "GP / hour"),
			PluginUi.statCell(heroRoi, "Net ROI"),
			PluginUi.statCell(heroFlips, "Flips"),
			PluginUi.statCell(heroTime, heroTimeCaption)
		));
		add(PluginUi.gap(PluginUi.SPACING_SM));
		add(PluginUi.sectionHeader(breakdownTitleLabel));
		add(metricsBlock);
		add(PluginUi.gap(PluginUi.SPACING_XS));
		add(sessionMetaLabel);
		add(PluginUi.gap(PluginUi.SPACING_MD));

		add(PluginUi.sectionHeader(topItemsTitleLabel));
		add(itemList);
		add(itemsEmptyLabel);
		add(PluginUi.gap(PluginUi.SPACING_MD));

		JButton portfolioLink = PluginUi.externalLinkButton("Open full portfolio");
		portfolioLink.addActionListener(e ->
			LinkBrowser.browse(FlipXConstants.baseUrl() + "/portfolio")
		);
		PluginUi.fullWidth(portfolioLink);
		add(portfolioLink);
		add(PluginUi.gap(PluginUi.SPACING_XS));
		refreshTimerLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		add(refreshTimerLabel);
		add(statusLabel);

		portfolioClient.setSessionListener(session ->
			SwingUtilities.invokeLater(() -> render(session, portfolioClient.getLatestSessionItems()))
		);
		portfolioClient.setSessionItemsListener(items ->
			SwingUtilities.invokeLater(() ->
			{
				LiveSessionStats session = portfolioClient.getLatestSession();
				if (session != null)
				{
					render(session, items);
				}
			})
		);

		loadSavedPeriod();
		applyPeriodLabels(portfolioClient.getPortfolioPeriod());
		fillMetricsPlaceholder();
		fillHeroPlaceholders();
	}

	private void loadSavedPeriod()
	{
		String savedId = FlipFinderConfigIO.getString(configManager, CONFIG_PORTFOLIO_PERIOD, PortfolioPeriod.DEFAULT.getId());
		PortfolioPeriod period = PortfolioPeriod.fromId(savedId);
		portfolioClient.setPortfolioPeriod(period);
		int index = PortfolioPeriod.indexOf(period);
		if (periodCombo.getSelectedIndex() != index)
		{
			periodCombo.setSelectedIndex(index);
		}
	}

	private void onPeriodSelected()
	{
		PortfolioPeriod period = PortfolioPeriod.fromIndex(periodCombo.getSelectedIndex());
		configManager.setConfiguration(FlipFinderConfig.GROUP, CONFIG_PORTFOLIO_PERIOD, period.getId());
		portfolioClient.setPortfolioPeriod(period);
		applyPeriodLabels(period);
	}

	private void applyPeriodLabels(PortfolioPeriod period)
	{
		if (period == null)
		{
			period = PortfolioPeriod.DEFAULT;
		}
		profitHeroCaption.setText(period.heroProfitTitle());
		breakdownTitleLabel.setText(period.breakdownSectionTitle());
		topItemsTitleLabel.setText(period.topItemsSectionTitle());
		heroTimeCaption.setText(period.heroTimeLabel());
		PluginUi.setGridStatCaption(
			heroTimeCaption,
			period.heroTimeLabel(),
			PluginUi.heroGridCellWidth(2)
		);
	}

	void refresh()
	{
		LiveSessionStats session = portfolioClient.getLatestSession();
		if (session != null)
		{
			render(session, portfolioClient.getLatestSessionItems());
		}
	}

	void updateRefreshTimer(boolean paired)
	{
		if (!paired || !portfolioClient.isActive())
		{
			refreshTimerLabel.setText(" ");
			return;
		}
		refreshTimerLabel.setText(RefreshCountdown.formatPolling(
			portfolioClient.getNextRefreshAtMs(),
			portfolioClient.isActive(),
			portfolioClient.isFetchInProgress()
		));
	}

	private void render(LiveSessionStats session, List<ItemPerformanceRow> items)
	{
		PortfolioPeriod period = portfolioClient.getPortfolioPeriod();
		boolean statsReady = portfolioClient.isSessionStatsForPeriod(period);
		boolean itemsReady = portfolioClient.isSessionItemsForPeriod(period);

		if (!itemsReady)
		{
			items = Collections.emptyList();
		}

		if (session == null || !statsReady)
		{
			fillHeroPlaceholders();
			fillMetricsPlaceholder();
			sessionMetaLabel.setText(
				portfolioClient.isFetchInProgress() || portfolioClient.isActive()
					? "Loading " + period.getLabel().toLowerCase() + "…"
					: " "
			);
			renderItems(items);
			if (portfolioClient.isSessionOffline() && portfolioClient.getSessionCachedAt() != null)
			{
				statusLabel.setText(
					"Offline | cached " + LocalCacheStore.formatCachedAt(portfolioClient.getSessionCachedAt())
				);
			}
			else
			{
				statusLabel.setText(" ");
			}
			revalidate();
			repaint();
			return;
		}

		boolean liveSession = period.isLiveSession();
		boolean hasActivity = liveSession
			? session.getFlipCount() > 0 || session.getOpenOfferCount() > 0
			: session.getFlipCount() > 0;

		metricsBlock.removeAll();

		if (!hasActivity)
		{
			profitLabel.setText(PluginUi.PLACEHOLDER);
			profitLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			setProfitSubline("After GE tax | completed flips");
			fillHeroPlaceholders();
			fillMetricsPlaceholder();
			sessionMetaLabel.setText(
				liveSession
					? "No synced flips this session"
					: "No completed flips in " + period.getLabel().toLowerCase()
			);
			renderItems(items);
		}
		else
		{
			long totalProfit = session.getTotalProfit();
			profitLabel.setText(MarketFormat.signedGp(totalProfit));
			profitLabel.setForeground(totalProfit >= 0 ? PluginUi.POSITIVE : PluginUi.NEGATIVE);

			long completed = session.getCompletedProfit();
			long live = session.getInProgressProfit();
			if (liveSession && (session.getOpenOfferCount() > 0 || live != 0))
			{
				setProfitSubline(String.format(
					"Completed %s | Live %s | %d open",
					MarketFormat.signedGp(completed),
					MarketFormat.signedGp(live),
					session.getOpenOfferCount()
				));
			}
			else
			{
				setProfitSubline("After GE tax | " + session.getFlipCount() + " flips closed");
			}

			long gpHr = Math.round(session.getGpPerHour());
			PluginUi.setHeroGridStatValue(
				heroGpHr,
				MarketFormat.gpCompactSigned(gpHr),
				MarketFormat.signedGp(gpHr)
			);
			heroGpHr.setForeground(gpHr >= 0 ? PluginUi.POSITIVE : PluginUi.NEGATIVE);
			double roiPct = session.getRoiPercent();
			PluginUi.setHeroGridStatValue(heroRoi, MarketFormat.percent(roiPct), null);
			heroRoi.setForeground(roiPct >= 0 ? PluginUi.POSITIVE : PluginUi.NEGATIVE);
			PluginUi.setHeroGridStatValue(heroFlips, String.valueOf(session.getFlipCount()), null);
			heroFlips.setForeground(Color.WHITE);
			String duration = formatDuration(session.getSessionDurationMs());
			PluginUi.setHeroGridStatValue(heroTime, duration, null);
			heroTime.setForeground(Color.WHITE);

			metricsBlock.removeAll();
			String completedLabel = liveSession ? "Completed P&L" : "Net P&L";
			PluginUi.addStatLine(
				metricsBlock,
				completedLabel,
				MarketFormat.signedGp(liveSession ? session.getCompletedProfit() : session.getTotalProfit()),
				session.getTotalProfit() >= 0 ? PluginUi.POSITIVE : PluginUi.NEGATIVE
			);
			if (liveSession && (session.getOpenOfferCount() > 0 || live != 0))
			{
				PluginUi.addStatLine(
					metricsBlock,
					"Live P&L (est.)",
					MarketFormat.signedGp(live),
					live >= 0 ? PluginUi.POSITIVE : PluginUi.NEGATIVE
				);
				PluginUi.addStatLine(
					metricsBlock,
					"Open offers",
					String.valueOf(session.getOpenOfferCount()),
					Color.WHITE
				);
			}
			PluginUi.addStatLine(
				metricsBlock,
				"GE tax paid",
				MarketFormat.gp(session.getTotalTax()),
				ColorScheme.LIGHT_GRAY_COLOR
			);

			if (session.getFlipsPerHour() > 0)
			{
				PluginUi.addStatLine(
					metricsBlock,
					"Flip pace",
					String.format("%.1f / hr", session.getFlipsPerHour()),
					ColorScheme.LIGHT_GRAY_COLOR
				);
			}

			double avg = session.getAvgGpPerHour7d();
			if (avg > 0)
			{
				double pct = ((session.getGpPerHour() - avg) / avg) * 100;
				Color vsColor = pct >= 0 ? PluginUi.POSITIVE : PluginUi.WARNING;
				PluginUi.addStatLine(
					metricsBlock,
					"vs 7-day avg",
					String.format("%+.0f%% GP/hr", pct),
					vsColor
				);
			}

			if (liveSession && session.getInProgressTax() > 0)
			{
				PluginUi.addStatLine(
					metricsBlock,
					"Live slot tax (est.)",
					MarketFormat.gp(session.getInProgressTax()),
					ColorScheme.LIGHT_GRAY_COLOR
				);
			}

			PluginUi.finalizeStatBlock(metricsBlock);

			sessionMetaLabel.setText(buildPeriodMeta(session, period));
			renderItems(items);
		}

		if (portfolioClient.isSessionOffline() && portfolioClient.getSessionCachedAt() != null)
		{
			statusLabel.setText(
				"Offline | cached " + LocalCacheStore.formatCachedAt(portfolioClient.getSessionCachedAt())
			);
		}
		else
		{
			statusLabel.setText(" ");
		}

		metricsBlock.revalidate();
		metricsBlock.repaint();
		revalidate();
		repaint();
	}

	private void fillMetricsPlaceholder()
	{
		metricsBlock.removeAll();
		PluginUi.addStatLine(metricsBlock, "Completed P&L", PluginUi.PLACEHOLDER, ColorScheme.LIGHT_GRAY_COLOR);
		PluginUi.addStatLine(metricsBlock, "GE tax paid", PluginUi.PLACEHOLDER, ColorScheme.LIGHT_GRAY_COLOR);
		PluginUi.addStatLine(metricsBlock, "Flip pace", PluginUi.PLACEHOLDER, ColorScheme.LIGHT_GRAY_COLOR);
		PluginUi.addStatLine(metricsBlock, "vs 7-day avg", PluginUi.PLACEHOLDER, ColorScheme.LIGHT_GRAY_COLOR);
		PluginUi.finalizeStatBlock(metricsBlock);
	}

	private void fillHeroPlaceholders()
	{
		PluginUi.setHeroGridStatValue(heroGpHr, PluginUi.PLACEHOLDER, null);
		heroGpHr.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		PluginUi.setHeroGridStatValue(heroRoi, PluginUi.PLACEHOLDER, null);
		heroRoi.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		PluginUi.setHeroGridStatValue(heroFlips, PluginUi.PLACEHOLDER, null);
		heroFlips.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		PluginUi.setHeroGridStatValue(heroTime, PluginUi.PLACEHOLDER, null);
		heroTime.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
	}

	private void setProfitSubline(String text)
	{
		int wrap = SidebarContentPanel.INNER_WIDTH - 24;
		profitSubLabel.setText(
			"<html><div style='width:" + wrap + "px;text-align:center;color:" + PluginUi.htmlColor(PluginUi.TEXT_HINT) + ";'>"
				+ escapeHtml(text)
				+ "</div></html>"
		);
	}

	private static String escapeHtml(String text)
	{
		if (text == null)
		{
			return "";
		}
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private void renderItems(List<ItemPerformanceRow> items)
	{
		itemList.removeAll();
		if (items == null || items.isEmpty())
		{
			itemsEmptyLabel.setVisible(true);
			itemsEmptyLabel.setText(
				portfolioClient.isSessionItemsOffline() ? "Item breakdown unavailable offline" :
					"Completed flips appear here"
			);
		}
		else
		{
			itemsEmptyLabel.setVisible(false);
			int limit = Math.min(items.size(), 8);
			for (int i = 0; i < limit; i++)
			{
				itemList.add(new SessionItemRow(items.get(i), itemManager));
				itemList.add(PluginUi.gap(PluginUi.SPACING_XS));
			}
			if (items.size() > limit)
			{
				JLabel more = PluginUi.caption("+" + (items.size() - limit) + " more on web");
				itemList.add(more);
			}
		}
		itemList.revalidate();
		itemList.repaint();
	}

	private static String buildPeriodMeta(LiveSessionStats session, PortfolioPeriod period)
	{
		if (period.isLiveSession())
		{
			if (session.getStartedAt() != null && !session.getStartedAt().isBlank())
			{
				return "Session started " + formatIsoShort(session.getStartedAt());
			}
			return " ";
		}

		String from = session.getStartedAt();
		String to = session.getEndedAt();
		if (from != null && !from.isBlank() && to != null && !to.isBlank())
		{
			return formatIsoShort(from) + " → " + formatIsoShort(to);
		}
		if (from != null && !from.isBlank())
		{
			return "Since " + formatIsoShort(from);
		}
		return period.getLabel();
	}

	private static String formatIsoShort(String iso)
	{
		if (iso.length() >= 16)
		{
			return iso.replace('T', ' ').substring(0, 16);
		}
		return iso.replace('T', ' ');
	}

	private static String formatDuration(long ms)
	{
		long totalSec = ms / 1000;
		long h = totalSec / 3600;
		long m = (totalSec % 3600) / 60;
		long s = totalSec % 60;
		if (h > 0)
		{
			return String.format("%d:%02d:%02d", h, m, s);
		}
		return String.format("%02d:%02d", m, s);
	}
}
