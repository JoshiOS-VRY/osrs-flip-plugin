package com.osrsflipfinder.runelite;

import java.awt.BorderLayout;
import java.awt.Color;
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

/** A styled, clickable opportunity row in the market list. */
class OpportunityRow extends JPanel
{
	private static final int ROW_HEIGHT = 56;

	OpportunityRow(FlipOpportunity opp, ItemManager itemManager, Runnable onClick)
	{
		setLayout(new BorderLayout(6, 0));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, scoreAccent(opp.getOpportunityScore())),
			BorderFactory.createEmptyBorder(6, 6, 6, 6)
		));
		setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		setAlignmentX(LEFT_ALIGNMENT);

		JLabel icon = new JLabel();
		icon.setPreferredSize(new java.awt.Dimension(32, 28));
		icon.setHorizontalAlignment(JLabel.CENTER);
		itemManager.getImage(opp.getId()).addTo(icon);

		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		text.setOpaque(false);

		JLabel name = PluginUi.truncatedLabel(opp.getName(), 22);
		name.setForeground(Color.WHITE);
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setToolTipText(opp.getName());

		JLabel stats = new JLabel(String.format(
			"<html><span style='color:%s;'>%s</span>"
				+ "<span style='color:#888888;'> · </span>"
				+ "<span style='color:#cccccc;'>%s</span>"
				+ "<span style='color:#888888;'> · </span>"
				+ "<span style='color:#D9B45B;'>%d</span></html>",
			toHex(opp.getNetProfitPerItem() >= 0 ? PluginUi.POSITIVE : PluginUi.NEGATIVE),
			MarketFormat.signedGp(opp.getNetProfitPerItem()),
			MarketFormat.percent(opp.getNetRoiPercent()),
			opp.getOpportunityScore()
		));
		stats.setFont(FontManager.getRunescapeSmallFont());

		text.add(name);
		text.add(stats);

		add(icon, BorderLayout.WEST);
		add(text, BorderLayout.CENTER);

		PluginUi.lockRowHeight(this, ROW_HEIGHT);

		MouseAdapter hover = new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				SwingUtilities.invokeLater(onClick);
			}

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

	private static Color scoreAccent(int score)
	{
		if (score >= 75)
		{
			return PluginUi.POSITIVE;
		}
		if (score >= 50)
		{
			return PluginUi.GOLD;
		}
		return ColorScheme.MEDIUM_GRAY_COLOR;
	}

	private static String toHex(Color color)
	{
		return String.format("#%06X", color.getRGB() & 0xFFFFFF);
	}
}
