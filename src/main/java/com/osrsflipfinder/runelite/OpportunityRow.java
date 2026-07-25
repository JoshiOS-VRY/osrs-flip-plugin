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
	private static final int ROW_HEIGHT = 52;

	OpportunityRow(FlipOpportunity opp, ItemManager itemManager, Runnable onClick)
	{
		setLayout(new BorderLayout(PluginUi.SPACING_SM, 0));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, scoreAccent(opp.getOpportunityScore())),
			BorderFactory.createEmptyBorder(PluginUi.SPACING_SM, PluginUi.SPACING_SM, PluginUi.SPACING_SM, PluginUi.SPACING_SM)
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

		JLabel stats = new JLabel("<html>"
			+ PluginUi.htmlSpan(
				opp.getNetProfitPerItem() >= 0 ? PluginUi.POSITIVE : PluginUi.NEGATIVE,
				MarketFormat.signedGp(opp.getNetProfitPerItem()))
			+ PluginUi.htmlSep()
			+ PluginUi.htmlSpan(PluginUi.TEXT_SOFT, MarketFormat.percent(opp.getNetRoiPercent()))
			+ PluginUi.htmlSep()
			+ PluginUi.htmlSpan(PluginUi.GOLD, String.valueOf(opp.getOpportunityScore()))
			+ "</html>");
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

}
