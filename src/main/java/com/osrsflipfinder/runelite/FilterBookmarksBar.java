package com.osrsflipfinder.runelite;

import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;

/** Saved filter/sort bookmarks bar for market and slot optimizer panels. */
class FilterBookmarksBar extends JPanel
{
	private static final int LOCAL_LIMIT = 5;

	interface SnapshotBuilder
	{
		FilterBookmark build(String name);
	}

	interface ApplyListener
	{
		void onApply(FilterBookmark bookmark);
	}

	private final String context;
	private final BookmarksClient bookmarksClient;
	private final Supplier<Boolean> cloudSyncSupplier;
	private final SnapshotBuilder snapshotBuilder;
	private final ApplyListener applyListener;
	private final ScheduledExecutorServiceAdapter executor;

	private final JLabel statusLabel = PluginUi.caption(" ");
	private final JLabel cloudBadge = PluginUi.caption("Local · up to " + LOCAL_LIMIT);
	private final JPanel chipsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
	private final JPanel savePanel = new JPanel();
	private final JTextField nameField = PluginUi.textField("");
	private final JLabel saveErrorLabel = PluginUi.hint(" ");

	private List<FilterBookmark> bookmarks = List.of();
	private String activeBookmarkId;
	private boolean loading;

	FilterBookmarksBar(
		String context,
		BookmarksClient bookmarksClient,
		Supplier<Boolean> cloudSyncSupplier,
		SnapshotBuilder snapshotBuilder,
		ApplyListener applyListener,
		java.util.concurrent.ScheduledExecutorService executorService
	)
	{
		this.context = context;
		this.bookmarksClient = bookmarksClient;
		this.cloudSyncSupplier = cloudSyncSupplier;
		this.snapshotBuilder = snapshotBuilder;
		this.applyListener = applyListener;
		this.executor = new ScheduledExecutorServiceAdapter(executorService);

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		PluginUi.transparent(this);
		setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		PluginUi.transparent(header);
		JLabel title = PluginUi.caption("Saved views");
		title.setForeground(PluginUi.GOLD);
		header.add(title);
		cloudBadge.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		header.add(cloudBadge);
		JButton saveButton = PluginUi.linkButton("Save current");
		saveButton.addActionListener(e -> toggleSavePanel());
		header.add(saveButton);
		PluginUi.fullWidth(header);
		add(header);
		add(PluginUi.gap(4));

		PluginUi.transparent(chipsPanel);
		chipsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		add(chipsPanel);
		add(statusLabel);
		add(savePanel);
		savePanel.setVisible(false);
		buildSavePanel();
	}

	void setActiveBookmarkId(String id)
	{
		activeBookmarkId = id;
		renderChips();
	}

	void refresh()
	{
		loading = true;
		statusLabel.setText("Loading bookmarks…");
		chipsPanel.removeAll();
		chipsPanel.revalidate();
		chipsPanel.repaint();

		boolean cloudSync = Boolean.TRUE.equals(cloudSyncSupplier.get());
		cloudBadge.setText(cloudSync ? "Cloud · synced" : "Local · up to " + LOCAL_LIMIT);
		cloudBadge.setForeground(cloudSync ? PluginUi.GOLD : ColorScheme.LIGHT_GRAY_COLOR);

		executor.execute(() ->
		{
			List<FilterBookmark> merged = bookmarksClient.listMerged(context, cloudSync);
			SwingUtilities.invokeLater(() ->
			{
				bookmarks = merged;
				loading = false;
				statusLabel.setText(" ");
				renderChips();
			});
		});
	}

	private void buildSavePanel()
	{
		savePanel.setLayout(new BoxLayout(savePanel, BoxLayout.Y_AXIS));
		PluginUi.transparent(savePanel);
		savePanel.add(PluginUi.caption("Name this view"));
		savePanel.add(PluginUi.gap(4));
		nameField.setToolTipText("e.g. Safe mid flips");
		savePanel.add(PluginUi.labeledField("Name", nameField));
		savePanel.add(saveErrorLabel);
		savePanel.add(PluginUi.gap(4));
		JButton confirm = PluginUi.secondaryButton("Save bookmark");
		confirm.addActionListener(e -> saveBookmark());
		JButton cancel = PluginUi.linkButton("Cancel");
		cancel.addActionListener(e -> hideSavePanel());
		savePanel.add(PluginUi.buttonRow(confirm, cancel));
	}

