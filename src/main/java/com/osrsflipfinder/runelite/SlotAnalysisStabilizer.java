package com.osrsflipfinder.runelite;

import java.util.HashMap;
import java.util.Map;

/** Reduces flip-manager UI flicker from threshold edge cases and brief null market data. */
final class SlotAnalysisStabilizer
{
	private static final long STAGNANT_ENTER_BUFFER_SEC = 45L;
	private static final long STAGNANT_EXIT_BUFFER_SEC = 20L;
	private static final double ISSUE_CLEAR_FACTOR = 0.5;

	private final Map<Integer, Boolean> stagnantLatched = new HashMap<>();
	private final Map<Integer, OfferPriceAnalyzer.Issue> issueLatched = new HashMap<>();
	private final Map<Integer, OfferPriceAnalyzer.Analysis> alertFallback = new HashMap<>();
	private final Map<Integer, OfferPriceAnalyzer.Analysis> holdFallback = new HashMap<>();

	void clearSlot(int slot)
	{
		stagnantLatched.remove(slot);
		issueLatched.remove(slot);
		alertFallback.remove(slot);
		holdFallback.remove(slot);
	}

	boolean stagnantForUi(int slot, long inactiveSec, long thresholdSec, boolean rawStagnant)
	{
		if (!rawStagnant)
		{
			stagnantLatched.remove(slot);
			return false;
		}
		if (Boolean.TRUE.equals(stagnantLatched.get(slot)))
		{
			if (inactiveSec < Math.max(0, thresholdSec - STAGNANT_EXIT_BUFFER_SEC))
			{
				stagnantLatched.remove(slot);
				return false;
			}
		 return true;
		}
		if (inactiveSec >= thresholdSec + STAGNANT_ENTER_BUFFER_SEC)
		{
			stagnantLatched.put(slot, true);
			return true;
		}
		return false;
	}

	OfferPriceAnalyzer.Analysis stabilize(int slot, OfferPriceAnalyzer.Analysis fresh)
	{
		if (fresh == null)
		{
			return null;
		}

		if (fresh.issue != null)
		{
			issueLatched.put(slot, fresh.issue);
			alertFallback.put(slot, fresh);
			return fresh;
		}

		if (fresh.action == OfferPriceAnalyzer.Action.ABORT_FLIP
			|| fresh.action == OfferPriceAnalyzer.Action.REPRICE_BUY
			|| fresh.action == OfferPriceAnalyzer.Action.REPRICE_SELL)
		{
			issueLatched.remove(slot);
			alertFallback.remove(slot);
			holdFallback.put(slot, fresh);
			return fresh;
		}

		if (fresh.action == OfferPriceAnalyzer.Action.WAIT)
		{
			alertFallback.put(slot, fresh);
			return fresh;
		}

		OfferPriceAnalyzer.Issue latchedIssue = issueLatched.get(slot);
		if (latchedIssue != null && fresh.deltaPercent != null)
		{
			if (Math.abs(fresh.deltaPercent) > OfferPriceAnalyzer.DEFAULT_THRESHOLD_PERCENT * ISSUE_CLEAR_FACTOR)
			{
				OfferPriceAnalyzer.Analysis prev = alertFallback.get(slot);
				if (prev != null)
				{
					return prev;
				}
			}
			else
			{
				issueLatched.remove(slot);
				alertFallback.remove(slot);
			}
		}

		if (fresh.action == OfferPriceAnalyzer.Action.HOLD)
		{
			if (fresh.marketPrice == null || fresh.marketPrice <= 0)
			{
				OfferPriceAnalyzer.Analysis prev = holdFallback.get(slot);
				if (prev != null && prev.marketPrice != null && prev.marketPrice > 0)
				{
					return prev;
				}
			}
			else
			{
				holdFallback.put(slot, fresh);
			}
		}

		return fresh;
	}
}
