package com.osrsflipfinder.runelite;

import lombok.Data;

/** Enriched open GE offer from portfolio API. */
@Data
public class EnrichedOpenGeOffer
{
	private String id;
	private int itemId;
	private String itemName;
	private String side;
	private String state;
	private long price;
	private int quantity;
	private int quantityFilled;
	private String eventTime;
	private Integer slot;
	private String accountHash;
	private Long marketBuyPrice;
	private Long marketSellPrice;
	private Double deltaPercent;
	private String priceIssue;
}
