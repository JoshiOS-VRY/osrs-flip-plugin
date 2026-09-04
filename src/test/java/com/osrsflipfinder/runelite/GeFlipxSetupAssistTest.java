package com.osrsflipfinder.runelite;

import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GeFlipxSetupAssistTest
{
	@Test
	public void findQuantityPluginAnchor_matchesPlus1kTextOverlay()
	{
		Widget setup = mock(Widget.class);
		Widget anchorGraphic = mock(Widget.class);
		Widget label = mock(Widget.class);

		when(anchorGraphic.getType()).thenReturn(WidgetType.GRAPHIC);
		when(anchorGraphic.getOriginalX()).thenReturn(151);
		when(anchorGraphic.getOriginalY()).thenReturn(136);

		when(label.getType()).thenReturn(WidgetType.TEXT);
		when(label.getText()).thenReturn("+1K");
		when(label.getOriginalX()).thenReturn(151);
		when(label.getOriginalY()).thenReturn(136);

		when(setup.getDynamicChildren()).thenReturn(new Widget[] { anchorGraphic, label });

		assertSame(anchorGraphic, GeFlipxSetupAssist.findQuantityPluginAnchor(setup));
	}

	@Test
	public void findGuidePriceAnchor_matchesGuidePriceOp()
	{
		Widget setup = mock(Widget.class);
		Widget guide = mock(Widget.class);
		when(guide.getType()).thenReturn(WidgetType.GRAPHIC);
		when(guide.getActions()).thenReturn(new String[] { "Guide price" });
		when(setup.getDynamicChildren()).thenReturn(new Widget[] { guide });
		assertSame(guide, GeFlipxSetupAssist.findGuidePriceAnchor(setup));
	}

	@Test
	public void findQuantityPluginAnchor_returnsNullWhenNoPlus1k()
	{
		Widget setup = mock(Widget.class);
		when(setup.getDynamicChildren()).thenReturn(new Widget[0]);
		assertNull(GeFlipxSetupAssist.findQuantityPluginAnchor(setup));
		assertNull(GeFlipxSetupAssist.findQuantityPluginAnchor(null));
	}

	@Test
	public void isLiveButton_requiresVisibleChildOfSetup()
	{
		Widget setup = mock(Widget.class);
		Widget live = mock(Widget.class);
		when(live.isHidden()).thenReturn(false);
		when(live.getParent()).thenReturn(setup);
		assertTrue(GeFlipxSetupAssist.isLiveButton(live, setup));

		Widget stale = mock(Widget.class);
		when(stale.isHidden()).thenReturn(false);
		when(stale.getParent()).thenReturn(mock(Widget.class));
		assertFalse(GeFlipxSetupAssist.isLiveButton(stale, setup));
		assertFalse(GeFlipxSetupAssist.isLiveButton(null, setup));
	}

	@Test
	public void stealNativeSlotClearsPlus1kOp()
	{
		Widget plus1k = mock(Widget.class);
		when(plus1k.getActions()).thenReturn(new String[] { "+1K" });

		GeFlipxSetupAssist.stealNativeSlot(plus1k);

		verify(plus1k).setHasListener(false);
		verify(plus1k).setAction(0, null);
		verify(plus1k).setHidden(true);
	}

	@Test
	public void nativeButtonMetrics_copiesPlusOneSize()
	{
		Widget setup = mock(Widget.class);
		Widget plusOne = mock(Widget.class);
		Widget plus1k = mock(Widget.class);

		when(plusOne.getActions()).thenReturn(new String[] { "+1" });
		when(plusOne.getOriginalY()).thenReturn(136);
		when(plusOne.getWidth()).thenReturn(30);
		when(plusOne.getHeight()).thenReturn(18);
		when(plusOne.getOriginalWidth()).thenReturn(25);
		when(plusOne.getOriginalHeight()).thenReturn(35);
		when(plusOne.getChildren()).thenReturn(null);
		when(plusOne.getDynamicChildren()).thenReturn(null);

		when(plus1k.getOriginalX()).thenReturn(151);
		when(plus1k.getOriginalY()).thenReturn(120);
		when(plus1k.getWidth()).thenReturn(80);
		when(plus1k.getOriginalWidth()).thenReturn(80);
		when(plus1k.getOriginalHeight()).thenReturn(40);

		when(setup.getActions()).thenReturn(null);
		when(setup.getChildren()).thenReturn(new Widget[] { plusOne });
		when(setup.getDynamicChildren()).thenReturn(null);

		GeFlipxSetupAssist.ButtonMetrics metrics =
			GeFlipxSetupAssist.nativeButtonMetrics(setup, plus1k, true);

		assertEquals(30, metrics.w);
		assertEquals(18, metrics.h);
		assertEquals(151 + (80 - 30) / 2, metrics.x);
		assertEquals(136, metrics.y);
	}
}
