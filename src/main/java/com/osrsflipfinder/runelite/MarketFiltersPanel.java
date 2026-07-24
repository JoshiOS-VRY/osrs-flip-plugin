package com.osrsflipfinder.runelite;

import java.awt.Color;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ColorScheme;

/** Collapsible market filters matching the web app's core controls. */
class MarketFiltersPanel
{
	private static final String[] MEMBERS_VALUES = { "all", "members", "f2p" };
	private static final String[] MEMBERS_LABELS = { "All worlds", "Members only", "F2P only" };

	private final FlipFinderConfig config;
	private final ConfigManager configManager;
	private final CoinBalanceService coinBalanceService;
	private final Runnable onChange;

	private final JCheckBox hideLowConfidence = PluginUi.checkBox("Hide low confidence", false);
	private final JCheckBox discountOnly = PluginUi.checkBox("Discount only", false);
	private final JCheckBox dumpedOnly = PluginUi.checkBox("Dumped only", false);
	private final JCheckBox useInventoryCoins = PluginUi.checkBox("Limit to inventory coins", false);
	private final JTextField maxCapitalField = PluginUi.textField("");
	private final JTextField minNetProfitField = PluginUi.textField("");
	private final JTextField minRoiField = PluginUi.textField("");
	private final JTextField minConfidenceField = PluginUi.textField("");
	private final JTextField minTotalProfitField = PluginUi.textField("");
	private final JTextField minGpPerHourField = PluginUi.textField("");
	private final JComboBox<String> membersCombo = new JComboBox<>(new DefaultComboBoxModel<>(MEMBERS_LABELS));
	private final JLabel coinsLabel = PluginUi.caption("Inventory coins: —");
	private final JButton useCoinsButton = PluginUi.linkButton("Use as max capital");
	private final JLabel advancedHint = PluginUi.hint("Pro unlocks quality filters, max capital, and more.");

	private final JPanel advancedBody = new JPanel();
	private PluginUi.CollapsibleSection section;
	private boolean advancedEnabled = false;
	private boolean suppressEvents = false;

	MarketFiltersPanel(
		FlipFinderConfig config,
		ConfigManager configManager,
		CoinBalanceService coinBalanceService,
		Runnable onChange
	)
	{
		this.config = config;
		this.configManager = configManager;
		this.coinBalanceService = coinBalanceService;
		this.onChange = onChange;

		styleMembersCombo();
		buildAdvancedBody();
		wireListeners();
		restoreFromConfig();
	}

	JPanel wrapper()
	{
		if (section == null)
		{
			JPanel body = new JPanel();
			body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
			PluginUi.transparent(body);
			body.add(coinsLabel);
			body.add(PluginUi.gap(4));
			useCoinsButton.addActionListener(e -> applyInventoryCoinsToField());
			PluginUi.fullWidth(useCoinsButton);
			body.add(PluginUi.gap(8));
			body.add(PluginUi.labeledField("Min net / item", minNetProfitField));
			body.add(PluginUi.gap(6));
			body.add(PluginUi.labeledField("Min ROI %", minRoiField));
			body.add(PluginUi.gap(6));
			body.add(PluginUi.labeledField("Members", membersCombo));
			body.add(PluginUi.gap(8));
			body.add(advancedBody);
			body.add(advancedHint);

			section = PluginUi.collapsibleSection("Filters", body, false);
		}
		return section.wrapper();
	}

	void setAdvancedEnabled(boolean enabled)
	{
		advancedEnabled = enabled;
		advancedBody.setVisible(enabled);
		advancedHint.setVisible(!enabled);

		hideLowConfidence.setEnabled(enabled);
		discountOnly.setEnabled(enabled);
		dumpedOnly.setEnabled(enabled);
		maxCapitalField.setEnabled(enabled);
		useInventoryCoins.setEnabled(enabled);
		minTotalProfitField.setEnabled(enabled);
		minGpPerHourField.setEnabled(enabled);
		minConfidenceField.setEnabled(enabled);
		useCoinsButton.setEnabled(enabled);
	}

