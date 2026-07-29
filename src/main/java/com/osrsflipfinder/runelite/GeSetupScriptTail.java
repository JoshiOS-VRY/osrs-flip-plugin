package com.osrsflipfinder.runelite;

import java.util.Arrays;
import net.runelite.api.Client;
import net.runelite.api.ScriptEventBuilder;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

/**
 * Captures the 15 argument tail from {@code ge_offers_setup_draw} (776/779) or
 * {@code ge_offers_setup_changequantity} (777). Without this tail, {@code runScript}
 * cannot update GE varbits 4396/4398.
 */
final class GeSetupScriptTail
{
	static final int CS_SETUP_DRAW = 776;
	static final int CS_CHANGE_QUANTITY = 777;
	static final int CS_CHANGE_PRICE = 778;

	static final int TAIL_LENGTH = 15;
	static final int OP_INDEX = 1;

	private static volatile Object[] tail;

	private GeSetupScriptTail()
	{
	}

	static void clear()
	{
		tail = null;
	}

	static boolean hasTail()
	{
		return tail != null;
	}

	static void tryCapture(ScriptPreFired event)
	{
		if (event.getScriptEvent() == null)
		{
			return;
		}
		assignTailFromLiveArgs(event.getScriptEvent().getArguments(), event.getScriptId());
	}

	/**
	 * Reads the native GE +/- button {@link Widget#getOnOpListener()} (or setup draw listeners)
	 * so we can invoke script 777 without clicking +1 (which would only add 1 to the varbit).
	 */
	static boolean tryBootstrapFromSetup(Widget setup)
	{
		if (hasTail() || setup == null)
		{
			return hasTail();
		}

		if (tryBootstrapFromDrawListener(setup.getOnVarTransmitListener()))
		{
			return true;
		}
		if (tryBootstrapFromDrawListener(setup.getOnLoadListener()))
		{
			return true;
		}

		for (String action : new String[] { "+1", "+10", "+100", "+1K", "-1" })
		{
			Widget button = findChildByAction(setup, action);
			if (button != null && tryBootstrapFromQuantityOpListener(button))
			{
				return true;
			}
		}

		for (String action : new String[] { "+5%", "-5%" })
		{
			Widget button = findChildByAction(setup, action);
			if (button != null && tryBootstrapFromPriceOpListener(button))
			{
				return true;
			}
		}

		return false;
	}

	static boolean ensureForSetup(Widget setup)
	{
		if (tryBootstrapFromSetup(setup))
		{
			return true;
		}
		return bootstrapFromWidgetTree(setup);
	}

	private static boolean bootstrapFromWidgetTree(Widget root)
	{
		if (root == null)
		{
			return false;
		}
		return bootstrapFromWidgetTreeRecursive(root, 0);
	}

	private static boolean bootstrapFromWidgetTreeRecursive(Widget widget, int depth)
	{
		if (widget == null || depth > 10)
		{
			return false;
		}
		if (tryBootstrapFromQuantityOpListener(widget)
			|| tryBootstrapFromPriceOpListener(widget))
		{
			return true;
		}
		Object[] draw = widget.getOnVarTransmitListener();
		if (tryBootstrapFromDrawListener(draw))
		{
			return true;
		}
		draw = widget.getOnLoadListener();
		if (tryBootstrapFromDrawListener(draw))
		{
			return true;
		}
		Widget[] staticChildren = widget.getChildren();
		if (staticChildren != null)
		{
			for (Widget child : staticChildren)
			{
				if (bootstrapFromWidgetTreeRecursive(child, depth + 1))
				{
					return true;
				}
			}
		}
		Widget[] dynamicChildren = widget.getDynamicChildren();
		if (dynamicChildren != null)
		{
			for (Widget child : dynamicChildren)
			{
				if (bootstrapFromWidgetTreeRecursive(child, depth + 1))
				{
					return true;
				}
			}
		}
		return false;
	}

	private static boolean tryBootstrapFromQuantityOpListener(Widget widget)
	{
		Object[] op = widget.getOnOpListener();
		if (op == null || op.length < 3 + TAIL_LENGTH)
		{
			return false;
		}
		if (intArg(op[0]) != CS_CHANGE_QUANTITY)
		{
			return false;
		}
		return storeTailFromIndex(op, 3);
	}

	private static boolean tryBootstrapFromPriceOpListener(Widget widget)
	{
		Object[] op = widget.getOnOpListener();
		if (op == null || op.length < 3 + TAIL_LENGTH)
		{
			return false;
		}
		if (intArg(op[0]) != CS_CHANGE_PRICE)
		{
			return false;
		}
		return storeTailFromIndex(op, 3);
	}

