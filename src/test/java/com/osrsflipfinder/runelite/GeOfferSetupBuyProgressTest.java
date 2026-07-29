package com.osrsflipfinder.runelite;

import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GeOfferSetupBuyProgressTest
{
	@Test
	public void parsesBoughtSoFarFromSetupText()
	{
		Widget setup = mock(Widget.class);
		Widget line = mock(Widget.class);
		when(line.getType()).thenReturn(WidgetType.TEXT);
		when(line.getText()).thenReturn(
			"You have bought a total of 5 so far for a total price of 14,354,425 coins."
		);
		when(setup.getDynamicChildren()).thenReturn(new Widget[] { line });
		when(setup.getChildren()).thenReturn(null);

		assertEquals(5, GeOfferSetupBuyProgress.parseBoughtSoFar(setup));
	}

	@Test
	public void returnsNegativeWhenMissing()
	{
		Widget setup = mock(Widget.class);
		when(setup.getDynamicChildren()).thenReturn(null);
		when(setup.getChildren()).thenReturn(null);
		assertEquals(-1, GeOfferSetupBuyProgress.parseBoughtSoFar(setup));
	}
}
