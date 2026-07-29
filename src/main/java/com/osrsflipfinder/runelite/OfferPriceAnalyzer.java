package com.osrsflipfinder.runelite;

/** Client-side offer vs market checks and data-driven GE actions. */
final class OfferPriceAnalyzer
{
	static final double DEFAULT_THRESHOLD_PERCENT = 1.0;

	enum Issue
	{
		BUY_OVERBID("buy_overbid", "Overbid"),
		BUY_UNDERBID("buy_underbid", "Underbid"),
		SELL_UNDERCUT("sell_undercut", "Undercut"),
		SELL_OVERCUT("sell_overcut", "Overcut");

		private final String apiKey;
		private final String label;

		Issue(String apiKey, String label)
		{
			this.apiKey = apiKey;
			this.label = label;
		}

		String apiKey()
		{
			return apiKey;
		}

		String label()
		{
			return label;
		}

		static Issue fromApiKey(String key)
		{
			if (key == null)
			{
				return null;
			}
			for (Issue issue : values())
			{
				if (issue.apiKey.equals(key))
				{
					return issue;
				}
			}
			return null;
		}
	}

	enum Action
	{
		HOLD("Hold offer"),
		/** Price is off market but offer is young - wait before cancel/re-list. */
		WAIT("Wait and watch"),
		REPRICE_BUY("Re-buy at"),
		REPRICE_SELL("Re-sell at"),
		/** Stuck a long time with a locked-in loss - consider exiting. */
		ABORT_FLIP("Consider exiting"),
		COLLECT("Collect items");

		private final String verb;

		Action(String verb)
		{
			this.verb = verb;
		}

		String verb()
		{
			return verb;
		}
	}

	static final class Analysis
	{
		final Issue issue;
		final Double deltaPercent;
		final Long marketPrice;
		final Action action;
		/** Exact gp to enter on the next offer after cancel (0 when not applicable). */
		final long recommendedPrice;
		final long projectedNetPerItem;
		final String actionLine;
		final String detailLine;

		Analysis(
			Issue issue,
			Double deltaPercent,
			Long marketPrice,
			Action action,
			long recommendedPrice,
			long projectedNetPerItem,
			String actionLine,
			String detailLine
		)
		{
			this.issue = issue;
			this.deltaPercent = deltaPercent;
			this.marketPrice = marketPrice;
			this.action = action;
			this.recommendedPrice = recommendedPrice;
			this.projectedNetPerItem = projectedNetPerItem;
			this.actionLine = actionLine;
			this.detailLine = detailLine;
		}

		/** @deprecated use {@link #actionLine} */
		String actionHint()
		{
			return actionLine;
		}

		static Analysis none(Long marketPrice, Double delta, FlipOpportunity opp, boolean stagnant, long inactiveSec)
		{
			return none(marketPrice, delta, opp, stagnant, inactiveSec, false);
		}

		static Analysis none(
			Long marketPrice,
			Double delta,
			FlipOpportunity opp,
			boolean stagnant,
			long inactiveSec,
			boolean buySide
		)
		{
			long net = opp != null ? opp.getNetProfitPerItem() : 0;
			long est = marketPrice != null && marketPrice > 0 ? marketPrice : 0;
			long recommended = est;
			if (opp != null && est > 0)
			{
				if (buySide)
				{
					recommended = GeOfferPriceStrategy.suggestedBuyOfferGp(opp);
				}
				else
				{
					recommended = GeOfferPriceStrategy.suggestedSellOfferGp(opp);
				}
			}
			if (opp != null && opp.isPriceDumped())
			{
				return new Analysis(
					null,
					delta,
					marketPrice,
					Action.WAIT,
					recommended,
					net,
					"Pause - unstable price",
					"Dump flag | wait for norms to settle before re-listing"
				);
			}
			String detail;
			if (marketPrice == null || marketPrice <= 0)
			{
				detail = "Waiting for FlipX prices on this item";
			}
			else if (stagnant)
			{
				detail = "Idle " + formatIdleMinutes(inactiveSec)
					+ " | still at est. " + MarketFormat.gp(marketPrice) + " gp";
			}
			else
			{
				detail = "At est. " + MarketFormat.gp(marketPrice) + " gp";
			}
			return new Analysis(
				null,
				delta,
				marketPrice,
				Action.HOLD,
				recommended,
				net,
				"Hold at FlipX estimate",
				detail
			);
		}
	}

