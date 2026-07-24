package com.osrsflipfinder.runelite;

import lombok.Data;

/** Response of {@code GET /api/plugin/session}. */
@Data
public class LiveSessionStats
{
	private long totalProfit;
	private long totalTax;
	private int flipCount;
	private double roiPercent;
	private double gpPerHour;
	private long sessionDurationMs;
	private String startedAt;
	private String endedAt;
	private double avgGpPerHour7d;
	private double flipsPerHour;
	private long completedProfit;
	private long inProgressProfit;
	private long inProgressTax;
	private int openOfferCount;
}
