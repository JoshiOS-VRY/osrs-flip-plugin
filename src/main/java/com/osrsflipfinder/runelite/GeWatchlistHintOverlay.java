package com.osrsflipfinder.runelite;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * When the user types {@code 1} in GE search, show watchlist items as a
 * display-only overlay. Does not modify GE search input or results.
 */
@Slf4j
public class GeWatchlistHintOverlay extends Overlay
{
	private final Client client;
	private final FlipFinderConfig config;
	private final WatchlistClient watchlistClient;

	private volatile boolean showHint;

	@Inject
	GeWatchlistHintOverlay(Client client, FlipFinderConfig config, WatchlistClient watchlistClient)
	{
		this.client = client;
		this.config = config;
		this.watchlistClient = watchlistClient;
		setPosition(OverlayPosition.TOP_CENTER);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	void setShowHint(boolean showHint)
	{
		this.showHint = showHint;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.enableWatchlistGeHint() || !showHint)
		{
			return null;
		}

		List<WatchlistResponse.WatchlistItem> items = watchlistClient.getCachedItems();
		if (items == null || items.isEmpty())
		{
			return null;
		}

		int x = 20;
		int y = 80;
		graphics.setColor(PluginUi.GOLD);
		graphics.drawString("Watchlist favorites", x, y);
		y += 14;
		graphics.setColor(Color.WHITE);
		int shown = 0;
		for (WatchlistResponse.WatchlistItem item : items)
		{
			if (shown >= 8)
			{
				break;
			}
			graphics.drawString("• " + item.getItemName(), x, y);
			y += 12;
			shown++;
		}

		return new Dimension(220, y);
	}
}