	private OfferPriceAnalyzer()
	{
	}

	static Analysis analyze(
		boolean isBuy,
		long offerPrice,
		FlipOpportunity opp,
		double thresholdPercent
	)
	{
		return analyze(isBuy, offerPrice, opp, thresholdPercent, false, 0, 0, 0, 0);
	}

	static Analysis analyze(
		boolean isBuy,
		long offerPrice,
		FlipOpportunity opp,
		double thresholdPercent,
		boolean stagnant,
		long inactiveSec,
		int quantityFilled,
		int totalQuantity,
		long stagnationThresholdSec
	)
	{
		return analyze(
			isBuy,
			offerPrice,
			opp,
			thresholdPercent,
			stagnant,
			inactiveSec,
			quantityFilled,
			totalQuantity,
			stagnationThresholdSec,
			0
		);
	}

	static Analysis analyze(
		boolean isBuy,
		long offerPrice,
		FlipOpportunity opp,
		double thresholdPercent,
		boolean stagnant,
		long inactiveSec,
		int quantityFilled,
		int totalQuantity,
		long stagnationThresholdSec,
		int unitFillPrice
	)
	{
		if (opp == null || offerPrice <= 0)
		{
			return Analysis.none(null, null, null, stagnant, inactiveSec);
		}

		int itemId = opp.getId();
		if (opp.isPriceDumped())
		{
			long est = isBuy ? opp.getEstimatedBuyPrice() : opp.getEstimatedSellPrice();
			return new Analysis(
				null,
				null,
				est,
				Action.WAIT,
				est,
				opp.getNetProfitPerItem(),
				"Pause - unstable price",
				"Dump flag | wait before canceling; refresh prices before next list"
			);
		}

		long marketBuy = opp.getEstimatedBuyPrice();
		long marketSell = opp.getEstimatedSellPrice();
		long netAtMarket = opp.getNetProfitPerItem();

		if (isBuy)
		{
			if (marketBuy <= 0)
			{
				return Analysis.none(null, null, opp, stagnant, inactiveSec);
			}
			double delta = ((offerPrice - marketBuy) * 100.0) / marketBuy;
			long buyForNet = GeOfferPricing.effectiveBuyForNet(
				offerPrice,
				marketBuy,
				quantityFilled,
				unitFillPrice
			);
			long netAtOffer = estimateNetPerItem(buyForNet, marketSell, itemId);
			long maxAggressiveBuy = GeOfferPriceStrategy.maxAggressiveBuyGp(marketBuy, marketSell, itemId);
			long suggestedBuy = GeOfferPriceStrategy.suggestedBuyOfferGp(marketBuy, marketSell, itemId);

			if (offerPrice > maxAggressiveBuy)
			{
				Issue issue = Issue.BUY_OVERBID;
				if (shouldConsiderExit(inactiveSec, stagnationThresholdSec, netAtOffer, quantityFilled))
				{
					return exitFlip(
						issue,
						delta,
						marketBuy,
						marketSell,
						offerPrice,
						netAtOffer,
						true,
						suggestedBuy
					);
				}
				if (!stagnant)
				{
					return waitOnIssue(
						issue,
						delta,
						suggestedBuy,
						offerPrice,
						netAtMarket,
						netAtOffer,
						stagnationThresholdSec,
						true,
						quantityFilled,
						totalQuantity
					);
				}
				return repriceBuy(
					issue,
					delta,
					marketBuy,
					marketSell,
					offerPrice,
					netAtMarket,
					suggestedBuy,
					itemId,
					quantityFilled,
					totalQuantity
				);
			}
			if (delta < -thresholdPercent)
			{
				Issue issue = Issue.BUY_UNDERBID;
				if (!stagnant)
				{
					return waitOnIssue(
						issue,
						delta,
						suggestedBuy,
						offerPrice,
						netAtMarket,
						netAtOffer,
						stagnationThresholdSec,
						true,
						quantityFilled,
						totalQuantity
					);
				}
				return repriceBuy(
					issue,
					delta,
					marketBuy,
					marketSell,
					offerPrice,
					netAtMarket,
					suggestedBuy,
					itemId,
					quantityFilled,
					totalQuantity
				);
			}
			return Analysis.none(marketBuy, delta, opp, stagnant, inactiveSec, true);
		}

		if (marketSell <= 0)
		{
			return Analysis.none(null, null, opp, stagnant, inactiveSec);
		}
		double delta = ((offerPrice - marketSell) * 100.0) / marketSell;
		long sellForNet = GeOfferPricing.effectiveSellForNet(
			offerPrice,
			marketSell,
			quantityFilled,
			unitFillPrice
		);
		long netAtOffer = estimateNetPerItem(marketBuy, sellForNet, itemId);
		long minAggressiveSell = GeOfferPriceStrategy.minAggressiveSellGp(marketBuy, marketSell, itemId);
		long suggestedSell = GeOfferPriceStrategy.suggestedSellOfferGp(marketBuy, marketSell, itemId);

		if (offerPrice < minAggressiveSell)
		{
			Issue issue = Issue.SELL_UNDERCUT;
			if (shouldConsiderExit(inactiveSec, stagnationThresholdSec, netAtOffer, quantityFilled))
			{
				return exitFlip(
					issue,
					delta,
					marketBuy,
					marketSell,
					offerPrice,
					netAtOffer,
					false,
					suggestedSell
				);
			}
			if (!stagnant)
			{
				return waitOnIssue(
					issue,
					delta,
					suggestedSell,
					offerPrice,
					netAtMarket,
					netAtOffer,
					stagnationThresholdSec,
					false,
					quantityFilled,
					totalQuantity
				);
			}
			return repriceSell(
				issue,
				delta,
				marketBuy,
				marketSell,
				offerPrice,
				netAtMarket,
				suggestedSell,
				itemId,
				quantityFilled,
				totalQuantity
			);
		}
		if (delta > thresholdPercent)
		{
			long netAtSuggested = estimateNetPerItem(marketBuy, suggestedSell, itemId);
			if (netAtOffer > netAtSuggested)
			{
				return profitableOvercutHold(
					delta,
					marketSell,
					offerPrice,
					netAtOffer,
					netAtSuggested,
					stagnant,
					inactiveSec
				);
			}
			Issue issue = Issue.SELL_OVERCUT;
			if (shouldConsiderExit(inactiveSec, stagnationThresholdSec, netAtOffer, quantityFilled))
			{
				return exitFlip(
					issue,
					delta,
					marketBuy,
					marketSell,
					offerPrice,
					netAtOffer,
					false,
					suggestedSell
				);
			}
			if (!stagnant)
			{
				return waitOnIssue(
					issue,
					delta,
					suggestedSell,
					offerPrice,
					netAtMarket,
					netAtOffer,
					stagnationThresholdSec,
					false,
					quantityFilled,
					totalQuantity
				);
			}
			return repriceSell(
				issue,
				delta,
				marketBuy,
				marketSell,
				offerPrice,
				netAtMarket,
				suggestedSell,
				itemId,
				quantityFilled,
				totalQuantity
			);
		}
		return Analysis.none(marketSell, delta, opp, stagnant, inactiveSec, false);
	}

