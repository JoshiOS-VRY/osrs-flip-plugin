package com.osrsflipfinder.runelite;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** Mini buy/sell sparkline on the GE offer setup screen. */
@Slf4j
public class GeChartOverlay extends Overlay
{
	private static final int MAX_POINTS = 24;

	private final Client client;
	private final FlipFinderConfig config;
	private final ItemsClient itemsClient;
	private final OpportunitiesClient opportunitiesClient;
	private final ScheduledExecutorService executorService;

	private final AtomicBoolean fetchInProgress = new AtomicBoolean(false);
	private volatile int pendingItemId = -1;

	@Inject
	GeChartOverlay(
		Client client,
		FlipFinderConfig config,
		ItemsClient itemsClient,
		OpportunitiesClient opportunitiesClient,
		ScheduledExecutorService executorService
	)
	{
		this.client = client;
		this.config = config;
		this.itemsClient = itemsClient;
		this.opportunitiesClient = opportunitiesClient;
		this.executorService = executorService;
		setPosition(OverlayPosition.TOP_RIGHT);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.enableGeChartOverlay() || config.apiKey().isBlank())
		{
			return null;
		}

		PluginEntitlements entitlements = opportunitiesClient.getEntitlements();
		if (entitlements != null && entitlements.getItemChartDays() <= 0)
		{
			return null;
		}

		int itemId = GeItemResolver.resolve(client);
		if (itemId <= 0)
		{
			return null;
		}

		ItemDetailResponse cachedDetail = itemsClient.peek(itemId);
		if (cachedDetail == null || cachedDetail.getOpportunity() == null
			|| cachedDetail.getOpportunity().getId() != itemId || itemsClient.isStale(itemId))
		{
			maybeFetch(itemId);
			graphics.setColor(Color.WHITE);
			graphics.drawString("Loading chart...", 10, 20);
			return new Dimension(140, 60);
		}

		List<ItemDetailResponse.PriceSnapshot> snapshots = cachedDetail.getSnapshots();
		if (snapshots == null || snapshots.isEmpty())
		{
			return null;
		}

		int width = 140;
		int height = 50;
		int x0 = 10;
		int y0 = 10;

		drawSparkline(graphics, snapshots, x0, y0, width, height, true);
		drawSparkline(graphics, snapshots, x0, y0, width, height, false);

		graphics.setColor(Color.WHITE);
		graphics.drawString("Buy / Sell", x0, y0 + height + 12);

		return new Dimension(width + 20, height + 24);
	}

	private void drawSparkline(
		Graphics2D graphics,
		List<ItemDetailResponse.PriceSnapshot> snapshots,
		int x,
		int y,
		int width,
		int height,
		boolean buy
	)
	{
		int step = Math.max(1, snapshots.size() / MAX_POINTS);
		long min = Long.MAX_VALUE;
		long max = Long.MIN_VALUE;
		int count = 0;
		for (int i = 0; i < snapshots.size(); i += step)
		{
			long value = buy ? snapshots.get(i).getEstimatedBuy() : snapshots.get(i).getEstimatedSell();
			min = Math.min(min, value);
			max = Math.max(max, value);
			count++;
		}
		if (count < 2 || max <= min)
		{
			return;
		}

		graphics.setColor(buy ? PluginUi.GOLD : Color.CYAN);
		int idx = 0;
		int prevX = x;
		int prevY = y + height;
		for (int i = 0; i < snapshots.size(); i += step)
		{
			long value = buy ? snapshots.get(i).getEstimatedBuy() : snapshots.get(i).getEstimatedSell();
			int px = x + (idx * width) / Math.max(1, count - 1);
			int py = y + height - (int) ((value - min) * height / (max - min));
			if (idx > 0)
			{
				graphics.drawLine(prevX, prevY, px, py);
			}
			prevX = px;
			prevY = py;
			idx++;
		}
	}

	private void maybeFetch(int itemId)
	{
		if (pendingItemId == itemId || !fetchInProgress.compareAndSet(false, true))
		{
			return;
		}
		pendingItemId = itemId;
		executorService.execute(() ->
		{
			try
			{
				itemsClient.fetch(itemId);
			}
			catch (IOException e)
			{
				log.debug("Chart fetch failed", e);
			}
			finally
			{
				pendingItemId = -1;
				fetchInProgress.set(false);
			}
		});
	}
}