	private void toggleSavePanel()
	{
		savePanel.setVisible(!savePanel.isVisible());
		saveErrorLabel.setText(" ");
		if (savePanel.isVisible())
		{
			nameField.requestFocusInWindow();
		}
		revalidate();
		repaint();
	}

	private void hideSavePanel()
	{
		savePanel.setVisible(false);
		nameField.setText("");
		saveErrorLabel.setText(" ");
		revalidate();
		repaint();
	}

	private void saveBookmark()
	{
		String name = nameField.getText().trim();
		if (name.isEmpty())
		{
			return;
		}
		saveErrorLabel.setText("Saving…");
		boolean cloudSync = Boolean.TRUE.equals(cloudSyncSupplier.get());
		FilterBookmark draft = snapshotBuilder.build(name);

		executor.execute(() ->
		{
			try
			{
				FilterBookmark saved = bookmarksClient.save(context, draft, cloudSync);
				SwingUtilities.invokeLater(() ->
				{
					activeBookmarkId = saved.getId();
					hideSavePanel();
					refresh();
				});
			}
			catch (IllegalStateException e)
			{
				SwingUtilities.invokeLater(() -> saveErrorLabel.setText(formatSaveError(e.getMessage())));
			}
			catch (Exception e)
			{
				SwingUtilities.invokeLater(() -> saveErrorLabel.setText(
					e.getMessage() != null ? e.getMessage() : "Save failed"
				));
			}
		});
	}

	private static String formatSaveError(String code)
	{
		if ("bookmark_limit".equals(code))
		{
			return "Bookmark limit reached.";
		}
		if ("bookmark_name_taken".equals(code))
		{
			return "A bookmark with that name already exists.";
		}
		return code != null ? code : "Save failed";
	}

	private void deleteBookmark(FilterBookmark bookmark)
	{
		boolean cloudSync = Boolean.TRUE.equals(cloudSyncSupplier.get());
		executor.execute(() ->
		{
			try
			{
				bookmarksClient.delete(context, bookmark, cloudSync);
				SwingUtilities.invokeLater(() ->
				{
					if (bookmark.getId() != null && bookmark.getId().equals(activeBookmarkId))
					{
						activeBookmarkId = null;
					}
					refresh();
				});
			}
			catch (Exception e)
			{
				SwingUtilities.invokeLater(() -> statusLabel.setText(
					e.getMessage() != null ? e.getMessage() : "Delete failed"
				));
			}
		});
	}

	private void renderChips()
	{
		chipsPanel.removeAll();
		if (loading)
		{
			chipsPanel.revalidate();
			chipsPanel.repaint();
			return;
		}
		if (bookmarks.isEmpty())
		{
			JLabel empty = PluginUi.hint("Save your filter + sort combo for one-click recall.");
			chipsPanel.add(empty);
		}
		else
		{
			for (FilterBookmark bookmark : bookmarks)
			{
				chipsPanel.add(buildChip(bookmark));
			}
		}
		chipsPanel.revalidate();
		chipsPanel.repaint();
	}

	private JButton buildChip(FilterBookmark bookmark)
	{
		String label = bookmark.getName();
		if (bookmark.isLocalOnly())
		{
			label += " · local";
		}
		JButton chip = PluginUi.secondaryButton(label);
		boolean active = bookmark.getId() != null && bookmark.getId().equals(activeBookmarkId);
		if (active)
		{
			chip.setForeground(PluginUi.GOLD);
		}
		chip.addActionListener(e -> applyListener.onApply(bookmark));
		chip.setToolTipText("Right-click to delete");
		chip.addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override
			public void mouseClicked(java.awt.event.MouseEvent e)
			{
				if (javax.swing.SwingUtilities.isRightMouseButton(e))
				{
					deleteBookmark(bookmark);
				}
			}
		});
		return chip;
	}

	/** Thin wrapper so the bar does not depend on injectable executor type in tests. */
	private static final class ScheduledExecutorServiceAdapter
	{
		private final java.util.concurrent.ScheduledExecutorService delegate;

		ScheduledExecutorServiceAdapter(java.util.concurrent.ScheduledExecutorService delegate)
		{
			this.delegate = delegate;
		}

		void execute(Runnable task)
		{
			delegate.execute(task);
		}
	}
}
