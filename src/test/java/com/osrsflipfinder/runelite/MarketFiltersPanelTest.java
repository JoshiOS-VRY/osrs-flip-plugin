package com.osrsflipfinder.runelite;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MarketFiltersPanelTest
{
	@Test
	public void buildFiltersOmitsQualityFiltersWithoutPro()
	{
		FlipFinderConfig config = mock(FlipFinderConfig.class);
		when(config.marketHideLowConfidence()).thenReturn(true);
		when(config.marketDiscountOnly()).thenReturn(true);
		when(config.marketDumpedOnly()).thenReturn(false);
		when(config.marketUseInventoryCoins()).thenReturn(false);
		when(config.marketMaxCapital()).thenReturn("");
		when(config.marketMinNetProfit()).thenReturn("");
		when(config.marketMinRoiPercent()).thenReturn("");
		when(config.marketMinTotalProfit()).thenReturn("");
		when(config.marketMinGpPerHour()).thenReturn("");
		when(config.marketMinConfidencePercent()).thenReturn("");
		when(config.marketMembersFilter()).thenReturn("all");

		MarketFiltersPanel panel = new MarketFiltersPanel(
			config,
			mock(net.runelite.client.config.ConfigManager.class),
			mock(CoinBalanceService.class),
			() -> {}
		);
		panel.setAdvancedEnabled(false);

		MarketQueryRequest.MarketFilters filters = panel.buildFilters();
		assertNull(filters.getHideLowConfidence());
		assertNull(filters.getDiscountOnly());
		assertNull(filters.getDumpedOnly());
		assertNull(filters.getMaxCapital());
		assertTrue(panel.hasIgnoredAdvancedFilters());
		assertTrue(panel.getEntitlementNotice().contains("Pro"));
	}

	@Test
	public void buildFiltersIncludesQualityFiltersWithPro()
	{
		FlipFinderConfig config = mock(FlipFinderConfig.class);
		when(config.marketHideLowConfidence()).thenReturn(true);
		when(config.marketDiscountOnly()).thenReturn(false);
		when(config.marketDumpedOnly()).thenReturn(false);
		when(config.marketUseInventoryCoins()).thenReturn(false);
		when(config.marketMaxCapital()).thenReturn("");
		when(config.marketMinNetProfit()).thenReturn("");
		when(config.marketMinRoiPercent()).thenReturn("");
		when(config.marketMinTotalProfit()).thenReturn("");
		when(config.marketMinGpPerHour()).thenReturn("");
		when(config.marketMinConfidencePercent()).thenReturn("");
		when(config.marketMembersFilter()).thenReturn("all");

		MarketFiltersPanel panel = new MarketFiltersPanel(
			config,
			mock(net.runelite.client.config.ConfigManager.class),
			mock(CoinBalanceService.class),
			() -> {}
		);
		panel.setAdvancedEnabled(true);

		MarketQueryRequest.MarketFilters filters = panel.buildFilters();
		assertTrue(filters.getHideLowConfidence());
		assertFalse(panel.hasIgnoredAdvancedFilters());
		assertNull(panel.getEntitlementNotice());
	}
}
