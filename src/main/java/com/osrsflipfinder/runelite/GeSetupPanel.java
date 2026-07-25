package com.osrsflipfinder.runelite;

import java.awt.Color;
import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.client.game.ItemManager;

/** Sidebar panel shown while configuring a GE offer for the current item. */
class GeSetupPanel extends SidebarContentPanel
{
	private final Client client;
	private final ItemManager itemManager;
	private final ItemsClient itemsClient;
	private final GeInterfaceListener geInterfaceListener;
	private final ScheduledExecutorService executorService;

	private final JPanel body = new JPanel();
	private final JLabel statusLabel = PluginUi.caption("Open the GE to configure an offer");
	private volatile int lastItemId = -1;
	private ScheduledFuture<?> pollTask;

	@Inject
	GeSetupPanel(
		Client client,
		ItemManager itemManager,
		ItemsClient itemsClient,
		GeInterfaceListener geInterfaceListener,
		ScheduledExecutorService executorService
	)
	{
		this.client = client;
		this.itemManager = itemManager;
		this.itemsClient = itemsClient;
		this.geInterfaceListener = geInterfaceListener;
		this.executorService = executorService;

		itemsClient.addUpdateListener(this::onItemUpdated);

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		PluginUi.transparent(this);
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		PluginUi.transparent(body);
		add(body);
		add(statusLabel);
	}

	void setActive(boolean active)
	{
		if (active && pollTask == null)
		{
			pollTask = executorService.scheduleAtFixedRate(this::pollItem, 0, 500, TimeUnit.MILLISECONDS);
		}
		else if (!active && pollTask != null)
		{
			pollTask.cancel(false);
			pollTask = null;
		}
	}

	private void pollItem()
	{
		int itemId = GeItemResolver.resolve(client);
		if (itemId <= 0)
		{
			if (lastItemId != -1)
			{
				lastItemId = -1;
				SwingUtilities.invokeLater(this::renderEmpty);
			}
			geInterfaceListener.updateSearchHint("");
			return;
		}

		if (itemId != lastItemId)
		{
			lastItemId = itemId;
			fetchAndRender(itemId);
			return;
		}

		if (itemsClient.isStale(itemId))
		{
			fetchAndRender(itemId);
		}
	}

	private void onItemUpdated(int itemId)
	{
		if (itemId != lastItemId || lastItemId <= 0)
		{
			return;
		}
		ItemDetailResponse detail = itemsClient.peek(lastItemId);
		if (detail != null)
		{
			SwingUtilities.invokeLater(() -> renderDetail(lastItemId, detail));
		}
	}

	private void fetchAndRender(int itemId)
	{
		executorService.execute(() ->
		{
			try
			{
				ItemDetailResponse detail = itemsClient.fetch(itemId);
				SwingUtilities.invokeLater(() -> renderDetail(itemId, detail));
			}
			catch (IOException ex)
			{
				SwingUtilities.invokeLater(() -> renderFallback(itemId));
			}
		});
	}

	private void renderEmpty()
	{
		body.removeAll();
		statusLabel.setText(isGeOpen() ? "Select a GE slot to view copilot data" : "Open the GE to configure an offer");
		revalidate();
		repaint();
	}

	private void renderFallback(int itemId)
	{
		body.removeAll();
		String name = itemManager.getItemComposition(itemId).getName();
		JLabel title = new JLabel(name);
		title.setForeground(Color.WHITE);
		body.add(title);
		statusLabel.setText("Could not load FlipX detail");
		revalidate();
		repaint();
	}

	private void renderDetail(int itemId, ItemDetailResponse detail)
	{
		body.removeAll();
		if (detail == null || detail.getOpportunity() == null)
		{
			renderFallback(itemId);
			return;
		}

		FlipOpportunity opp = detail.getOpportunity();
		body.add(GeCopilotUi.buildBody(itemManager, itemId, opp));
		geInterfaceListener.updateSearchHint(opp.getName());
		statusLabel.setText(" ");
		revalidate();
		repaint();
	}

	private boolean isGeOpen()
	{
		var universe = client.getWidget(net.runelite.api.gameval.InterfaceID.GeOffers.UNIVERSE);
		return universe != null && !universe.isSelfHidden();
	}
}
