package com.osrsflipfinder.runelite;

import net.runelite.api.Client;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class GeScriptRunnerTest
{
	@Test
	public void invokePassesScriptArrayToRunScript()
	{
		Client client = mock(Client.class);
		Object[] args = new Object[] { 776, 465 << 16, 11, 17 };
		GeScriptRunner.invoke(client, args);
		verify(client).runScript(args);
	}
}
