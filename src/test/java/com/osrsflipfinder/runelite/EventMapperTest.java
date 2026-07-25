package com.osrsflipfinder.runelite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.time.Instant;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class EventMapperTest
{
	@Test
	public void mapsBuyingOfferToBuySide()
	{
		GrandExchangeOffer offer = mockOffer(
			GrandExchangeOfferState.BUYING,
			4151,
			1_000_000,
			1,
			0
		);

		IngestGeEvent event = EventMapper.mapOffer(
			offer,
			2,
			12345L,
			"TestPlayer",
			"Abyssal whip",
			Instant.parse("2026-01-15T12:00:00Z")
		);

		assertEquals("buying", event.getState());
		assertEquals("buy", event.getSide());
		assertEquals("plugin", event.getSource());
		assertEquals(Integer.valueOf(2), event.getSlot());
		assertEquals("12345", event.getAccountHash());
	}

	@Test
	public void mapsSoldOfferToSellSide()
	{
		GrandExchangeOffer offer = mockOffer(
			GrandExchangeOfferState.SOLD,
			995,
			100,
			1000,
			1000
		);

		IngestGeEvent event = EventMapper.mapOffer(
			offer,
			0,
			99L,
			null,
			"Coins",
			Instant.parse("2026-01-15T12:00:00Z")
		);

		assertEquals("sold", event.getState());
		assertEquals("sell", event.getSide());
	}

	@Test
	public void skipsEmptyOfferState()
	{
		GrandExchangeOffer offer = mockOffer(
			GrandExchangeOfferState.EMPTY,
			0,
			0,
			0,
			0
		);

		assertNull(EventMapper.mapOffer(
			offer,
			0,
			1L,
			null,
			null,
			Instant.parse("2026-01-15T12:00:00Z")
		));
	}

	@Test
	public void mapsAllTrackedStates()
	{
		assertEquals("buying", EventMapper.mapState(GrandExchangeOfferState.BUYING));
		assertEquals("selling", EventMapper.mapState(GrandExchangeOfferState.SELLING));
		assertEquals("cancelled_buy", EventMapper.mapState(GrandExchangeOfferState.CANCELLED_BUY));
		assertEquals("cancelled_sell", EventMapper.mapState(GrandExchangeOfferState.CANCELLED_SELL));
		assertEquals("bought", EventMapper.mapState(GrandExchangeOfferState.BOUGHT));
		assertEquals("sold", EventMapper.mapState(GrandExchangeOfferState.SOLD));
	}

	@Test
	public void mapsSlotClearedFromActiveBuyOfferWithFillToBought()
	{
		OfferSnapshot previous = OfferSnapshot.from(mockOffer(
			GrandExchangeOfferState.BUYING,
			33639,
			136_505_000,
			2,
			2
		));

		IngestGeEvent event = EventMapper.mapSlotCleared(
			previous,
			0,
			12345L,
			"TestPlayer",
			"Necklace of rupture",
			Instant.parse("2026-01-15T12:01:00Z")
		);

		assertEquals("bought", event.getState());
		assertEquals("buy", event.getSide());
		assertEquals(2, event.getQuantity());
		assertEquals(2, event.getQuantityFilled());
	}

	@Test
	public void mapsSlotClearedFromActiveBuyOffer()
	{
		OfferSnapshot previous = OfferSnapshot.from(mockOffer(
			GrandExchangeOfferState.BUYING,
			11840,
			18_215_000,
			5,
			0
		));

		IngestGeEvent event = EventMapper.mapSlotCleared(
			previous,
			1,
			12345L,
			"TestPlayer",
			"Primordial boots",
			Instant.parse("2026-01-15T12:01:00Z")
		);

		assertEquals("cancelled_buy", event.getState());
		assertEquals("buy", event.getSide());
		assertEquals(Integer.valueOf(1), event.getSlot());
		assertEquals(11840, event.getItemId());
	}

	@Test
	public void skipsSlotClearedWhenPreviousOfferAlreadyTerminal()
	{
		OfferSnapshot previous = OfferSnapshot.from(mockOffer(
			GrandExchangeOfferState.BOUGHT,
			11840,
			18_215_000,
			5,
			5
		));

		assertNull(EventMapper.mapSlotCleared(
			previous,
			1,
			12345L,
			null,
			null,
			Instant.parse("2026-01-15T12:01:00Z")
		));
	}

	private static GrandExchangeOffer mockOffer(
		GrandExchangeOfferState state,
		int itemId,
		int price,
		int totalQuantity,
		int quantitySold
	)
	{
		GrandExchangeOffer offer = mock(GrandExchangeOffer.class);
		when(offer.getState()).thenReturn(state);
		when(offer.getItemId()).thenReturn(itemId);
		when(offer.getPrice()).thenReturn(price);
		when(offer.getTotalQuantity()).thenReturn(totalQuantity);
		when(offer.getQuantitySold()).thenReturn(quantitySold);
		return offer;
	}
}
