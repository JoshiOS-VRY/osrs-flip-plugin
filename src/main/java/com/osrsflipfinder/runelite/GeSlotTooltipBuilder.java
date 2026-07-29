package com.osrsflipfinder.runelite;

import net.runelite.api.GrandExchangeOfferState;

/** HTML tooltips for GE slot flip context (sidebar rows + in-game hover). */
final class GeSlotTooltipBuilder
{
	private GeSlotTooltipBuilder()
	{
	}

	/** Extra lines for the Flip manager sidebar row (title has item name). */
	static String buildSidebarTooltip(
		OfferPriceAnalyzer.Analysis analysis,
		boolean stagnant,
		long inactiveSeconds
	)
	{
		StringBuilder tip = new StringBuilder("<html>");
		appendAnalysisLines(tip, analysis, stagnant, inactiveSeconds);
		tip.append("</html>");
		return tip.length() > 13 ? tip.toString() : null;
	}

	static String buildGeSlotHoverTooltip(GeSlotTooltipContext ctx)
	{
		StringBuilder tip = new StringBuilder("<html><b>FlipX</b>");
		if (ctx.itemName != null && !ctx.itemName.isEmpty())
		{
			tip.append(" · ").append(escape(ctx.itemName));
		}
		tip.append("<br>");

		tip.append(escape(sideLabel(ctx.state, ctx.isBuy)));
		tip.append(" · ").append(ctx.filled).append("/").append(ctx.totalQty).append(" filled");
		tip.append("<br>");

		if (ctx.filled > 0 && ctx.unitFillPrice > 0 && ctx.unitFillPrice != ctx.limitPrice)
		{
			tip.append("Limit ")
				.append(MarketFormat.gp(ctx.limitPrice))
				.append(" gp · fill avg ")
				.append(MarketFormat.gp(ctx.unitFillPrice))
				.append(" gp<br>");
		}
		else
		{
			tip.append("Price ").append(MarketFormat.gp(ctx.limitPrice)).append(" gp<br>");
		}

		if (ctx.state == GrandExchangeOfferState.BOUGHT || ctx.state == GrandExchangeOfferState.SOLD)
		{
			tip.append("Collect in GE to free slot<br>");
		}

		FlipOpportunity opp = ctx.opportunity;
		if (opp != null)
		{
			tip.append(FlipCopilotPresenter.scoreLine(opp)).append("<br>");
			tip.append("Est. net at FlipX prices ")
				.append(MarketFormat.signedGp(opp.getNetProfitPerItem()))
				.append("/item · ROI ")
				.append(MarketFormat.percent(opp.getNetRoiPercent()))
				.append("<br>");
			tip.append("Est. buy ")
				.append(MarketFormat.gp(opp.getEstimatedBuyPrice()))
				.append(" · sell ")
				.append(MarketFormat.gp(opp.getEstimatedSellPrice()))
				.append(" gp<br>");

			long gpHr = FlipCopilotPresenter.estimatedGpPerHour(opp);
			if (gpHr > 0)
			{
				tip.append("GP/hr ~").append(MarketFormat.gp(gpHr)).append(" gp<br>");
			}

			if (ctx.breakEvenSell > 0)
			{
				tip.append("Break-even sell ").append(MarketFormat.gp(ctx.breakEvenSell)).append(" gp<br>");
			}
		}
		else
		{
			tip.append("<i>Pair FlipX & open Market for scores and estimates</i><br>");
		}

		appendAnalysisLines(tip, ctx.analysis, ctx.stagnant, ctx.inactiveSeconds);
		tip.append("</html>");
		return tip.toString();
	}

	/**
	 * Converts Swing HTML tooltips for {@link net.runelite.client.ui.overlay.tooltip.TooltipManager}.
	 * RuneLite only understands {@code <br>}, {@code <col=RRGGBB>}, and {@code <img=N>}, not {@code <html>}.
	 */
	static String formatForRuneliteOverlay(String swingHtmlTooltip)
	{
		if (swingHtmlTooltip == null || swingHtmlTooltip.isBlank())
		{
			return swingHtmlTooltip;
		}
		String text = swingHtmlTooltip;
		text = text.replace("<html>", "").replace("</html>", "");
		text = text.replace("<b>", "").replace("</b>", "");
		text = text.replace("<i>", "").replace("</i>", "");
		text = text.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">");
		return text.trim();
	}

	private static void appendAnalysisLines(
		StringBuilder tip,
		OfferPriceAnalyzer.Analysis analysis,
		boolean stagnant,
		long inactiveSeconds
	)
	{
		if (analysis == null)
		{
			if (stagnant)
			{
				tip.append("Idle ").append(formatInactive(inactiveSeconds));
			}
			return;
		}

		if (analysis.issue != null)
		{
			tip.append(escape(analysis.issue.label()));
			if (analysis.deltaPercent != null)
			{
				tip.append(" · ")
					.append(String.format("%.1f", Math.abs(analysis.deltaPercent)))
					.append("% vs market");
			}
			tip.append("<br>");
		}

		if (analysis.actionLine != null && !analysis.actionLine.isBlank())
		{
			tip.append(escape(analysis.actionLine)).append("<br>");
		}

		if (analysis.detailLine != null && !analysis.detailLine.isBlank())
		{
			tip.append(escape(analysis.detailLine)).append("<br>");
		}

		if (analysis.recommendedPrice > 0)
		{
			tip.append("Target ").append(MarketFormat.gp(analysis.recommendedPrice)).append(" gp<br>");
		}

		if (analysis.projectedNetPerItem > Long.MIN_VALUE / 4
			&& analysis.action != OfferPriceAnalyzer.Action.COLLECT
			&& (analysis.action != OfferPriceAnalyzer.Action.HOLD || analysis.issue == null))
		{
			tip.append("Est. net ")
				.append(MarketFormat.signedGp(analysis.projectedNetPerItem))
				.append(" / item<br>");
		}

		if (stagnant)
		{
			tip.append("Idle ").append(formatInactive(inactiveSeconds));
		}
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

	private static String escape(String text)
	{
		if (text == null)
		{
			return "";
		}
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	static final class GeSlotTooltipContext
	{
		final String itemName;
		final GrandExchangeOfferState state;
		final boolean isBuy;
		final long limitPrice;
		final long unitFillPrice;
		final int filled;
		final int totalQty;
		final long breakEvenSell;
		final FlipOpportunity opportunity;
		final OfferPriceAnalyzer.Analysis analysis;
		final boolean stagnant;
		final long inactiveSeconds;

		GeSlotTooltipContext(
			String itemName,
			GrandExchangeOfferState state,
			boolean isBuy,
			long limitPrice,
			long unitFillPrice,
			int filled,
			int totalQty,
			long breakEvenSell,
			FlipOpportunity opportunity,
			OfferPriceAnalyzer.Analysis analysis,
			boolean stagnant,
			long inactiveSeconds
		)
		{
			this.itemName = itemName;
			this.state = state;
			this.isBuy = isBuy;
			this.limitPrice = limitPrice;
			this.unitFillPrice = unitFillPrice;
			this.filled = filled;
			this.totalQty = totalQty;
			this.breakEvenSell = breakEvenSell;
			this.opportunity = opportunity;
			this.analysis = analysis;
			this.stagnant = stagnant;
			this.inactiveSeconds = inactiveSeconds;
		}
	}
}
