package com.osrsflipfinder.runelite;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(FlipFinderConfig.GROUP)
public interface FlipFinderConfig extends Config
{
	String GROUP = "flipx";

	@ConfigItem(
		keyName = "enableUpload",
		name = "Enable GE upload",
		description = "When enabled, GE offer activity is sent to your FlipX account",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers"
	)
	default boolean enableUpload()
	{
		return false;
	}

	@ConfigItem(
		keyName = "deviceId",
		name = "Device ID",
		description = "Stable install identifier used during pairing",
		hidden = true
	)
	default String deviceId()
	{
		return "";
	}

	@ConfigItem(
		keyName = "apiKey",
		name = "API key",
		description = "Plugin API key from pairing (stored locally)",
		secret = true
	)
	default String apiKey()
	{
		return "";
	}

	@ConfigItem(
		keyName = "pairedAt",
		name = "Paired at",
		description = "When this device was last paired",
		hidden = true
	)
	default String pairedAt()
	{
		return "";
	}

	@ConfigItem(
		keyName = "pairedBaseUrl",
		name = "Paired API base URL",
		description = "FlipX API host this device was paired against",
		hidden = true
	)
	default String pairedBaseUrl()
	{
		return "";
	}

	@ConfigItem(
		keyName = "enableMarketPanel",
		name = "Enable Market panel",
		description = "Show live flip opportunities from your account in the plugin's Market tab (requires pairing + Pro)",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers"
	)
	default boolean enableMarketPanel()
	{
		return false;
	}

	@ConfigItem(
		keyName = "enableGeOverlay",
		name = "Enable GE copilot overlay",
		description = "Show live score and estimated profit for the item in your open Grand Exchange offer (requires pairing + Ultra)",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers"
	)
	default boolean enableGeOverlay()
	{
		return false;
	}

	@ConfigItem(
		keyName = "marketPresetId",
		name = "Market preset",
		description = "Last selected Market quick preset",
		hidden = true
	)
	default String marketPresetId()
	{
		return "all";
	}

	@ConfigItem(
		keyName = "marketVolumeEmphasis",
		name = "Volume emphasis",
		description = "Score volume-emphasis slider position (0-100)",
		hidden = true
	)
	default int marketVolumeEmphasis()
	{
		return 50;
	}

	@ConfigItem(
		keyName = "marketHideLowConfidence",
		name = "Hide low confidence",
		description = "Persisted market filter",
		hidden = true
	)
	default boolean marketHideLowConfidence()
	{
		return false;
	}

	@ConfigItem(
		keyName = "marketDiscountOnly",
		name = "Discount only",
		description = "Persisted market filter",
		hidden = true
	)
	default boolean marketDiscountOnly()
	{
		return false;
	}

	@ConfigItem(
		keyName = "marketDumpedOnly",
		name = "Dumped only",
		description = "Persisted market filter",
		hidden = true
	)
	default boolean marketDumpedOnly()
	{
		return false;
	}

	@ConfigItem(
		keyName = "marketUseInventoryCoins",
		name = "Use inventory coins",
		description = "Persisted market filter",
		hidden = true
	)
	default boolean marketUseInventoryCoins()
	{
		return false;
	}

	@ConfigItem(
		keyName = "marketMaxCapital",
		name = "Max capital filter",
		description = "Persisted market filter",
		hidden = true
	)
	default String marketMaxCapital()
	{
		return "";
	}

	@ConfigItem(
		keyName = "marketMinNetProfit",
		name = "Min net profit filter",
		description = "Persisted market filter",
		hidden = true
	)
	default String marketMinNetProfit()
	{
		return "";
	}

	@ConfigItem(
		keyName = "marketMinRoiPercent",
		name = "Min ROI filter",
		description = "Persisted market filter",
		hidden = true
	)
	default String marketMinRoiPercent()
	{
		return "";
	}

	@ConfigItem(
		keyName = "marketMinTotalProfit",
		name = "Min total profit filter",
		description = "Persisted market filter",
		hidden = true
	)
	default String marketMinTotalProfit()
	{
		return "";
	}

	@ConfigItem(
		keyName = "marketMinGpPerHour",
		name = "Min GP/hr filter",
		description = "Persisted market filter",
		hidden = true
	)
	default String marketMinGpPerHour()
	{
		return "";
	}

	@ConfigItem(
		keyName = "marketMinConfidencePercent",
		name = "Min confidence filter",
		description = "Persisted market filter",
		hidden = true
	)
	default String marketMinConfidencePercent()
	{
		return "";
	}

