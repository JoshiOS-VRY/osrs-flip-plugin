package com.osrsflipfinder.runelite;

import net.runelite.api.ScriptEvent;
import net.runelite.api.ScriptEventBuilder;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GeSetupScriptTailTest
{
	private static final int SETUP_PACKED = InterfaceID.GE_OFFERS << 16 | 0x1a;

	@Before
	public void clearTail()
	{
		GeSetupScriptTail.clear();
	}

	@Test
	public void capturesTailFromSetupBuild779()
	{
		Object[] args = new Object[16];
		args[0] = ScriptID.GE_OFFERS_SETUP_BUILD;
		args[1] = SETUP_PACKED;
		for (int i = 2; i < 16; i++)
		{
			args[i] = i;
		}

		ScriptEvent scriptEvent = mock(ScriptEvent.class);
		when(scriptEvent.getArguments()).thenReturn(args);

		ScriptPreFired fired = new ScriptPreFired(ScriptID.GE_OFFERS_SETUP_BUILD);
		fired.setScriptEvent(scriptEvent);
		GeSetupScriptTail.tryCapture(fired);

		assertTrue(GeSetupScriptTail.hasTail());
		Object[] built = GeSetupScriptTail.buildChangeQuantity(5);
		assertNotNull(built);
		assertArrayEquals(new Object[] {777, 5, 1, SETUP_PACKED, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15}, built);
	}

	@Test
	public void bootstrapsTailFromPlusOneOpListener()
	{
		int setupPacked = InterfaceID.GeOffers.SETUP;
		Object[] op = new Object[3 + GeSetupScriptTail.TAIL_LENGTH];
		op[0] = GeSetupScriptTail.CS_CHANGE_QUANTITY;
		op[1] = 1;
		op[2] = 1;
		op[3] = setupPacked;
		for (int i = 4; i < op.length; i++)
		{
			op[i] = i;
		}

		Widget plusOne = mock(Widget.class);
		when(plusOne.getOnOpListener()).thenReturn(op);
		when(plusOne.getActions()).thenReturn(new String[] { "+1" });

		Widget setup = mock(Widget.class);
		when(setup.getChildren()).thenReturn(new Widget[] { plusOne });
		when(setup.getDynamicChildren()).thenReturn(null);

		assertTrue(GeSetupScriptTail.tryBootstrapFromSetup(setup));
		assertNotNull(GeSetupScriptTail.buildChangeQuantity(-3));
	}

	@Test
	public void runChangeQuantityUsesScriptEventBuilder()
	{
		capturesTailFromSetupBuild779();

		net.runelite.api.Client client = mock(net.runelite.api.Client.class);
		ScriptEventBuilder builder = mock(ScriptEventBuilder.class);
		ScriptEvent event = mock(ScriptEvent.class);
		when(client.createScriptEventBuilder(any())).thenReturn(builder);
		when(builder.setOp(1)).thenReturn(builder);
		when(builder.setSource(any())).thenReturn(builder);
		when(builder.build()).thenReturn(event);
		when(client.getWidget(InterfaceID.GeOffers.SETUP)).thenReturn(mock(Widget.class));

		GeSetupScriptTail.runChangeQuantity(client, 3);

		verify(builder).setOp(eq(1));
		verify(event).run();
	}
}
