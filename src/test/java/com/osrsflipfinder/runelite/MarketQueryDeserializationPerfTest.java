package com.osrsflipfinder.runelite;

import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import org.junit.Assert;
import org.junit.Test;

/** Guards Gson parse time for large plugin market payloads. */
public class MarketQueryDeserializationPerfTest
{
	private static final Gson GSON = new Gson();

	@Test
	public void deserializesFiveHundredRowMarketResponseQuickly()
	{
		String json = buildSamplePayload(500);
		long start = System.nanoTime();
		MarketQueryResponse response = GSON.fromJson(json, MarketQueryResponse.class);
		long elapsedMs = (System.nanoTime() - start) / 1_000_000;
		Assert.assertNotNull(response);
		Assert.assertEquals(500, response.getOpportunities().size());
		Assert.assertTrue(
			"Expected Gson parse under 250ms, was " + elapsedMs + "ms",
			elapsedMs < 250
		);
	}

	@Test
	public void slimPayloadIsSmallerThanLegacyFatShape()
	{
		int slimBytes = buildSamplePayload(500).getBytes(StandardCharsets.UTF_8).length;
		int fatBytes = buildLegacyFatPayload(500).getBytes(StandardCharsets.UTF_8).length;
		Assert.assertTrue(
			"Slim payload should be materially smaller (" + slimBytes + " vs " + fatBytes + " bytes)",
			slimBytes < fatBytes * 0.55
		);
	}

	private static String buildSamplePayload(int count)
	{
		StringBuilder opportunities = new StringBuilder("[");
		for (int i = 0; i < count; i++)
		{
			if (i > 0)
			{
				opportunities.append(',');
			}
			opportunities.append('{')
				.append("\"id\":").append(i + 1).append(',')
				.append("\"name\":\"Item ").append(i + 1).append("\",")
				.append("\"members\":true,")
				.append("\"buyLimit\":8,")
				.append("\"estimatedBuyPrice\":75000000,")
				.append("\"estimatedSellPrice\":75264405,")
				.append("\"netProfitPerItem\":-1240883,")
				.append("\"netRoiPercent\":-1.65,")
				.append("\"opportunityScore\":12,")
				.append("\"confidenceScore\":0.51,")
				.append("\"estimatedProfitAtQuantity\":-9927064,")
				.append("\"estimatedProfit30m\":0,")
				.append("\"estimatedProfitPerHour\":0,")
				.append("\"estimatedCapitalRequired\":600000000,")
				.append("\"estimatedTurnoverHours\":0.67,")
				.append("\"estimatedTradableQuantity\":2,")
				.append("\"fiveMinuteVolume\":5,")
				.append("\"oneHourVolume\":53,")
				.append("\"referenceTradingPrice\":75132203,")
				.append("\"geGuidePrice\":null,")
				.append("\"isPriceDumped\":false")
				.append('}');
		}
		opportunities.append(']');

		return "{"
			+ "\"opportunities\":" + opportunities + ","
			+ "\"summary\":{\"opportunitiesFound\":" + count + ",\"highestNetProfit\":100,\"highestRoiPercent\":5,\"visibleCount\":" + count + "},"
			+ "\"movers\":" + opportunities + ","
			+ "\"meta\":{\"lastUpdatedMs\":1,\"stale\":false,\"refreshIntervalMs\":60000,\"tier\":\"pro\"}"
			+ "}";
	}

	private static String buildLegacyFatPayload(int count)
	{
		StringBuilder opportunities = new StringBuilder("[");
		for (int i = 0; i < count; i++)
		{
			if (i > 0)
			{
				opportunities.append(',');
			}
			opportunities.append('{')
				.append("\"id\":").append(i + 1).append(',')
				.append("\"name\":\"Item ").append(i + 1).append("\",")
				.append("\"members\":true,")
				.append("\"buyLimit\":8,")
				.append("\"icon\":\"item.png\",")
				.append("\"estimatedBuyPrice\":75000000,")
				.append("\"estimatedSellPrice\":75264405,")
				.append("\"referenceTradingPrice\":75132203,")
				.append("\"currentTradingPrice\":75100000,")
				.append("\"fiveMinuteTradingPrice\":75050000,")
				.append("\"geGuidePrice\":600000,")
				.append("\"isPriceDumped\":false,")
				.append("\"grossMargin\":264405,")
				.append("\"taxPerItem\":1505288,")
				.append("\"netProfitPerItem\":-1240883,")
				.append("\"grossMarginPercent\":0.35,")
				.append("\"netRoiPercent\":-1.65,")
				.append("\"fiveMinuteVolume\":5,")
				.append("\"oneHourVolume\":53,")
				.append("\"fiveMinuteHighVolume\":3,")
				.append("\"fiveMinuteLowVolume\":2,")
				.append("\"oneHourHighVolume\":29,")
				.append("\"oneHourLowVolume\":24,")
				.append("\"estimatedTradableQuantity\":2,")
				.append("\"estimatedAchievableQuantity30m\":0,")
				.append("\"estimatedProfitAtQuantity\":-9927064,")
				.append("\"estimatedProfit30m\":0,")
				.append("\"estimatedCapitalRequired\":600000000,")
				.append("\"estimatedTurnoverHours\":0.67,")
				.append("\"turnoverSpeedScore\":0.84,")
				.append("\"slotHourScore\":0,")
				.append("\"throughputScore\":0,")
				.append("\"estimatedProfitPerHour\":0,")
				.append("\"rankingGpPerHour\":0,")
				.append("\"hourlyVolumeVsDailyBaseline\":1.2,")
				.append("\"volumeRegimeMultiplier\":1,")
				.append("\"volumeHealthScore\":0.18,")
				.append("\"freshnessScore\":0.86,")
				.append("\"confidenceScore\":0.51,")
				.append("\"profit30mScore\":0,")
				.append("\"limitUpsideScore\":0,")
				.append("\"perItemProfitScore\":0,")
				.append("\"roiScore\":0,")
				.append("\"opportunityScore\":12,")
				.append("\"latestTradeTimestamp\":1700000000")
				.append('}');
		}
		opportunities.append(']');

		return "{"
			+ "\"opportunities\":" + opportunities + ","
			+ "\"summary\":{\"opportunitiesFound\":" + count + ",\"highestNetProfit\":100,\"highestRoiPercent\":5,\"visibleCount\":" + count + "},"
			+ "\"movers\":" + opportunities + ","
			+ "\"meta\":{\"lastUpdatedMs\":1,\"stale\":false,\"refreshIntervalMs\":60000,\"tier\":\"pro\"}"
			+ "}";
	}
}
