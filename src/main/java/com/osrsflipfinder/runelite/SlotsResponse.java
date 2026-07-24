package com.osrsflipfinder.runelite;

import java.util.List;
import lombok.Data;

/** Response of {@code POST /api/plugin/slots/optimize}. */
@Data
public class SlotsResponse
{
	private List<SlotRecommendation> slots;
	private MarketQueryResponse.Meta meta;
}