	private static boolean tryBootstrapFromDrawListener(Object[] args)
	{
		if (args == null || args.length < 1 + TAIL_LENGTH)
		{
			return false;
		}
		int scriptId = intArg(args[0]);
		if (scriptId != CS_SETUP_DRAW && scriptId != ScriptID.GE_OFFERS_SETUP_BUILD)
		{
			return false;
		}
		return storeTailFromIndex(args, 1);
	}

	private static void assignTailFromLiveArgs(Object[] args, int scriptId)
	{
		if (args == null)
		{
			return;
		}

		if (scriptId == CS_CHANGE_QUANTITY && args.length >= 3 + TAIL_LENGTH)
		{
			storeTailFromIndex(args, 3);
			return;
		}

		if (scriptId == CS_CHANGE_PRICE && args.length >= 3 + TAIL_LENGTH)
		{
			storeTailFromIndex(args, 3);
			return;
		}

		if ((scriptId == CS_SETUP_DRAW || scriptId == ScriptID.GE_OFFERS_SETUP_BUILD)
			&& args.length >= 1 + TAIL_LENGTH)
		{
			storeTailFromIndex(args, 1);
		}
	}

	private static boolean storeTailFromIndex(Object[] args, int tailStart)
	{
		if (args.length < tailStart + TAIL_LENGTH || !isGeOffersComponent(args[tailStart]))
		{
			return false;
		}
		tail = Arrays.copyOfRange(args, tailStart, tailStart + TAIL_LENGTH);
		return true;
	}

	private static int intArg(Object value)
	{
		return value instanceof Number ? ((Number) value).intValue() : -1;
	}

	static Widget findChildByAction(Widget setup, String action)
	{
		return GeSetupWidgetSearch.findByAction(setup, action);
	}

	static void runChangeQuantity(Client client, int delta)
	{
		Object[] invoke = buildChangeQuantity(delta);
		if (invoke == null)
		{
			return;
		}
		Widget setup = client.getWidget(InterfaceID.GeOffers.SETUP);
		ScriptEventBuilder builder = client.createScriptEventBuilder(invoke).setOp(OP_INDEX);
		if (setup != null)
		{
			builder.setSource(setup);
		}
		builder.build().run();
	}

	static void runSetupDraw(Client client)
	{
		Object[] invoke = buildSetupDraw();
		if (invoke == null)
		{
			return;
		}
		Widget setup = client.getWidget(InterfaceID.GeOffers.SETUP);
		ScriptEventBuilder builder = client.createScriptEventBuilder(invoke);
		if (setup != null)
		{
			builder.setSource(setup);
		}
		builder.build().run();
	}

	static void runChangePrice(Client client, int delta)
	{
		Object[] invoke = buildChangePrice(delta);
		if (invoke == null)
		{
			return;
		}
		Widget setup = client.getWidget(InterfaceID.GeOffers.SETUP);
		ScriptEventBuilder builder = client.createScriptEventBuilder(invoke).setOp(OP_INDEX);
		if (setup != null)
		{
			builder.setSource(setup);
		}
		builder.build().run();
	}

	static Object[] buildChangePrice(int delta)
	{
		Object[] t = tail;
		if (t == null)
		{
			return null;
		}
		Object[] script = new Object[3 + TAIL_LENGTH];
		script[0] = CS_CHANGE_PRICE;
		script[1] = delta;
		script[2] = OP_INDEX;
		System.arraycopy(t, 0, script, 3, TAIL_LENGTH);
		return script;
	}

	static Object[] buildChangeQuantity(int delta)
	{
		Object[] t = tail;
		if (t == null)
		{
			return null;
		}
		Object[] script = new Object[3 + TAIL_LENGTH];
		script[0] = CS_CHANGE_QUANTITY;
		script[1] = delta;
		script[2] = OP_INDEX;
		System.arraycopy(t, 0, script, 3, TAIL_LENGTH);
		return script;
	}

	static Object[] buildSetupDraw()
	{
		Object[] t = tail;
		if (t == null)
		{
			return null;
		}
		Object[] script = new Object[1 + TAIL_LENGTH];
		script[0] = CS_SETUP_DRAW;
		System.arraycopy(t, 0, script, 1, TAIL_LENGTH);
		return script;
	}

	static boolean isGeOffersComponent(Object packed)
	{
		if (!(packed instanceof Number))
		{
			return false;
		}
		int id = ((Number) packed).intValue();
		return (id >>> 16) == InterfaceID.GE_OFFERS;
	}
}
