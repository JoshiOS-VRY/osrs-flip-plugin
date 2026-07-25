package com.osrsflipfinder.runelite;

import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.swing.JComponent;

/**
 * Shows one sidebar section at a time and sizes to that section only. {@link
 * java.awt.CardLayout} would keep every section in the tree and use the tallest
 * section's height, which clips shorter views and breaks scrolling.
 */
class SectionContentHost extends SidebarContentPanel
{
	private JComponent visible;

	SectionContentHost()
	{
		setLayout(new BorderLayout());
		setAlignmentX(LEFT_ALIGNMENT);
		SidebarContentPanel.lockWidth(this);
	}

	void showSection(JComponent section)
	{
		removeAll();
		visible = section;
		add(section, BorderLayout.NORTH);
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
