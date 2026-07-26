package com.osrsflipfinder.runelite;

/** Wiki-aligned refresh countdown for GE copilot sidebar and overlay. */
final class CopilotRefreshLabels
{
	private CopilotRefreshLabels()
	{
	}

	static String pollingLine(
		OpportunitiesClient opportunities,
		ItemsClient items,
		boolean paired,
		boolean geCopilotActive
	)
	{
		if (!paired)
		{
			return " ";
		}

		long nextAt = resolveNextRefreshAtMs(opportunities, items);
		boolean pollingActive =
			opportunities.isActive() || geCopilotActive;
		return RefreshCountdown.formatPolling(
			nextAt,
			pollingActive,
			opportunities.isFetchInProgress() || items.isItemFetchInProgress()
		);
	}

	private static long resolveNextRefreshAtMs(
		OpportunitiesClient opportunities,
		ItemsClient items
	)
	{
		long marketNext = opportunities.getNextRefreshAtMs();
		if (marketNext > System.currentTimeMillis())
		{
			return marketNext;
		}

		MarketQueryResponse latest = opportunities.getLatest();
		if (latest != null && latest.getMeta() != null)
		{
			PluginEntitlements entitlements = opportunities.getEntitlements();
			long publishLeadMs = entitlements != null && entitlements.getPublishLeadMs() > 0
				? entitlements.getPublishLeadMs()
				: 500L;
			long fallbackMs = entitlements != null && entitlements.getRefreshIntervalMs() > 0
				? entitlements.getRefreshIntervalMs()
				: 60_000L;
			long fromMeta = ClientSchedule.computeNextFetchAtMs(
				latest.getMeta(),
				publishLeadMs,
				fallbackMs,
				System.currentTimeMillis()
			);
			if (fromMeta > System.currentTimeMillis())
			{
				return fromMeta;
			}
		}

		long itemNext = items.getNextWikiRefreshAtMs();
		if (itemNext > System.currentTimeMillis())
		{
			return itemNext;
		}

		return marketNext > 0 ? marketNext : itemNext;
	}

	static String withUpdatedTimestamp(String pollingLine, long lastUpdatedMs)
	{
		String updated = lastUpdatedMs > 0
			? "Updated " + MarketFormat.updatedClock(lastUpdatedMs)
			: null;
		return RefreshCountdown.combine(pollingLine, updated);
	}
}
