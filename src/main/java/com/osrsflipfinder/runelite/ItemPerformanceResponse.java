package com.osrsflipfinder.runelite;

import java.util.List;
import lombok.Data;

/** Response of {@code GET /api/plugin/analytics/items}. */
@Data
class ItemPerformanceResponse
{
	private List<ItemPerformanceRow> items;
}
