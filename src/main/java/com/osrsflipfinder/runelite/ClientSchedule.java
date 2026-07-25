package com.osrsflipfinder.runelite;

/** Client poll scheduling aligned with {@code src/lib/osrs/wiki-clock.ts}. */
final class ClientSchedule
{
	static final double PHASE_CONFIDENCE_THRESHOLD = 0.35;
	static final int JITTER_MS = 400;

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
		if (meta != null
			&& meta.getPhaseConfidence() >= PHASE_CONFIDENCE_THRESHOLD
			&& meta.getNextPublishInMs() != null
			&& meta.getNextPublishInMs() >= 0)
		{
			long jitter = (long) (Math.random() * JITTER_MS);
			return nowMs + meta.getNextPublishInMs() + publishLeadMs + jitter;
		}
		return nowMs + Math.max(0, fallbackIntervalMs);
	}

	static long msUntilNextFetch(long nextFetchAtMs, long nowMs)
	{
		return Math.max(0, nextFetchAtMs - nowMs);
	}
}
