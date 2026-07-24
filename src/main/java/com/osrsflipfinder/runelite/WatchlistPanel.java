package com.osrsflipfinder.runelite;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/** Watchlist sub-view with styled rows and compact remove control. */
class WatchlistPanel extends SidebarContentPanel
{
	private final WatchlistClient watchlistClient;
	private final ItemManager itemManager;
	private final ScheduledExecutorService executorService;
	private final Consumer<String> errorListener;

	private final JLabel statusLabel = PluginUi.caption("Loading watchlist…");
	private final JPanel listContainer = PluginUi.listContainer();

	WatchlistPanel(
		WatchlistClient watchlistClient,
		ItemManager itemManager,
		ScheduledExecutorService executorService,
		Runnable onBack,
		Consumer<String> errorListener
	)
	{
		this.watchlistClient = watchlistClient;
		this.itemManager = itemManager;
		this.executorService = executorService;
		this.errorListener = errorListener;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setAlignmentX(LEFT_ALIGNMENT);

		add(PluginUi.subViewHeader("Watchlist", onBack, statusLabel));
		PluginUi.fullWidthGrow(listContainer);
		add(listContainer);
	}

	void load()
	{
		statusLabel.setText("Loading watchlist…");
		listContainer.removeAll();
		listContainer.revalidate();
		listContainer.repaint();

		executorService.execute(() ->
		{
			try
			{
				WatchlistResponse response = watchlistClient.fetch();
				SwingUtilities.invokeLater(() -> render(response));
			}
			catch (IOException e)
			{
				errorListener.accept(e.getMessage());
				SwingUtilities.invokeLater(() -> statusLabel.setText(e.getMessage()));
			}
		});
	}

	private void render(WatchlistResponse response)
	{
		listContainer.removeAll();
		List<WatchlistResponse.WatchlistItem> items = response != null ? response.getItems() : null;
		if (items == null || items.isEmpty())
		{
			statusLabel.setText("No items yet — add from item detail.");
		}
		else
		{
			int max = response.getMaxItems();
			statusLabel.setText(items.size() + (max > 0 && max < 9999 ? " / " + max : "") + " items");
			for (WatchlistResponse.WatchlistItem item : items)
			{
				listContainer.add(buildRow(item));
			}
		}
		listContainer.revalidate();
		listContainer.repaint();
	}

	private JPanel buildRow(WatchlistResponse.WatchlistItem item)
	{
		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(javax.swing.BorderFactory.createEmptyBorder(6, 8, 6, 8));
		PluginUi.lockRowHeight(row, 40);

		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(36, 32));
		icon.setHorizontalAlignment(JLabel.CENTER);
		itemManager.getImage(item.getItemId()).addTo(icon);

		String itemName = item.getItemName() != null ? item.getItemName() : "#" + item.getItemId();
		JLabel name = PluginUi.truncatedLabel(itemName, 20);
		name.setForeground(Color.WHITE);
		name.setFont(FontManager.getRunescapeSmallFont());

		JLabel remove = new JLabel("✕");
		remove.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		remove.setToolTipText("Remove from watchlist");
		remove.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		remove.addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override
			public void mousePressed(java.awt.event.MouseEvent e)
			{
				removeItem(item.getItemId(), row);
			}

			@Override
			public void mouseEntered(java.awt.event.MouseEvent e)
			{
				remove.setForeground(PluginUi.NEGATIVE);
			}

			@Override
			public void mouseExited(java.awt.event.MouseEvent e)
			{
				remove.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			}
		});

		row.add(icon, BorderLayout.WEST);
		row.add(name, BorderLayout.CENTER);
		row.add(remove, BorderLayout.EAST);
		return row;
	}

	private void removeItem(int itemId, JPanel row)
	{
		executorService.execute(() ->
		{
			try
			{
				watchlistClient.remove(itemId);
				SwingUtilities.invokeLater(() ->
				{
					listContainer.remove(row);
					listContainer.revalidate();
					listContainer.repaint();
				});
			}
			catch (IOException e)
			{
				errorListener.accept(e.getMessage());
			}
		});
	}
}
