package com.osrsflipfinder.runelite;

import java.util.List;
import lombok.Data;

@Data
public class RecipesResponse
{
	private List<RecipeOpportunity> recipes;
	private RecipesMeta meta;

	@Data
	public static class RecipesMeta
	{
		private String refreshedAt;
	}
}
