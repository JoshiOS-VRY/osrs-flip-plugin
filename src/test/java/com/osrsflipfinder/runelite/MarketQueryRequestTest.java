package com.osrsflipfinder.runelite;

import com.google.gson.Gson;
import java.util.Collections;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class MarketQueryRequestTest
{
	@Test
	public void gsonIncludesQualityFilters()
	{
		MarketQueryRequest request = new MarketQueryRequest();
		request.setPresetId("all");
		request.setLimit(300);
		MarketQueryRequest.MarketFilters filters = new MarketQueryRequest.MarketFilters();
		filters.setHideLowConfidence(true);
		filters.setDiscountOnly(true);
		request.setFilters(filters);
		request.setSort(Collections.singletonList(new MarketQueryRequest.Sort("netProfit", true)));

		String json = new Gson().toJson(request);
		assertTrue(json.contains("\"hideLowConfidence\":true"));
		assertTrue(json.contains("\"discountOnly\":true"));
		assertTrue(json.contains("\"presetId\":\"all\""));
	}
}
