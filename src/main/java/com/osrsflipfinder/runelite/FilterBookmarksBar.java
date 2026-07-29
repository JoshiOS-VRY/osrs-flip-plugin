package com.osrsflipfinder.runelite;

import java.awt.Component;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;

/** Saved filter/sort bookmarks bar for market and slot optimizer panels. */
class FilterBookmarksBar extends JPanel
{
	private static final int LOCAL_LIMIT = 5;
	private static final String COMBO_PLACEHOLDER = "Select saved view...";

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
	private final JLabel cloudBadge = PluginUi.caption("Local | up to " + LOCAL_LIMIT);
	private final JComboBox<String> bookmarkCombo = new JComboBox<>();
	private final JButton deleteButton = PluginUi.linkButton("Delete");
	private final JPanel savePanel = new JPanel();
	private final JTextField nameField = PluginUi.textField("");
	private final JLabel saveErrorLabel = PluginUi.hint(" ");

	private List<FilterBookmark> bookmarks = List.of();
	private String activeBookmarkId;
	private boolean loading;
	private boolean suppressComboEvents;

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
		SidebarContentPanel.lockWidth(this);

		JLabel title = PluginUi.caption("Saved views");
		title.setForeground(PluginUi.GOLD);
		JButton saveButton = PluginUi.linkButton("Save current");
		saveButton.addActionListener(e -> toggleSavePanel());

		JPanel titleRow = new SidebarContentPanel();
		titleRow.setLayout(new BoxLayout(titleRow, BoxLayout.X_AXIS));
		PluginUi.transparent(titleRow);
		title.setAlignmentX(Component.LEFT_ALIGNMENT);
		saveButton.setAlignmentX(Component.RIGHT_ALIGNMENT);
		titleRow.add(title);
		titleRow.add(javax.swing.Box.createHorizontalGlue());
		titleRow.add(saveButton);
		SidebarContentPanel.lockWidth(titleRow);

		cloudBadge.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		cloudBadge.setAlignmentX(Component.LEFT_ALIGNMENT);

		PluginUi.styleCombo(bookmarkCombo);
		bookmarkCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
		bookmarkCombo.addActionListener(e -> onBookmarkComboSelected());

		deleteButton.setAlignmentX(Component.LEFT_ALIGNMENT);
		deleteButton.setEnabled(false);
		deleteButton.addActionListener(e -> onDeleteSelected());

		JPanel header = PluginUi.verticalStack(titleRow, cloudBadge);
		add(header);
		add(PluginUi.gap(PluginUi.SPACING_XS));
		add(bookmarkCombo);
		add(PluginUi.gap(PluginUi.SPACING_XS));
		add(deleteButton);
		add(statusLabel);
		add(savePanel);
		savePanel.setVisible(false);
		buildSavePanel();
	}

	void setActiveBookmarkId(String id)
	{
		activeBookmarkId = id;
		syncComboSelection();
	}

	void refresh()
	{
		loading = true;
		statusLabel.setText("Loading bookmarks...");
		setComboLoading(true);

		boolean cloudSync = Boolean.TRUE.equals(cloudSyncSupplier.get());
		cloudBadge.setText(cloudSync ? "Cloud | synced" : "Local | up to " + LOCAL_LIMIT);
		cloudBadge.setForeground(cloudSync ? PluginUi.GOLD : ColorScheme.LIGHT_GRAY_COLOR);

		executor.execute(() ->
		{
			List<FilterBookmark> merged = bookmarksClient.listMerged(context, cloudSync);
			SwingUtilities.invokeLater(() ->
			{
				bookmarks = merged;
				loading = false;
				statusLabel.setText(" ");
				rebuildBookmarkCombo();
			});
		});
	}

	private void buildSavePanel()
	{
		savePanel.setLayout(new BoxLayout(savePanel, BoxLayout.Y_AXIS));
		PluginUi.transparent(savePanel);
		savePanel.add(PluginUi.caption("Name this view"));
		savePanel.add(PluginUi.gap(PluginUi.SPACING_XS));
		nameField.setToolTipText("e.g. Safe mid flips");
		savePanel.add(PluginUi.labeledField("Name", nameField));
		savePanel.add(saveErrorLabel);
		savePanel.add(PluginUi.gap(PluginUi.SPACING_XS));
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
		saveErrorLabel.setText("Saving...");
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

	private void setComboLoading(boolean loadingUi)
	{
		suppressComboEvents = true;
		try
		{
			bookmarkCombo.removeAllItems();
			if (loadingUi)
			{
				bookmarkCombo.addItem("Loading...");
			}
			bookmarkCombo.setEnabled(!loadingUi);
			deleteButton.setEnabled(false);
		}
		finally
		{
			suppressComboEvents = false;
		}
	}

	private void rebuildBookmarkCombo()
	{
		suppressComboEvents = true;
		try
		{
			bookmarkCombo.removeAllItems();
			if (bookmarks.isEmpty())
			{
				bookmarkCombo.addItem("Save a view to recall filters here.");
				bookmarkCombo.setEnabled(false);
				deleteButton.setEnabled(false);
				return;
			}

			bookmarkCombo.setEnabled(true);
			bookmarkCombo.addItem(COMBO_PLACEHOLDER);
			for (FilterBookmark bookmark : bookmarks)
			{
				bookmarkCombo.addItem(displayLabel(bookmark));
			}
			syncComboSelection();
		}
		finally
		{
			suppressComboEvents = false;
		}
	}

	private void syncComboSelection()
	{
		if (loading || bookmarks.isEmpty())
		{
			return;
		}

		suppressComboEvents = true;
		try
		{
			if (activeBookmarkId == null)
			{
				bookmarkCombo.setSelectedIndex(0);
				deleteButton.setEnabled(false);
				return;
			}

			for (int i = 0; i < bookmarks.size(); i++)
			{
				FilterBookmark bookmark = bookmarks.get(i);
				if (activeBookmarkId.equals(bookmark.getId()))
				{
					bookmarkCombo.setSelectedIndex(i + 1);
					deleteButton.setEnabled(true);
					return;
				}
			}
			bookmarkCombo.setSelectedIndex(0);
			deleteButton.setEnabled(false);
		}
		finally
		{
			suppressComboEvents = false;
		}
	}

	private void onBookmarkComboSelected()
	{
		if (suppressComboEvents || loading || bookmarks.isEmpty())
		{
			return;
		}

		int index = bookmarkCombo.getSelectedIndex();
		if (index <= 0)
		{
			activeBookmarkId = null;
			deleteButton.setEnabled(false);
			return;
		}

		FilterBookmark bookmark = bookmarks.get(index - 1);
		activeBookmarkId = bookmark.getId();
		deleteButton.setEnabled(true);
		applyListener.onApply(bookmark);
	}

	private void onDeleteSelected()
	{
		int index = bookmarkCombo.getSelectedIndex();
		if (index <= 0 || index > bookmarks.size())
		{
			return;
		}
		deleteBookmark(bookmarks.get(index - 1));
	}

	private static String displayLabel(FilterBookmark bookmark)
	{
		String label = bookmark.getName();
		if (bookmark.isLocalOnly())
		{
			label += " | local";
		}
		return label;
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
