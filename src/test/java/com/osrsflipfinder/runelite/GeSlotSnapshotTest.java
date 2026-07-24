package com.osrsflipfinder.runelite;

import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class GeSlotSnapshotTest
{
	@Test
	public void boughtAndSoldSlotsAreNotEmpty()
	{
		assertFalse(GeSlotSnapshot.from(0, offer(GrandExchangeOfferState.BOUGHT)).isEmpty());
		assertFalse(GeSlotSnapshot.from(1, offer(GrandExchangeOfferState.SOLD)).isEmpty());
	}

	@Test
	public void activeAndCancelledSlotsMatchOccupancy()
	{
		assertFalse(GeSlotSnapshot.from(0, offer(GrandExchangeOfferState.BUYING)).isEmpty());
		assertFalse(GeSlotSnapshot.from(1, offer(GrandExchangeOfferState.SELLING)).isEmpty());
		assertTrue(GeSlotSnapshot.from(2, offer(GrandExchangeOfferState.EMPTY)).isEmpty());
		assertTrue(GeSlotSnapshot.from(3, offer(GrandExchangeOfferState.CANCELLED_BUY)).isEmpty());
		assertTrue(GeSlotSnapshot.from(4, null).isEmpty());
	}

	private static GrandExchangeOffer offer(GrandExchangeOfferState state)
	{
		GrandExchangeOffer offer = mock(GrandExchangeOffer.class);
		when(offer.getState()).thenReturn(state);
		return offer;
	}
}
