package com.osrsflipfinder.runelite;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.LookAndFeel;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.util.SwingUtil;

/**
 * Shared RuneLite-native styling for the FlipX sidebar.
 *
 * <p>Content must stay within {@link PluginPanel#PANEL_WIDTH}. The client reserves
 * {@link PluginPanel#SCROLLBAR_WIDTH} beside that column via {@code PluginPanel}'s
 * wrapped panel - do not add a nested {@code JScrollPane} or manual gutter padding.
 */
final class PluginUi
{
	static final Color GOLD = new Color(0xD9B45B);
	static final Color GOLD_DIM = new Color(0x8A6F3A);
	static final Color DISCORD_BLURPLE = new Color(0x58, 0x65, 0xF2);
	static final Color POSITIVE = ColorScheme.PROGRESS_COMPLETE_COLOR;
	static final Color NEGATIVE = ColorScheme.PROGRESS_ERROR_COLOR;
	static final Color WARNING = ColorScheme.PROGRESS_INPROGRESS_COLOR;
	/** Soft secondary values in HTML metric lines. */
	static final Color TEXT_SOFT = new Color(0xCCCCCC);
	/** Dim separators / tertiary text in HTML metric lines. */
	static final Color TEXT_DIM = new Color(0x888888);
	/** Hint/caption gray for wrapped captions. */
	static final Color TEXT_HINT = new Color(0x999999);

	/** RuneScape bitmap fonts only cover ASCII - avoid Unicode arrows/symbols in labels. */
	static final String PLACEHOLDER = "-";

	static final int CONTENT_WIDTH = PluginPanel.PANEL_WIDTH;
	static final int INNER_WIDTH = SidebarContentPanel.INNER_WIDTH;
	static final int PADDING = PluginPanel.BORDER_OFFSET;

	/** Spacing scale - see docs/DESIGN_SYSTEM.md */
	static final int SPACING_XS = 4;
	static final int SPACING_SM = 6;
	static final int SPACING_MD = 8;
	static final int SPACING_LG = 12;
	static final int SPACING_XL = PADDING;

