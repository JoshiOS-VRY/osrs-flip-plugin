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
		int unitPrice = GeOfferPricing.unitPrice(offer);

		String idempotencyKey = IdempotencyKeyBuilder.build(
			accountHashValue,
			offer.getItemId(),
			side,
			unitPrice,
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
			.price(unitPrice)
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
			case SELLING:
				state = GeTerminalState.resolveOnSlotClear(
					previous.getState(),
					previous.getQuantityFilled()
				);
				break;
			default:
				return null;
		}

		String side = mapSide(previous.getState());
		int terminalQty = GeTerminalState.terminalQuantity(
			previous.getQuantity(),
			previous.getQuantityFilled()
		);
		int unitPrice = previous.getPrice();
		String occurredAtIso = occurredAt.toString();
		String accountHashValue = String.valueOf(accountHash);
		String idempotencyKey = IdempotencyKeyBuilder.build(
			accountHashValue,
			previous.getItemId(),
			side,
			unitPrice,
			terminalQty,
			terminalQty,
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
			.price(unitPrice)
			.quantity(terminalQty)
			.quantityFilled(terminalQty)
			.state(state)
			.occurredAt(occurredAtIso)
			.slot(slot)
			.source("plugin")
			.build();
	}
}
