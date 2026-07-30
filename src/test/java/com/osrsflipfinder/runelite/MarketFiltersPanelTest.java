package com.osrsflipfinder.runelite;

import java.util.HashMap;
import java.util.Map;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MarketFiltersPanelTest
{
	/** Panel state is restored from persisted config keys, not the config proxy. */
	private static ConfigManager configManagerWith(Map<String, String> saved)
	{
		ConfigManager configManager = mock(ConfigManager.class);
		when(configManager.getConfiguration(eq(FlipFinderConfig.GROUP), anyString()))
			.thenAnswer(invocation -> saved.get(invocation.<String>getArgument(1)));
		return configManager;
	}

	private static Map<String, String> savedQualityFilters()
	{
		Map<String, String> saved = new HashMap<>();
		saved.put("marketHideLowConfidence", "true");
		saved.put("marketDiscountOnly", "true");
		saved.put("marketMembersFilter", "all");
		return saved;
	}

	@Test
	public void buildFiltersOmitsQualityFiltersWithoutPro()
	{
		MarketFiltersPanel panel = new MarketFiltersPanel(
			configManagerWith(savedQualityFilters()),
			mock(CoinBalanceService.class),
			() -> {}
		);
		panel.setAdvancedEnabled(false);

		MarketQueryRequest.MarketFilters filters = panel.buildFilters();
		assertNull(filters.getHideLowConfidence());
		assertNull(filters.getDiscountOnly());
		assertNull(filters.getDeepDiscountOnly());
		assertNull(filters.getDumpedOnly());
		assertNull(filters.getMaxCapital());
		assertTrue(panel.hasIgnoredAdvancedFilters());
		assertTrue(panel.getEntitlementNotice().contains("Pro"));
	}

	@Test
	public void buildFiltersIncludesQualityFiltersWithPro()
	{
		MarketFiltersPanel panel = new MarketFiltersPanel(
			configManagerWith(savedQualityFilters()),
			mock(CoinBalanceService.class),
			() -> {}
		);
		panel.setAdvancedEnabled(true);

		MarketQueryRequest.MarketFilters filters = panel.buildFilters();
		assertTrue(filters.getHideLowConfidence());
		assertTrue(filters.getDiscountOnly());
		assertFalse(Boolean.TRUE.equals(filters.getDeepDiscountOnly()));
		assertFalse(panel.hasIgnoredAdvancedFilters());
		assertNull(panel.getEntitlementNotice());
	}

	@Test
	public void applyFromFiltersClearsQualityFlagsOmittedFromBookmark()
	{
		Map<String, String> saved = new HashMap<>();
		saved.put("marketDeepDiscountOnly", "true");
		saved.put("marketDiscountOnly", "true");
		saved.put("marketMembersFilter", "all");
		MarketFiltersPanel panel = new MarketFiltersPanel(
			configManagerWith(saved),
			mock(CoinBalanceService.class),
			() -> {}
		);
		panel.setAdvancedEnabled(true);

		MarketQueryRequest.MarketFilters bookmarkFilters = new MarketQueryRequest.MarketFilters();
		bookmarkFilters.setDiscountOnly(true);

		panel.applyFromFilters(bookmarkFilters);

		MarketQueryRequest.MarketFilters built = panel.buildFilters();
		assertTrue(built.getDiscountOnly());
		assertNull(built.getDeepDiscountOnly());
	}
}
