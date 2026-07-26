package com.osrsflipfinder.runelite;

import java.awt.BorderLayout;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollBar;
import javax.swing.SwingUtilities;
import javax.swing.plaf.ScrollBarUI;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.laf.RuneLiteScrollBarUI;
import net.runelite.client.util.LinkBrowser;

/**
 * Unified FlipX sidebar with a section dropdown - one view at a time instead of
 * one long scroll stack.
 */
@Slf4j
public class FlipFinderPanel extends PluginPanel
{
	static final String PLUGIN_VERSION = "1.0.0";

	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter
		.ofPattern("HH:mm:ss")
		.withZone(ZoneId.systemDefault());

	private final FlipFinderConfig config;
	private final ConfigManager configManager;
	private final PairingService pairingService;
	private final IngestClient ingestClient;
	private final OpportunitiesClient opportunitiesClient;
	private final PortfolioClient portfolioClient;
	private final MarketPanel marketPanel;
	private final MySlotsPanel mySlotsPanel;
	private final SessionStatsPanel sessionStatsPanel;
	private final GeSetupPanel geSetupPanel;
	private final GeEventListener geEventListener;
	private final ImportPanel importPanel;
	private final RecipeFlipsPanel recipeFlipsPanel;
	private final ScheduledExecutorService executorService;

	private final JLabel statusBadge = PluginUi.statusBadge(PluginState.NOT_PAIRED);
	private final JLabel metaLabel = PluginUi.caption("Not paired");
	private final JLabel queueLabel = PluginUi.caption("Queue: 0 events");
	private final JLabel lastSyncLabel = PluginUi.caption("Last sync: never");
	private final JLabel errorLabel = PluginUi.errorLabel();
	private final JPasswordField codeField = PluginUi.passwordField();
	private final JButton connectButton = PluginUi.primaryButton("Connect");
	private final JButton disconnectButton = PluginUi.secondaryButton("Disconnect");
	private final JButton settingsButton = PluginUi.linkButton("Web settings");
	private final JButton networkButton = PluginUi.linkButton("Network opt-in");
	private final JButton privacyButton = PluginUi.linkButton("Privacy");
	private final JLabel pairingSuccessLabel = PluginUi.caption("");

	private final JPanel pairingForm = new JPanel();
	private final JPanel connectedSummary = new JPanel();
	private final JPanel connectionViewPanel = new JPanel();
	private final SectionContentHost sectionContent = new SectionContentHost();
	private final JComboBox<String> sectionCombo = new JComboBox<>(sectionLabels());
	private SidebarSection currentSection = SidebarSection.CONNECTION;
	private boolean sidebarActive;
	private volatile PluginState authFailureState;
	private ScheduledFuture<?> refreshTimerUiTask;

