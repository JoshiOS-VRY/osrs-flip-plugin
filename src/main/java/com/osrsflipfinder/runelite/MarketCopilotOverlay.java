package com.osrsflipfinder.runelite;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
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
 * current item's live score and estimated flip economics. Read-only - never
 * injects prices, fills fields, or simulates clicks (Jagex/Hub compliance).
 */
@Slf4j
public class MarketCopilotOverlay extends OverlayPanel
{
	private static final int PANEL_WIDTH = 300;
	private static final Color PANEL_BG = new Color(15, 17, 21, 215);

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
		setPosition(OverlayPosition.TOP_LEFT);
		setPreferredLocation(new Point(8, 52));
		panelComponent.setPreferredSize(new Dimension(PANEL_WIDTH, 0));
		panelComponent.setBackgroundColor(PANEL_BG);
		panelComponent.setBorder(new Rectangle(6, 5, 6, 5));
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		panelComponent.getChildren().clear();

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

		if (itemsClient.isStale(itemId))
		{
			maybeFetch(itemId);
		}

		FlipOpportunity opp = itemsClient.peekOpportunity(itemId);
		if (opp == null)
		{
			panelComponent.getChildren().add(TitleComponent.builder()
				.text("FlipX copilot")
				.color(ColorScheme.BRAND_ORANGE)
				.build());
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Loading...")
				.leftColor(ColorScheme.LIGHT_GRAY_COLOR)
				.build());
			return super.render(graphics);
		}

		FlipCopilotPresenter.Verdict verdict = FlipCopilotPresenter.verdict(opp);

		panelComponent.getChildren().add(TitleComponent.builder()
			.text(truncate(opp.getName(), 24))
			.color(Color.WHITE)
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left(FlipCopilotPresenter.verdictLabel(verdict))
			.leftColor(FlipCopilotPresenter.verdictColor(verdict))
			.right(FlipCopilotPresenter.scoreLineCompact(opp))
			.rightColor(PluginUi.GOLD_DIM)
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Net")
			.right(MarketFormat.signedGp(opp.getNetProfitPerItem()))
			.rightColor(opp.getNetProfitPerItem() >= 0 ? PluginUi.POSITIVE : PluginUi.NEGATIVE)
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("ROI | GP/hr")
			.right(MarketFormat.percent(opp.getNetRoiPercent()) + " | "
				+ MarketFormat.gp(opp.getEstimatedProfitPerHour()))
			.rightColor(ColorScheme.GRAND_EXCHANGE_ALCH)
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Buy | Sell")
			.right(MarketFormat.gp(opp.getEstimatedBuyPrice()) + " | "
				+ MarketFormat.gp(opp.getEstimatedSellPrice()))
			.rightColor(Color.WHITE)
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Qty | ~30m")
			.right(MarketFormat.qtyLimit(opp.getEstimatedTradableQuantity(), opp.getBuyLimit())
				+ " | " + MarketFormat.signedGp(opp.getEstimatedProfit30m()))
			.rightColor(ColorScheme.LIGHT_GRAY_COLOR)
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Fill | 1h vol")
			.right(FlipCopilotPresenter.formatTurnover(opp.getEstimatedTurnoverHours())
				+ " | " + MarketFormat.gp(opp.getOneHourVolume()))
			.rightColor(ColorScheme.LIGHT_GRAY_COLOR)
			.build());

		String hint = resolveHint(opp, itemId);
		if (hint != null)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left(truncate(hint, 36))
				.leftColor(opp.isPriceDumped() ? PluginUi.NEGATIVE : PluginUi.WARNING)
				.build());
		}
		else if (opp.isPriceDumped())
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Dump vs hourly norm")
				.leftColor(PluginUi.NEGATIVE)
				.build());
		}

		CopilotItem copilot = copilotClient.peek(itemId);
		if (copilot != null && copilot.getRiskWarning() != null && !copilot.getRiskWarning().isBlank())
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left(truncate(copilot.getRiskWarning(), 38))
				.leftColor(PluginUi.WARNING)
				.build());
		}

		return super.render(graphics);
	}

	private String resolveHint(FlipOpportunity opp, int itemId)
	{
		String hint = FlipCopilotPresenter.repriceHint(opp);
		if (hint != null)
		{
			return hint;
		}
		CopilotItem item = copilotClient.peek(itemId);
		if (item != null && item.getRepriceHint() != null && !item.getRepriceHint().isBlank())
		{
			return item.getRepriceHint();
		}
		return null;
	}

	private static String truncate(String text, int max)
	{
		if (text == null)
		{
			return "";
		}
		if (text.length() <= max)
		{
			return text;
		}
		int keep = Math.max(0, max - 3);
		return text.substring(0, keep) + "...";
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