	private static Analysis profitableOvercutHold(
		double delta,
		long marketSell,
		long offerPrice,
		long netAtOffer,
		long netAtSuggested,
		boolean stagnant,
		long inactiveSec
	)
	{
		String detail = "Above FlipX sell est. | est. net "
			+ MarketFormat.signedGp(netAtOffer)
			+ "/item at your "
			+ MarketFormat.gp(offerPrice)
			+ " gp vs "
			+ MarketFormat.signedGp(netAtSuggested)
			+ "/item at est. sell — wait for fill";
		if (stagnant)
		{
			detail += " | idle " + formatIdleMinutes(inactiveSec);
		}
		return new Analysis(
			null,
			delta,
			marketSell,
			Action.HOLD,
			offerPrice,
			netAtOffer,
			"Hold - premium sell price",
			detail
		);
	}

	private static Analysis waitOnIssue(
		Issue issue,
		double delta,
		long repriceTargetGp,
		long offerPrice,
		long netAtMarket,
		long netAtOffer,
		long stagnationThresholdSec,
		boolean isBuy,
		int quantityFilled,
		int totalQuantity
	)
	{
		String waitMin = formatStagnationMinutes(stagnationThresholdSec);
		String side = isBuy ? "buy" : "sell";
		String detail = "No need to cancel yet | if still stuck after ~" + waitMin
			+ ", re-" + side + " at " + MarketFormat.gp(repriceTargetGp) + " gp"
			+ partialFillNote(quantityFilled, totalQuantity);
		if (netAtOffer > Long.MIN_VALUE / 4)
		{
			detail += " | at your price est. net " + MarketFormat.signedGp(netAtOffer) + "/item";
		}
		detail += " | at FlipX target est. net " + MarketFormat.signedGp(netAtMarket) + "/item";
		return new Analysis(
			issue,
			delta,
			repriceTargetGp,
			Action.WAIT,
			repriceTargetGp,
			netAtMarket,
			"Wait - " + issue.label().toLowerCase(),
			detail
		);
	}