	@Inject
	FlipFinderPanel(
		FlipFinderConfig config,
		ConfigManager configManager,
		PairingService pairingService,
		IngestClient ingestClient,
		OpportunitiesClient opportunitiesClient,
		PortfolioClient portfolioClient,
		MarketPanel marketPanel,
		MySlotsPanel mySlotsPanel,
		SessionStatsPanel sessionStatsPanel,
		GeSetupPanel geSetupPanel,
		GeEventListener geEventListener,
		ImportPanel importPanel,
		RecipeFlipsPanel recipeFlipsPanel,
		ScheduledExecutorService executorService
	)
	{
		super();
		this.config = config;
		this.configManager = configManager;
		this.pairingService = pairingService;
		this.ingestClient = ingestClient;
		this.opportunitiesClient = opportunitiesClient;
		this.portfolioClient = portfolioClient;
		this.marketPanel = marketPanel;
		this.mySlotsPanel = mySlotsPanel;
		this.sessionStatsPanel = sessionStatsPanel;
		this.geSetupPanel = geSetupPanel;
		this.geEventListener = geEventListener;
		this.importPanel = importPanel;
		this.recipeFlipsPanel = recipeFlipsPanel;
		this.executorService = executorService;

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		buildConnectionView();
		buildSectionNav();

		JPanel layoutPanel = new SidebarContentPanel();
		layoutPanel.setBorder(PluginUi.pageInsets());
		layoutPanel.setLayout(new BoxLayout(layoutPanel, BoxLayout.Y_AXIS));
		layoutPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		layoutPanel.add(PluginUi.header(
			"FlipX",
			"v" + PLUGIN_VERSION + " | Market & portfolio sync",
			statusBadge
		));
		layoutPanel.add(PluginUi.gap(PluginUi.SPACING_SM));
		layoutPanel.add(PluginUi.labeledField("View", sectionCombo));
		layoutPanel.add(PluginUi.gap(PluginUi.SPACING_MD));
		layoutPanel.add(sectionContent);
		SidebarContentPanel.lockWidth(layoutPanel);
		add(layoutPanel, BorderLayout.NORTH);

		connectButton.addActionListener(e -> connect());
		disconnectButton.addActionListener(e -> disconnect());
		settingsButton.addActionListener(e -> openUrl("/settings/devices"));
		networkButton.addActionListener(e -> openUrl("/settings#runelite-pairing"));
		privacyButton.addActionListener(e -> openUrl("/privacy"));

		sectionCombo.addActionListener(e ->
		{
			SidebarSection selected = SidebarSection.navOrder()[sectionCombo.getSelectedIndex()];
			showSection(selected);
		});

		ingestClient.setSyncStatsListener(this::onSyncStats);
		ingestClient.setStateListener(this::onStateChanged);
		ingestClient.setErrorListener(this::onError);
		opportunitiesClient.setStateListener(state ->
		{
			onStateChanged(state);
			if (state == PluginState.CONNECTED)
			{
				recipeFlipsPanel.load();
			}
		});
		portfolioClient.setAuthFailureListener(state -> onStateChanged(state));

		marketPanel.setScrollToTop(this::scrollSidebarToTop);

		geEventListener.addOfferChangeListener(() -> SwingUtilities.invokeLater(mySlotsPanel::refreshLocal));

		showSection(defaultSectionForState());
		if (PairingCredentials.clearIfApiHostChanged(configManager, config))
		{
			onError("API URL changed - pair again in Connection (flipx.gg).");
		}
		refreshUi();
	}

	private static String[] sectionLabels()
	{
		SidebarSection[] sections = SidebarSection.navOrder();
		String[] labels = new String[sections.length];
		for (int i = 0; i < sections.length; i++)
		{
			labels[i] = sections[i].label();
		}
		return labels;
	}

	private void buildSectionNav()
	{
		PluginUi.styleCombo(sectionCombo);
		sectionCombo.setToolTipText("Choose a FlipX sidebar section");
	}

	private JPanel panelForSection(SidebarSection section)
	{
		switch (section)
		{
			case CONNECTION:
				return connectionViewPanel;
			case MY_SLOTS:
				return mySlotsPanel;
			case SESSION:
				return sessionStatsPanel;
			case MARKET:
				return marketPanel;
			case GE_SETUP:
				return geSetupPanel;
			case IMPORT:
				return importPanel;
			case RECIPE_FLIPS:
				return recipeFlipsPanel;
			default:
				throw new IllegalArgumentException("Unknown section: " + section);
		}
	}

	private SidebarSection defaultSectionForState()
	{
		return isPairedForMarket() ? SidebarSection.MY_SLOTS : SidebarSection.CONNECTION;
	}

	private void showSection(SidebarSection section)
	{
		currentSection = section;
		sectionContent.showSection(panelForSection(section));
		scrollSidebarToTop();
		applySectionActiveStates();
		refreshSectionData(section);
		tickRefreshTimerLabels();
		revalidate();
		repaint();
	}

	private void refreshSectionData(SidebarSection section)
	{
		switch (section)
		{
			case MY_SLOTS:
				mySlotsPanel.refreshLocal();
				break;
			case SESSION:
				sessionStatsPanel.refresh();
				break;
			case MARKET:
				marketPanel.refreshUi();
				break;
			case RECIPE_FLIPS:
				if (isPairedForMarket() && config.enableMarketPanel())
				{
					recipeFlipsPanel.load();
				}
				break;
			default:
				break;
		}
	}

	void scrollSidebarToTop()
	{
		SwingUtilities.invokeLater(() ->
		{
			var scroll = getScrollPane();
			if (scroll != null)
			{
				scroll.getVerticalScrollBar().setValue(0);
			}
		});
	}

	@Override
	public void onActivate()
	{
		reapplyScrollBarLaf();
		setSidebarActive(true);
	}

