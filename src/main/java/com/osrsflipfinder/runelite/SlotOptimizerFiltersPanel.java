package com.osrsflipfinder.runelite;

import java.awt.Color;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ColorScheme;

/** Collapsible slot-optimizer filters (persisted separately from the market list). */
class SlotOptimizerFiltersPanel
{
	private static final String[] MEMBERS_VALUES = { "all", "members", "f2p" };
	private static final String[] MEMBERS_LABELS = { "All worlds", "Members only", "F2P only" };

	private final FlipFinderConfig config;
	private final ConfigManager configManager;
	private final Runnable onChange;

	private final JCheckBox hideLowConfidence = PluginUi.checkBox("Hide low confidence", false);
	private final JCheckBox discountOnly = PluginUi.checkBox("Discount only", false);
	private final JCheckBox dumpedOnly = PluginUi.checkBox("Dumped only", false);
	private final JTextField minNetProfitField = PluginUi.textField("");
	private final JTextField minRoiField = PluginUi.textField("");
	private final JTextField minTotalProfitField = PluginUi.textField("");
	private final JTextField minGpPerHourField = PluginUi.textField("");
	private final JTextField minConfidenceField = PluginUi.textField("");
	private final JComboBox<String> membersCombo = new JComboBox<>(new DefaultComboBoxModel<>(MEMBERS_LABELS));
	private final JLabel advancedHint = PluginUi.hint("Pro unlocks quality filters and advanced numeric filters.");

	private final JPanel advancedBody = new JPanel();
	private PluginUi.CollapsibleSection section;
	private boolean advancedEnabled = false;
	private boolean suppressEvents = false;

	SlotOptimizerFiltersPanel(
		FlipFinderConfig config,
		ConfigManager configManager,
		Runnable onChange
	)
	{
		this.config = config;
		this.configManager = configManager;
		this.onChange = onChange;

		membersCombo.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		membersCombo.setForeground(Color.WHITE);
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
		minTotalProfitField.setEnabled(enabled);
		minGpPerHourField.setEnabled(enabled);
		minConfidenceField.setEnabled(enabled);
	}

	boolean hasIgnoredAdvancedFilters()
	{
		if (advancedEnabled)
		{
			return false;
		}
		return config.slotOptHideLowConfidence()
			|| config.slotOptDiscountOnly()
			|| config.slotOptDumpedOnly()
			|| hasText(config.slotOptMinTotalProfit())
			|| hasText(config.slotOptMinGpPerHour())
			|| hasText(config.slotOptMinConfidencePercent());
	}

	String getEntitlementNotice()
	{
		if (!hasIgnoredAdvancedFilters())
		{
			return null;
		}
		return "Pro filters are saved but not applied on this account.";
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
			filters.setMinTotalProfit(parseLong(minTotalProfitField.getText()));
			filters.setMinGpPerHour(parseLong(minGpPerHourField.getText()));
			filters.setMinConfidenceScore(parseConfidencePercent(minConfidenceField.getText()));
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
		minTotalProfitField.setText(formatLong(filters.getMinTotalProfit()));
		minGpPerHourField.setText(formatLong(filters.getMinGpPerHour()));
		minConfidenceField.setText(formatConfidencePercent(filters.getMinConfidenceScore()));
		suppressEvents = false;
		saveToConfig();
	}

	String filtersFingerprint()
	{
		return hideLowConfidence.isSelected()
			+ ":" + discountOnly.isSelected()
			+ ":" + dumpedOnly.isSelected()
			+ ":" + minNetProfitField.getText().trim()
			+ ":" + minRoiField.getText().trim()
			+ ":" + membersCombo.getSelectedIndex()
			+ ":" + minTotalProfitField.getText().trim()
			+ ":" + minGpPerHourField.getText().trim()
			+ ":" + minConfidenceField.getText().trim()
			+ ":" + advancedEnabled;
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
		advancedBody.add(discountOnly);
		advancedBody.add(dumpedOnly);
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

		minNetProfitField.getDocument().addDocumentListener(textListener);
		minRoiField.getDocument().addDocumentListener(textListener);
		minConfidenceField.getDocument().addDocumentListener(textListener);
		minTotalProfitField.getDocument().addDocumentListener(textListener);
		minGpPerHourField.getDocument().addDocumentListener(textListener);

		javax.swing.event.ChangeListener toggleListener = e -> persistAndNotify();
		hideLowConfidence.addChangeListener(toggleListener);
		discountOnly.addChangeListener(toggleListener);
		dumpedOnly.addChangeListener(toggleListener);
		membersCombo.addActionListener(e -> persistAndNotify());
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
		hideLowConfidence.setSelected(config.slotOptHideLowConfidence());
		discountOnly.setSelected(config.slotOptDiscountOnly());
		dumpedOnly.setSelected(config.slotOptDumpedOnly());
		minNetProfitField.setText(config.slotOptMinNetProfit());
		minRoiField.setText(config.slotOptMinRoiPercent());
		minTotalProfitField.setText(config.slotOptMinTotalProfit());
		minGpPerHourField.setText(config.slotOptMinGpPerHour());
		minConfidenceField.setText(config.slotOptMinConfidencePercent());

		String members = config.slotOptMembersFilter();
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
	}

	private void saveToConfig()
	{
		configManager.setConfiguration(
			FlipFinderConfig.GROUP,
			"slotOptHideLowConfidence",
			hideLowConfidence.isSelected()
		);
		configManager.setConfiguration(
			FlipFinderConfig.GROUP,
			"slotOptDiscountOnly",
			discountOnly.isSelected()
		);
		configManager.setConfiguration(
			FlipFinderConfig.GROUP,
			"slotOptDumpedOnly",
			dumpedOnly.isSelected()
		);
		configManager.setConfiguration(FlipFinderConfig.GROUP, "slotOptMinNetProfit", minNetProfitField.getText().trim());
		configManager.setConfiguration(FlipFinderConfig.GROUP, "slotOptMinRoiPercent", minRoiField.getText().trim());
		configManager.setConfiguration(FlipFinderConfig.GROUP, "slotOptMinTotalProfit", minTotalProfitField.getText().trim());
		configManager.setConfiguration(FlipFinderConfig.GROUP, "slotOptMinGpPerHour", minGpPerHourField.getText().trim());
		configManager.setConfiguration(
			FlipFinderConfig.GROUP,
			"slotOptMinConfidencePercent",
			minConfidenceField.getText().trim()
		);
		int membersIndex = Math.max(0, membersCombo.getSelectedIndex());
		configManager.setConfiguration(FlipFinderConfig.GROUP, "slotOptMembersFilter", MEMBERS_VALUES[membersIndex]);
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
}
