package com.osrsflipfinder.runelite;

/** Maps unified item detail into the copilot overlay shape. */
final class ItemCopilotMapper
{
	private ItemCopilotMapper()
	{
	}

	static CopilotItem from(FlipOpportunity opp, long updatedAtMs)
	{
		CopilotItem item = new CopilotItem();
		item.setItemId(opp.getId());
		item.setName(opp.getName());
		item.setEstimatedBuyPrice(opp.getEstimatedBuyPrice());
		item.setEstimatedSellPrice(opp.getEstimatedSellPrice());
		item.setOpportunityScore(opp.getOpportunityScore());
		item.setConfidenceScore(opp.getConfidenceScore());
		item.setNetProfitPerItem(opp.getNetProfitPerItem());
		item.setNetRoiPercent(opp.getNetRoiPercent());
		item.setReferenceTradingPrice(
			opp.getReferenceTradingPrice() != null ? opp.getReferenceTradingPrice().longValue() : 0L
		);
		item.setGeGuidePrice(opp.getGeGuidePrice());
		item.setUpdatedAt(updatedAtMs);
		item.setInstantBuyPrice(opp.getEstimatedBuyPrice());
		item.setInstantSellPrice(opp.getEstimatedSellPrice());
		item.setRepriceHint(null);
		item.setPriceIssue(null);
		item.setDeltaPercent(null);
		return item;
	}

	static CopilotItem from(ItemDetailResponse detail)
	{
		if (detail == null || detail.getOpportunity() == null)
		{
			return null;
		}
		long updatedAt = detail.getMeta() != null ? detail.getMeta().getLastUpdatedMs() : 0L;
		return from(detail.getOpportunity(), updatedAt);
	}
}
