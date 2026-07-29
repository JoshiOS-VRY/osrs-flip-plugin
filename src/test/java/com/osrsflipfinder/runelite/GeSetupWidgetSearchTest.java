package com.osrsflipfinder.runelite;

import net.runelite.api.widgets.Widget;
import org.junit.Test;

import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GeSetupWidgetSearchTest
{
	@Test
	public void findsActionOnNestedChild()
	{
		Widget plusOne = mock(Widget.class);
		when(plusOne.getActions()).thenReturn(new String[] { "+1" });
		when(plusOne.getChildren()).thenReturn(null);
		when(plusOne.getDynamicChildren()).thenReturn(null);

		Widget setup = mock(Widget.class);
		when(setup.getActions()).thenReturn(null);
		when(setup.getChildren()).thenReturn(new Widget[] { plusOne });
		when(setup.getDynamicChildren()).thenReturn(null);

		assertSame(plusOne, GeSetupWidgetSearch.findByAction(setup, "+1"));
	}
}
