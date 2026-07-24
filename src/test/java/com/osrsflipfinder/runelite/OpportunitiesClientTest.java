package com.osrsflipfinder.runelite;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OpportunitiesClientTest
{
	@Test
	public void entitlementsChangedDetectsTierUpgrade()
	{
		PluginEntitlements before = new PluginEntitlements();
		before.setTier("pro");
		before.setPaid(true);
		before.setSlotOptimizerSlots(8);
		before.setRefreshIntervalMs(30_000);
		before.setMaxOpportunities(300);

		PluginEntitlements after = new PluginEntitlements();
		after.setTier("ultra");
		after.setPaid(true);
		after.setUltra(true);
		after.setSlotOptimizerSlots(8);
		after.setRefreshIntervalMs(15_000);
		after.setMaxOpportunities(500);

		assertTrue(OpportunitiesClient.entitlementsChanged(before, after));
	}

	@Test
	public void entitlementsChangedIgnoresIdenticalSnapshot()
	{
		PluginEntitlements before = new PluginEntitlements();
		before.setTier("ultra");
		before.setPaid(true);
		before.setUltra(true);
		before.setSlotOptimizerSlots(8);
		before.setRefreshIntervalMs(15_000);
		before.setMaxOpportunities(500);
		before.setAdvancedFilters(true);

		PluginEntitlements after = new PluginEntitlements();
		after.setTier("ultra");
		after.setPaid(true);
		after.setUltra(true);
		after.setSlotOptimizerSlots(8);
		after.setRefreshIntervalMs(15_000);
		after.setMaxOpportunities(500);
		after.setAdvancedFilters(true);

		assertFalse(OpportunitiesClient.entitlementsChanged(before, after));
	}
}
