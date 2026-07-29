package com.osrsflipfinder.runelite;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * Wiki catalog search (all tradable items). Separate from the market list
 * filter in {@link MarketPanel}.
 */
@Slf4j
class ItemCatalogSearchBar extends JPanel
{
	private static final int MIN_QUERY_LEN = 2;
	private static final long DEBOUNCE_MS = 350L;
	private static final int RESULT_LIMIT = 12;
	private static final int ROW_HEIGHT = 44;

	private final ItemsClient itemsClient;
	private final ItemManager itemManager;
	private final ScheduledExecutorService executorService;
	private final BiConsumer<Integer, String> onItemPicked;
	private final JTextField field = PluginUi.textField("");
	private final JPanel resultsContainer = PluginUi.listContainer();
	private final JLabel hintLabel = PluginUi.hint(" ");

	private ScheduledFuture<?> debounceTask;
	private volatile int searchGeneration;

	ItemCatalogSearchBar(
		ItemsClient itemsClient,
		ItemManager itemManager,
		ScheduledExecutorService executorService,
		BiConsumer<Integer, String> onItemPicked
	)
	{
		this.itemsClient = itemsClient;
		this.itemManager = itemManager;
		this.executorService = executorService;
		this.onItemPicked = onItemPicked;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setAlignmentX(LEFT_ALIGNMENT);
		PluginUi.transparent(resultsContainer);

		field.setToolTipText("Search the full GE item catalog by name or ID");
		field.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				scheduleSearch();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				scheduleSearch();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				scheduleSearch();
			}
		});

		hintLabel.setVisible(false);
		resultsContainer.setVisible(false);

		add(PluginUi.labeledField("Find item", field));
		add(hintLabel);
		add(resultsContainer);
		SidebarContentPanel.lockWidth(this);
	}

	@Override
	public void setEnabled(boolean enabled)
	{
		field.setEnabled(enabled);
		if (!enabled)
		{
			clearResults();
			field.setText("");
		}
	}

	private void scheduleSearch()
	{
		if (debounceTask != null)
		{
			debounceTask.cancel(false);
		}
		final int generation = ++searchGeneration;
		debounceTask = executorService.schedule(() -> runSearch(generation), DEBOUNCE_MS, TimeUnit.MILLISECONDS);
	}

	private void runSearch(int generation)
	{
		String query = field.getText().trim();
		if (query.length() < MIN_QUERY_LEN)
		{
			SwingUtilities.invokeLater(this::clearResults);
			return;
		}

		try
		{
			List<ItemSearchResponse.ItemSearchHit> hits = itemsClient.searchItems(query, RESULT_LIMIT);
			if (generation != searchGeneration)
			{
				return;
			}
			SwingUtilities.invokeLater(() -> showResults(hits, query));
		}
		catch (Exception ex)
		{
			log.debug("Item catalog search failed", ex);
			if (generation != searchGeneration)
			{
				return;
			}
			SwingUtilities.invokeLater(() ->
			{
				clearResults();
				hintLabel.setText("Search unavailable — check connection or subscription.");
				hintLabel.setVisible(true);
			});
		}
	}

	private void clearResults()
	{
		resultsContainer.removeAll();
		resultsContainer.setVisible(false);
		hintLabel.setVisible(false);
		revalidate();
		repaint();
	}

	private void showResults(List<ItemSearchResponse.ItemSearchHit> hits, String query)
	{
		resultsContainer.removeAll();
		if (hits.isEmpty())
		{
			hintLabel.setText("No items match \"" + query + "\".");
			hintLabel.setVisible(true);
			resultsContainer.setVisible(false);
		}
		else
		{
			hintLabel.setVisible(false);
			for (ItemSearchResponse.ItemSearchHit hit : hits)
			{
				resultsContainer.add(buildResultRow(hit));
			}
			resultsContainer.setVisible(true);
		}
		revalidate();
		repaint();
	}

	private JPanel buildResultRow(ItemSearchResponse.ItemSearchHit hit)
	{
		JPanel row = new JPanel(new BorderLayout(PluginUi.SPACING_SM, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(javax.swing.BorderFactory.createEmptyBorder(
			PluginUi.SPACING_XS, PluginUi.SPACING_SM, PluginUi.SPACING_XS, PluginUi.SPACING_SM));
		row.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		row.setAlignmentX(LEFT_ALIGNMENT);

		JLabel icon = new JLabel();
		icon.setPreferredSize(new java.awt.Dimension(32, 28));
		icon.setHorizontalAlignment(JLabel.CENTER);
		itemManager.getImage(hit.getId()).addTo(icon);

		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setOpaque(false);

		JLabel name = PluginUi.truncatedLabel(hit.getName(), 24);
		name.setForeground(Color.WHITE);
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setToolTipText(hit.getName());

		String priceLine = formatPriceLine(hit);
		JLabel stats = new JLabel(priceLine);
		stats.setFont(FontManager.getRunescapeSmallFont());
		stats.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		text.add(name);
		text.add(stats);

		row.add(icon, BorderLayout.WEST);
		row.add(text, BorderLayout.CENTER);
		PluginUi.lockRowHeight(row, ROW_HEIGHT);

		MouseAdapter pick = new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				field.setText("");
				clearResults();
				onItemPicked.accept(hit.getId(), hit.getName());
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				row.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			}
		};
		row.addMouseListener(pick);
		icon.addMouseListener(pick);
		text.addMouseListener(pick);
		name.addMouseListener(pick);
		stats.addMouseListener(pick);

		return row;
	}

	private static String formatPriceLine(ItemSearchResponse.ItemSearchHit hit)
	{
		Long buy = hit.getInstantBuy();
		Long sell = hit.getInstantSell();
		if (buy != null && sell != null && buy > 0 && sell > 0)
		{
			long margin = hit.getMargin() != null ? hit.getMargin() : sell - buy;
			return "Buy " + MarketFormat.gp(buy) + " · Sell " + MarketFormat.gp(sell)
				+ " · " + MarketFormat.signedGp(margin);
		}
		return "ID " + hit.getId() + (hit.isMembers() ? " · Members" : "");
	}
}
