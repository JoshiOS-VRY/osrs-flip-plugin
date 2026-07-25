package com.osrsflipfinder.runelite;

import java.awt.Color;
import java.util.List;
import javax.inject.Inject;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.LinkBrowser;

/** Live session dashboard - profit hero, metrics, ranked item breakdown. */
class SessionStatsPanel extends SidebarContentPanel
{
	private final PortfolioClient portfolioClient;
	private final ItemManager itemManager;

	private final JLabel profitLabel = new JLabel(PluginUi.PLACEHOLDER);
	private final JLabel profitSubLabel = PluginUi.caption("After GE tax | completed flips");
	private final JLabel heroGpHr = new JLabel(PluginUi.PLACEHOLDER, JLabel.CENTER);
	private final JLabel heroRoi = new JLabel(PluginUi.PLACEHOLDER, JLabel.CENTER);
	private final JLabel heroFlips = new JLabel(PluginUi.PLACEHOLDER, JLabel.CENTER);
	private final JLabel heroTime = new JLabel(PluginUi.PLACEHOLDER, JLabel.CENTER);
	private final JPanel metricsBlock = PluginUi.statBlock();
	private final JLabel sessionMetaLabel = PluginUi.caption(" ");
	private final JPanel itemList = PluginUi.listContainer();
	private final JLabel itemsEmptyLabel = PluginUi.caption("Completed flips appear here");
	private final JLabel statusLabel = PluginUi.caption("Pair and enable GE upload");
	private final JLabel refreshTimerLabel = PluginUi.caption(" ");

	@Inject
	SessionStatsPanel(PortfolioClient portfolioClient, ItemManager itemManager)
	{
		this.portfolioClient = portfolioClient;
		this.itemManager = itemManager;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		PluginUi.transparent(this);

		add(PluginUi.sessionProfitHero(profitLabel, "Session P&L", profitSubLabel));
		add(PluginUi.gap(PluginUi.SPACING_SM));
		add(PluginUi.detailHeroGrid(
			PluginUi.statCell(heroGpHr, "GP / hour"),
			PluginUi.statCell(heroRoi, "Net ROI"),
			PluginUi.statCell(heroFlips, "Flips"),
			PluginUi.statCell(heroTime, "Session time")
		));
		add(PluginUi.gap(PluginUi.SPACING_SM));
		add(PluginUi.sectionHeader("Session breakdown"));
		add(metricsBlock);
		add(PluginUi.gap(PluginUi.SPACING_XS));
		add(sessionMetaLabel);
		add(PluginUi.gap(PluginUi.SPACING_MD));

		add(PluginUi.sectionHeader("Top items this session"));
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

		fillMetricsPlaceholder();
		fillHeroPlaceholders();
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
		boolean hasActivity = session.getFlipCount() > 0 || session.getOpenOfferCount() > 0;

		metricsBlock.removeAll();

		if (!hasActivity)
		{
			profitLabel.setText(PluginUi.PLACEHOLDER);
			profitLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			setProfitSubline("After GE tax | completed flips");
			fillHeroPlaceholders();
			fillMetricsPlaceholder();
			sessionMetaLabel.setText("No synced flips this session");
			renderItems(null);
		}
		else
		{
			long totalProfit = session.getTotalProfit();
			profitLabel.setText(MarketFormat.signedGp(totalProfit));
			profitLabel.setForeground(totalProfit >= 0 ? PluginUi.POSITIVE : PluginUi.NEGATIVE);

			long completed = session.getCompletedProfit();
			long live = session.getInProgressProfit();
			if (session.getOpenOfferCount() > 0 || live != 0)
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
			PluginUi.addStatLine(
				metricsBlock,
				"Completed P&L",
				MarketFormat.signedGp(session.getCompletedProfit()),
				session.getCompletedProfit() >= 0 ? PluginUi.POSITIVE : PluginUi.NEGATIVE
			);
			if (session.getOpenOfferCount() > 0 || live != 0)
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

			if (session.getInProgressTax() > 0)
			{
				PluginUi.addStatLine(
					metricsBlock,
					"Live slot tax (est.)",
					MarketFormat.gp(session.getInProgressTax()),
					ColorScheme.LIGHT_GRAY_COLOR
				);
			}

			PluginUi.finalizeStatBlock(metricsBlock);

			sessionMetaLabel.setText(buildSessionMeta(session));
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

	private static String buildSessionMeta(LiveSessionStats session)
	{
		if (session.getStartedAt() != null && !session.getStartedAt().isBlank())
		{
			return "Session started " + session.getStartedAt().replace('T', ' ').substring(0, 16);
		}
		return " ";
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
