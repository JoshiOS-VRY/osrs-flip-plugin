package com.osrsflipfinder.runelite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PortfolioPeriodTest
{
	@Test
	public void fromIdDefaultsToSession()
	{
		assertEquals(PortfolioPeriod.SESSION, PortfolioPeriod.fromId(null));
		assertEquals(PortfolioPeriod.SESSION, PortfolioPeriod.fromId("invalid"));
		assertEquals(PortfolioPeriod.WEEK_1, PortfolioPeriod.fromId("1w"));
	}

	@Test
	public void sessionQueryIncludesLiveSession()
	{
		String q = PortfolioPeriod.SESSION.toApiQuery();
		assertTrue(q.contains("period=session"));
		assertTrue(q.contains("liveSession=1"));
	}

	@Test
	public void weekQueryIncludesFromAndTo()
	{
		String q = PortfolioPeriod.WEEK_1.toApiQuery();
		assertTrue(q.contains("period=1w"));
		assertTrue(q.contains("from="));
		assertTrue(q.contains("to="));
		assertTrue(q.contains("days=7"));
	}
}
