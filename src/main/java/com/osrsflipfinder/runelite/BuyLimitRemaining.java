package com.osrsflipfinder.runelite;

import lombok.Data;

/** {@code GET /api/plugin/portfolio/buy-limit} — rolling 4h GE buy limit usage. */
@Data
class BuyLimitRemaining
{
	private int itemId;
	private String itemName;
	private String accountHash;
	private Integer buyLimit;
	private int quantityBoughtInWindow;
	private Integer remainingQuantity;
	private String windowResetsAt;
	private String confidence;
}
