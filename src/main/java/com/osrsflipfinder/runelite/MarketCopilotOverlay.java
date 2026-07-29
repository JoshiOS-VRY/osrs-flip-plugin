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
 *
 * <p>Keep this panel sparse — long copy belongs in the FlipX sidebar GE setup tab.
 */
@Slf4j
public class MarketCopilotOverlay extends OverlayPanel
{
	private static final int PANEL_WIDTH = 300;
	private static final int TITLE_MAX_CHARS = 18;
	private static final int REFRESH_MAX_CHARS = 24;
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
	private volatile int copilotMissItemId = -1;
	private volatile long copilotMissUntilMs = 0L;
	private static final long COPILOT_MISS_COOLDOWN_MS = 60_000L;

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
			long now = System.currentTimeMillis();
			if (itemId != copilotMissItemId || now >= copilotMissUntilMs)
			{
				maybeFetch(itemId);
			}
		}

		FlipOpportunity opp = itemsClient.peekOpportunity(itemId);
		ItemDetailResponse detail = itemsClient.peek(itemId);
		if (opp == null)
		{
			panelComponent.getChildren().add(TitleComponent.builder()
				.text("FlipX")
				.color(ColorScheme.BRAND_ORANGE)
				.build());
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Loading…")
				.leftColor(ColorScheme.LIGHT_GRAY_COLOR)
				.build());
			return super.render(graphics);
		}

		FlipCopilotPresenter.Verdict verdict = FlipCopilotPresenter.verdict(opp);

		panelComponent.getChildren().add(TitleComponent.builder()
			.text(truncate(opp.getName(), TITLE_MAX_CHARS))
			.color(Color.WHITE)
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left(FlipCopilotPresenter.verdictLabelOverlay(verdict))
			.leftColor(FlipCopilotPresenter.verdictColor(verdict))
			.right(FlipCopilotPresenter.scoreLineCompact(opp))
			.rightColor(PluginUi.GOLD_DIM)
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Net")
			.right(MarketFormat.signedGp(DisplayPriceResolver.resolveNetProfit(detail)))
			.rightColor(DisplayPriceResolver.resolveNetProfit(detail) >= 0 ? PluginUi.POSITIVE : PluginUi.NEGATIVE)
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("ROI")
			.right(MarketFormat.percent(DisplayPriceResolver.resolveNetRoi(detail)))
			.rightColor(ColorScheme.GRAND_EXCHANGE_ALCH)
			.build());

		GeAssistPricing.PriceSource buySource =
			DisplayPriceResolver.resolveBuySource(detail, entitlements);
		GeAssistPricing.PriceSource sellSource =
			DisplayPriceResolver.resolveSellSource(detail, entitlements);

		panelComponent.getChildren().add(LineComponent.builder()
			.left(DisplayPriceResolver.overlayBuyLabel(buySource))
			.right(MarketFormat.gp(DisplayPriceResolver.resolveBuyPrice(detail, entitlements)))
			.rightColor(FlipCopilotPresenter.buyPriceColor())
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left(DisplayPriceResolver.overlaySellLabel(sellSource))
			.right(MarketFormat.gp(DisplayPriceResolver.resolveSellPrice(detail, entitlements)))
			.rightColor(FlipCopilotPresenter.sellPriceColor())
			.build());

		long breakEven = GeTax.breakEvenSellPrice(
			DisplayPriceResolver.resolveBuyPrice(detail, entitlements),
			opp.getId()
		);
		if (breakEven > 0)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("BE sell")
				.right(MarketFormat.gp(breakEven))
				.rightColor(ColorScheme.LIGHT_GRAY_COLOR)
				.build());
		}

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Qty | 30m")
			.right(MarketFormat.qtyLimit(opp.getEstimatedTradableQuantity(), opp.getBuyLimit())
				+ " | " + MarketFormat.signedGp(opp.getEstimatedProfit30m()))
			.rightColor(ColorScheme.LIGHT_GRAY_COLOR)
			.build());

		String alert = FlipCopilotPresenter.overlayAlertLine(opp, 22);
		if (alert != null)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left(alert)
				.leftColor(opp.isPriceDumped() || opp.getNetProfitPerItem() <= 0
					? PluginUi.NEGATIVE
					: PluginUi.WARNING)
				.build());
		}

		String refreshLine = CopilotRefreshLabels.pollingLine(
			opportunitiesClient,
			itemsClient,
			true,
			true
		);
		if (refreshLine != null && !refreshLine.isBlank())
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left(truncate(refreshLine, REFRESH_MAX_CHARS))
				.leftColor(ColorScheme.LIGHT_GRAY_COLOR)
				.build());
		}

		return super.render(graphics);
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
		int keep = Math.max(0, max - 1);
		return text.substring(0, keep) + "…";
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
				copilotMissItemId = -1;
				copilotMissUntilMs = 0L;
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
				copilotMissItemId = itemId;
				copilotMissUntilMs = System.currentTimeMillis() + COPILOT_MISS_COOLDOWN_MS;
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
