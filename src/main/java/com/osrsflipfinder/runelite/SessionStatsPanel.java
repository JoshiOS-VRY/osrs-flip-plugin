package com.osrsflipfinder.runelite;

import java.awt.Color;
import javax.inject.Inject;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.LinkBrowser;

/** Compact live session GP/hr widget. */
class SessionStatsPanel extends SidebarContentPanel
{
	private final PortfolioClient portfolioClient;
	private final FlipFinderConfig config;

	private final JLabel gpHrLabel = new JLabel("—");
	private final JLabel flipsLabel = PluginUi.caption("—");
	private final JLabel vsAvgLabel = PluginUi.caption(" ");
	private final JLabel statusLabel = PluginUi.caption("Pair and enable GE upload");

	@Inject
	SessionStatsPanel(PortfolioClient portfolioClient, FlipFinderConfig config)
	{
		this.portfolioClient = portfolioClient;
		this.config = config;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		PluginUi.transparent(this);

		JPanel card = PluginUi.card();
		gpHrLabel.setForeground(Color.WHITE);
		gpHrLabel.setFont(gpHrLabel.getFont().deriveFont(14f));
		card.add(PluginUi.statCell(gpHrLabel, "GP / hr"));
		card.add(PluginUi.gap(4));
		card.add(flipsLabel);
		card.add(vsAvgLabel);
		card.add(PluginUi.gap(6));

		JButton portfolioLink = PluginUi.externalLinkButton("Open portfolio");
		portfolioLink.addActionListener(e ->
			LinkBrowser.browse(FlipXConstants.baseUrl() + "/portfolio")
		);
		PluginUi.fullWidth(portfolioLink);
		card.add(portfolioLink);
		add(card);
		add(statusLabel);

		portfolioClient.setSessionListener(session -> SwingUtilities.invokeLater(() -> render(session)));
	}

	void refresh()
	{
		LiveSessionStats session = portfolioClient.getLatestSession();
		if (session != null)
		{
			render(session);
		}
	}

	private void render(LiveSessionStats session)
	{
		if (session.getFlipCount() <= 0 && session.getOpenOfferCount() <= 0)
		{
			gpHrLabel.setText("—");
			flipsLabel.setText("No synced flips this session");
			vsAvgLabel.setText(" ");
		}
		else
		{
			gpHrLabel.setText(MarketFormat.signedGp(Math.round(session.getGpPerHour())));
			gpHrLabel.setForeground(session.getGpPerHour() >= 0 ? PluginUi.POSITIVE : PluginUi.NEGATIVE);
			flipsLabel.setText(session.getFlipCount() + " flips · " + formatDuration(session.getSessionDurationMs()));
			double avg = session.getAvgGpPerHour7d();
			if (avg > 0)
			{
				double pct = ((session.getGpPerHour() - avg) / avg) * 100;
				vsAvgLabel.setText(String.format("%.0f%% vs 7-day avg", pct));
				vsAvgLabel.setForeground(pct >= 0 ? PluginUi.POSITIVE : PluginUi.NEGATIVE);
			}
			else
			{
				vsAvgLabel.setText(" ");
			}
		}

		if (portfolioClient.isSessionOffline() && portfolioClient.getSessionCachedAt() != null)
		{
			statusLabel.setText("Offline · cached " + LocalCacheStore.formatCachedAt(portfolioClient.getSessionCachedAt()));
		}
		else
		{
			statusLabel.setText(" ");
		}
	}

	private static String formatDuration(long ms)
	{
		long totalSec = ms / 1000;
		long h = totalSec / 3600;
		long m = (totalSec % 3600) / 60;
		return String.format("%02d:%02d", h, m);
	}
}
