package com.osrsflipfinder.runelite;

import lombok.Data;

/** Elite network intelligence bundled on item detail (server-tier-filtered). */
@Data
public class NetworkIntelResponse
{
	private NetworkSignalSummary signal;
	private Double edgeScore;
	private DivergenceSummary divergence;
	private CoopFlipBandSummary coopFlipBand;
	private NetworkPriceHints networkPrices;

	@Data
	public static class NetworkSignalSummary
	{
		private Integer itemId;
		private Integer saturationScore;
		private Double buyFillProb5m;
		private Double sellFillProb15m;
		private String smartMoneyDirection;
		private Integer edgeHalfLifeMinutes;
		private String signalConfidence;
		private String crowdWarning;
		private String computedAt;
	}

	@Data
	public static class DivergenceSummary
	{
		private String headline;
		private String detail;
	}

	@Data
	public static class CoopFlipBandSummary
	{
		private Long referenceBuy;
		private Long referenceSell;
		private Long toleranceGp;
		private String status;
		private String headline;
		private String detail;
	}

	@Data
	public static class NetworkPriceHints
	{
		private Long medianBuy;
		private Long medianSell;
		private Integer buySamples;
		private Integer sellSamples;
		private Double divergenceBuyPct;
		private Double divergenceSellPct;
	}
}
