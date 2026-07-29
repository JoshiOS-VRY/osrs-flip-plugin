package com.osrsflipfinder.runelite;

import com.google.gson.Gson;
import org.junit.Assert;
import org.junit.Test;

public class FlipOpportunityDeserializationTest
{
	@Test
	public void parsesFractionalEstimatedProfitPerHour()
	{
		String json = "{"
			+ "\"id\":1,"
			+ "\"name\":\"Test\","
			+ "\"estimatedProfitPerHour\":181494.5"
			+ "}";

		FlipOpportunity opp = new Gson().fromJson(json, FlipOpportunity.class);
		Assert.assertEquals(181494.5, opp.getEstimatedProfitPerHour(), 0.001);
	}
}