	@Override
	public void onDeactivate()
	{
		setSidebarActive(false);
	}

	private void setSidebarActive(boolean active)
	{
		sidebarActive = active;
		applySectionActiveStates();
		if (active)
		{
			startRefreshTimerUi();
			refreshSectionData(currentSection);
			tickRefreshTimerLabels();
		}
		else
		{
			stopRefreshTimerUi();
		}
	}

	private void startRefreshTimerUi()
	{
		if (refreshTimerUiTask != null)
		{
			return;
		}
		refreshTimerUiTask = executorService.scheduleAtFixedRate(
			() -> SwingUtilities.invokeLater(this::tickRefreshTimerLabels),
			0,
			1,
			TimeUnit.SECONDS
		);
	}

	private void stopRefreshTimerUi()
	{
		if (refreshTimerUiTask != null)
		{
			refreshTimerUiTask.cancel(false);
			refreshTimerUiTask = null;
		}
	}

	private void tickRefreshTimerLabels()
	{
		if (!sidebarActive)
		{
			return;
		}
		boolean paired = isPairedForMarket();
		switch (currentSection)
		{
			case MARKET:
				marketPanel.updateRefreshTimer(paired);
				break;
			case MY_SLOTS:
				mySlotsPanel.updateRefreshTimer(paired);
				break;
		case SESSION:
				sessionStatsPanel.updateRefreshTimer(paired);
				break;
			case GE_SETUP:
				geSetupPanel.updateRefreshTimer(paired);
				break;
			case RECIPE_FLIPS:
				recipeFlipsPanel.updateRefreshTimer();
				break;
			default:
				break;
		}
	}

	private boolean portfolioPollingEnabled()
	{
		return config.enableUpload()
			|| currentSection == SidebarSection.MY_SLOTS
			|| currentSection == SidebarSection.SESSION;
	}

	private void applySectionActiveStates()
	{
		boolean active = sidebarActive;
		boolean paired = isPairedForMarket();

		marketPanel.setActive(active && paired && currentSection == SidebarSection.MARKET);
		portfolioClient.setActive(active && paired && portfolioPollingEnabled());
		geSetupPanel.setActive(active && currentSection == SidebarSection.GE_SETUP);
		mySlotsPanel.setActive(active && currentSection == SidebarSection.MY_SLOTS);
	}

	/** Reapply RuneLite's 7px scrollbar after updateComponentTreeUI resets LAF on macOS. */
	void reapplyScrollBarLaf()
	{
		var scroll = getScrollPane();
		if (scroll == null)
		{
			return;
		}
		applyScrollBarLaf(scroll.getVerticalScrollBar());
		applyScrollBarLaf(scroll.getHorizontalScrollBar());
	}

	private static void applyScrollBarLaf(JScrollBar bar)
	{
		ScrollBarUI ui = (ScrollBarUI) RuneLiteScrollBarUI.createUI(bar);
		bar.setUI(ui);
	}

	private void buildConnectionView()
	{
		pairingForm.setLayout(new BoxLayout(pairingForm, BoxLayout.Y_AXIS));
		PluginUi.transparent(pairingForm);

		pairingForm.add(PluginUi.hint(
			"Unofficial tool - not endorsed by Jagex. Does not place or modify GE offers."
		));
		pairingForm.add(PluginUi.gap(PluginUi.SPACING_SM));
		JPanel pairingInner = PluginUi.verticalStack(
			PluginUi.hint(
				"On flipx.gg: generate a pairing code (network intelligence is on by default), then enter it below."
			),
			PluginUi.hint(
				"Enable GE upload or the Market panel in plugin settings first."
			),
			PluginUi.labeledField("Pairing code", codeField),
			connectButton
		);
		PluginUi.fullWidth(connectButton);
		pairingForm.add(PluginUi.formCard(pairingInner));
		SidebarContentPanel.lockWidth(pairingForm);

		connectedSummary.setLayout(new BoxLayout(connectedSummary, BoxLayout.Y_AXIS));
		PluginUi.transparent(connectedSummary);
		JPanel summaryInner = PluginUi.verticalStack(
			metaLabel,
			queueLabel,
			lastSyncLabel,
			pairingSuccessLabel,
			disconnectButton
		);
		PluginUi.fullWidth(disconnectButton);
		JPanel summaryCard = PluginUi.formCard(summaryInner);
		PluginUi.fullWidth(summaryCard);
		connectedSummary.add(summaryCard);
		SidebarContentPanel.lockWidth(connectedSummary);

		JPanel footerLinks = PluginUi.buttonRow(settingsButton, networkButton, privacyButton);
		PluginUi.fullWidth(footerLinks);

		connectionViewPanel.setLayout(new BoxLayout(connectionViewPanel, BoxLayout.Y_AXIS));
		PluginUi.transparent(connectionViewPanel);
		connectionViewPanel.add(pairingForm);
		connectionViewPanel.add(connectedSummary);
		connectionViewPanel.add(PluginUi.gap(PluginUi.SPACING_MD));
		connectionViewPanel.add(footerLinks);
		connectionViewPanel.add(PluginUi.gap(PluginUi.SPACING_SM));
		connectionViewPanel.add(errorLabel);
		SidebarContentPanel.lockWidth(connectionViewPanel);
	}

