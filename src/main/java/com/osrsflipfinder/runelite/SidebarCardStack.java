package com.osrsflipfinder.runelite;

import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * Shows one sidebar card at a time and sizes to that card only. {@link
 * java.awt.CardLayout} keeps the tallest card's height and leaves blank space
 * below shorter views (e.g. item detail).
 */
class SidebarCardStack extends SidebarContentPanel
{
	private JComponent visible;

	SidebarCardStack()
	{
		setLayout(new BorderLayout());
		setAlignmentX(LEFT_ALIGNMENT);
		SidebarContentPanel.lockWidth(this);
	}

	void showCard(JComponent card)
	{
		removeAll();
		visible = card;
		add(card, BorderLayout.NORTH);
		revalidate();
		repaint();
	}

	@Override
	public Dimension getPreferredSize()
	{
		if (visible != null)
		{
			Dimension size = visible.getPreferredSize();
			return new Dimension(CONTENT_WIDTH, size.height);
		}
		return new Dimension(CONTENT_WIDTH, 0);
	}

	@Override
	public Dimension getMinimumSize()
	{
		return getPreferredSize();
	}
}
