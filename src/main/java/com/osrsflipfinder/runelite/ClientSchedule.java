package com.osrsflipfinder.runelite;

/** Client poll scheduling aligned with {@code src/lib/osrs/wiki-clock.ts}. */
final class ClientSchedule
{
	static final double PHASE_CONFIDENCE_THRESHOLD = 0.35;

	private ClientSchedule()
	{
	}

	static long computeNextFetchAtMs(
		MarketQueryResponse.Meta meta,
		long publishLeadMs,
		long fallbackIntervalMs,
		long nowMs
	)
	{
		if (meta == null)
		{
			return nowMs + Math.max(0, fallbackIntervalMs);
		}
		return computeNextFetchAtMs(
			meta.getNextWikiPublishAtMs(),
			meta.getNextPublishInMs(),
			meta.getPhaseConfidence(),
			publishLeadMs,
			fallbackIntervalMs,
			nowMs
		);
	}

	static long computeNextFetchAtMs(
		ItemDetailResponse.ItemDetailMeta meta,
		long publishLeadMs,
		long fallbackIntervalMs,
		long nowMs
	)
	{
		if (meta == null)
		{
			return nowMs + Math.max(0, fallbackIntervalMs);
		}
		return computeNextFetchAtMs(
			meta.getNextWikiPublishAtMs(),
			meta.getNextPublishInMs(),
			meta.getPhaseConfidence(),
			publishLeadMs,
			fallbackIntervalMs,
			nowMs
		);
	}

	private static long computeNextFetchAtMs(
		Long nextWikiPublishAtMs,
		Long nextPublishInMs,
		double phaseConfidence,
		long publishLeadMs,
		long fallbackIntervalMs,
		long nowMs
	)
	{
		long fallback = nowMs + Math.max(0, fallbackIntervalMs);

		if (nextWikiPublishAtMs != null
			&& nextWikiPublishAtMs > 0
			&& phaseConfidence >= PHASE_CONFIDENCE_THRESHOLD)
		{
			long aligned = nextWikiPublishAtMs + publishLeadMs;
			if (aligned > nowMs)
			{
				return aligned;
			}
		}

		if (phaseConfidence >= PHASE_CONFIDENCE_THRESHOLD
			&& nextPublishInMs != null
			&& nextPublishInMs >= 0)
		{
			if (nextPublishInMs <= publishLeadMs)
			{
				return fallback;
			}
			return nowMs + nextPublishInMs + publishLeadMs;
		}

		return fallback;
	}

	static long msUntilNextFetch(long nextFetchAtMs, long nowMs)
	{
		return Math.max(0, nextFetchAtMs - nowMs);
	}
}
