package com.osrsflipfinder.runelite;

import lombok.Data;

/** Response of {@code GET /api/plugin/items/{id}/live}. */
@Data
public class ItemDetailLiveResponse
{
	private FlipOpportunity opportunity;
	private ItemDetailResponse.ItemDetailMeta meta;
	private NetworkIntelResponse networkIntel;
	private DisplayPricesResponse displayPrices;
}