	boolean isUsingInventoryCoins()
	{
		return advancedEnabled && useInventoryCoins.isSelected();
	}

	/** True when saved Pro-only filters are active but the account lacks advancedFilters. */
	boolean hasIgnoredAdvancedFilters()
	{
		if (advancedEnabled)
		{
			return false;
		}
		return config.marketHideLowConfidence()
			|| config.marketDiscountOnly()
			|| config.marketDumpedOnly()
			|| hasText(config.marketMaxCapital())
			|| config.marketUseInventoryCoins()
			|| hasText(config.marketMinTotalProfit())
			|| hasText(config.marketMinGpPerHour())
			|| hasText(config.marketMinConfidencePercent());
	}

	String getEntitlementNotice()
	{
		if (!hasIgnoredAdvancedFilters())
		{
			return null;
		}
		return "Pro filters are saved but not applied on this account.";
	}

	void refreshCoinsLabel()
	{
		if (coinBalanceService.hasCoins())
		{
			coinsLabel.setText("Inventory coins: " + MarketFormat.gp(coinBalanceService.getCoins()));
		}
		else
		{
			coinsLabel.setText("Inventory coins: log in to detect");
		}
	}

	MarketQueryRequest.MarketFilters buildFilters()
	{
		MarketQueryRequest.MarketFilters filters = new MarketQueryRequest.MarketFilters();

		filters.setMinNetProfit(parseLong(minNetProfitField.getText()));
		filters.setMinRoiPercent(parseDouble(minRoiField.getText()));

		int membersIndex = Math.max(0, membersCombo.getSelectedIndex());
		String members = MEMBERS_VALUES[membersIndex];
		filters.setMembers("all".equals(members) ? null : members);

		if (advancedEnabled)
		{
			filters.setHideLowConfidence(hideLowConfidence.isSelected() ? true : null);
			filters.setDiscountOnly(discountOnly.isSelected() ? true : null);
			filters.setDumpedOnly(dumpedOnly.isSelected() ? true : null);

			Long maxCapital = parseLong(maxCapitalField.getText());
			if (useInventoryCoins.isSelected() && coinBalanceService.hasCoins())
			{
				maxCapital = coinBalanceService.getCoins();
			}
			filters.setMaxCapital(maxCapital);

			filters.setMinTotalProfit(parseLong(minTotalProfitField.getText()));
			filters.setMinGpPerHour(parseLong(minGpPerHourField.getText()));
			Double minConfidence = parseConfidencePercent(minConfidenceField.getText());
			filters.setMinConfidenceScore(minConfidence);
		}

		return filters;
	}

	void applyFromFilters(MarketQueryRequest.MarketFilters filters)
	{
		if (filters == null)
		{
			return;
		}
		suppressEvents = true;
		minNetProfitField.setText(formatLong(filters.getMinNetProfit()));
		minRoiField.setText(formatDouble(filters.getMinRoiPercent()));

		String members = filters.getMembers();
		int index = 0;
		if (members != null)
		{
			for (int i = 0; i < MEMBERS_VALUES.length; i++)
			{
				if (MEMBERS_VALUES[i].equals(members))
				{
					index = i;
					break;
				}
			}
		}
		membersCombo.setSelectedIndex(index);

		hideLowConfidence.setSelected(Boolean.TRUE.equals(filters.getHideLowConfidence()));
		discountOnly.setSelected(Boolean.TRUE.equals(filters.getDiscountOnly()));
		dumpedOnly.setSelected(Boolean.TRUE.equals(filters.getDumpedOnly()));
		maxCapitalField.setText(formatLong(filters.getMaxCapital()));
		useInventoryCoins.setSelected(false);
		minTotalProfitField.setText(formatLong(filters.getMinTotalProfit()));
		minGpPerHourField.setText(formatLong(filters.getMinGpPerHour()));
		minConfidenceField.setText(formatConfidencePercent(filters.getMinConfidenceScore()));
		suppressEvents = false;
		saveToConfig();
		refreshCoinsLabel();
	}

