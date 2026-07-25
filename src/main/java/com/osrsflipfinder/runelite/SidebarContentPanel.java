package com.osrsflipfinder.runelite;

import java.awt.Dimension;
import javax.swing.JComponent;
import javax.swing.JPanel;
import net.runelite.client.ui.PluginPanel;

/**
 * Panel constrained to the RuneLite sidebar content column ({@link PluginPanel#PANEL_WIDTH}).
 * Height is allowed to grow - never lock max height to preferred height.
 */
class SidebarContentPanel extends JPanel
{
	static final int CONTENT_WIDTH = PluginPanel.PANEL_WIDTH;
	/** Width for stacked controls inside {@link PluginUi#pageInsets()} - avoids clipping at panel edges. */
	static final int INNER_WIDTH = CONTENT_WIDTH - (2 * PluginPanel.BORDER_OFFSET);

	@Override
	public Dimension getPreferredSize()
	{
		Dimension size = super.getPreferredSize();
		return new Dimension(CONTENT_WIDTH, size.height);
	}

	@Override
	public Dimension getMaximumSize()
	{
		return new Dimension(CONTENT_WIDTH, Short.MAX_VALUE);
	}

	@Override
	public Dimension getMinimumSize()
	{
		Dimension size = super.getMinimumSize();
		return new Dimension(CONTENT_WIDTH, size.height);
	}

	static void lockWidth(JComponent component)
	{
		component.setAlignmentX(JComponent.LEFT_ALIGNMENT);
		component.setMaximumSize(new Dimension(INNER_WIDTH, Short.MAX_VALUE));
	}

	static void lockWidthFixed(JComponent component, int height)
	{
		component.setAlignmentX(JComponent.LEFT_ALIGNMENT);
		Dimension size = new Dimension(INNER_WIDTH, height);
		component.setPreferredSize(size);
		component.setMinimumSize(size);
		component.setMaximumSize(size);
	}
}
