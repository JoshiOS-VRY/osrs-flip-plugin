package com.osrsflipfinder.runelite;

import java.time.Instant;
import net.runelite.client.config.ConfigManager;

/** Local pairing state - must match {@link FlipXConstants#baseUrl()}. */
final class PairingCredentials
{
	private static final long PAIRING_GRACE_MS = 15_000L;
	private static volatile long lastPairSaveMs = 0L;

	private PairingCredentials()
	{
	}

	static boolean isWithinPairingGracePeriod()
	{
		return System.currentTimeMillis() - lastPairSaveMs < PAIRING_GRACE_MS;
	}

	static void clearIfAuthFailedForKey(
		ConfigManager configManager,
		FlipFinderConfig config,
		String apiKeyUsed
	)
	{
		if (apiKeyUsed == null || apiKeyUsed.isBlank())
		{
			return;
		}
		if (!apiKeyUsed.equals(config.apiKey()))
		{
			return;
		}
		if (isWithinPairingGracePeriod())
		{
			return;
		}
		clear(configManager);
	}

	static boolean isPaired(FlipFinderConfig config)
	{
		return config.apiKey() != null && !config.apiKey().isBlank();
	}

	static boolean isPairedForCurrentApi(FlipFinderConfig config)
	{
		if (!isPaired(config))
		{
			return false;
		}
		String pairedBase = config.pairedBaseUrl();
		if (pairedBase == null || pairedBase.isBlank())
		{
			return true;
		}
		return FlipXConstants.canonicalBaseUrl(pairedBase).equals(FlipXConstants.canonicalBaseUrl(FlipXConstants.baseUrl()));
	}

	/**
	 * Clears stored credentials when the JVM points at a different API host
	 * (e.g. localhost dev client -> production flipx.gg).
	 *
	 * @return true if credentials were cleared
	 */
	static boolean clearIfApiHostChanged(ConfigManager configManager, FlipFinderConfig config)
	{
		if (!isPaired(config))
		{
			return false;
		}
		if (isPairedForCurrentApi(config))
		{
			return false;
		}
		clear(configManager);
		return true;
	}

	static void save(ConfigManager configManager, String apiKey)
	{
		lastPairSaveMs = System.currentTimeMillis();
		configManager.setConfiguration(FlipFinderConfig.GROUP, "apiKey", apiKey);
		configManager.setConfiguration(
			FlipFinderConfig.GROUP,
			"pairedAt",
			Instant.now().toString()
		);
		configManager.setConfiguration(
			FlipFinderConfig.GROUP,
			"pairedBaseUrl",
			FlipXConstants.canonicalBaseUrl(FlipXConstants.baseUrl())
		);
	}

	static void clear(ConfigManager configManager)
	{
		configManager.unsetConfiguration(FlipFinderConfig.GROUP, "apiKey");
		configManager.unsetConfiguration(FlipFinderConfig.GROUP, "pairedAt");
		configManager.unsetConfiguration(FlipFinderConfig.GROUP, "pairedBaseUrl");
	}
}