	/** Clears every manual filter without firing the change callback. */
	void clearAll()
	{
		suppressEvents = true;
		hideLowConfidence.setSelected(false);
		discountOnly.setSelected(false);
		dumpedOnly.setSelected(false);
		useInventoryCoins.setSelected(false);
		maxCapitalField.setText("");
		minNetProfitField.setText("");
		minRoiField.setText("");
		minTotalProfitField.setText("");
		minGpPerHourField.setText("");
		minConfidenceField.setText("");
		membersCombo.setSelectedIndex(0);
		suppressEvents = false;
		saveToConfig();
		refreshCoinsLabel();
	}

	void applyInventoryCoinsToField()
	{
		if (!advancedEnabled)
		{
			return;
		}
		if (coinBalanceService.hasCoins())
		{
			maxCapitalField.setText(String.valueOf(coinBalanceService.getCoins()));
			useInventoryCoins.setSelected(true);
			persistAndNotify();
		}
	}

	private void buildAdvancedBody()
	{
		advancedBody.setLayout(new BoxLayout(advancedBody, BoxLayout.Y_AXIS));
		PluginUi.transparent(advancedBody);
		advancedBody.add(PluginUi.labeledField("Min total profit", minTotalProfitField));
		advancedBody.add(PluginUi.gap(6));
		advancedBody.add(PluginUi.labeledField("Min GP / hr", minGpPerHourField));
		advancedBody.add(PluginUi.gap(6));
		advancedBody.add(PluginUi.labeledField("Min confidence %", minConfidenceField));
		minConfidenceField.setToolTipText("0–100, e.g. 55");
		advancedBody.add(PluginUi.gap(8));
		advancedBody.add(PluginUi.caption("Quality"));
		advancedBody.add(hideLowConfidence);
		hideLowConfidence.setToolTipText("Hides items below the engine low-confidence threshold.");
		advancedBody.add(discountOnly);
		discountOnly.setToolTipText("Passive est. buy is below the in-game GE guide price.");
		advancedBody.add(dumpedOnly);
		dumpedOnly.setToolTipText("Trading below 33% of GE guide and crashed under recent 1h norms.");
		advancedBody.add(PluginUi.gap(6));
		advancedBody.add(PluginUi.labeledField("Max capital", maxCapitalField));
		advancedBody.add(PluginUi.gap(4));
		advancedBody.add(useInventoryCoins);
	}

