package com.osrsflipfinder.runelite;

import javax.swing.JLabel;
import javax.swing.JPanel;

/** Renders tier-filtered network intel in the plugin sidebar. */
final class NetworkIntelUi
{
	private NetworkIntelUi()
	{
	}

	static void render(JPanel panel, NetworkIntelResponse intel, FlipOpportunity opp)
	{
		panel.removeAll();
		if (intel == null)
		{
			PluginUi.finalizeStatBlock(panel);
			return;
		}

		NetworkIntelResponse.NetworkSignalSummary signal = intel.getSignal();
		if (signal != null && signal.getCrowdWarning() != null && !signal.getCrowdWarning().isBlank())
		{
			JLabel warn = PluginUi.caption(signal.getCrowdWarning());
			warn.setForeground(PluginUi.NEGATIVE);
			panel.add(warn);
			panel.add(PluginUi.gap(PluginUi.SPACING_XS));
		}

		if (intel.getEdgeScore() != null)
		{
			PluginUi.addStatLine(panel, "Edge score", String.format("%.0f", intel.getEdgeScore()));
		}

		if (intel.getDivergence() != null && intel.getDivergence().getHeadline() != null)
		{
			panel.add(PluginUi.caption(intel.getDivergence().getHeadline()));
			panel.add(PluginUi.gap(PluginUi.SPACING_XS));
		}

		if (intel.getCoopFlipBand() != null && intel.getCoopFlipBand().getHeadline() != null)
		{
			panel.add(PluginUi.caption(intel.getCoopFlipBand().getHeadline()));
			panel.add(PluginUi.gap(PluginUi.SPACING_XS));
		}

		NetworkIntelResponse.NetworkPriceHints prices = intel.getNetworkPrices();
		if (prices != null)
		{
			if (prices.getMedianBuy() != null)
			{
				PluginUi.addStatLine(
					panel,
					"Network buy (fills)",
					MarketFormat.gp(prices.getMedianBuy()) + sampleSuffix(prices.getBuySamples())
				);
			}
			if (prices.getMedianSell() != null)
			{
				PluginUi.addStatLine(
					panel,
					"Network sell (fills)",
					MarketFormat.gp(prices.getMedianSell()) + sampleSuffix(prices.getSellSamples())
				);
			}
		}

		if (signal != null)
		{
			if (signal.getBuyFillProb5m() != null)
			{
				PluginUi.addStatLine(
					panel,
					"Buy fill (5m)",
					String.format("%.0f%%", signal.getBuyFillProb5m() * 100)
				);
			}
			if (signal.getSellFillProb15m() != null)
			{
				PluginUi.addStatLine(
					panel,
					"Sell fill (15m)",
					String.format("%.0f%%", signal.getSellFillProb15m() * 100)
				);
			}
			if (signal.getSmartMoneyDirection() != null)
			{
				PluginUi.addStatLine(panel, "Smart money", capitalize(signal.getSmartMoneyDirection()));
			}
			if (signal.getEdgeHalfLifeMinutes() != null)
			{
				PluginUi.addStatLine(panel, "Edge decay", signal.getEdgeHalfLifeMinutes() + "m");
			}
			if (signal.getSaturationScore() != null)
			{
				PluginUi.addStatLine(panel, "Saturation", String.valueOf(signal.getSaturationScore()));
			}
		}

		PluginUi.finalizeStatBlock(panel);
	}

	static String coopOverlayLine(NetworkIntelResponse intel, FlipOpportunity opp)
	{
		if (intel == null || intel.getCoopFlipBand() == null || opp == null)
		{
			return null;
		}
		String status = intel.getCoopFlipBand().getStatus();
		if (status == null || "aligned".equals(status) || "insufficient_data".equals(status))
		{
			return null;
		}
		return intel.getCoopFlipBand().getHeadline();
	}

	static String networkUpdatedLine(NetworkIntelResponse intel)
	{
		if (intel == null || intel.getSignal() == null)
		{
			return null;
		}
		String computedAt = intel.getSignal().getComputedAt();
		if (computedAt == null || computedAt.isBlank())
		{
			return null;
		}
		return "Network · updated " + computedAt.replace('T', ' ').replace("Z", " UTC");
	}

	private static String sampleSuffix(Integer samples)
	{
		if (samples == null || samples <= 0)
		{
			return "";
		}
		return " · " + samples + " fills";
	}

	private static String capitalize(String value)
	{
		if (value == null || value.isEmpty())
		{
			return value;
		}
		return Character.toUpperCase(value.charAt(0)) + value.substring(1);
	}
}
