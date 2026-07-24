package com.osrsflipfinder.runelite;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * Display-only Grand Exchange copilot: when an offer is being set up, shows the
 * current item's live score and estimated flip economics. Read-only — never
 * injects prices, fills fields, or simulates clicks (Jagex/Hub compliance).
 */
@Slf4j
public class MarketCopilotOverlay extends OverlayPanel
{
	private final Client client;
	private final FlipFinderConfig config;
	private final CopilotClient copilotClient;
	private final ItemsClient itemsClient;
	private final OpportunitiesClient opportunitiesClient;
	private final ScheduledExecutorService executorService;

	private final AtomicBoolean fetchInProgress = new AtomicBoolean(false);
	private volatile int pendingItemId = -1;
	private volatile boolean ultraBlocked = false;

	@Inject
	MarketCopilotOverlay(
		Client client,
		FlipFinderConfig config,
		CopilotClient copilotClient,
		ItemsClient itemsClient,
		OpportunitiesClient opportunitiesClient,
		ScheduledExecutorService executorService
	)
	{
		this.client = client;
		this.config = config;
		this.copilotClient = copilotClient;
		this.itemsClient = itemsClient;
		this.opportunitiesClient = opportunitiesClient;
		this.executorService = executorService;
		setPosition(OverlayPosition.TOP_CENTER);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.enableGeOverlay() || config.apiKey().isBlank() || ultraBlocked)
		{
			return null;
		}

		PluginEntitlements entitlements = opportunitiesClient.getEntitlements();
		if (entitlements != null && !entitlements.isCopilotApi())
		{
			return null;
		}

		int itemId = GeItemResolver.resolve(client);
		if (itemId <= 0)
		{
			return null;
		}

		panelComponent.getChildren().add(TitleComponent.builder()
			.text("Flip copilot")
			.color(ColorScheme.BRAND_ORANGE)
			.build());

		if (itemsClient.isStale(itemId))
		{
			maybeFetch(itemId);
		}

		CopilotItem item = copilotClient.peek(itemId);
		if (item == null)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Loading…")
				.build());
			return super.render(graphics);
		}

		addLine("Buy", MarketFormat.gpExact(item.getEstimatedBuyPrice()), ColorScheme.GRAND_EXCHANGE_PRICE);
		addLine("Sell", MarketFormat.gpExact(item.getEstimatedSellPrice()), ColorScheme.GRAND_EXCHANGE_ALCH);
		if (item.getInstantBuyPrice() != null)
		{
			addLine("Insta buy", MarketFormat.gpExact(item.getInstantBuyPrice()), ColorScheme.GRAND_EXCHANGE_PRICE);
		}
		if (item.getInstantSellPrice() != null)
		{
			addLine("Insta sell", MarketFormat.gpExact(item.getInstantSellPrice()), ColorScheme.GRAND_EXCHANGE_ALCH);
		}
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Net")
			.right(MarketFormat.signedGp(item.getNetProfitPerItem()))
			.rightColor(item.getNetProfitPerItem() >= 0 ? PluginUi.POSITIVE : PluginUi.NEGATIVE)
			.build());
		addLine("ROI", MarketFormat.percent(item.getNetRoiPercent()), ColorScheme.GRAND_EXCHANGE_ALCH);
		addLine("Score", item.getOpportunityScore() + "/100 · " + Math.round(item.getConfidenceScore() * 100) + "%", PluginUi.GOLD);
		if (item.getRepriceHint() != null && !item.getRepriceHint().isBlank())
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Hint")
				.right(item.getRepriceHint())
				.rightColor(PluginUi.WARNING)
				.build());
		}

		return super.render(graphics);
	}

	private void addLine(String left, String right)
	{
		addLine(left, right, Color.WHITE);
	}

	private void addLine(String left, String right, Color rightColor)
	{
		panelComponent.getChildren().add(LineComponent.builder()
			.left(left)
			.right(right)
			.rightColor(rightColor)
			.build());
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
				copilotClient.fetch(itemId);
			}
			catch (PluginApiException e)
			{
				if (e.getState() == PluginState.UPGRADE_REQUIRED)
				{
					ultraBlocked = true;
				}
				log.debug("Copilot fetch failed", e);
			}
			catch (IOException e)
			{
				log.debug("Copilot fetch failed", e);
			}
			finally
			{
				pendingItemId = -1;
				fetchInProgress.set(false);
			}
		});
	}
}
