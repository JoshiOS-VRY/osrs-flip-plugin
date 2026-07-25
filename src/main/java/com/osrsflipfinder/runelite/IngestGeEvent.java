package com.osrsflipfinder.runelite;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class IngestGeEvent
{
	String idempotencyKey;
	String accountHash;
	String accountDisplayName;
	int itemId;
	String itemName;
	String side;
	/** Per-item executed trade price (average fill), not the limit entered on the GE form. */
	int price;
	int quantity;
	int quantityFilled;
	String state;
	String occurredAt;
	Integer slot;
	String source;
}