	private static Analysis exitFlip(
		Issue issue,
		double delta,
		long marketBuy,
		long marketSell,
		long offerPrice,
		long netAtOffer,
		boolean isBuy,
		long repriceTargetGp
	)
	{
		String detail = "Idle a long time with est. net "
			+ MarketFormat.signedGp(netAtOffer)
			+ "/item at your "
			+ MarketFormat.gp(offerPrice)
			+ " gp | cancel only if you accept the loss or want to re-"
			+ (isBuy ? "buy" : "sell")
			+ " at "
			+ MarketFormat.gp(repriceTargetGp)
			+ " gp";
		return new Analysis(
			issue,
			delta,
			isBuy ? marketBuy : marketSell,
			Action.ABORT_FLIP,
			repriceTargetGp,
			netAtOffer,
			"Consider exiting - locked loss",
			detail
		);
	}

	private static boolean shouldConsiderExit(
		long inactiveSec,
		long stagnationThresholdSec,
		long netAtOffer,
		int quantityFilled
	)
	{
		if (netAtOffer > 0)
		{
			return false;
		}
		long minIdle = Math.max(stagnationThresholdSec * 2L, 600L);
		if (inactiveSec < minIdle)
		{
			return false;
		}
		return quantityFilled <= 0 || netAtOffer <= 0;
	}

	private static String formatStagnationMinutes(long stagnationSec)
	{
		long minutes = Math.max(1, stagnationSec / 60);
		return minutes + "m";
	}

	private static Analysis repriceBuy(
		Issue issue,
		double delta,
		long marketBuy,
		long marketSell,
		long offerPrice,
		long netAtMarket,
		long suggestedBuy,
		int itemId,
		int quantityFilled,
		int totalQuantity
	)
	{
		long netAtSuggested = estimateNetPerItem(suggestedBuy, marketSell, itemId);
		long gpDelta = Math.abs(offerPrice - suggestedBuy);
		String partial = partialFillNote(quantityFilled, totalQuantity);
		String actionLine = "Set buy to " + MarketFormat.gp(suggestedBuy) + " gp";
		String detail = "Cancel offer, then buy at " + MarketFormat.gp(suggestedBuy) + " gp"
			+ (issue == Issue.BUY_OVERBID
				? " (save " + MarketFormat.gp(gpDelta) + " gp vs now)"
				: " (+" + MarketFormat.gp(gpDelta) + " gp vs now)")
			+ partial
			+ " | est. net " + MarketFormat.signedGp(netAtSuggested) + "/item at sell "
			+ MarketFormat.gp(marketSell);
		return new Analysis(
			issue,
			delta,
			marketBuy,
			Action.REPRICE_BUY,
			suggestedBuy,
			netAtSuggested,
			actionLine,
			detail
		);
	}

