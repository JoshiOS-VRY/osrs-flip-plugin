package com.osrsflipfinder.runelite;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.UUID;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "FlipX",
	description = "Browse live flip opportunities and sync Grand Exchange offers to FlipX.",
	tags = {"grand exchange", "flipping", "ge", "portfolio", "market"}
)
public class OsrsFlipFinderPlugin extends Plugin
{
	@Inject
	private FlipFinderConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private FlipFinderPanel panel;

	@Inject
	private GeEventListener geEventListener;

	@Inject
	private IngestClient ingestClient;

	@Inject
	private OpportunitiesClient opportunitiesClient;

	@Inject
	private CoinBalanceService coinBalanceService;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private MarketCopilotOverlay marketCopilotOverlay;

	@Inject
	private GeChartOverlay geChartOverlay;

	@Inject
	private GeWatchlistHintOverlay geWatchlistHintOverlay;

	@Inject
	private GeInterfaceListener geInterfaceListener;

	@Inject
	private PortfolioClient portfolioClient;

	@Inject
	private EventBus eventBus;

	private NavigationButton navButton;

	@Override
	protected void startUp()
	{
		ensureDeviceId();

		eventBus.register(geEventListener);
		eventBus.register(coinBalanceService);
		eventBus.register(geInterfaceListener);
		eventBus.register(this);
		ingestClient.start();
		opportunitiesClient.start();
		portfolioClient.start();
		coinBalanceService.start();
		overlayManager.add(marketCopilotOverlay);
		overlayManager.add(geChartOverlay);
		overlayManager.add(geWatchlistHintOverlay);

		BufferedImage icon = loadIcon();

		navButton = NavigationButton.builder()
			.tooltip("FlipX")
			.icon(icon)
			.priority(6)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navButton);
		SwingUtilities.invokeLater(() ->
		{
			SwingUtilities.updateComponentTreeUI(panel.getWrappedPanel());
			panel.reapplyScrollBarLaf();
		});
		panel.refreshUi();
		log.debug("FlipX plugin started");
	}

	@Override
	protected void shutDown()
	{
		ingestClient.shutdown();
		opportunitiesClient.shutdown();
		portfolioClient.shutdown();
		overlayManager.remove(marketCopilotOverlay);
		overlayManager.remove(geChartOverlay);
		overlayManager.remove(geWatchlistHintOverlay);
		eventBus.unregister(geEventListener);
		eventBus.unregister(coinBalanceService);
		eventBus.unregister(geInterfaceListener);
		eventBus.unregister(this);
		clientToolbar.removeNavigation(navButton);
		log.debug("FlipX plugin stopped");
	}

	@Provides
	FlipFinderConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(FlipFinderConfig.class);
	}

	@Subscribe
	void onConfigChanged(ConfigChanged event)
	{
		if (!FlipFinderConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		String key = event.getKey();
		if (key == null)
		{
			panel.refreshUi();
			return;
		}
		switch (key)
		{
			case "enableUpload":
			case "enableMarketPanel":
			case "enableGeOverlay":
			case "enableGeChartOverlay":
			case "enableWatchlistGeHint":
			case "apiKey":
			case "pairedAt":
			case "pairedBaseUrl":
				panel.refreshUi();
				break;
			default:
				break;
		}
	}

	private void ensureDeviceId()
	{
		if (config.deviceId() == null || config.deviceId().isBlank())
		{
			configManager.setConfiguration(
				FlipFinderConfig.GROUP,
				"deviceId",
				UUID.randomUUID().toString()
			);
		}
	}

	private static BufferedImage loadIcon()
	{
		try
		{
			return ImageUtil.loadImageResource(OsrsFlipFinderPlugin.class, "icon.png");
		}
		catch (RuntimeException ex)
		{
			log.debug("Using fallback plugin icon", ex);
			return createFallbackIcon();
		}
	}

	private static BufferedImage createFallbackIcon()
	{
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		for (int x = 0; x < 16; x++)
		{
			for (int y = 0; y < 16; y++)
			{
				image.setRGB(x, y, 0xFFD9B45B);
			}
		}
		return image;
	}
}
