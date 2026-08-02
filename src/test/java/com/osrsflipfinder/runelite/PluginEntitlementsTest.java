package com.osrsflipfinder.runelite;

import com.google.gson.Gson;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PluginEntitlementsTest
{
	@Test
	public void gsonDeserializesIsPaid()
	{
		PluginEntitlements entitlements = new Gson().fromJson(
			"{\"tier\":\"pro\",\"isPaid\":true,\"isTrialing\":false}",
			PluginEntitlements.class
		);

		assertTrue(entitlements.isPaid());
		assertTrue(entitlements.hasProAccess());
	}

	@Test
	public void hasProAccessFromLegacyUltraTier()
	{
		PluginEntitlements entitlements = new PluginEntitlements();
		entitlements.setTier("ultra");
		entitlements.setPaid(false);

		assertTrue(entitlements.hasProAccess());
	}

	@Test
	public void freeTierShowsUpgradeCta()
	{
		PluginEntitlements entitlements = new PluginEntitlements();
		entitlements.setTier("free");
		entitlements.setPaid(false);

		assertFalse(entitlements.hasProAccess());
	}
}