	private static Analysis repriceSell(
		Issue issue,
		double delta,
		long marketBuy,
		long marketSell,
		long offerPrice,
		long netAtMarket,
		long suggestedSell,
		int itemId,
		int quantityFilled,
		int totalQuantity
	)
	{
		long netAtSuggested = estimateNetPerItem(marketBuy, suggestedSell, itemId);
		long gpDelta = Math.abs(offerPrice - suggestedSell);
		String partial = partialFillNote(quantityFilled, totalQuantity);
		String actionLine = "Set sell to " + MarketFormat.gp(suggestedSell) + " gp";
		String detail = "Cancel offer, then sell at " + MarketFormat.gp(suggestedSell) + " gp"
			+ (issue == Issue.SELL_UNDERCUT
				? " (+" + MarketFormat.gp(gpDelta) + " gp vs now)"
				: " (-" + MarketFormat.gp(gpDelta) + " gp vs now)")
			+ partial
			+ " | est. net " + MarketFormat.signedGp(netAtSuggested) + "/item";
		return new Analysis(
			issue,
			delta,
			marketSell,
			Action.REPRICE_SELL,
			suggestedSell,
			netAtSuggested,
			actionLine,
			detail
		);
	}

	static Analysis mergePreferLocal(Analysis local, EnrichedOpenGeOffer enriched)
	{
		if (local != null && local.issue != null)
		{
			return local;
		}
		if (enriched == null || enriched.getPriceIssue() == null)
		{
			return local != null ? local : Analysis.none(null, null, null, false, 0);
		}
		Issue issue = Issue.fromApiKey(enriched.getPriceIssue());
		if (issue == null)
		{
			return local != null ? local : Analysis.none(null, null, null, false, 0);
		}
		long marketBuy = enriched.getMarketBuyPrice() != null ? enriched.getMarketBuyPrice() : 0;
		long marketSell = enriched.getMarketSellPrice() != null ? enriched.getMarketSellPrice() : 0;
		boolean isBuy = issue == Issue.BUY_OVERBID || issue == Issue.BUY_UNDERBID;
		long recommended = isBuy ? marketBuy : marketSell;
		Action action = isBuy ? Action.REPRICE_BUY : Action.REPRICE_SELL;
		String actionLine = isBuy
			? "Set buy to " + MarketFormat.gp(recommended) + " gp"
			: "Set sell to " + MarketFormat.gp(recommended) + " gp";
		return new Analysis(
			issue,
			enriched.getDeltaPercent(),
			recommended,
			action,
			recommended,
			0,
			actionLine,
			"Synced portfolio | cancel and re-place at " + MarketFormat.gp(recommended) + " gp"
		);
	}

	static long estimateNetPerItem(long buyPrice, long sellPrice)
	{
		return estimateNetPerItem(buyPrice, sellPrice, 0);
	}

	static long estimateNetPerItem(long buyPrice, long sellPrice, int itemId)
	{
		if (buyPrice <= 0 || sellPrice <= 0 || sellPrice <= buyPrice)
		{
			return Long.MIN_VALUE / 2;
		}
		return sellPrice - buyPrice - GeTax.geTaxPerItem(sellPrice, itemId);
	}

	private static String partialFillNote(int filled, int total)
	{
		if (total <= 0 || filled <= 0 || filled >= total)
		{
			return "";
		}
		return " | " + filled + "/" + total + " filled on current offer";
	}

	private static String formatIdle(long seconds)
	{
		return formatIdleMinutes(seconds);
	}

	/** Whole minutes only - avoids hold-line flicker every second. */
	static String formatIdleMinutes(long seconds)
	{
		if (seconds < 60)
		{
			return "under 1m";
		}
		long minutes = seconds / 60;
		if (minutes < 60)
		{
			return minutes + "m";
		}
		return (minutes / 60) + "h " + (minutes % 60) + "m";
	}
}
