package com.osrsflipfinder.runelite;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class GeAssistPricingTest
{
	@Test
	public void ignoresNetworkIntelUsesWikiPrices()
	{
		FlipOpportunity opp = new FlipOpportunity();
		opp.setEstimatedBuyPrice(100);
		opp.setEstimatedSellPrice(120);

		NetworkIntelResponse.NetworkPriceHints hints = new NetworkIntelResponse.NetworkPriceHints();
		hints.setMedianBuy(105L);
		hints.setBuySamples(12);
		hints.setMedianSell(125L);
		hints.setSellSamples(8);

		NetworkIntelResponse intel = new NetworkIntelResponse();
		intel.setNetworkPrices(hints);

		ItemDetailResponse detail = new ItemDetailResponse();
		detail.setOpportunity(opp);
		detail.setNetworkIntel(intel);

		PluginEntitlements entitlements = new PluginEntitlements();
		entitlements.setPaid(true);

		GeAssistPricing.ResolvedPrice buy = GeAssistPricing.resolve(detail, true, entitlements);
		assertNotNull(buy);
		assertEquals(100L, buy.priceGp);
		assertEquals(GeAssistPricing.PriceSource.WIKI, buy.source);
	}

	@Test
	public void nonEliteUsesWikiPrices()
	{
		FlipOpportunity opp = new FlipOpportunity();
		opp.setEstimatedBuyPrice(100);
		opp.setEstimatedSellPrice(120);

		NetworkIntelResponse.NetworkPriceHints hints = new NetworkIntelResponse.NetworkPriceHints();
		hints.setMedianBuy(105L);
		hints.setBuySamples(12);

		NetworkIntelResponse intel = new NetworkIntelResponse();
		intel.setNetworkPrices(hints);

		ItemDetailResponse detail = new ItemDetailResponse();
		detail.setOpportunity(opp);
		detail.setNetworkIntel(intel);

		PluginEntitlements entitlements = new PluginEntitlements();

		GeAssistPricing.ResolvedPrice buy = GeAssistPricing.resolve(detail, true, entitlements);
		assertNotNull(buy);
		assertEquals(100L, buy.priceGp);
		assertEquals(GeAssistPricing.PriceSource.WIKI, buy.source);
	}

	@Test
	public void usesWikiWhenOnlyEstimatesPresent()
	{
		FlipOpportunity opp = new FlipOpportunity();
		opp.setEstimatedBuyPrice(100);

		ItemDetailResponse detail = new ItemDetailResponse();
		detail.setOpportunity(opp);

		GeAssistPricing.ResolvedPrice buy = GeAssistPricing.resolve(detail, true, null);
		assertNotNull(buy);
		assertEquals(100L, buy.priceGp);
		assertEquals(GeAssistPricing.PriceSource.WIKI, buy.source);
	}

	@Test
	public void geOfferPriceWikiBuyAddsOne()
	{
		GeAssistPricing.ResolvedPrice wiki = new GeAssistPricing.ResolvedPrice(
			100L,
			GeAssistPricing.PriceSource.WIKI
		);
		assertEquals(101L, GeAssistPricing.geOfferPriceGp(wiki, true));
	}

	@Test
	public void geOfferPriceWikiSellSubtractsOne()
	{
		GeAssistPricing.ResolvedPrice wiki = new GeAssistPricing.ResolvedPrice(
			120L,
			GeAssistPricing.PriceSource.WIKI
		);
		assertEquals(119L, GeAssistPricing.geOfferPriceGp(wiki, false));
	}

	@Test
	public void geOfferPriceWikiSellClampsToOne()
	{
		GeAssistPricing.ResolvedPrice wiki = new GeAssistPricing.ResolvedPrice(
			1L,
			GeAssistPricing.PriceSource.WIKI
		);
		assertEquals(1L, GeAssistPricing.geOfferPriceGp(wiki, false));
	}

	@Test
	public void geOfferPriceNetworkIsExact()
	{
		GeAssistPricing.ResolvedPrice network = new GeAssistPricing.ResolvedPrice(
			105L,
			GeAssistPricing.PriceSource.NETWORK
		);
		assertEquals(105L, GeAssistPricing.geOfferPriceGp(network, true));
		assertEquals(105L, GeAssistPricing.geOfferPriceGp(network, false));
	}

	@Test
	public void returnsNullWhenNoPrices()
	{
		FlipOpportunity opp = new FlipOpportunity();
		opp.setEstimatedBuyPrice(0);

		ItemDetailResponse detail = new ItemDetailResponse();
		detail.setOpportunity(opp);

		assertNull(GeAssistPricing.resolve(detail, true, null));
	}
}
