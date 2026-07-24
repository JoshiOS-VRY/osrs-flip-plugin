package com.osrsflipfinder.runelite;

import lombok.Data;

@Data
public class RecipeOpportunity
{
	private String id;
	private String name;
	private int outputItemId;
	private int outputQty;
	private String skill;
	private long inputCost;
	private long outputValue;
	private long netProfit;
	private double roiPercent;
}
