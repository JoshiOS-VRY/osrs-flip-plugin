package com.osrsflipfinder.runelite;

import java.util.Collections;
import java.util.List;
import lombok.Data;

/** Body for {@code POST /api/plugin/slots/optimize}. Null fields are omitted. */
@Data
public class SlotsOptimizeRequest
{
	private Long maxCapital;
	private String membersFilter;
	private Double minConfidence;
	private MarketQueryRequest.MarketFilters filters;
	private List<MarketQueryRequest.Sort> sort;
	private Integer availableSlots;
	private List<Integer> excludeItemIds;

	public void setRankSort(String sortId, boolean desc)
	{
		this.sort = Collections.singletonList(new MarketQueryRequest.Sort(sortId, desc));
	}
}
