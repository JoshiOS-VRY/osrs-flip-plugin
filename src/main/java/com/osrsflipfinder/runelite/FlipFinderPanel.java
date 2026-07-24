package com.osrsflipfinder.runelite;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
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
 * Unified FlipX sidebar with a section dropdown — one view at a time instead of
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
	private final JButton privacyButton = PluginUi.linkButton("Privacy");

	private final JPanel pairingForm = new JPanel();
	private final JPanel connectedSummary = new JPanel();
	private final JPanel connectionViewPanel = new JPanel();
	private final JPanel sectionContent = new JPanel(new CardLayout());
	private final JComboBox<String> sectionCombo = new JComboBox<>(sectionLabels());
	private SidebarSection currentSection = SidebarSection.CONNECTION;
	private boolean sidebarActive;

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
		layoutPanel.setLayout(new BoxLayout(layoutPanel, BoxLayout.Y_AXIS));
		layoutPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		layoutPanel.add(PluginUi.header(
			"FlipX",
			"v" + PLUGIN_VERSION + " · Market & portfolio sync",
			statusBadge
		));
		layoutPanel.add(PluginUi.gap(6));
		layoutPanel.add(PluginUi.labeledField("View", sectionCombo));
		layoutPanel.add(PluginUi.gap(8));
		layoutPanel.add(sectionContent);
		SidebarContentPanel.lockWidth(layoutPanel);
		add(layoutPanel, BorderLayout.NORTH);

		connectButton.addActionListener(e -> connect());
		disconnectButton.addActionListener(e -> disconnect());
		settingsButton.addActionListener(e -> openUrl("/settings/devices"));
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

		marketPanel.setScrollToTop(this::scrollSidebarToTop);

		geEventListener.addOfferChangeListener(() -> SwingUtilities.invokeLater(mySlotsPanel::refreshLocal));

		showSection(defaultSectionForState());
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

		sectionContent.setBackground(ColorScheme.DARK_GRAY_COLOR);
		sectionContent.setAlignmentX(JPanel.LEFT_ALIGNMENT);
		SidebarContentPanel.lockWidth(sectionContent);

		for (SidebarSection section : SidebarSection.navOrder())
		{
			sectionContent.add(panelForSection(section), section.name());
		}
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
		((CardLayout) sectionContent.getLayout()).show(sectionContent, section.name());
		scrollSidebarToTop();
		applySectionActiveStates();
		refreshSectionData(section);
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
			refreshSectionData(currentSection);
		}
	}

	private void applySectionActiveStates()
	{
		boolean active = sidebarActive;
		boolean paired = isPairedForMarket();

		marketPanel.setActive(active && paired && currentSection == SidebarSection.MARKET);
		portfolioClient.setActive(active && paired && config.enableUpload());
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
			"Unofficial tool — not endorsed by Jagex. Does not place or modify GE offers."
		));
		pairingForm.add(PluginUi.gap(6));
		pairingForm.add(PluginUi.hint(
			"Enable GE upload or the Market panel in plugin settings, then pair below."
		));
		pairingForm.add(PluginUi.gap(10));
		pairingForm.add(PluginUi.labeledField("Pairing code", codeField));
		pairingForm.add(PluginUi.gap(10));
		pairingForm.add(connectButton);
		PluginUi.fullWidth(connectButton);
		SidebarContentPanel.lockWidth(pairingForm);

		connectedSummary.setLayout(new BoxLayout(connectedSummary, BoxLayout.Y_AXIS));
		PluginUi.transparent(connectedSummary);
		JPanel summaryCard = PluginUi.card();
		summaryCard.add(metaLabel);
		summaryCard.add(PluginUi.gap(4));
		summaryCard.add(queueLabel);
		summaryCard.add(PluginUi.gap(4));
		summaryCard.add(lastSyncLabel);
		summaryCard.add(PluginUi.gap(8));
		summaryCard.add(disconnectButton);
		PluginUi.fullWidth(summaryCard);
		PluginUi.fullWidth(disconnectButton);
		connectedSummary.add(summaryCard);
		SidebarContentPanel.lockWidth(connectedSummary);

		JPanel footerLinks = PluginUi.buttonRow(settingsButton, privacyButton);
		PluginUi.fullWidth(footerLinks);

		connectionViewPanel.setLayout(new BoxLayout(connectionViewPanel, BoxLayout.Y_AXIS));
		PluginUi.transparent(connectionViewPanel);
		connectionViewPanel.add(pairingForm);
		connectionViewPanel.add(connectedSummary);
		connectionViewPanel.add(PluginUi.gap(8));
		connectionViewPanel.add(footerLinks);
		connectionViewPanel.add(PluginUi.gap(6));
		connectionViewPanel.add(errorLabel);
		SidebarContentPanel.lockWidth(connectionViewPanel);
	}

	void refreshUi()
	{
		SwingUtilities.invokeLater(() ->
		{
			PluginState state = resolveState();
			updateStatusBadge(state);
			updateConnectionLayout(state);
			updateMeta();
			queueLabel.setText("Queue: " + ingestClient.getQueueSize() + " events");
			disconnectButton.setEnabled(!config.apiKey().isBlank());
			applySectionActiveStates();
			refreshSectionData(currentSection);
		});
	}

	private boolean isPairedForMarket()
	{
		return config.apiKey() != null && !config.apiKey().isBlank();
	}

	private void updateConnectionLayout(PluginState state)
	{
		boolean paired = isPairedForMarket();
		pairingForm.setVisible(!paired);
		connectedSummary.setVisible(paired);
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

				configManager.setConfiguration(FlipFinderConfig.GROUP, "apiKey", result.getApiKey());
				configManager.setConfiguration(
					FlipFinderConfig.GROUP,
					"pairedAt",
					Instant.now().toString()
				);

				SwingUtilities.invokeLater(() ->
				{
					codeField.setText("");
					connectButton.setEnabled(true);
					disconnectButton.setEnabled(true);
					onStateChanged(PluginState.CONNECTED);
					onError(null);
					updateMeta();
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
				configManager.unsetConfiguration(FlipFinderConfig.GROUP, "apiKey");
				configManager.unsetConfiguration(FlipFinderConfig.GROUP, "pairedAt");
				codeField.setText("");
				connectButton.setEnabled(true);
				disconnectButton.setEnabled(false);
				onStateChanged(PluginState.NOT_PAIRED);
				onError(null);
				updateMeta();
				selectSection(SidebarSection.CONNECTION);
				marketPanel.refreshUi();
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
		if (config.apiKey() == null || config.apiKey().isBlank())
		{
			return PluginState.NOT_PAIRED;
		}
		if (!config.enableUpload() && !config.enableMarketPanel())
		{
			return PluginState.NOT_PAIRED;
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
				"Last sync %s · +%d · skipped %d",
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
			updateStatusBadge(state);
			updateConnectionLayout(state);
			queueLabel.setText("Queue: " + ingestClient.getQueueSize() + " events");
			if (state == PluginState.REPAIR_REQUIRED || state == PluginState.NOT_PAIRED)
			{
				disconnectButton.setEnabled(false);
				if (currentSection != SidebarSection.CONNECTION)
				{
					selectSection(SidebarSection.CONNECTION);
				}
				else
				{
					applySectionActiveStates();
				}
			}
			else if (state == PluginState.CONNECTED)
			{
				applySectionActiveStates();
			}
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
