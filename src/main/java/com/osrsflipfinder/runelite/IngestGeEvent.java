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
	int price;
	int quantity;
	int quantityFilled;
	String state;
	String occurredAt;
	Integer slot;
	String source;
}
