package com.osrsflipfinder.runelite;

import java.time.Instant;
import javax.annotation.Nullable;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;

public final class EventMapper
{
	private EventMapper()
	{
	}

	@Nullable
	public static IngestGeEvent mapOffer(
		GrandExchangeOffer offer,
		int slot,
		long accountHash,
		@Nullable String accountDisplayName,
		@Nullable String itemName,
		Instant occurredAt
	)
	{
		if (offer == null)
		{
			return null;
		}

		GrandExchangeOfferState offerState = offer.getState();
		if (offerState == GrandExchangeOfferState.EMPTY)
		{
			return null;
		}

		String state = mapState(offerState);
		if (state == null)
		{
			return null;
		}

		String side = mapSide(offerState);
		String occurredAtIso = occurredAt.toString();
		String accountHashValue = String.valueOf(accountHash);

		String idempotencyKey = IdempotencyKeyBuilder.build(
			accountHashValue,
			offer.getItemId(),
			side,
			offer.getPrice(),
			offer.getTotalQuantity(),
			offer.getQuantitySold(),
			state,
			occurredAtIso,
			slot
		);

		return IngestGeEvent.builder()
			.idempotencyKey(idempotencyKey)
			.accountHash(accountHashValue)
			.accountDisplayName(accountDisplayName)
			.itemId(offer.getItemId())
			.itemName(itemName)
			.side(side)
			.price(offer.getPrice())
			.quantity(offer.getTotalQuantity())
			.quantityFilled(offer.getQuantitySold())
			.state(state)
			.occurredAt(occurredAtIso)
			.slot(slot)
			.source("plugin")
			.build();
	}

	@Nullable
	static String mapState(GrandExchangeOfferState offerState)
	{
		switch (offerState)
		{
			case BUYING:
				return "buying";
			case SELLING:
				return "selling";
			case CANCELLED_BUY:
				return "cancelled_buy";
			case CANCELLED_SELL:
				return "cancelled_sell";
			case BOUGHT:
				return "bought";
			case SOLD:
				return "sold";
			default:
				return null;
		}
	}

	static String mapSide(GrandExchangeOfferState offerState)
	{
		switch (offerState)
		{
			case BUYING:
			case CANCELLED_BUY:
			case BOUGHT:
				return "buy";
			default:
				return "sell";
		}
	}

	@Nullable
	public static IngestGeEvent mapSlotCleared(
		OfferSnapshot previous,
		int slot,
		long accountHash,
		@Nullable String accountDisplayName,
		@Nullable String itemName,
		Instant occurredAt
	)
	{
		if (previous == null)
		{
			return null;
		}

		String state;
		switch (previous.getState())
		{
			case BUYING:
				state = "cancelled_buy";
				break;
			case SELLING:
				state = "cancelled_sell";
				break;
			default:
				return null;
		}

		String side = mapSide(previous.getState());
		String occurredAtIso = occurredAt.toString();
		String accountHashValue = String.valueOf(accountHash);
		String idempotencyKey = IdempotencyKeyBuilder.build(
			accountHashValue,
			previous.getItemId(),
			side,
			previous.getPrice(),
			previous.getQuantity(),
			previous.getQuantityFilled(),
			state,
			occurredAtIso,
			slot
		);

		return IngestGeEvent.builder()
			.idempotencyKey(idempotencyKey)
			.accountHash(accountHashValue)
			.accountDisplayName(accountDisplayName)
			.itemId(previous.getItemId())
			.itemName(itemName)
			.side(side)
			.price(previous.getPrice())
			.quantity(previous.getQuantity())
			.quantityFilled(previous.getQuantityFilled())
			.state(state)
			.occurredAt(occurredAtIso)
			.slot(slot)
			.source("plugin")
			.build();
	}
}