	void refreshUi()
	{
		SwingUtilities.invokeLater(() ->
		{
			if (PairingCredentials.clearIfApiHostChanged(configManager, config))
			{
				onError("API URL changed - pair again in Connection (flipx.gg).");
			}
			PluginState state = resolveState();
			updateStatusBadge(state);
			updateConnectionLayout(state);
			updateMeta();
			queueLabel.setText("Queue: " + ingestClient.getQueueSize() + " events");
			disconnectButton.setEnabled(PairingCredentials.isPaired(config));
			applySectionActiveStates();
			marketPanel.refreshUi();
			if (isPairedForMarket())
			{
				opportunitiesClient.requestEntitlementsRefresh();
			}
			refreshSectionData(currentSection);
		});
	}

	private boolean isPairedForMarket()
	{
		return PairingCredentials.isPairedForCurrentApi(config);
	}

	private void updateConnectionLayout(PluginState state)
	{
		boolean paired = isPairedForMarket();
		pairingForm.setVisible(!paired);
		connectedSummary.setVisible(paired);
		if (!paired)
		{
			pairingSuccessLabel.setText("");
		}
	}

	private void selectSection(SidebarSection section)
	{
		int index = section.ordinal();
		if (sectionCombo.getSelectedIndex() != index)
		{
			sectionCombo.setSelectedIndex(index);
		}
		else
		{
			showSection(section);
		}
	}

	private void connect()
	{
		if (!config.enableUpload() && !config.enableMarketPanel())
		{
			onError("Enable GE upload or the Market panel in plugin settings first");
			selectSection(SidebarSection.CONNECTION);
			return;
		}

		String code = new String(codeField.getPassword()).trim();
		if (code.length() != 6)
		{
			onError("Enter the 6-digit pairing code from the web app");
			selectSection(SidebarSection.CONNECTION);
			return;
		}

		connectButton.setEnabled(false);
		onStateChanged(PluginState.SYNCING);
		onError(null);

		executorService.execute(() ->
		{
			try
			{
				String deviceId = ensureDeviceId();

				PairingService.PairingResult result = pairingService.pair(
					code,
					deviceId,
					"RuneLite"
				);

				PairingCredentials.save(configManager, result.getApiKey());

				SwingUtilities.invokeLater(() ->
				{
					codeField.setText("");
					connectButton.setEnabled(true);
					disconnectButton.setEnabled(true);
					onError(null);
					if (result.isNetworkIntelligenceOptedIn())
					{
						if (result.isWelcomeProGranted())
						{
							pairingSuccessLabel.setText(
								"Network intelligence enabled on your FlipX account (welcome Pro unlocked on web)."
							);
						}
						else
						{
							pairingSuccessLabel.setText("Network intelligence enabled on your FlipX account.");
						}
					}
					else
					{
						pairingSuccessLabel.setText(
							"Paired. Enable network intelligence on the web to contribute anonymized GE signals."
						);
					}
					refreshUi();
					geEventListener.backfillAllSlots();
					selectSection(SidebarSection.MY_SLOTS);
				});
			}
			catch (Exception e)
			{
				log.warn("Pairing failed", e);
				SwingUtilities.invokeLater(() ->
				{
					connectButton.setEnabled(true);
					onStateChanged(PluginState.NOT_PAIRED);
					onError(e.getMessage());
					selectSection(SidebarSection.CONNECTION);
				});
			}
		});
	}

