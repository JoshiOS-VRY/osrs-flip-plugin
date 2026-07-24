package com.osrsflipfinder.runelite;

import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.client.eventbus.Subscribe;

/**
 * Detects GE search text equal to {@code 1} and toggles the watchlist hint overlay.
 */
@Slf4j
@Singleton
public class GeInterfaceListener
{
	private final GeWatchlistHintOverlay watchlistHintOverlay;

	@Inject
	GeInterfaceListener(GeWatchlistHintOverlay watchlistHintOverlay)
	{
		this.watchlistHintOverlay = watchlistHintOverlay;
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		// GE interface loads frequently; hint visibility is driven by periodic checks from GeSetupPanel.
	}

	void updateSearchHint(String searchText)
	{
		watchlistHintOverlay.setShowHint("1".equals(searchText != null ? searchText.trim() : ""));
	}
}