	private void wireListeners()
	{
		DocumentListener textListener = new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				persistAndNotify();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				persistAndNotify();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				persistAndNotify();
			}
		};

		maxCapitalField.getDocument().addDocumentListener(textListener);
		minNetProfitField.getDocument().addDocumentListener(textListener);
		minRoiField.getDocument().addDocumentListener(textListener);
		minConfidenceField.getDocument().addDocumentListener(textListener);
		minTotalProfitField.getDocument().addDocumentListener(textListener);
		minGpPerHourField.getDocument().addDocumentListener(textListener);

		javax.swing.event.ChangeListener toggleListener = e -> persistAndNotify();
		hideLowConfidence.addChangeListener(toggleListener);
		discountOnly.addChangeListener(toggleListener);
		dumpedOnly.addChangeListener(toggleListener);
		useInventoryCoins.addChangeListener(toggleListener);
		membersCombo.addActionListener(e -> persistAndNotify());

		maxCapitalField.addFocusListener(new FocusAdapter()
		{
			@Override
			public void focusGained(FocusEvent e)
			{
				if (useInventoryCoins.isSelected())
				{
					useInventoryCoins.setSelected(false);
				}
			}
		});
	}

	private void persistAndNotify()
	{
		if (suppressEvents)
		{
			return;
		}
		saveToConfig();
		onChange.run();
	}

	private void restoreFromConfig()
	{
		suppressEvents = true;
		hideLowConfidence.setSelected(config.marketHideLowConfidence());
		discountOnly.setSelected(config.marketDiscountOnly());
		dumpedOnly.setSelected(config.marketDumpedOnly());
		useInventoryCoins.setSelected(config.marketUseInventoryCoins());
		maxCapitalField.setText(config.marketMaxCapital());
		minNetProfitField.setText(config.marketMinNetProfit());
		minRoiField.setText(config.marketMinRoiPercent());
		minTotalProfitField.setText(config.marketMinTotalProfit());
		minGpPerHourField.setText(config.marketMinGpPerHour());
		minConfidenceField.setText(config.marketMinConfidencePercent());

		String members = config.marketMembersFilter();
		int index = 0;
		for (int i = 0; i < MEMBERS_VALUES.length; i++)
		{
			if (MEMBERS_VALUES[i].equals(members))
			{
				index = i;
				break;
			}
		}
		membersCombo.setSelectedIndex(index);
		suppressEvents = false;
		refreshCoinsLabel();
	}

	private void saveToConfig()
	{
		configManager.setConfiguration(
			FlipFinderConfig.GROUP,
			"marketHideLowConfidence",
			hideLowConfidence.isSelected()
		);
		configManager.setConfiguration(
			FlipFinderConfig.GROUP,
			"marketDiscountOnly",
			discountOnly.isSelected()
		);
		configManager.setConfiguration(
			FlipFinderConfig.GROUP,
			"marketDumpedOnly",
			dumpedOnly.isSelected()
		);
		configManager.setConfiguration(
			FlipFinderConfig.GROUP,
			"marketUseInventoryCoins",
			useInventoryCoins.isSelected()
		);
		configManager.setConfiguration(FlipFinderConfig.GROUP, "marketMaxCapital", maxCapitalField.getText().trim());
		configManager.setConfiguration(FlipFinderConfig.GROUP, "marketMinNetProfit", minNetProfitField.getText().trim());
		configManager.setConfiguration(FlipFinderConfig.GROUP, "marketMinRoiPercent", minRoiField.getText().trim());
		configManager.setConfiguration(FlipFinderConfig.GROUP, "marketMinTotalProfit", minTotalProfitField.getText().trim());
		configManager.setConfiguration(FlipFinderConfig.GROUP, "marketMinGpPerHour", minGpPerHourField.getText().trim());
		configManager.setConfiguration(
			FlipFinderConfig.GROUP,
			"marketMinConfidencePercent",
			minConfidenceField.getText().trim()
		);
		int membersIndex = Math.max(0, membersCombo.getSelectedIndex());
		configManager.setConfiguration(FlipFinderConfig.GROUP, "marketMembersFilter", MEMBERS_VALUES[membersIndex]);
	}

	private static boolean hasText(String raw)
	{
		return raw != null && !raw.trim().isEmpty();
	}

	private static Long parseLong(String raw)
	{
		if (raw == null)
		{
			return null;
		}
		String trimmed = raw.trim().replace(",", "");
		if (trimmed.isEmpty())
		{
			return null;
		}
		try
		{
			return Long.parseLong(trimmed);
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	private static Double parseDouble(String raw)
	{
		if (raw == null)
		{
			return null;
		}
		String trimmed = raw.trim().replace(",", "");
		if (trimmed.isEmpty())
		{
			return null;
		}
		try
		{
			return Double.parseDouble(trimmed);
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	private static Double parseConfidencePercent(String raw)
	{
		Double value = parseDouble(raw);
		if (value == null)
		{
			return null;
		}
		return Math.max(0, Math.min(100, value)) / 100.0;
	}

	private static String formatLong(Long value)
	{
		return value != null ? String.valueOf(value) : "";
	}

	private static String formatDouble(Double value)
	{
		return value != null ? String.valueOf(value) : "";
	}

	private static String formatConfidencePercent(Double score)
	{
		if (score == null)
		{
			return "";
		}
		return String.valueOf(Math.round(score * 100.0));
	}

	private void styleMembersCombo()
	{
		membersCombo.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		membersCombo.setForeground(Color.WHITE);
	}
}
