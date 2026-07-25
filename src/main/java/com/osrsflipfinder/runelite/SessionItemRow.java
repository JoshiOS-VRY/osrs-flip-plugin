package com.osrsflipfinder.runelite;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/** Ranked session item row with icon and profit accent. */
class SessionItemRow extends JPanel
{
	private static final int ROW_HEIGHT = 48;

	SessionItemRow(ItemPerformanceRow item, ItemManager itemManager)
	{
		setLayout(new BorderLayout(PluginUi.SPACING_SM, 0));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, profitAccent(item.getTotalProfit())),
			BorderFactory.createEmptyBorder(
				PluginUi.SPACING_SM, PluginUi.SPACING_SM, PluginUi.SPACING_SM, PluginUi.SPACING_SM
			)
		));
		setAlignmentX(LEFT_ALIGNMENT);

		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(32, 28));
		icon.setHorizontalAlignment(JLabel.CENTER);
		itemManager.getImage(item.getItemId()).addTo(icon);

		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		text.setOpaque(false);

		String name = item.getItemName() != null ? item.getItemName() : "Item " + item.getItemId();
		JLabel nameLabel = PluginUi.truncatedLabel(name, 20);
		nameLabel.setForeground(Color.WHITE);
		nameLabel.setFont(FontManager.getRunescapeSmallFont());
		nameLabel.setToolTipText(name);

		Color profitColor = item.getTotalProfit() >= 0 ? PluginUi.POSITIVE : PluginUi.NEGATIVE;
		JLabel stats = new JLabel("<html>"
			+ PluginUi.htmlSpan(profitColor, MarketFormat.signedGp(item.getTotalProfit()))
			+ PluginUi.htmlSep()
			+ PluginUi.htmlSpan(PluginUi.TEXT_SOFT, MarketFormat.percent(item.getAvgRoiPercent()))
			+ PluginUi.htmlSep()
			+ PluginUi.htmlSpan(PluginUi.TEXT_DIM, "x " + item.getFlipCount())
			+ "</html>");
		stats.setFont(FontManager.getRunescapeSmallFont());

		text.add(nameLabel);
		text.add(stats);

		JLabel chevron = new JLabel(">");
		chevron.setForeground(PluginUi.GOLD_DIM);
		chevron.setFont(FontManager.getRunescapeSmallFont());

		add(icon, BorderLayout.WEST);
		add(text, BorderLayout.CENTER);
		add(chevron, BorderLayout.EAST);

		PluginUi.lockRowHeight(this, ROW_HEIGHT);

		MouseAdapter hover = new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
				text.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				setBackground(ColorScheme.DARKER_GRAY_COLOR);
				text.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			}
		};
		addMouseListener(hover);
	}

	private static Color profitAccent(long profit)
	{
		if (profit > 0)
		{
			return PluginUi.POSITIVE;
		}
		if (profit < 0)
		{
			return PluginUi.NEGATIVE;
		}
		return ColorScheme.MEDIUM_GRAY_COLOR;
	}

}
