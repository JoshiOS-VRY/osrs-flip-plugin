package com.osrsflipfinder.runelite;

import net.runelite.client.config.ConfigManager;

/** Reads persisted flipx config keys without relying on a stale {@link FlipFinderConfig} proxy. */
final class FlipFinderConfigIO
{
	private FlipFinderConfigIO()
	{
	}

	static boolean getBoolean(ConfigManager configManager, String key, boolean defaultValue)
	{
		String raw = configManager.getConfiguration(FlipFinderConfig.GROUP, key);
		if (raw == null || raw.isBlank())
		{
			return defaultValue;
		}
		return "true".equalsIgnoreCase(raw);
	}

	static String getString(ConfigManager configManager, String key, String defaultValue)
	{
		String raw = configManager.getConfiguration(FlipFinderConfig.GROUP, key);
		if (raw == null)
		{
			return defaultValue;
		}
		return raw;
	}
}
