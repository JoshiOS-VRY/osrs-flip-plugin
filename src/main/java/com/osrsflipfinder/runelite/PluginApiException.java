package com.osrsflipfinder.runelite;

import java.io.IOException;
import lombok.Getter;

/**
 * Raised by {@link PluginApiClient} when a market API request fails. Carries a
 * {@link PluginState} so the UI can react (e.g. prompt re-pair or upgrade).
 */
@Getter
public class PluginApiException extends IOException
{
	private final PluginState state;

	public PluginApiException(PluginState state, String message)
	{
		super(message);
		this.state = state;
	}
}
