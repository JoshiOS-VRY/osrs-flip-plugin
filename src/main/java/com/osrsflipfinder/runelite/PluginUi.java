package com.osrsflipfinder.runelite;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.SwingUtil;

/**
 * Shared RuneLite-native styling for the FlipX sidebar.
 *
 * <p>Content must stay within {@link PluginPanel#PANEL_WIDTH}. The client reserves
 * {@link PluginPanel#SCROLLBAR_WIDTH} beside that column via {@code PluginPanel}'s
 * wrapped panel — do not add a nested {@code JScrollPane} or manual gutter padding.
 */
final class PluginUi
{
	static final Color GOLD = new Color(0xD9B45B);
	static final Color GOLD_DIM = new Color(0x8A6F3A);
	static final Color POSITIVE = ColorScheme.PROGRESS_COMPLETE_COLOR;
	static final Color NEGATIVE = ColorScheme.PROGRESS_ERROR_COLOR;
	static final Color WARNING = ColorScheme.PROGRESS_INPROGRESS_COLOR;

	/** RuneScape bitmap fonts only cover ASCII — avoid Unicode arrows/symbols in labels. */
	static final String PLACEHOLDER = "-";

	static final int CONTENT_WIDTH = PluginPanel.PANEL_WIDTH;
	static final int PADDING = PluginPanel.BORDER_OFFSET;

