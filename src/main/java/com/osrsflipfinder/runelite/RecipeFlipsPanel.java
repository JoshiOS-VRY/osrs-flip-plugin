package com.osrsflipfinder.runelite;

import java.io.IOException;
import java.util.List;
import javax.inject.Inject;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.util.concurrent.ScheduledExecutorService;
import net.runelite.client.util.LinkBrowser;

/** Read-only recipe flip opportunities (Ultra+). */
class RecipeFlipsPanel extends SidebarContentPanel
{
	private final FlipFinderConfig config;
	private final PluginApiClient apiClient;
	private final OpportunitiesClient opportunitiesClient;
	private final ScheduledExecutorService executorService;

	private final JPanel list = PluginUi.listContainer();
	private final JLabel statusLabel = PluginUi.caption(" ");

	@Inject
	RecipeFlipsPanel(
		FlipFinderConfig config,
		PluginApiClient apiClient,
		OpportunitiesClient opportunitiesClient,
		ScheduledExecutorService executorService
	)
	{
		this.config = config;
		this.apiClient = apiClient;
		this.opportunitiesClient = opportunitiesClient;
		this.executorService = executorService;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		PluginUi.transparent(this);
		add(list);
		add(statusLabel);

		JButton link = PluginUi.externalLinkButton("Full list on web");
		link.addActionListener(e ->
			LinkBrowser.browse(FlipXConstants.baseUrl() + "/recipes")
		);
		PluginUi.fullWidth(link);
		add(link);
	}

	void load()
	{
		list.removeAll();

		if (config.apiKey() == null || config.apiKey().isBlank())
		{
			statusLabel.setText("Pair your device above to view recipe flips");
			revalidate();
			repaint();
			return;
		}

		if (!config.enableMarketPanel())
		{
			statusLabel.setText("Enable \"Market panel\" in plugin settings");
			revalidate();
			repaint();
			return;
		}

		PluginEntitlements entitlements = opportunitiesClient.getEntitlements();
		if (entitlements == null)
		{
			statusLabel.setText("Loading account…");
			opportunitiesClient.requestImmediateRefresh();
			revalidate();
			repaint();
			return;
		}

		if (!entitlements.isRecipeFlips())
		{
			statusLabel.setText("Ultra subscription required");
			revalidate();
			repaint();
			return;
		}

		statusLabel.setText("Loading…");
		executorService.execute(() ->
		{
			try
			{
				RecipesResponse response = apiClient.get("/api/plugin/recipes/opportunities", RecipesResponse.class);
				SwingUtilities.invokeLater(() -> render(response));
			}
			catch (PluginApiException ex)
			{
				SwingUtilities.invokeLater(() ->
				{
					if (ex.getState() == PluginState.UPGRADE_REQUIRED)
					{
						statusLabel.setText("Ultra subscription required");
					}
					else
					{
						statusLabel.setText(ex.getMessage());
					}
				});
			}
			catch (IOException ex)
			{
				SwingUtilities.invokeLater(() -> statusLabel.setText(ex.getMessage()));
			}
		});
	}

	private void render(RecipesResponse response)
	{
		list.removeAll();
		List<RecipeOpportunity> recipes = response != null ? response.getRecipes() : null;
		if (recipes == null || recipes.isEmpty())
		{
			statusLabel.setText("No recipe opportunities");
			revalidate();
			repaint();
			return;
		}

		int shown = 0;
		for (RecipeOpportunity recipe : recipes)
		{
			if (shown >= 10)
			{
				break;
			}
			JPanel row = PluginUi.card();
			JLabel title = new JLabel(recipe.getName());
			title.setForeground(java.awt.Color.WHITE);
			row.add(title);
			row.add(PluginUi.caption("Net " + MarketFormat.signedGp(recipe.getNetProfit())
				+ " · ROI " + MarketFormat.percent(recipe.getRoiPercent())));
			list.add(row);
			shown++;
		}
		statusLabel.setText(recipes.size() + " recipes ranked");
		revalidate();
		repaint();
	}
}
