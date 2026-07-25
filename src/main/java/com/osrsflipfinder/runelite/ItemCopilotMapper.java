package com.osrsflipfinder.runelite;

/** Maps unified item detail into the copilot overlay shape. */
final class ItemCopilotMapper
{
	private ItemCopilotMapper()
	{
	}

	static CopilotItem from(FlipOpportunity opp, long updatedAtMs)
	{
		return from(opp, updatedAtMs, null);
	}

	static CopilotItem from(
		FlipOpportunity opp,
		long updatedAtMs,
		ItemDetailResponse.MarketRegimeSummary regime)
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
		item.setInstantBuyPrice(opp.getEstimatedSellPrice());
		item.setInstantSellPrice(opp.getEstimatedBuyPrice());
		item.setRepriceHint(FlipCopilotPresenter.repriceHint(opp));
		item.setPriceIssue(null);
		item.setDeltaPercent(null);
		item.setVerdict(FlipCopilotPresenter.verdict(opp).name().toLowerCase());
		item.setActionSummary(FlipCopilotPresenter.actionSummary(opp));
		if (regime != null)
		{
			item.setRiskRegimeState(regime.getState());
			item.setRiskWarning(regime.getWarning());
		}
		return item;
	}

	static CopilotItem from(ItemDetailResponse detail)
	{
		if (detail == null || detail.getOpportunity() == null)
		{
			return null;
		}
		long updatedAt = detail.getMeta() != null ? detail.getMeta().getLastUpdatedMs() : 0L;
		return from(detail.getOpportunity(), updatedAt, detail.getMarketRegime());
	}
}