	private static final EmptyBorder CARD_PADDING = new EmptyBorder(PADDING, PADDING, PADDING, PADDING);
	private static final MatteBorder SECTION_RULE = new MatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR);

	private static final int CHECKBOX_GLYPH = 14;
	/** Left inset matching {@link #checkboxGroup} icon column (rule + padding). */
	private static final int CHECKBOX_ICON_INDENT = 2 + SPACING_SM;
	/** Left inset aligning nested checkbox label with labels inside {@link #checkboxGroup}. */
	private static final int CHECKBOX_LABEL_INDENT = CHECKBOX_ICON_INDENT + CHECKBOX_GLYPH + SPACING_SM;

	private static final Icon CHECKBOX_OFF = checkboxIcon(false, true);
	private static final Icon CHECKBOX_ON = checkboxIcon(true, true);
	private static final Icon CHECKBOX_OFF_DISABLED = checkboxIcon(false, false);
	private static final Icon CHECKBOX_ON_DISABLED = checkboxIcon(true, false);

	private PluginUi()
	{
	}

	/** Standard inset for the main sidebar column (aligns with RuneLite {@link PluginPanel}). */
	static EmptyBorder pageInsets()
	{
		return new EmptyBorder(PADDING, PADDING, PADDING, PADDING);
	}

	/** Wrap width for multi-line body copy in the content column. */
	static int bodyTextWrapWidth()
	{
		return INNER_WIDTH;
	}

	/** Wrap width for text beside a 32px icon in a slot/opportunity row. */
	static int rowTextWrapWidth()
	{
		return INNER_WIDTH - 32 - (SPACING_SM * 3) - 4;
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

	/** Vertical stack with {@link #SPACING_SM} between children. */
	static JPanel verticalStack(JComponent... children)
	{
		JPanel stack = new SidebarContentPanel();
		stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
		transparent(stack);
		stack.setAlignmentX(Component.LEFT_ALIGNMENT);
		for (int i = 0; i < children.length; i++)
		{
			if (children[i] == null)
			{
				continue;
			}
			children[i].setAlignmentX(Component.LEFT_ALIGNMENT);
			stack.add(children[i]);
			if (i < children.length - 1)
			{
				stack.add(gap(SPACING_SM));
			}
		}
		SidebarContentPanel.lockWidth(stack);
		return stack;
	}

	/** Card wrapping a form block (browse controls, connection form, etc.). */
	static JPanel formCard(JComponent inner)
	{
		JPanel shell = card();
		inner.setAlignmentX(Component.LEFT_ALIGNMENT);
		shell.add(inner);
		return shell;
	}

	/** Indented checkbox group (e.g. Quality filters). */
	static JPanel checkboxGroup(JCheckBox... boxes)
	{
		JPanel group = new SidebarContentPanel();
		group.setLayout(new BoxLayout(group, BoxLayout.Y_AXIS));
		transparent(group);
		group.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 2, 0, 0, ColorScheme.MEDIUM_GRAY_COLOR),
			new EmptyBorder(0, SPACING_SM, 0, 0)
		));
		group.setAlignmentX(Component.LEFT_ALIGNMENT);
		for (JCheckBox box : boxes)
		{
			styleCheckBox(box);
			box.setAlignmentX(Component.LEFT_ALIGNMENT);
			group.add(box);
		}
		SidebarContentPanel.lockWidth(group);
		return group;
	}

	/**
	 * FlipX sidebar checkboxes use custom icons - FlatLaf/RuneLite defaults paint a checkmark the same
	 * tone as {@link ColorScheme#DARKER_GRAY_COLOR} panels (effectively invisible).
	 */
	static void styleCheckBox(JCheckBox box)
	{
		box.setFont(FontManager.getRunescapeSmallFont());
		box.setForeground(ColorScheme.TEXT_COLOR);
		box.setFocusPainted(false);
		box.setIconTextGap(SPACING_SM);
		box.setOpaque(false);
		LookAndFeel.installProperty(box, "contentAreaFilled", false);
		box.setIcon(CHECKBOX_OFF);
		box.setSelectedIcon(CHECKBOX_ON);
		box.setDisabledIcon(CHECKBOX_OFF_DISABLED);
		box.setDisabledSelectedIcon(CHECKBOX_ON_DISABLED);
		box.setBorder(new EmptyBorder(SPACING_XS, 0, SPACING_XS, 0));
	}

	private static Icon checkboxIcon(boolean selected, boolean enabled)
	{
		return new Icon()
		{
			@Override
			public void paintIcon(Component c, Graphics g, int x, int y)
			{
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				int w = CHECKBOX_GLYPH;
				int h = CHECKBOX_GLYPH;
				g2.setColor(ColorScheme.MEDIUM_GRAY_COLOR);
				g2.drawRect(x, y, w - 1, h - 1);
				g2.setColor(enabled ? ColorScheme.DARKER_GRAY_COLOR : ColorScheme.DARK_GRAY_COLOR);
				g2.fillRect(x + 1, y + 1, w - 2, h - 2);
				if (selected)
				{
					g2.setColor(enabled ? GOLD : ColorScheme.LIGHT_GRAY_COLOR);
					g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
					g2.drawLine(x + 3, y + 7, x + 6, y + 10);
					g2.drawLine(x + 6, y + 10, x + 11, y + 4);
				}
				g2.dispose();
			}

			@Override
			public int getIconWidth()
			{
				return CHECKBOX_GLYPH + 1;
			}

			@Override
			public int getIconHeight()
			{
				return CHECKBOX_GLYPH + 1;
			}
		};
	}

	static void insetCheckBox(JCheckBox box)
	{
		styleCheckBox(box);
	}

	/** Secondary control nested under a field (e.g. sort direction). */
	static JPanel indented(JComponent child)
	{
		JPanel panel = new SidebarContentPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		transparent(panel);
		int indent = child instanceof JCheckBox ? CHECKBOX_LABEL_INDENT : SPACING_LG;
		panel.setBorder(new EmptyBorder(0, indent, 0, 0));
		child.setAlignmentX(Component.LEFT_ALIGNMENT);
		if (child instanceof JCheckBox)
		{
			styleCheckBox((JCheckBox) child);
		}
		panel.add(child);
		SidebarContentPanel.lockWidth(panel);
		return panel;
	}

	/** Checkbox row aligned with the icon column inside {@link #checkboxGroup}. */
	static JPanel nestedCheckbox(JCheckBox box)
	{
		JPanel panel = new SidebarContentPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		transparent(panel);
		panel.setBorder(new EmptyBorder(0, CHECKBOX_ICON_INDENT, 0, 0));
		styleCheckBox(box);
		box.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(box);
		SidebarContentPanel.lockWidth(panel);
		return panel;
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
		return sectionHeader(sectionTitle(title));
	}

	static JPanel sectionHeader(JLabel titleLabel)
	{
		JPanel row = new SidebarContentPanel();
		row.setLayout(new BorderLayout());
		transparent(row);
		row.setBorder(new CompoundBorder(SECTION_RULE, new EmptyBorder(SPACING_MD, 0, SPACING_MD, 0)));
		row.add(titleLabel, BorderLayout.CENTER);
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

	/** Persistent community CTA — opens {@link CommunityLinks#DISCORD_INVITE_URL}. */
	static JPanel discordCommunityBanner(Runnable onJoin)
	{
		JPanel card = card();
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(2, 0, 0, 0, DISCORD_BLURPLE),
				BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR, 1, true)
			),
			CARD_PADDING
		));

		JLabel title = new JLabel("Join FlipX Discord");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		title.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel body = caption(" ");
		body.setAlignmentX(Component.LEFT_ALIGNMENT);
		setCardCaption(body, "Help, flips & announcements. Pairing help in #help.");

		JButton join = primaryButton("Open Discord");
		join.setAlignmentX(Component.LEFT_ALIGNMENT);
		join.addActionListener(e -> onJoin.run());

		card.add(title);
		card.add(gap(SPACING_XS));
		card.add(body);
		card.add(gap(SPACING_SM));
		card.add(join);
		SidebarContentPanel.lockWidth(card);
		return card;
	}

	static CollapsibleSection collapsibleSection(String title, JPanel body, boolean startOpen)
	{
		JPanel wrapper = new SidebarContentPanel();
		wrapper.setLayout(new BorderLayout());
		transparent(wrapper);
		wrapper.setBorder(new EmptyBorder(0, 0, SPACING_MD, 0));
		wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
		SidebarContentPanel.lockWidth(wrapper);

		JPanel header = new JPanel(new BorderLayout(6, 0));
		transparent(header);
		header.setBorder(new CompoundBorder(SECTION_RULE, new EmptyBorder(SPACING_MD, 0, SPACING_MD, 0)));
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
		return wrappedCaption(text, INNER_WIDTH);
	}

	static JLabel cardHint(String text)
	{
		return wrappedCaption(text, INNER_WIDTH - (PADDING * 2) - 6);
	}

	/** Hex color for inline HTML spans (always from {@link Color} tokens). */
	static String htmlColor(Color color)
	{
		return String.format("#%06x", color.getRGB() & 0xFFFFFF);
	}

	static String htmlSpan(Color color, String text)
	{
		return "<span style='color:" + htmlColor(color) + ";'>" + escapeHtml(text) + "</span>";
	}

	static String htmlSep()
	{
		return htmlSpan(TEXT_DIM, " | ");
	}

	static JLabel wrappedBody(String text, int wrapWidth, Color color, boolean bold)
	{
		Font font = bold ? FontManager.getRunescapeBoldFont() : FontManager.getRunescapeSmallFont();
		JLabel label = new JLabel("<html><div style='width:" + Math.max(80, wrapWidth) + "px;color:"
			+ htmlColor(color) + ";'>" + escapeHtml(text) + "</div></html>");
		label.setFont(font);
		return label;
	}

	static void stackLine(JPanel column, JComponent line)
	{
		line.setAlignmentX(Component.LEFT_ALIGNMENT);
		column.add(line);
		column.add(gap(SPACING_XS));
	}

	static JLabel wrappedCaption(String text, int wrapWidth)
	{
		JLabel label = new JLabel("<html><div style='width:" + Math.max(120, wrapWidth) + "px;color:"
			+ htmlColor(TEXT_HINT) + ";'>" + escapeHtml(text) + "</div></html>");
		label.setFont(FontManager.getRunescapeSmallFont());
		return label;
	}

	static JLabel hint(String text)
	{
		JLabel label = wrappedCaption(text, INNER_WIDTH);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		fullWidthGrow(label);
		return label;
	}

	/** Compact empty-state block for list panels. */
	static JPanel emptyState(String message)
	{
		JPanel panel = new SidebarContentPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		transparent(panel);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		JLabel body = hint(message);
		panel.add(body);
		fullWidthGrow(panel);
		return panel;
	}

	static void setMultilineCaption(JLabel label, String text)
	{
		setMultilineCaption(label, text, ColorScheme.LIGHT_GRAY_COLOR);
	}

	static void setMultilineCaption(JLabel label, String text, Color color)
	{
		setMultilineCaption(label, text, color, INNER_WIDTH);
	}

	static void setMultilineCaption(JLabel label, String text, Color color, int wrapWidth)
	{
		if (text == null || text.isBlank())
		{
			label.setText(" ");
			return;
		}
		label.setText(
			"<html><div style='width:" + Math.max(80, wrapWidth) + "px;color:" + htmlColor(color) + ";'>"
				+ escapeHtml(text) + "</div></html>"
		);
	}

	/** Caption inside a {@link #formCard} / {@link #card()} (accounts for card padding). */
	static void setCardCaption(JLabel label, String text)
	{
		setMultilineCaption(label, text, ColorScheme.LIGHT_GRAY_COLOR, cardBodyWrapWidth());
	}

	static void setSidebarError(JLabel label, String text)
	{
		if (text == null || text.isBlank())
		{
			label.setText(" ");
			return;
		}
		setMultilineCaption(label, text, NEGATIVE, INNER_WIDTH);
	}

	static JLabel loadingCaption(String text)
	{
		JLabel label = caption(text);
		label.setForeground(WARNING);
		return label;
	}

	/** Session stats hero - gold top rule, large signed profit, optional subline (height grows with text). */
	static JPanel sessionProfitHero(JLabel profitLabel, JLabel captionLabel, JLabel subLabel)
	{
		JPanel hero = new SidebarContentPanel();
		hero.setLayout(new BoxLayout(hero, BoxLayout.Y_AXIS));
		hero.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		hero.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(2, 0, 0, 0, GOLD),
				BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR, 1, true)
			),
			new EmptyBorder(8, 8, 8, 8)
		));
		hero.setAlignmentX(Component.LEFT_ALIGNMENT);

		captionLabel.setFont(FontManager.getRunescapeSmallFont());
		captionLabel.setForeground(GOLD);
		captionLabel.setHorizontalAlignment(JLabel.CENTER);
		captionLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

		profitLabel.setHorizontalAlignment(JLabel.CENTER);
		profitLabel.setFont(FontManager.getRunescapeBoldFont().deriveFont(18f));
		profitLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

		subLabel.setHorizontalAlignment(JLabel.CENTER);
		subLabel.setFont(FontManager.getRunescapeSmallFont());
		subLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		subLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

		hero.add(captionLabel);
		hero.add(profitLabel);
		hero.add(subLabel);
		SidebarContentPanel.lockWidth(hero);
		return hero;
	}

	/** Session stats hero - gold top rule, large signed profit, optional subline (height grows with text). */
	static JPanel sessionProfitHero(JLabel profitLabel, String caption, JLabel subLabel)
	{
		JLabel captionLabel = new JLabel(caption, JLabel.CENTER);
		return sessionProfitHero(profitLabel, captionLabel, subLabel);
	}

	/** @deprecated use {@link #sessionProfitHero(JLabel, String, JLabel)} */
	static JPanel sessionProfitHero(JLabel profitLabel, String caption)
	{
		return sessionProfitHero(profitLabel, caption, caption(" "));
	}

	/** Horizontal padding inside hero/summary grids (border insets left + right). */
	private static final int GRID_STAT_H_INSET = 8;
	private static final int GRID_STAT_HGAP = 4;

	/** Width of one cell in a 2-column hero grid (~100px at default sidebar width). */
	static int heroGridCellWidth(int columns)
	{
		int cols = Math.max(1, columns);
		return Math.max(
			44,
			(INNER_WIDTH - GRID_STAT_H_INSET - GRID_STAT_HGAP * (cols - 1)) / cols
		);
	}

	/** Centered value text that wraps inside a grid stat cell (use for GP in hero grids). */
	static void setHeroGridStatValue(JLabel label, String displayText, String tooltipText)
	{
		int w = heroGridCellWidth(2);
		label.setText(
			"<html><div style='width:" + w + "px;text-align:center;'>"
				+ escapeHtml(displayText)
				+ "</div></html>"
		);
		label.setToolTipText(tooltipText != null && !tooltipText.isEmpty() ? tooltipText : null);
	}

	/** Four-up hero metrics in a 2x2 grid - fits large GP values in the narrow sidebar. */
	static JPanel detailHeroGrid(JComponent... stats)
	{
		JPanel grid = new SidebarContentPanel();
		grid.setLayout(new GridLayout(2, 2, GRID_STAT_HGAP, GRID_STAT_HGAP));
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
		SidebarContentPanel.lockWidth(grid);
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

	/** Opens FlipX pricing with feature attribution for conversion tracking. */
	static JButton upgradeButton(String feature)
	{
		JButton button = new JButton("Upgrade to Pro");
		styleButton(button);
		button.setForeground(GOLD);
		button.setFont(FontManager.getRunescapeSmallFont());
		button.addActionListener(e ->
			LinkBrowser.browse(
				FlipXConstants.baseUrl()
					+ "/pricing?src=plugin&feature="
					+ java.net.URLEncoder.encode(feature, java.nio.charset.StandardCharsets.UTF_8)
			)
		);
		return button;
	}

	static void syncUpgradeButton(JButton button, PluginEntitlements entitlements)
	{
		button.setVisible(entitlements == null || !entitlements.hasProAccess());
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
		panel.setBorder(new EmptyBorder(0, 0, SPACING_XS, 0));
		SidebarContentPanel.lockWidth(panel);
		return panel;
	}

	static void styleCombo(JComboBox<?> combo)
	{
		combo.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		combo.setForeground(Color.WHITE);
		fullWidth(combo);
	}

	static JPanel summaryStrip(JComponent... stats)
	{
		int columns = Math.max(1, stats.length);
		JPanel strip = new SidebarContentPanel();
		strip.setLayout(new GridLayout(1, columns, GRID_STAT_HGAP, 0));
		strip.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		strip.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(2, 0, 0, 0, GOLD_DIM),
				BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR, 1, true)
			),
			new EmptyBorder(SPACING_SM, SPACING_XS, SPACING_SM, SPACING_XS)
		));
		strip.setAlignmentX(Component.LEFT_ALIGNMENT);
		for (JComponent stat : stats)
		{
			strip.add(stat);
		}
		SidebarContentPanel.lockWidth(strip);
		return strip;
	}

	static JPanel statCell(JLabel valueLabel, String caption)
	{
		return statCell(valueLabel, caption, heroGridCellWidth(2));
	}

	static JPanel statCell(JLabel valueLabel, JLabel captionLabel)
	{
		return statCell(valueLabel, captionLabel, heroGridCellWidth(2));
	}

	static JPanel statCell(JLabel valueLabel, JLabel captionLabel, int cellWidth)
	{
		JPanel cell = new JPanel();
		cell.setLayout(new BoxLayout(cell, BoxLayout.Y_AXIS));
		cell.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		cell.setAlignmentX(Component.CENTER_ALIGNMENT);
		valueLabel.setForeground(Color.WHITE);
		valueLabel.setHorizontalAlignment(JLabel.CENTER);
		valueLabel.setFont(FontManager.getRunescapeSmallFont());
		valueLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
		captionLabel.setFont(FontManager.getRunescapeSmallFont());
		captionLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		captionLabel.setHorizontalAlignment(JLabel.CENTER);
		captionLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
		setGridStatCaption(captionLabel, captionLabel.getText(), cellWidth);
		cell.add(valueLabel);
		cell.add(gap(SPACING_XS));
		cell.add(captionLabel);
		Dimension cellMax = new Dimension(cellWidth, Short.MAX_VALUE);
		cell.setMaximumSize(cellMax);
		cell.setPreferredSize(new Dimension(cellWidth, cell.getPreferredSize().height));
		return cell;
	}

	static JPanel statCell(JLabel valueLabel, String caption, int cellWidth)
	{
		JPanel cell = new JPanel();
		cell.setLayout(new BoxLayout(cell, BoxLayout.Y_AXIS));
		cell.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		cell.setAlignmentX(Component.CENTER_ALIGNMENT);
		valueLabel.setForeground(Color.WHITE);
		valueLabel.setHorizontalAlignment(JLabel.CENTER);
		valueLabel.setFont(FontManager.getRunescapeSmallFont());
		valueLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
		JLabel captionLabel = new JLabel();
		captionLabel.setFont(FontManager.getRunescapeSmallFont());
		captionLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		captionLabel.setHorizontalAlignment(JLabel.CENTER);
		captionLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
		setGridStatCaption(captionLabel, caption, cellWidth);
		cell.add(valueLabel);
		cell.add(gap(SPACING_XS));
		cell.add(captionLabel);
		Dimension cellMax = new Dimension(cellWidth, Short.MAX_VALUE);
		cell.setMaximumSize(cellMax);
		return cell;
	}

	static void setGridStatCaption(JLabel captionLabel, String caption, int cellWidth)
	{
		captionLabel.setText(
			"<html><div style='width:" + cellWidth + "px;text-align:center;'>"
				+ escapeHtml(caption)
				+ "</div></html>"
		);
	}

	/** Bordered block of label/value rows for item detail - fixed row heights, no vertical stretch. */
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
		SidebarContentPanel.lockWidthFixed(row, 18);
		block.add(row);
	}

	/** Wrap width for item titles inside a {@link #card()}. */
	static int cardBodyWrapWidth()
	{
		return Math.max(80, INNER_WIDTH - (PADDING * 2) - 4);
	}

	static void finalizeStatBlock(JPanel block)
	{
		int rows = block.getComponentCount();
		int height = Math.max(24, rows * 18 + 14);
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
		lockRowHeight(component, height, false);
	}

	/**
	 * Fixed-height rows (e.g. market list). Set {@code variableLines} when line count can change -
	 * never cap max height or text will overlap.
	 */
	static void lockRowHeight(JComponent component, int height, boolean variableLines)
	{
		component.setAlignmentX(Component.LEFT_ALIGNMENT);
		if (variableLines)
		{
			SidebarContentPanel.lockWidth(component);
			component.setMinimumSize(new Dimension(INNER_WIDTH, height));
			return;
		}
		SidebarContentPanel.lockWidthFixed(component, height);
	}

	static JPanel subViewHeader(String title, Runnable onBack, JLabel statusLabel)
	{
		JPanel header = new SidebarContentPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		transparent(header);
		header.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.add(backButton(onBack));
		header.add(gap(SPACING_SM));
		JLabel titleLabel = sectionTitle(title);
		titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.add(titleLabel);
		if (statusLabel != null)
		{
			statusLabel.setBorder(new EmptyBorder(4, 0, 0, 0));
			statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
			header.add(statusLabel);
		}
		header.add(gap(SPACING_MD));
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
			int keep = Math.max(0, maxChars - 3);
			display = text.substring(0, keep) + "...";
		}
		JLabel label = new JLabel(display);
		label.setToolTipText(text);
		return label;
	}

	static JCheckBox checkBox(String label, boolean selected)
	{
		JCheckBox box = new JCheckBox(label, selected);
		styleCheckBox(box);
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
