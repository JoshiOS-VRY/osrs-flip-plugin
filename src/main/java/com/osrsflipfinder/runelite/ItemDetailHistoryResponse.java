package com.osrsflipfinder.runelite;

import java.util.List;
import lombok.Data;

/** Response of {@code GET /api/plugin/items/{id}/history}. */
@Data
public class ItemDetailHistoryResponse
{
	private List<ItemDetailResponse.PriceSnapshot> snapshots;
	private int chartDays;
	private ItemDetailResponse.MarketRegimeSummary marketRegime;
	private ItemDetailResponse.ItemDetailMeta meta;
}
