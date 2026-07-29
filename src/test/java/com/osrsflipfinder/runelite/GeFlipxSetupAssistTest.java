package com.osrsflipfinder.runelite;

import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import org.junit.Test;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
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
}
