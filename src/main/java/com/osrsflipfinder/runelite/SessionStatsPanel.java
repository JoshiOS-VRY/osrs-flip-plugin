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

/** Live session dashboard — profit hero, metrics grid, ranked item breakdown. */
class SessionStatsPanel extends SidebarContentPanel
{
	private final PortfolioClient portfolioClient;
	private final ItemManager itemManager;

	private final JLabel profitLabel = new JLabel(PluginUi.PLACEHOLDER);
	private final JLabel gpHrLabel = new JLabel(PluginUi.PLACEHOLDER);
	private final JLabel roiLabel = new JLabel(PluginUi.PLACEHOLDER);
	private final JLabel flipsLabel = new JLabel(PluginUi.PLACEHOLDER);
	private final JLabel taxLabel = new JLabel(PluginUi.PLACEHOLDER);
	private final JLabel sessionMetaLabel = PluginUi.caption(" ");
	private final JLabel livePnlLabel = PluginUi.caption(" ");
	private final JPanel itemList = PluginUi.listContainer();
	private final JLabel itemsEmptyLabel = PluginUi.caption("Completed flips appear here");
	private final JLabel statusLabel = PluginUi.caption("Pair and enable GE upload");

	@Inject
	SessionStatsPanel(PortfolioClient portfolioClient, ItemManager itemManager)
	{
		this.portfolioClient = portfolioClient;
		this.itemManager = itemManager;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		PluginUi.transparent(this);

		add(PluginUi.sessionProfitHero(profitLabel, "Session profit"));
		add(PluginUi.gap(6));

		JPanel metrics = PluginUi.detailHeroGrid(
			PluginUi.statCell(gpHrLabel, "GP / hr"),
			PluginUi.statCell(roiLabel, "ROI"),
			PluginUi.statCell(flipsLabel, "Flips"),
			PluginUi.statCell(taxLabel, "Tax paid")
		);
		add(metrics);
		add(PluginUi.gap(4));
		add(sessionMetaLabel);
		add(livePnlLabel);
		add(PluginUi.gap(8));

		add(PluginUi.sectionHeader("Top items this session"));
		add(itemList);
		add(itemsEmptyLabel);
		add(PluginUi.gap(8));

		JButton portfolioLink = PluginUi.externalLinkButton("Open full portfolio");
		portfolioLink.addActionListener(e ->
			LinkBrowser.browse(FlipXConstants.baseUrl() + "/portfolio")
		);
		PluginUi.fullWidth(portfolioLink);
		add(portfolioLink);
		add(PluginUi.gap(4));
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
	}

	void refresh()
	{
		LiveSessionStats session = portfolioClient.getLatestSession();
		if (session != null)
		{
			render(session, portfolioClient.getLatestSessionItems());
		}
	}

	private void render(LiveSessionStats session, List<ItemPerformanceRow> items)
	{
		boolean hasActivity = session.getFlipCount() > 0 || session.getOpenOfferCount() > 0;

		if (!hasActivity)
		{
			profitLabel.setText(PluginUi.PLACEHOLDER);
			profitLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			gpHrLabel.setText(PluginUi.PLACEHOLDER);
			roiLabel.setText(PluginUi.PLACEHOLDER);
			flipsLabel.setText(PluginUi.PLACEHOLDER);
			taxLabel.setText(PluginUi.PLACEHOLDER);
			sessionMetaLabel.setText("No synced flips this session");
			livePnlLabel.setText(" ");
			renderItems(null);
		}
		else
		{
			long totalProfit = session.getTotalProfit();
			profitLabel.setText(MarketFormat.signedGp(totalProfit));
			profitLabel.setForeground(totalProfit >= 0 ? PluginUi.POSITIVE : PluginUi.NEGATIVE);

			long gpHr = Math.round(session.getGpPerHour());
			gpHrLabel.setText(MarketFormat.signedGp(gpHr));
			gpHrLabel.setForeground(gpHr >= 0 ? PluginUi.POSITIVE : PluginUi.NEGATIVE);

			roiLabel.setText(MarketFormat.percent(session.getRoiPercent()));
			roiLabel.setForeground(session.getRoiPercent() >= 0 ? PluginUi.POSITIVE : PluginUi.NEGATIVE);

			flipsLabel.setText(String.valueOf(session.getFlipCount()));
			flipsLabel.setForeground(Color.WHITE);

			taxLabel.setText(MarketFormat.gp(session.getTotalTax()));
			taxLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

			sessionMetaLabel.setText(buildSessionMeta(session));

			if (session.getOpenOfferCount() > 0 || session.getInProgressProfit() != 0)
			{
				livePnlLabel.setText(String.format(
					"Live: %s in %d open slot%s",
					MarketFormat.signedGp(session.getInProgressProfit()),
					session.getOpenOfferCount(),
					session.getOpenOfferCount() == 1 ? "" : "s"
				));
				livePnlLabel.setForeground(
					session.getInProgressProfit() >= 0 ? PluginUi.GOLD : PluginUi.WARNING
				);
			}
			else
			{
				livePnlLabel.setText(" ");
			}

			renderItems(items);
		}

		if (portfolioClient.isSessionOffline() && portfolioClient.getSessionCachedAt() != null)
		{
			String cached = LocalCacheStore.formatCachedAt(portfolioClient.getSessionCachedAt());
			if (portfolioClient.isSessionItemsOffline() && portfolioClient.getSessionItemsCachedAt() != null)
			{
				statusLabel.setText("Offline · cached " + cached);
			}
			else
			{
				statusLabel.setText("Offline · cached " + cached);
			}
		}
		else
		{
			statusLabel.setText(" ");
		}
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
			for (ItemPerformanceRow item : items)
			{
				itemList.add(new SessionItemRow(item, itemManager));
				itemList.add(PluginUi.gap(3));
			}
		}
		itemList.revalidate();
		itemList.repaint();
	}

	private static String buildSessionMeta(LiveSessionStats session)
	{
		StringBuilder meta = new StringBuilder();
		meta.append(formatDuration(session.getSessionDurationMs()));
		meta.append(" session");

		double avg = session.getAvgGpPerHour7d();
		if (avg > 0)
		{
			double pct = ((session.getGpPerHour() - avg) / avg) * 100;
			meta.append(" · ");
			meta.append(String.format("%+.0f%% vs 7-day avg", pct));
		}

		if (session.getFlipsPerHour() > 0)
		{
			meta.append(" · ");
			meta.append(String.format("%.1f flips/hr", session.getFlipsPerHour()));
		}

		return meta.toString();
	}

	private static String formatDuration(long ms)
	{
		long totalSec = ms / 1000;
		long h = totalSec / 3600;
		long m = (totalSec % 3600) / 60;
		return String.format("%02d:%02d", h, m);
	}
}