	private void disconnect()
	{
		String apiKey = config.apiKey();

		disconnectButton.setEnabled(false);
		connectButton.setEnabled(false);

		executorService.execute(() ->
		{
			if (apiKey != null && !apiKey.isBlank())
			{
				try
				{
					pairingService.revokeSelf(apiKey);
				}
				catch (IOException e)
				{
					log.warn("Remote revoke failed; clearing local credentials", e);
				}
			}

			SwingUtilities.invokeLater(() ->
			{
				PairingCredentials.clear(configManager);
				codeField.setText("");
				connectButton.setEnabled(true);
				disconnectButton.setEnabled(false);
				onError(null);
				refreshUi();
				selectSection(SidebarSection.CONNECTION);
			});
		});
	}

	private void openUrl(String path)
	{
		LinkBrowser.browse(FlipXConstants.baseUrl() + path);
	}

	private String ensureDeviceId()
	{
		String deviceId = config.deviceId();
		if (deviceId == null || deviceId.isBlank())
		{
			deviceId = UUID.randomUUID().toString();
			configManager.setConfiguration(FlipFinderConfig.GROUP, "deviceId", deviceId);
		}
		return deviceId;
	}

	private PluginState resolveState()
	{
		if (authFailureState == PluginState.REPAIR_REQUIRED && PairingCredentials.isPaired(config))
		{
			return PluginState.REPAIR_REQUIRED;
		}
		if (!PairingCredentials.isPairedForCurrentApi(config))
		{
			return PluginState.NOT_PAIRED;
		}
		if (authFailureState == PluginState.UPGRADE_REQUIRED)
		{
			return PluginState.UPGRADE_REQUIRED;
		}
		return PluginState.CONNECTED;
	}

	private void updateMeta()
	{
		String pairedAt = config.pairedAt();
		if (pairedAt == null || pairedAt.isBlank())
		{
			metaLabel.setText("Not paired");
			return;
		}
		metaLabel.setText("Paired " + pairedAt.replace('T', ' ').substring(0, Math.min(19, pairedAt.length())));
	}

	private void onSyncStats(IngestClient.SyncStats stats)
	{
		SwingUtilities.invokeLater(() ->
		{
			lastSyncLabel.setText(String.format(
				"Last sync %s | +%d | skipped %d",
				TIME_FORMAT.format(stats.getSyncedAt()),
				stats.getInserted(),
				stats.getSkipped()
			));
			queueLabel.setText("Queue: " + ingestClient.getQueueSize() + " events");
		});
	}

	private void onStateChanged(PluginState state)
	{
		SwingUtilities.invokeLater(() ->
		{
			if (state == PluginState.REPAIR_REQUIRED || state == PluginState.UPGRADE_REQUIRED)
			{
				authFailureState = state;
			}
			else if (state == PluginState.CONNECTED)
			{
				authFailureState = null;
			}

			if (state == PluginState.REPAIR_REQUIRED || state == PluginState.NOT_PAIRED)
			{
				refreshUi();
				if (state == PluginState.REPAIR_REQUIRED)
				{
					onError("Invalid API key - pair again in Connection.");
				}
				if (currentSection != SidebarSection.CONNECTION)
				{
					selectSection(SidebarSection.CONNECTION);
				}
				return;
			}

			// Avoid re-fetching market on every successful poll (refreshUi -> requestMarketRefresh loop).
			updateStatusBadge(resolveState());
			queueLabel.setText("Queue: " + ingestClient.getQueueSize() + " events");
			disconnectButton.setEnabled(PairingCredentials.isPaired(config));
			applySectionActiveStates();
		});
	}

	private void onError(String message)
	{
		SwingUtilities.invokeLater(() ->
		{
			if (message == null || message.isBlank())
			{
				errorLabel.setText(" ");
			}
			else
			{
				errorLabel.setText(message);
			}
		});
	}

	private void updateStatusBadge(PluginState state)
	{
		JLabel fresh = PluginUi.statusBadge(state);
		statusBadge.setText(fresh.getText());
		statusBadge.setForeground(fresh.getForeground());
		statusBadge.setBorder(fresh.getBorder());
	}
}
