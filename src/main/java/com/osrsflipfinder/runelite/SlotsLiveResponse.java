package com.osrsflipfinder.runelite;

import java.util.List;
import lombok.Data;

/** Response of {@code GET /api/plugin/slots/live}. */
@Data
public class SlotsLiveResponse
{
	private List<EnrichedOpenGeOffer> offers;
	private SlotsLiveMeta meta;

	@Data
	public static class SlotsLiveMeta
	{
		private String refreshedAt;
		private int count;
	}
}
