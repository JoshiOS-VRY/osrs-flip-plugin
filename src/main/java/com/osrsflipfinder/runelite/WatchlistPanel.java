package com.osrsflipfinder.runelite;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
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
	private static final int ROW_HEIGHT = 40;

	private final WatchlistClient watchlistClient;
	private final ItemManager itemManager;
	private final ScheduledExecutorService executorService;
	private final Consumer<String> errorListener;

	private final JLabel statusLabel = PluginUi.caption("Loading watchlist...");
	private final JLabel marketRefreshTimerLabel = PluginUi.caption(" ");
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
		marketRefreshTimerLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		marketRefreshTimerLabel.setAlignmentX(LEFT_ALIGNMENT);
		add(marketRefreshTimerLabel);
		PluginUi.fullWidthGrow(listContainer);
		add(listContainer);
	}

	void load()
	{
		statusLabel.setText("Loading watchlist...");
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
			statusLabel.setText("No items yet");
			listContainer.add(PluginUi.emptyState("Add items from item detail."));
		}
		else
		{
			int max = response.getMaxItems();
			statusLabel.setText(items.size() + (max > 0 && max < 9999 ? " / " + max : "") + " items");
			for (WatchlistResponse.WatchlistItem item : items)
			{
				listContainer.add(buildRow(item));
				listContainer.add(PluginUi.gap(PluginUi.SPACING_XS));
			}
		}
		listContainer.revalidate();
		listContainer.repaint();
	}

	private JPanel buildRow(WatchlistResponse.WatchlistItem item)
	{
		JPanel row = new SidebarContentPanel();
		row.setLayout(new BorderLayout(PluginUi.SPACING_SM, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, PluginUi.GOLD_DIM),
			BorderFactory.createEmptyBorder(
				PluginUi.SPACING_SM, PluginUi.SPACING_SM, PluginUi.SPACING_SM, PluginUi.SPACING_SM
			)
		));
		row.setAlignmentX(LEFT_ALIGNMENT);
		PluginUi.lockRowHeight(row, ROW_HEIGHT);

		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(32, 28));
		icon.setHorizontalAlignment(JLabel.CENTER);
		itemManager.getImage(item.getItemId()).addTo(icon);

		String itemName = item.getItemName() != null ? item.getItemName() : "#" + item.getItemId();
		JLabel name = PluginUi.truncatedLabel(itemName, 20);
		name.setForeground(Color.WHITE);
		name.setFont(FontManager.getRunescapeSmallFont());

		JLabel remove = new JLabel("X");
		remove.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		remove.setFont(FontManager.getRunescapeSmallFont());
		remove.setToolTipText("Remove from watchlist");
		remove.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		remove.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				removeItem(item.getItemId(), row);
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				remove.setForeground(PluginUi.NEGATIVE);
				row.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				remove.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
				row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			}
		});

		row.add(icon, BorderLayout.WEST);
		row.add(name, BorderLayout.CENTER);
		row.add(remove, BorderLayout.EAST);
		SidebarContentPanel.lockWidth(row);
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

	void updateMarketRefreshTimer(String text)
	{
		marketRefreshTimerLabel.setText(text != null ? text : " ");
	}
}
