package com.osrsflipfinder.runelite;

import java.io.File;
import javax.inject.Inject;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.util.concurrent.ScheduledExecutorService;
import net.runelite.client.util.LinkBrowser;

/** Flipping Utilities import CTA and optional in-plugin file upload. */
class ImportPanel extends SidebarContentPanel
{
	private final FlipFinderConfig config;
	private final ImportClient importClient;
	private final ScheduledExecutorService executorService;

	private final JLabel resultLabel = PluginUi.caption(" ");

	@Inject
	ImportPanel(FlipFinderConfig config, ImportClient importClient, ScheduledExecutorService executorService)
	{
		this.config = config;
		this.importClient = importClient;
		this.executorService = executorService;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		PluginUi.transparent(this);

		JPanel card = PluginUi.card();
		card.add(PluginUi.cardHint(
			"Flipping Utilities user? Export CSV from the Stats tab, then import on the web or pick a file below."
		));
		card.add(PluginUi.gap(8));

		JButton webButton = PluginUi.externalLinkButton("Import on web");
		webButton.addActionListener(e ->
			LinkBrowser.browse(FlipXConstants.baseUrl() + "/settings/import")
		);
		PluginUi.fullWidth(webButton);
		card.add(webButton);
		card.add(PluginUi.gap(6));

		JButton fileButton = PluginUi.secondaryButton("Choose CSV file…");
		fileButton.addActionListener(e -> chooseFile());
		PluginUi.fullWidth(fileButton);
		card.add(fileButton);
		card.add(PluginUi.gap(6));
		card.add(resultLabel);
		add(card);
	}

	private void chooseFile()
	{
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Select Flipping Utilities export");
		chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
			"Flipping Utilities exports (*.csv, *.json)", "csv", "json"
		));
		if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION)
		{
			return;
		}
		File file = chooser.getSelectedFile();
		if (file == null)
		{
			return;
		}

		resultLabel.setText("Importing…");
		executorService.execute(() ->
		{
			try
			{
				ImportClient.ImportResult result = importClient.importFile(file.toPath());
				final String successMessage = result.getSkippedRows() > 0
					? "Imported " + result.getInserted() + " events ("
						+ result.getSkipped() + " duplicates skipped, "
						+ result.getSkippedRows() + " rows skipped)"
					: "Imported " + result.getInserted() + " events ("
						+ result.getSkipped() + " duplicates skipped)";
				SwingUtilities.invokeLater(() -> resultLabel.setText(successMessage));
			}
			catch (PluginApiException ex)
			{
				SwingUtilities.invokeLater(() -> resultLabel.setText(
					ex.getMessage() != null ? ex.getMessage() : "Import failed"
				));
			}
			catch (Exception ex)
			{
				SwingUtilities.invokeLater(() -> resultLabel.setText(
					"Import failed — use a Flipping Utilities CSV export from Stats"
				));
			}
		});
	}
}