	@ConfigItem(
		keyName = "marketMembersFilter",
		name = "Members filter",
		description = "Persisted market filter",
		hidden = true
	)
	default String marketMembersFilter()
	{
		return "all";
	}

	@ConfigItem(
		keyName = "marketSortId",
		name = "Market sort column",
		description = "Last selected Market sort column",
		hidden = true
	)
	default String marketSortId()
	{
		return "score";
	}

	@ConfigItem(
		keyName = "marketSortDesc",
		name = "Market sort descending",
		description = "Last selected Market sort direction",
		hidden = true
	)
	default boolean marketSortDesc()
	{
		return true;
	}

	@ConfigItem(
		keyName = "slotOptHideLowConfidence",
		name = "Slot opt hide low confidence",
		description = "Persisted slot optimizer filter",
		hidden = true
	)
	default boolean slotOptHideLowConfidence()
	{
		return false;
	}

	@ConfigItem(
		keyName = "slotOptDiscountOnly",
		name = "Slot opt discount only",
		description = "Persisted slot optimizer filter",
		hidden = true
	)
	default boolean slotOptDiscountOnly()
	{
		return false;
	}

	@ConfigItem(
		keyName = "slotOptDumpedOnly",
		name = "Slot opt dumped only",
		description = "Persisted slot optimizer filter",
		hidden = true
	)
	default boolean slotOptDumpedOnly()
	{
		return false;
	}

	@ConfigItem(
		keyName = "slotOptMinNetProfit",
		name = "Slot opt min net profit",
		description = "Persisted slot optimizer filter",
		hidden = true
	)
	default String slotOptMinNetProfit()
	{
		return "";
	}

	@ConfigItem(
		keyName = "slotOptMinRoiPercent",
		name = "Slot opt min ROI",
		description = "Persisted slot optimizer filter",
		hidden = true
	)
	default String slotOptMinRoiPercent()
	{
		return "";
	}

	@ConfigItem(
		keyName = "slotOptMinTotalProfit",
		name = "Slot opt min total profit",
		description = "Persisted slot optimizer filter",
		hidden = true
	)
	default String slotOptMinTotalProfit()
	{
		return "";
	}

	@ConfigItem(
		keyName = "slotOptMinGpPerHour",
		name = "Slot opt min GP/hr",
		description = "Persisted slot optimizer filter",
		hidden = true
	)
	default String slotOptMinGpPerHour()
	{
		return "";
	}

	@ConfigItem(
		keyName = "slotOptMinConfidencePercent",
		name = "Slot opt min confidence",
		description = "Persisted slot optimizer filter",
		hidden = true
	)
	default String slotOptMinConfidencePercent()
	{
		return "";
	}

	@ConfigItem(
		keyName = "slotOptMembersFilter",
		name = "Slot opt members filter",
		description = "Persisted slot optimizer filter",
		hidden = true
	)
	default String slotOptMembersFilter()
	{
		return "all";
	}

	@ConfigItem(
		keyName = "slotOptSortId",
		name = "Slot optimizer sort column",
		description = "Last selected slot optimizer rank column",
		hidden = true
	)
	default String slotOptSortId()
	{
		return "gpPerSlotHour";
	}

	@ConfigItem(
		keyName = "slotOptSortDesc",
		name = "Slot optimizer sort descending",
		description = "Last selected slot optimizer sort direction",
		hidden = true
	)
	default boolean slotOptSortDesc()
	{
		return true;
	}

	@ConfigItem(
		keyName = "enableOfflineCache",
		name = "Enable offline cache",
		description = "Keep last-known market and portfolio data on disk for offline display"
	)
	default boolean enableOfflineCache()
	{
		return true;
	}

	@ConfigItem(
		keyName = "tradeStagnationMinutes",
		name = "Stagnation threshold (minutes)",
		description = "Highlight inactive slots in the Flip manager sidebar after this many minutes"
	)
	default int tradeStagnationMinutes()
	{
		return 15;
	}

	@ConfigItem(
		keyName = "enableGeChartOverlay",
		name = "Enable GE price chart overlay",
		description = "Show a mini price chart on the Grand Exchange offer setup screen",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers"
	)
	default boolean enableGeChartOverlay()
	{
		return false;
	}

	@ConfigItem(
		keyName = "enableWatchlistGeHint",
		name = "Enable watchlist GE hint",
		description = "When you type 1 in GE search, show your watchlist as a display-only overlay",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers"
	)
	default boolean enableWatchlistGeHint()
	{
		return false;
	}
}
