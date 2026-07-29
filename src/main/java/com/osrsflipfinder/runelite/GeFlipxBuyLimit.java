package com.osrsflipfinder.runelite;

import javax.annotation.Nullable;
import net.runelite.client.game.ItemStats;

/**
 * Resolves GE quantity for FlipX buy-limit assist.
 *
 * <p>OSRS enforces a per-item GE buy limit on a rolling 4-hour window (sum of quantities
 * purchased). The client shows current usage on the buy setup panel; FlipX also syncs fills
 * from the plugin for cross-session accuracy.
 */
final class GeFlipxBuyLimit
{
	private GeFlipxBuyLimit()
	{
	}

	/**
	 * Quantity to set on the offer. Never returns {@code Integer.MAX_VALUE} / +1K sentinel values.
	 *
	 * <p>Layer 1: remaining GE buy limit (4h window + synced history). Layer 2: cap by inventory
	 * coins at {@code offerPriceGp} when price and coins are known.
	 *
	 * @param clientBoughtSoFar from {@link GeOfferSetupBuyProgress}, or {@code -1} if unknown
	 * @param offerPriceGp per-item price on the GE setup panel (varbit), or {@code <= 0} to skip coin cap
	 * @param inventoryCoins coins in inventory, or {@code < 0} to skip coin cap
	 */
	static int quantityToApply(
		@Nullable BuyLimitRemaining synced,
		int itemId,
		@Nullable ItemStats itemStats,
		int clientBoughtSoFar,
		int offerPriceGp,
		long inventoryCoins
	)
	{
		int fromLimit = remainingBuyLimitQuantity(synced, itemStats, clientBoughtSoFar);
		return capByInventoryCoins(fromLimit, offerPriceGp, inventoryCoins);
	}

	static int quantityToApply(
		@Nullable BuyLimitRemaining synced,
		int itemId,
		@Nullable ItemStats itemStats,
		int clientBoughtSoFar
	)
	{
		return quantityToApply(synced, itemId, itemStats, clientBoughtSoFar, 0, -1);
	}

	static int capByInventoryCoins(int limitQuantity, int offerPriceGp, long inventoryCoins)
	{
		if (limitQuantity <= 0)
		{
			return 0;
		}
		if (offerPriceGp <= 0 || inventoryCoins < 0)
		{
			return limitQuantity;
		}
		long affordable = inventoryCoins / offerPriceGp;
		if (affordable <= 0)
		{
			return 0;
		}
		return (int) Math.min(limitQuantity, affordable);
	}

	private static int remainingBuyLimitQuantity(
		@Nullable BuyLimitRemaining synced,
		@Nullable ItemStats itemStats,
		int clientBoughtSoFar
	)
	{
		int geLimit = itemStats != null ? itemStats.getGeLimit() : 0;
		Integer apiLimit = synced != null ? synced.getBuyLimit() : null;
		int limit = positiveOrElse(apiLimit, geLimit);

		int bought = Math.max(
			clientBoughtSoFar >= 0 ? clientBoughtSoFar : 0,
			synced != null ? synced.getQuantityBoughtInWindow() : 0
		);

		if (limit > 0)
		{
			return Math.max(0, Math.min(limit - bought, limit));
		}

		if (synced != null && synced.getRemainingQuantity() != null)
		{
			return Math.max(0, synced.getRemainingQuantity());
		}

		return geLimit > 0 ? geLimit : 0;
	}

	/** @deprecated use {@link #quantityToApply(BuyLimitRemaining, int, ItemStats, int, int, long)} */
	static int quantityToApply(
		@Nullable BuyLimitRemaining synced,
		int itemId,
		@Nullable ItemStats itemStats
	)
	{
		return quantityToApply(synced, itemId, itemStats, -1);
	}

	private static int positiveOrElse(Integer primary, int fallback)
	{
		if (primary != null && primary > 0)
		{
			return primary;
		}
		return fallback > 0 ? fallback : 0;
	}
}
