package com.osrsflipfinder.runelite;

import java.awt.Color;
import net.runelite.client.ui.ColorScheme;

/** Readable verdicts, labels, and hints for GE copilot surfaces. */
final class FlipCopilotPresenter
{
	enum Verdict
	{
		STRONG,
		OK,
		WEAK,
		AVOID
	}

	private FlipCopilotPresenter()
	{
	}

	static Verdict verdict(FlipOpportunity opp)
	{
		if (opp == null)
		{
			return Verdict.AVOID;
		}
		if (opp.getNetProfitPerItem() <= 0)
		{
			return Verdict.AVOID;
		}
		if (opp.isPriceDumped())
		{
			return Verdict.WEAK;
		}
		if (opp.getOpportunityScore() >= 70 && opp.getConfidenceScore() >= 0.55)
		{
			return Verdict.STRONG;
		}
		if (opp.getOpportunityScore() >= 45 && opp.getNetRoiPercent() >= 0.5)
		{
			return Verdict.OK;
		}
		if (opp.getOpportunityScore() < 35 || opp.getNetRoiPercent() < 0.2)
		{
			return Verdict.WEAK;
		}
		return Verdict.OK;
	}

	static String verdictLabel(Verdict verdict)
	{
		switch (verdict)
		{
			case STRONG:
				return "Strong flip";
			case OK:
				return "Decent flip";
			case WEAK:
				return "Proceed with care";
			case AVOID:
			default:
				return "Skip for now";
		}
	}

	/** Verdict text for the small GE overlay (fits one column). */
	static String verdictLabelOverlay(Verdict verdict)
	{
		switch (verdict)
		{
			case STRONG:
				return "Strong";
			case OK:
				return "Decent";
			case WEAK:
				return "Careful";
			case AVOID:
			default:
				return "Skip";
		}
	}

	/**
	 * At most one short alert for the GE overlay — full hints live in the sidebar.
	 * {@code maxLen} includes room for ellipsis when truncated.
	 */
	static String overlayAlertLine(FlipOpportunity opp, int maxLen)
	{
		if (opp == null || maxLen < 4)
		{
			return null;
		}
		String line;
		if (opp.isPriceDumped())
		{
			line = "Dump vs norm";
		}
		else if (opp.getNetProfitPerItem() <= 0)
		{
			line = "Loss after tax";
		}
		else if (opp.getEstimatedSellPrice() <= opp.getEstimatedBuyPrice())
		{
			line = "Sell below buy";
		}
		else
		{
			long spread = opp.getEstimatedSellPrice() - opp.getEstimatedBuyPrice();
			if (opp.getEstimatedBuyPrice() > 0)
			{
				double spreadPct = spread * 100.0 / opp.getEstimatedBuyPrice();
				if (spreadPct < 0.5)
				{
					line = "Tight spread";
				}
				else if (opp.getEstimatedTurnoverHours() > 6)
				{
					line = "Slow fill";
				}
				else
				{
					return null;
				}
			}
			else
			{
				return null;
			}
		}
		if (line.length() <= maxLen)
		{
			return line;
		}
		return line.substring(0, Math.max(0, maxLen - 3)) + "...";
	}

	static Color verdictColor(Verdict verdict)
	{
		switch (verdict)
		{
			case STRONG:
				return PluginUi.POSITIVE;
			case OK:
				return PluginUi.GOLD;
			case WEAK:
				return PluginUi.WARNING;
			case AVOID:
			default:
				return PluginUi.NEGATIVE;
		}
	}

	static String actionSummary(FlipOpportunity opp)
	{
		Verdict v = verdict(opp);
		if (v == Verdict.AVOID)
		{
			return "No net profit after GE tax at these prices.";
		}
		if (opp.isPriceDumped())
		{
			return "Price looks dumped vs recent norms - widen margins or wait.";
		}
		return "";
	}

	static String repriceHint(FlipOpportunity opp)
	{
		if (opp.getEstimatedSellPrice() <= opp.getEstimatedBuyPrice())
		{
			return "Sell target is not above buy - check prices before listing.";
		}
		if (opp.getNetProfitPerItem() <= 0)
		{
			return "Margin is negative after tax - raise sell or lower buy.";
		}
		long spread = opp.getEstimatedSellPrice() - opp.getEstimatedBuyPrice();
		if (opp.getEstimatedBuyPrice() > 0)
		{
			double spreadPct = spread * 100.0 / opp.getEstimatedBuyPrice();
			if (spreadPct < 0.5)
			{
				return "Spread is very tight - small reprices can erase profit.";
			}
		}
		if (opp.getEstimatedTurnoverHours() > 6)
		{
			return "Slow fill expected - try a fresher item if slots are tight.";
		}
		return null;
	}

	static String scoreLine(FlipOpportunity opp)
	{
		int confPct = (int) Math.round(opp.getConfidenceScore() * 100);
		return opp.getOpportunityScore() + "/100 score | " + confPct + "% confidence";
	}

	/** Short score for narrow GE overlay rows. */
	static String scoreLineCompact(FlipOpportunity opp)
	{
		int confPct = (int) Math.round(opp.getConfidenceScore() * 100);
		return opp.getOpportunityScore() + "/100 | " + confPct + "%";
	}

	static String scoreBar(FlipOpportunity opp)
	{
		int filled = Math.max(0, Math.min(10, opp.getOpportunityScore() / 10));
		StringBuilder bar = new StringBuilder();
		for (int i = 0; i < 10; i++)
		{
			bar.append(i < filled ? '|' : '.');
		}
		return bar.toString();
	}

	static String formatVolume(long volume)
	{
		if (volume >= 1_000_000)
		{
			return String.format("%.1fM", volume / 1_000_000.0);
		}
		if (volume >= 1_000)
		{
			return String.format("%.1fK", volume / 1_000.0);
		}
		return Long.toString(volume);
	}

	static String formatTurnover(double hours)
	{
		if (hours <= 0 || !Double.isFinite(hours))
		{
			return PluginUi.PLACEHOLDER;
		}
		if (hours < 1)
		{
			return String.format("%.0fm fill est.", hours * 60);
		}
		if (hours < 10)
		{
			return String.format("%.1fh fill est.", hours);
		}
		return String.format("%.0fh fill est.", hours);
	}

	static long estimatedGpPerHour(FlipOpportunity opp)
	{
		if (opp == null)
		{
			return 0L;
		}
		long hr = Math.round(opp.getEstimatedProfitPerHour());
		if (hr > 0)
		{
			return hr;
		}
		long atLimit = opp.getEstimatedProfitAtQuantity();
		double turnover = opp.getEstimatedTurnoverHours();
		if (atLimit > 0 && turnover > 0 && Double.isFinite(turnover))
		{
			return Math.round(atLimit / turnover);
		}
		return 0L;
	}

	static Color buyPriceColor()
	{
		return ColorScheme.GRAND_EXCHANGE_PRICE;
	}

	static Color sellPriceColor()
	{
		return ColorScheme.GRAND_EXCHANGE_ALCH;
	}
}