	private static final EmptyBorder CARD_PADDING = new EmptyBorder(PADDING, PADDING, PADDING, PADDING);
	private static final MatteBorder SECTION_RULE = new MatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR);

	private PluginUi()
	{
	}

	static void transparent(JComponent component)
	{
		component.setBackground(ColorScheme.DARK_GRAY_COLOR);
	}

	static JPanel gap(int height)
	{
		JPanel gap = new JPanel();
		gap.setBackground(ColorScheme.DARK_GRAY_COLOR);
		SidebarContentPanel.lockWidthFixed(gap, height);
		return gap;
	}

	static JPanel header(String title, String subtitle, JLabel statusBadge)
	{
		JPanel header = new SidebarContentPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		transparent(header);
		header.setBorder(new EmptyBorder(0, 0, PADDING, 0));

		JLabel titleLabel = new JLabel(title);
		titleLabel.setForeground(Color.WHITE);
		titleLabel.setFont(FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, 16f));
		titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel subtitleLabel = wrappedCaption(subtitle);
		subtitleLabel.setBorder(new EmptyBorder(2, 0, 6, 0));
		subtitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

		header.add(titleLabel);
		header.add(subtitleLabel);

		if (statusBadge != null)
		{
			statusBadge.setAlignmentX(Component.LEFT_ALIGNMENT);
			header.add(statusBadge);
		}
		SidebarContentPanel.lockWidth(header);
		return header;
	}

	static JPanel sectionHeader(String title)
	{
		JPanel row = new SidebarContentPanel();
		row.setLayout(new BorderLayout());
		transparent(row);
		row.setBorder(new CompoundBorder(SECTION_RULE, new EmptyBorder(PADDING, 0, PADDING, 0)));
		row.add(sectionTitle(title), BorderLayout.CENTER);
		SidebarContentPanel.lockWidth(row);
		return row;
	}

	static JLabel statusBadge(PluginState state)
	{
		String text;
		Color color;
		switch (state)
		{
			case CONNECTED:
				text = "Connected";
				color = POSITIVE;
				break;
			case SYNCING:
				text = "Syncing";
				color = WARNING;
				break;
			case UPGRADE_REQUIRED:
				text = "Upgrade";
				color = GOLD;
				break;
			case REPAIR_REQUIRED:
				text = "Re-pair";
				color = NEGATIVE;
				break;
			case ERROR:
				text = "Retrying";
				color = WARNING;
				break;
			case NOT_PAIRED:
			default:
				text = "Not paired";
				color = ColorScheme.LIGHT_GRAY_COLOR;
				break;
		}

		JLabel badge = new JLabel(text, JLabel.CENTER);
		badge.setFont(FontManager.getRunescapeSmallFont());
		badge.setForeground(color);
		badge.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(color.darker(), 1, true),
			new EmptyBorder(3, 8, 3, 8)
		));
		return badge;
	}

	static JPanel card()
	{
		JPanel card = new SidebarContentPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR, 1, true),
			CARD_PADDING
		));
		card.setAlignmentX(Component.LEFT_ALIGNMENT);
		return card;
	}

	static CollapsibleSection collapsibleSection(String title, JPanel body, boolean startOpen)
	{
		JPanel wrapper = new SidebarContentPanel();
		wrapper.setLayout(new BorderLayout());
		transparent(wrapper);
		wrapper.setBorder(new EmptyBorder(0, 0, PADDING, 0));
		wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
		SidebarContentPanel.lockWidth(wrapper);

		JPanel header = new JPanel(new BorderLayout(6, 0));
		transparent(header);
		header.setBorder(new CompoundBorder(SECTION_RULE, new EmptyBorder(PADDING, 0, PADDING, 0)));
		header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		JButton toggle = new JButton(startOpen ? "v" : ">");
		styleIconButton(toggle);
		toggle.setPreferredSize(new Dimension(18, 18));

		JLabel titleLabel = sectionTitle(title);
		header.add(toggle, BorderLayout.WEST);
		header.add(titleLabel, BorderLayout.CENTER);

		body.setVisible(startOpen);
		body.setBorder(new EmptyBorder(0, 0, 4, 0));

		wrapper.add(header, BorderLayout.NORTH);
		wrapper.add(body, BorderLayout.CENTER);

		MouseAdapter toggleListener = new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				boolean open = !body.isVisible();
				body.setVisible(open);
				toggle.setText(open ? "v" : ">");
				wrapper.revalidate();
				wrapper.repaint();
			}
		};
		header.addMouseListener(toggleListener);
		toggle.addActionListener(e -> toggleListener.mousePressed(null));

		return new CollapsibleSection(wrapper, body, toggle);
	}

	static JLabel sectionTitle(String text)
	{
		JLabel label = new JLabel(text);
		label.setForeground(ColorScheme.BRAND_ORANGE);
		label.setFont(FontManager.getRunescapeBoldFont());
		return label;
	}

	static JLabel caption(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		return label;
	}

	static JLabel wrappedCaption(String text)
	{
		return wrappedCaption(text, CONTENT_WIDTH - 2);
	}

	static JLabel cardHint(String text)
	{
		return wrappedCaption(text, CONTENT_WIDTH - (PADDING * 2) - 6);
	}

	static JLabel wrappedCaption(String text, int wrapWidth)
	{
		JLabel label = new JLabel("<html><div style='width:" + Math.max(120, wrapWidth) + "px;color:#999999;'>"
			+ escapeHtml(text) + "</div></html>");
		label.setFont(FontManager.getRunescapeSmallFont());
		return label;
	}

	static JLabel hint(String text)
	{
		return wrappedCaption(text);
	}

	static JLabel loadingCaption(String text)
	{
		JLabel label = caption(text);
		label.setForeground(WARNING);
		return label;
	}

	/** Session stats hero — gold top rule, large signed profit, caption. */
	static JPanel sessionProfitHero(JLabel profitLabel, String caption)
	{
		JPanel hero = new SidebarContentPanel();
		hero.setLayout(new BoxLayout(hero, BoxLayout.Y_AXIS));
		hero.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		hero.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(2, 0, 0, 0, GOLD),
				BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR, 1, true)
			),
			new EmptyBorder(8, 8, 6, 8)
		));
		hero.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel captionLabel = new JLabel(caption, JLabel.CENTER);
		captionLabel.setFont(FontManager.getRunescapeSmallFont());
		captionLabel.setForeground(GOLD);
		captionLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

		profitLabel.setHorizontalAlignment(JLabel.CENTER);
		profitLabel.setFont(FontManager.getRunescapeBoldFont().deriveFont(18f));
		profitLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

		hero.add(captionLabel);
		hero.add(profitLabel);
		SidebarContentPanel.lockWidthFixed(hero, 56);
		return hero;
	}

	/** Four-up hero metrics in a 2x2 grid — fits large GP values in the narrow sidebar. */
	static JPanel detailHeroGrid(JComponent... stats)
	{
		JPanel grid = new SidebarContentPanel();
		grid.setLayout(new GridLayout(2, 2, 4, 4));
		grid.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		grid.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(2, 0, 0, 0, GOLD_DIM),
				BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR, 1, true)
			),
			new EmptyBorder(6, 4, 4, 4)
		));
		grid.setAlignmentX(Component.LEFT_ALIGNMENT);
		for (JComponent stat : stats)
		{
			grid.add(stat);
		}
		SidebarContentPanel.lockWidthFixed(grid, 72);
		return grid;
	}

	static JLabel fieldLabel(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setBorder(new EmptyBorder(0, 0, 3, 0));
		return label;
	}

	static JLabel errorLabel()
	{
		JLabel label = new JLabel(" ");
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(NEGATIVE);
		return label;
	}

	static JTextField textField(String value)
	{
		JTextField field = new JTextField(value);
		field.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		field.setForeground(Color.WHITE);
		field.setCaretColor(Color.WHITE);
		field.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
			new EmptyBorder(5, 6, 5, 6)
		));
		return field;
	}

	static JPasswordField passwordField()
	{
		JPasswordField field = new JPasswordField();
		field.setEchoChar('*');
		field.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		field.setForeground(Color.WHITE);
		field.setCaretColor(Color.WHITE);
		field.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
			new EmptyBorder(5, 6, 5, 6)
		));
		return field;
	}

	static JButton primaryButton(String text)
	{
		JButton button = new JButton(text);
		styleButton(button);
		button.setForeground(GOLD);
		return button;
	}

	static JButton secondaryButton(String text)
	{
		JButton button = new JButton(text);
		styleButton(button);
		button.setForeground(Color.WHITE);
		return button;
	}

	static JButton linkButton(String text)
	{
		JButton button = new JButton(text);
		styleButton(button);
		button.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		button.setFont(FontManager.getRunescapeSmallFont());
		return button;
	}

	static JButton backButton(Runnable onBack)
	{
		JButton button = new JButton("< Back");
		SwingUtil.removeButtonDecorations(button);
		button.setHorizontalAlignment(JButton.LEFT);
		button.setFont(FontManager.getRunescapeSmallFont());
		button.setForeground(GOLD);
		button.setBackground(ColorScheme.DARK_GRAY_COLOR);
		button.setBorder(new EmptyBorder(2, 0, 6, 0));
		button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		button.setFocusPainted(false);
		button.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				button.setForeground(Color.WHITE);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				button.setForeground(GOLD);
			}
		});
		button.addActionListener(e -> onBack.run());
		fullWidth(button);
		return button;
	}

	static JButton externalLinkButton(String text)
	{
		JButton button = linkButton(text + " >");
		button.setForeground(GOLD_DIM);
		button.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				button.setForeground(GOLD);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				button.setForeground(GOLD_DIM);
			}
		});
		return button;
	}

	static JPanel buttonRow(JComponent... buttons)
	{
		JPanel row = new SidebarContentPanel();
		row.setLayout(new GridLayout(buttons.length, 1, 0, 6));
		transparent(row);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		for (JComponent button : buttons)
		{
			row.add(button);
		}
		SidebarContentPanel.lockWidth(row);
		return row;
	}

	static JPanel labeledField(String label, Component field)
	{
		JPanel panel = new SidebarContentPanel();
		panel.setLayout(new BorderLayout(0, 0));
		transparent(panel);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(fieldLabel(label), BorderLayout.NORTH);
		panel.add(field, BorderLayout.CENTER);
		SidebarContentPanel.lockWidth(panel);
		return panel;
	}

	static void styleCombo(JComboBox<?> combo)
	{
		combo.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		combo.setForeground(Color.WHITE);
	}

	static JPanel summaryStrip(JComponent... stats)
	{
		int columns = stats.length;
		JPanel strip = new SidebarContentPanel();
		strip.setLayout(new GridLayout(1, columns, 4, 0));
		strip.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		strip.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(2, 0, 0, 0, GOLD_DIM),
				BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR, 1, true)
			),
			new EmptyBorder(6, 4, 4, 4)
		));
		strip.setAlignmentX(Component.LEFT_ALIGNMENT);
		for (JComponent stat : stats)
		{
			strip.add(stat);
		}
		SidebarContentPanel.lockWidthFixed(strip, 38);
		return strip;
	}

	static JPanel statCell(JLabel valueLabel, String caption)
	{
		JPanel cell = new JPanel();
		cell.setLayout(new BoxLayout(cell, BoxLayout.Y_AXIS));
		cell.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		valueLabel.setForeground(Color.WHITE);
		valueLabel.setHorizontalAlignment(JLabel.CENTER);
		valueLabel.setFont(FontManager.getRunescapeBoldFont());
		valueLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
		JLabel captionLabel = new JLabel(caption, JLabel.CENTER);
		captionLabel.setFont(FontManager.getRunescapeSmallFont());
		captionLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		captionLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
		cell.add(valueLabel);
		cell.add(captionLabel);
		SidebarContentPanel.lockWidthFixed(cell, 30);
		return cell;
	}

	/** Bordered block of label/value rows for item detail — fixed row heights, no vertical stretch. */
	static JPanel statBlock()
	{
		JPanel block = new SidebarContentPanel();
		block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));
		block.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		block.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR, 1, true),
			new EmptyBorder(6, 8, 6, 8)
		));
		block.setAlignmentX(Component.LEFT_ALIGNMENT);
		SidebarContentPanel.lockWidth(block);
		return block;
	}

	static void addStatLine(JPanel block, String label, String value)
	{
		addStatLine(block, label, value, Color.WHITE);
	}

	static void addStatLine(JPanel block, String label, String value, Color valueColor)
	{
		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		JLabel labelLabel = caption(label);
		JLabel valueLabel = new JLabel(value);
		valueLabel.setForeground(valueColor);
		valueLabel.setFont(FontManager.getRunescapeSmallFont());
		valueLabel.setHorizontalAlignment(JLabel.RIGHT);
		row.add(labelLabel, BorderLayout.WEST);
		row.add(valueLabel, BorderLayout.EAST);
		SidebarContentPanel.lockWidthFixed(row, 16);
		block.add(row);
	}

	static void finalizeStatBlock(JPanel block)
	{
		int rows = block.getComponentCount();
		int height = Math.max(24, rows * 16 + 14);
		SidebarContentPanel.lockWidthFixed(block, height);
	}

	static JPanel listContainer()
	{
		JPanel container = new SidebarContentPanel();
		container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
		container.setBackground(ColorScheme.DARK_GRAY_COLOR);
		container.setAlignmentX(Component.LEFT_ALIGNMENT);
		SidebarContentPanel.lockWidth(container);
		return container;
	}

	static void fullWidth(JComponent component)
	{
		component.setAlignmentX(Component.LEFT_ALIGNMENT);
		int height = component.getPreferredSize().height;
		if (height <= 0)
		{
			height = 28;
		}
		SidebarContentPanel.lockWidthFixed(component, height);
	}

	static void fullWidthGrow(JComponent component)
	{
		SidebarContentPanel.lockWidth(component);
	}

	static void lockRowHeight(JComponent component, int height)
	{
		component.setAlignmentX(Component.LEFT_ALIGNMENT);
		SidebarContentPanel.lockWidthFixed(component, height);
	}

	static JPanel subViewHeader(String title, Runnable onBack, JLabel statusLabel)
	{
		JPanel header = new SidebarContentPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		transparent(header);
		header.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.add(backButton(onBack));
		header.add(gap(6));
		JLabel titleLabel = sectionTitle(title);
		titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.add(titleLabel);
		if (statusLabel != null)
		{
			statusLabel.setBorder(new EmptyBorder(4, 0, 0, 0));
			statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
			header.add(statusLabel);
		}
		header.add(gap(8));
		SidebarContentPanel.lockWidth(header);
		return header;
	}

	static JPanel statGrid()
	{
		return statBlock();
	}

	static JPanel compactStatGrid(int columns)
	{
		return statBlock();
	}

	static void addStat(JPanel block, String label, String value)
	{
		addStatLine(block, label, value);
	}

	static void addCompactStat(JPanel block, String label, String value, Color valueColor)
	{
		addStatLine(block, label, value, valueColor);
	}

	/** Wrap card content so CardLayout does not stretch it to the viewport height. */
	static JPanel wrapNorth(JComponent content)
	{
		JPanel wrap = new SidebarContentPanel();
		wrap.setLayout(new BorderLayout());
		transparent(wrap);
		wrap.add(content, BorderLayout.NORTH);
		SidebarContentPanel.lockWidth(wrap);
		return wrap;
	}

	static JLabel truncatedLabel(String text, int maxChars)
	{
		String display = text;
		if (text != null && text.length() > maxChars)
		{
			display = text.substring(0, maxChars - 1) + "…";
		}
		JLabel label = new JLabel(display);
		label.setToolTipText(text);
		return label;
	}

	static JCheckBox checkBox(String label, boolean selected)
	{
		JCheckBox box = new JCheckBox(label, selected);
		box.setBackground(ColorScheme.DARK_GRAY_COLOR);
		box.setForeground(Color.WHITE);
		box.setFont(FontManager.getRunescapeSmallFont());
		box.setFocusPainted(false);
		return box;
	}

	private static void styleButton(JButton button)
	{
		SwingUtil.removeButtonDecorations(button);
		button.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		button.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR, 1, true),
			new EmptyBorder(6, 10, 6, 10)
		));
	}

	private static void styleIconButton(JButton button)
	{
		SwingUtil.removeButtonDecorations(button);
		button.setForeground(ColorScheme.BRAND_ORANGE);
		button.setBackground(ColorScheme.DARK_GRAY_COLOR);
	}

	private static String escapeHtml(String text)
	{
		if (text == null)
		{
			return "";
		}
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	static final class CollapsibleSection
	{
		private final JPanel wrapper;
		private final JPanel body;
		private final JButton toggle;

		private CollapsibleSection(JPanel wrapper, JPanel body, JButton toggle)
		{
			this.wrapper = wrapper;
			this.body = body;
			this.toggle = toggle;
		}

		JPanel wrapper()
		{
			return wrapper;
		}

		void setExpanded(boolean expanded)
		{
			body.setVisible(expanded);
			toggle.setText(expanded ? "v" : ">");
			wrapper.revalidate();
			wrapper.repaint();
		}
	}
}
