package com.osrsflipfinder.runelite;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/** One GE slot row in the flip manager - price alerts, fill progress, stagnation. */
class GeSlotRow extends JPanel
{
	GeSlotRow(
		int slot,
		GrandExchangeOfferState state,
		int itemId,
		String itemName,
		boolean isBuy,
		long limitPrice,
		long unitFillPrice,
		int filled,
		int totalQty,
		long inactiveSeconds,
		boolean stagnant,
		OfferPriceAnalyzer.Analysis analysis,
		ItemManager itemManager
	)
	{
		setLayout(new BorderLayout(PluginUi.SPACING_SM, 0));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(borderColor(analysis, stagnant), 1, true),
			BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 4, 0, 0, accentColor(analysis, stagnant, isBuy)),
				BorderFactory.createEmptyBorder(PluginUi.SPACING_SM, PluginUi.SPACING_SM, PluginUi.SPACING_SM, PluginUi.SPACING_SM)
			)
		));
		setAlignmentX(LEFT_ALIGNMENT);

		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(32, 28));
		itemManager.getImage(itemId).addTo(icon);

		JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setOpaque(false);

		int wrap = PluginUi.rowTextWrapWidth();

		JLabel title = new JLabel(String.format(
			"Slot %d | %s | %s",
			slot + 1,
			sideLabel(state, isBuy),
			truncate(itemName, 18)
		));
		title.setForeground(Color.WHITE);
		title.setFont(FontManager.getRunescapeSmallFont());
		title.setToolTipText(itemName);
		PluginUi.stackLine(body, title);

		JLabel priceLine = new JLabel("<html><div style='width:" + wrap + "px;'>"
			+ formatPriceLine(limitPrice, unitFillPrice, filled, totalQty)
			+ "</div></html>");
		priceLine.setFont(FontManager.getRunescapeSmallFont());
		PluginUi.stackLine(body, priceLine);

		if (analysis != null)
		{
			addActionBlock(body, analysis, state, wrap);
			body.setToolTipText(buildTooltip(analysis, stagnant, inactiveSeconds));
		}
		else if (stagnant)
		{
			JLabel stall = PluginUi.wrappedBody(
				"Stagnant | " + formatInactive(inactiveSeconds) + " idle",
				wrap,
				PluginUi.WARNING,
				false
			);
			body.add(stall);
		}

		add(icon, BorderLayout.WEST);
		add(body, BorderLayout.CENTER);

		SidebarContentPanel.lockWidth(this);
	}

	/**
	 * Progressive disclosure: issue + primary action + one muted secondary line.
	 * Extra guidance (exact re-list price, projected net) stays in the tooltip.
	 */
	private static void addActionBlock(
		JPanel body,
		OfferPriceAnalyzer.Analysis analysis,
		GrandExchangeOfferState state,
		int wrap
	)
	{
		if (state == GrandExchangeOfferState.BOUGHT || state == GrandExchangeOfferState.SOLD)
		{
			PluginUi.stackLine(body, PluginUi.wrappedBody("Collect in GE to free slot", wrap, PluginUi.GOLD, true));
			return;
		}

		if (analysis.issue != null)
		{
			String issueText = analysis.issue.label() + formatDeltaSuffix(analysis.deltaPercent);
			PluginUi.stackLine(body, PluginUi.wrappedBody(issueText, wrap, issueColor(analysis.issue), false));
		}

		if (analysis.actionLine != null && !analysis.actionLine.isBlank())
		{
			PluginUi.stackLine(body, PluginUi.wrappedBody(
				analysis.actionLine,
				wrap,
				actionColor(analysis.action),
				true
			));
		}

		String secondary = secondaryLine(analysis);
		if (secondary != null)
		{
			PluginUi.stackLine(body, PluginUi.wrappedBody(
				secondary,
				wrap,
				ColorScheme.LIGHT_GRAY_COLOR,
				false
			));
		}
	}

	private static String secondaryLine(OfferPriceAnalyzer.Analysis analysis)
	{
		if (analysis.recommendedPrice > 0
			&& (analysis.action == OfferPriceAnalyzer.Action.REPRICE_BUY
			|| analysis.action == OfferPriceAnalyzer.Action.REPRICE_SELL))
		{
			return "Enter " + MarketFormat.gp(analysis.recommendedPrice) + " gp on re-list";
		}
		if (analysis.action == OfferPriceAnalyzer.Action.WAIT && analysis.recommendedPrice > 0)
		{
			return "Later: " + MarketFormat.gp(analysis.recommendedPrice) + " gp";
		}
		if (analysis.detailLine != null && !analysis.detailLine.isBlank())
		{
			return analysis.detailLine;
		}
		if (analysis.marketPrice != null && analysis.issue == null)
		{
			return "Est. " + MarketFormat.gp(analysis.marketPrice) + " gp" + formatDelta(analysis.deltaPercent);
		}
		return null;
	}

	private static String buildTooltip(
		OfferPriceAnalyzer.Analysis analysis,
		boolean stagnant,
		long inactiveSeconds
	)
	{
		StringBuilder tip = new StringBuilder("<html>");
		if (analysis.detailLine != null && !analysis.detailLine.isBlank())
		{
			tip.append(analysis.detailLine);
		}
		if (analysis.recommendedPrice > 0)
		{
			if (tip.length() > 6)
			{
				tip.append("<br>");
			}
			tip.append("Target ").append(MarketFormat.gp(analysis.recommendedPrice)).append(" gp");
		}
		if (analysis.projectedNetPerItem > Long.MIN_VALUE / 4
			&& analysis.action != OfferPriceAnalyzer.Action.HOLD
			&& analysis.action != OfferPriceAnalyzer.Action.COLLECT)
		{
			if (tip.length() > 6)
			{
				tip.append("<br>");
			}
			tip.append("Est. net ").append(MarketFormat.signedGp(analysis.projectedNetPerItem)).append(" / item");
		}
		if (stagnant)
		{
			if (tip.length() > 6)
			{
				tip.append("<br>");
			}
			tip.append("Idle ").append(formatInactive(inactiveSeconds));
		}
		tip.append("</html>");
		return tip.length() > 13 ? tip.toString() : null;
	}

	private static Color actionColor(OfferPriceAnalyzer.Action action)
	{
		if (action == OfferPriceAnalyzer.Action.ABORT_FLIP)
		{
			return PluginUi.NEGATIVE;
		}
		if (action == OfferPriceAnalyzer.Action.WAIT)
		{
			return PluginUi.WARNING;
		}
		if (action == OfferPriceAnalyzer.Action.REPRICE_BUY || action == OfferPriceAnalyzer.Action.REPRICE_SELL)
		{
			return PluginUi.GOLD;
		}
		return Color.WHITE;
	}

	private static Color borderColor(OfferPriceAnalyzer.Analysis analysis, boolean stagnant)
	{
		if (analysis != null && analysis.action == OfferPriceAnalyzer.Action.ABORT_FLIP)
		{
			return PluginUi.NEGATIVE.darker();
		}
		if (analysis != null && analysis.action == OfferPriceAnalyzer.Action.WAIT && analysis.issue != null)
		{
			return PluginUi.WARNING.darker();
		}
		if (analysis != null && analysis.issue != null)
		{
			return issueColor(analysis.issue).darker();
		}
		if (stagnant)
		{
			return PluginUi.WARNING.darker();
		}
		return ColorScheme.MEDIUM_GRAY_COLOR;
	}

	private static Color accentColor(OfferPriceAnalyzer.Analysis analysis, boolean stagnant, boolean isBuy)
	{
		if (analysis != null && analysis.action == OfferPriceAnalyzer.Action.ABORT_FLIP)
		{
			return PluginUi.NEGATIVE;
		}
		if (analysis != null && analysis.action == OfferPriceAnalyzer.Action.WAIT && analysis.issue != null)
		{
			return PluginUi.WARNING;
		}
		if (analysis != null && analysis.issue != null)
		{
			return issueColor(analysis.issue);
		}
		if (stagnant)
		{
			return PluginUi.WARNING;
		}
		return isBuy ? ColorScheme.GRAND_EXCHANGE_PRICE : ColorScheme.GRAND_EXCHANGE_ALCH;
	}

	private static Color issueColor(OfferPriceAnalyzer.Issue issue)
	{
		switch (issue)
		{
			case BUY_OVERBID:
			case SELL_OVERCUT:
				return PluginUi.NEGATIVE;
			case BUY_UNDERBID:
			case SELL_UNDERCUT:
			default:
				return PluginUi.WARNING;
		}
	}

	private static String formatPriceLine(long limitPrice, long unitFillPrice, int filled, int totalQty)
	{
		String fillPart = PluginUi.htmlSpan(PluginUi.TEXT_DIM, filled + "/" + totalQty + " filled");
		if (filled > 0 && unitFillPrice > 0 && unitFillPrice != limitPrice)
		{
			return PluginUi.htmlSpan(PluginUi.TEXT_SOFT, "Limit ")
				+ PluginUi.htmlSpan(Color.WHITE, MarketFormat.gp(limitPrice) + " gp")
				+ PluginUi.htmlSep()
				+ PluginUi.htmlSpan(PluginUi.TEXT_SOFT, "Fill avg ")
				+ PluginUi.htmlSpan(Color.WHITE, MarketFormat.gp(unitFillPrice) + " gp")
				+ PluginUi.htmlSep()
				+ fillPart;
		}
		return PluginUi.htmlSpan(PluginUi.TEXT_SOFT, "Price ")
			+ PluginUi.htmlSpan(Color.WHITE, MarketFormat.gp(limitPrice) + " gp")
			+ PluginUi.htmlSep()
			+ fillPart;
	}

	private static String sideLabel(GrandExchangeOfferState state, boolean isBuy)
	{
		if (state == GrandExchangeOfferState.BOUGHT)
		{
			return "BUY DONE";
		}
		if (state == GrandExchangeOfferState.SOLD)
		{
			return "SELL DONE";
		}
		return isBuy ? "BUY" : "SELL";
	}

	private static String formatDelta(Double delta)
	{
		if (delta == null)
		{
			return "";
		}
		return String.format(" (%+.1f%%)", delta);
	}

	private static String formatDeltaSuffix(Double delta)
	{
		if (delta == null)
		{
			return "";
		}
		return " | " + String.format("%.1f", Math.abs(delta)) + "% vs market";
	}

	private static String formatInactive(long seconds)
	{
		if (seconds < 60)
		{
			return seconds + "s";
		}
		long minutes = seconds / 60;
		if (minutes < 60)
		{
			return minutes + "m";
		}
		return (minutes / 60) + "h " + (minutes % 60) + "m";
	}

	private static String truncate(String text, int max)
	{
		if (text == null)
		{
			return "";
		}
		if (text.length() <= max)
		{
			return text;
		}
		int keep = Math.max(0, max - 3);
		return text.substring(0, keep) + "...";
	}
}
