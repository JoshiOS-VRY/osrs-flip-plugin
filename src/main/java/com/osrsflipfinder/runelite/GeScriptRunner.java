package com.osrsflipfinder.runelite;

import net.runelite.api.Client;

/** Invokes {@link Client#runScript(Object...)} with a stored argument array. */
final class GeScriptRunner
{
	private GeScriptRunner()
	{
	}

	static void invoke(Client client, Object[] args)
	{
		if (args == null || args.length == 0)
		{
			return;
		}
		client.runScript(args);
	}
}
