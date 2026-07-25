package com.osrsflipfinder.runelite;

import org.junit.Test;

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
	public void entitlementsChangedDetectsEliteUpgrade()
	{
		PluginEntitlements before = new PluginEntitlements();
		before.setTier("ultra");
		before.setPaid(true);
		before.setUltra(true);
		before.setPublishLeadMs(5_000);

		PluginEntitlements after = new PluginEntitlements();
		after.setTier("elite");
		after.setPaid(true);
		after.setUltra(true);
		after.setElite(true);
		after.setPublishLeadMs(500);

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

		org.junit.Assert.assertFalse(OpportunitiesClient.entitlementsChanged(before, after));
	}

	@Test
	public void computeNextRefreshAtMsUsesPublishMetaWhenConfident()
	{
		long now = 1_000_000L;
		MarketQueryResponse.Meta meta = new MarketQueryResponse.Meta();
		meta.setPhaseConfidence(0.9);
		meta.setNextPublishInMs(12_000L);

		long at = OpportunitiesClient.computeNextRefreshAtMs(meta, 500L, 60_000L, now);
		assertTrue(at >= now + 12_000L + 500L);
		assertTrue(at <= now + 12_000L + 500L + 400L);
	}

	@Test
	public void computeNextRefreshAtMsFallsBackWhenConfidenceLow()
	{
		long now = 1_000_000L;
		MarketQueryResponse.Meta meta = new MarketQueryResponse.Meta();
		meta.setPhaseConfidence(0.1);
		meta.setNextPublishInMs(12_000L);

		long at = OpportunitiesClient.computeNextRefreshAtMs(meta, 500L, 30_000L, now);
		org.junit.Assert.assertEquals(now + 30_000L, at);
	}
}
