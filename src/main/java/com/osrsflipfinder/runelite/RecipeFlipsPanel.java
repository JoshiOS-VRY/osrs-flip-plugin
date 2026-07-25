package com.osrsflipfinder.runelite;

import java.awt.BorderLayout;
import java.awt.Color;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.LinkBrowser;

/** Read-only recipe flip opportunities (Ultra+). */
class RecipeFlipsPanel extends SidebarContentPanel
{
	private static final int ROW_HEIGHT = 48;

	private final FlipFinderConfig config;
	private final PluginApiClient apiClient;
	private final OpportunitiesClient opportunitiesClient;
	private final ScheduledExecutorService executorService;

	private final JPanel list = PluginUi.listContainer();
	private final JLabel statusLabel = PluginUi.caption(" ");
	private final JLabel refreshTimerLabel = PluginUi.caption("Updates when you open this tab");

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
		add(statusLabel);
		add(PluginUi.gap(PluginUi.SPACING_XS));
		refreshTimerLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		add(refreshTimerLabel);
		add(PluginUi.gap(PluginUi.SPACING_XS));

		PluginUi.fullWidthGrow(list);
		add(list);
		add(PluginUi.gap(PluginUi.SPACING_MD));

		JButton link = PluginUi.externalLinkButton("Full list on web");
		link.addActionListener(e ->
			LinkBrowser.browse(FlipXConstants.baseUrl() + "/recipes")
		);
		PluginUi.fullWidth(link);
		add(link);
		opportunitiesClient.addEntitlementsListener(entitlements ->
			SwingUtilities.invokeLater(this::onEntitlementsUpdated)
		);
	}

	private void onEntitlementsUpdated()
	{
		if (config.apiKey() == null || config.apiKey().isBlank())
		{
			return;
		}
		load();
	}

	void updateRefreshTimer()
	{
		refreshTimerLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		refreshTimerLabel.setText("Updates when you open this tab");
	}

	void load()
	{
		list.removeAll();

		if (config.apiKey() == null || config.apiKey().isBlank())
		{
			statusLabel.setText("Pair required");
			list.add(PluginUi.emptyState("Pair your device in Connection to view recipe flips."));
			revalidate();
			repaint();
			return;
		}

		if (!config.enableMarketPanel())
		{
			statusLabel.setText("Market panel off");
			list.add(PluginUi.emptyState("Enable \"Market panel\" in plugin settings."));
			revalidate();
			repaint();
			return;
		}

		PluginEntitlements entitlements = opportunitiesClient.getEntitlements();
		if (entitlements == null)
		{
			statusLabel.setText("Loading account...");
			opportunitiesClient.requestImmediateRefresh();
			revalidate();
			repaint();
			return;
		}

		if (!entitlements.isRecipeFlips())
		{
			statusLabel.setText("Ultra required");
			list.add(PluginUi.emptyState("Recipe flips need an Ultra subscription."));
			revalidate();
			repaint();
			return;
		}

		statusLabel.setText("Loading...");
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
					list.removeAll();
					if (ex.getState() == PluginState.UPGRADE_REQUIRED)
					{
						statusLabel.setText("Ultra required");
						list.add(PluginUi.emptyState("Recipe flips need an Ultra subscription."));
					}
					else
					{
						statusLabel.setText(ex.getMessage());
					}
					revalidate();
					repaint();
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
			statusLabel.setText("No recipes");
			list.add(PluginUi.emptyState("No recipe opportunities match right now."));
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
			list.add(buildRow(recipe));
			list.add(PluginUi.gap(PluginUi.SPACING_XS));
			shown++;
		}
		statusLabel.setText(recipes.size() + " recipes ranked");
		revalidate();
		repaint();
	}

	private JPanel buildRow(RecipeOpportunity recipe)
	{
		JPanel row = new SidebarContentPanel();
		row.setLayout(new BorderLayout(PluginUi.SPACING_SM, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, scoreAccent(recipe.getNetProfit())),
			BorderFactory.createEmptyBorder(
				PluginUi.SPACING_SM, PluginUi.SPACING_SM, PluginUi.SPACING_SM, PluginUi.SPACING_SM
			)
		));
		row.setAlignmentX(LEFT_ALIGNMENT);

		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setOpaque(false);

		JLabel title = PluginUi.truncatedLabel(recipe.getName() != null ? recipe.getName() : "Recipe", 24);
		title.setForeground(Color.WHITE);
		title.setFont(FontManager.getRunescapeSmallFont());

		Color netColor = recipe.getNetProfit() >= 0 ? PluginUi.POSITIVE : PluginUi.NEGATIVE;
		JLabel stats = new JLabel("<html>"
			+ PluginUi.htmlSpan(netColor, "Net " + MarketFormat.signedGp(recipe.getNetProfit()))
			+ PluginUi.htmlSep()
			+ PluginUi.htmlSpan(PluginUi.TEXT_SOFT, "ROI " + MarketFormat.percent(recipe.getRoiPercent()))
			+ "</html>");
		stats.setFont(FontManager.getRunescapeSmallFont());

		text.add(title);
		text.add(stats);
		row.add(text, BorderLayout.CENTER);
		PluginUi.lockRowHeight(row, ROW_HEIGHT);
		SidebarContentPanel.lockWidth(row);
		return row;
	}

	private static Color scoreAccent(long netProfit)
	{
		if (netProfit > 0)
		{
			return PluginUi.POSITIVE;
		}
		if (netProfit < 0)
		{
			return PluginUi.NEGATIVE;
		}
		return ColorScheme.MEDIUM_GRAY_COLOR;
	}
}
