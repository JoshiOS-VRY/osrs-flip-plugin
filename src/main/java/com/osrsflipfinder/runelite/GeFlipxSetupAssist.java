package com.osrsflipfinder.runelite;

import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.util.ImageUtil;

/**
 * FlipX icons on the GE setup panel: buy-limit on the quantity row (buy only) and
 * suggested price on the Guide price slot (buy and sell). Uses Jagex clientscripts
 * {@code ge_offers_setup_changequantity} (777) when available; otherwise Enter-quantity meslayer
 * (same as manual X amount). Price uses script 778 when the GE tail is captured, otherwise
 * Enter-price meslayer (async fallback). +/- stepping is last resort only.
 */
@Slf4j
@Singleton
class GeFlipxSetupAssist
{
	private static final int GE_OFFERS_GROUP = 465;
	/** Clientscript wrapper; {@link ScriptID#GE_OFFERS_SETUP_BUILD} is the proc it calls. */
	private static final int GE_OFFERS_SETUP_DRAW_CLIENTSCRIPT = 776;
	/** Fired when GE price/count meslayer chat opens after Enter price (Flipping Utilities). */
	private static final int CS_MESLAYER_CHAT_OPEN = 108;
	private static final int GE_BTN = 35;
	private static final int ICON_W = 14;
	private static final int ICON_H = 12;
	private static final double BTN_SCALE = 0.72;
	private static final int FLIPX_GE_BUTTON_SPRITE = 0x7f1_0001;

	private final Client client;
	private final ClientThread clientThread;
	private final FlipFinderConfig config;
	private final ItemManager itemManager;
	private final ItemsClient itemsClient;
	private final BuyLimitClient buyLimitClient;
	private final OpportunitiesClient opportunitiesClient;
	private final CoinBalanceService coinBalanceService;
	private final ScheduledExecutorService executorService;

	/** Optional script-777 tail when captured from a native GE click. */
	private Widget quantityButton;
	private Widget priceButton;
	private volatile int pendingBuyLimitItemId = -1;
	private volatile int lastSetupItemId = -1;
	private volatile int pendingPriceItemId = -1;
	private volatile int pendingQuantityTarget = -1;
	private volatile int pendingPriceTarget = -1;
	private static final int MAX_APPLY_TICKS = 48;
	private int quantityTicksRemaining;
	private int priceTicksRemaining;
	private final GeOfferSetupExactPrice.PriceApplyState priceApplyState =
		new GeOfferSetupExactPrice.PriceApplyState();
	private final GeOfferSetupExactQuantity.QuantityApplyState quantityApplyState =
		new GeOfferSetupExactQuantity.QuantityApplyState();
	private volatile boolean quantityNativeFallback;

	@Inject
	GeFlipxSetupAssist(
		Client client,
		ClientThread clientThread,
		FlipFinderConfig config,
		ItemManager itemManager,
		ItemsClient itemsClient,
		BuyLimitClient buyLimitClient,
		OpportunitiesClient opportunitiesClient,
		CoinBalanceService coinBalanceService,
		ScheduledExecutorService executorService
	)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.config = config;
		this.itemManager = itemManager;
		this.itemsClient = itemsClient;
		this.buyLimitClient = buyLimitClient;
		this.opportunitiesClient = opportunitiesClient;
		this.coinBalanceService = coinBalanceService;
		this.executorService = executorService;
	}

	@Subscribe
	public void onScriptPreFired(ScriptPreFired event)
	{
		GeSetupScriptTail.tryCapture(event);
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (isSetupDrawScript(event.getScriptId()))
		{
			scheduleAttachButtons();
			return;
		}
		if (event.getScriptId() == CS_MESLAYER_CHAT_OPEN)
		{
			if (pendingPriceTarget > 0
				&& GeOfferSetupExactPrice.needsAsyncContinuation(priceApplyState))
			{
				clientThread.invokeLater(this::continuePriceApplyOneShot);
			}
			if (pendingQuantityTarget > 0
				&& GeOfferSetupExactQuantity.needsAsyncContinuation(quantityApplyState))
			{
				clientThread.invokeLater(this::continueQuantityApplyOneShot);
			}
		}
	}

	private static boolean isSetupDrawScript(int scriptId)
	{
		return scriptId == ScriptID.GE_OFFERS_SETUP_BUILD
			|| scriptId == GE_OFFERS_SETUP_DRAW_CLIENTSCRIPT;
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == GE_OFFERS_GROUP)
		{
			scheduleAttachButtons();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!isFeatureEnabled())
		{
			return;
		}
		Widget setup = client.getWidget(InterfaceID.GeOffers.SETUP);
		if (setup == null || setup.isSelfHidden())
		{
			hideButtons();
			clearAppliedOfferState();
			return;
		}
		int itemId = GeItemResolver.resolve(client);
		if (itemId > 0 && itemId != lastSetupItemId)
		{
			cancelQuantityApply();
			hideButtons();
			scheduleAttachButtons();
			return;
		}
		if (quantityButton == null || priceButton == null)
		{
			if (needsAssistButtons())
			{
				scheduleAttachButtons();
			}
		}
		runPendingSetupApplies(setup);
	}

	private void runPendingSetupApplies(Widget setup)
	{
		if (pendingQuantityTarget > 0)
		{
			if (quantityTicksRemaining-- <= 0)
			{
				cancelQuantityApply();
			}
			else if (quantityApplyState.phase != GeOfferSetupExactQuantity.Phase.IDLE)
			{
				continueQuantityApplyOneShot();
			}
			else if (quantityNativeFallback)
			{
				runQuantityNativeFallbackOnce();
			}
		}
		if (pendingPriceTarget > 0 && priceApplyState.phase != GeOfferSetupExactPrice.Phase.IDLE)
		{
			if (priceTicksRemaining-- <= 0)
			{
				GeOfferSetupExactPrice.finishFailed(client, priceApplyState);
				pendingPriceTarget = -1;
			}
			else
			{
				runPriceStepOnce();
			}
		}
	}

	private boolean needsAssistButtons()
	{
		if (priceButton != null)
		{
			return isBuyOfferSetup() && quantityButton == null;
		}
		return true;
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (!isFeatureEnabled())
		{
			return;
		}
		if (event.getVarbitId() == VarbitID.GE_NEWOFFER_TYPE)
		{
			GeSetupScriptTail.clear();
		}
	}

	private void scheduleAttachButtons()
	{
		if (!isFeatureEnabled())
		{
			clientThread.invokeLater(this::hideButtons);
			return;
		}
		clientThread.invokeLater(this::attachButtons);
	}

	private void attachButtons()
	{
		if (!isFeatureEnabled())
		{
			hideButtons();
			return;
		}

		int itemId = GeItemResolver.resolve(client);
		if (itemId <= 0)
		{
			hideButtons();
			return;
		}
		if (itemId != lastSetupItemId)
		{
			lastSetupItemId = itemId;
			GeSetupScriptTail.clear();
		}

		Widget setup = client.getWidget(InterfaceID.GeOffers.SETUP);
		if (setup == null || setup.isSelfHidden())
		{
			hideButtons();
			return;
		}

		prefetchSetupAssistData(itemId);

		if (!ensureFlipxSprite())
		{
			return;
		}

		boolean buyOffer = isBuyOfferSetup();
		boolean missingQty = buyOffer && quantityButton == null;
		boolean missingPrice = priceButton == null;
		if (!missingQty && !missingPrice)
		{
			GeSetupScriptTail.tryBootstrapFromSetup(setup);
			return;
		}

		if (missingQty || missingPrice)
		{
			hideButtons();
		}

		if (buyOffer)
		{
			Widget qtyAnchor = findQuantityPluginAnchor(setup);
			if (qtyAnchor != null)
			{
				hidePlus1kLabel(setup, qtyAnchor);
				quantityButton = attachIconButton(
					setup,
					qtyAnchor,
					"FlipX buy limit",
					() -> onBuyLimitClicked(itemId)
				);
			}
		}

		Widget priceAnchor = findGuidePriceAnchor(setup);
		if (priceAnchor != null)
		{
			String action = buyOffer ? "FlipX buy price" : "FlipX sell price";
			priceButton = attachIconButton(
				setup,
				priceAnchor,
				action,
				() -> onFlipxPriceClicked(itemId, buyOffer)
			);
		}

		GeSetupScriptTail.tryBootstrapFromSetup(setup);
	}

	private void prefetchSetupAssistData(int itemId)
	{
		executorService.execute(() ->
		{
			try
			{
				if (itemsClient.peek(itemId) == null || itemsClient.isStale(itemId))
				{
					itemsClient.fetch(itemId);
				}
			}
			catch (Exception e)
			{
				log.debug("FlipX setup prefetch item detail failed", e);
			}
		});

		long accountHash = client.getAccountHash();
		if (accountHash == -1)
		{
			return;
		}
		String account = String.valueOf(accountHash);
		if (buyLimitClient.peek(account, itemId) != null)
		{
			return;
		}
		executorService.execute(() ->
		{
			try
			{
				buyLimitClient.fetch(account, itemId);
			}
			catch (Exception e)
			{
				log.debug("FlipX setup prefetch buy limit failed", e);
			}
		});
	}

	private Widget attachIconButton(
		Widget setup,
		Widget anchor,
		String action,
		Runnable onClick
	)
	{
		int ax = anchor.getOriginalX();
		int ay = anchor.getOriginalY();
		int aw = anchor.getOriginalWidth() > 0 ? anchor.getOriginalWidth() : GE_BTN;
		int ah = anchor.getOriginalHeight() > 0 ? anchor.getOriginalHeight() : GE_BTN;
		int btnW = Math.max(ICON_W, (int) (aw * BTN_SCALE));
		int btnH = Math.max(ICON_H, (int) (ah * BTN_SCALE));
		ax += (aw - btnW) / 2;
		ay += (ah - btnH) / 2;
		aw = btnW;
		ah = btnH;

		Widget btn = setup.createChild(-1, WidgetType.GRAPHIC);
		btn.setOriginalWidth(aw);
		btn.setOriginalHeight(ah);
		btn.setOriginalX(ax);
		btn.setOriginalY(ay);
		btn.setSpriteId(FLIPX_GE_BUTTON_SPRITE);
		btn.setHasListener(true);
		btn.setNoClickThrough(true);
		btn.setAction(0, action);
		btn.setOnOpListener((JavaScriptCallback) ev ->
			clientThread.invoke(onClick)
		);
		btn.revalidate();
		return btn;
	}

	static Widget findQuantityPluginAnchor(Widget setup)
	{
		if (setup == null)
		{
			return null;
		}

		Widget[] children = setup.getDynamicChildren();
		if (children == null)
		{
			return null;
		}

		for (Widget text : children)
		{
			if (text == null || text.getType() != WidgetType.TEXT)
			{
				continue;
			}
			if (!"+1K".equals(text.getText()))
			{
				continue;
			}
			int tx = text.getOriginalX();
			int ty = text.getOriginalY();
			for (Widget graphic : children)
			{
				if (graphic == null || graphic.getType() != WidgetType.GRAPHIC)
				{
					continue;
				}
				if (graphic.getOriginalX() == tx && graphic.getOriginalY() == ty)
				{
					return graphic;
				}
			}
		}

		return null;
	}

	static Widget findGuidePriceAnchor(Widget setup)
	{
		if (setup == null)
		{
			return null;
		}
		Widget[] children = setup.getDynamicChildren();
		if (children == null)
		{
			return null;
		}
		for (Widget w : children)
		{
			if (w == null || w.getType() != WidgetType.GRAPHIC)
			{
				continue;
			}
			String[] actions = w.getActions();
			if (actions == null)
			{
				continue;
			}
			for (String action : actions)
			{
				if ("Guide price".equals(action))
				{
					return w;
				}
			}
		}
		return null;
	}

	private static void hidePlus1kLabel(Widget setup, Widget anchor)
	{
		Widget[] children = setup.getDynamicChildren();
		if (children == null)
		{
			return;
		}
		int ax = anchor.getOriginalX();
		int ay = anchor.getOriginalY();
		for (Widget text : children)
		{
			if (text != null
				&& text.getType() == WidgetType.TEXT
				&& "+1K".equals(text.getText())
				&& text.getOriginalX() == ax
				&& text.getOriginalY() == ay)
			{
				text.setHidden(true);
			}
		}
	}

	private void onBuyLimitClicked(int itemId)
	{
		Widget setup = client.getWidget(InterfaceID.GeOffers.SETUP);
		int clientBought = GeOfferSetupBuyProgress.parseBoughtSoFar(setup);

		long accountHash = client.getAccountHash();
		if (accountHash == -1)
		{
			applyBuyLimitQuantity(itemId, null, clientBought);
			return;
		}
		String account = String.valueOf(accountHash);

		BuyLimitRemaining cached = buyLimitClient.peek(account, itemId);
		if (cached == null)
		{
			pendingBuyLimitItemId = itemId;
			if (clientBought >= 0)
			{
				applyBuyLimitQuantity(itemId, null, clientBought);
			}
			executorService.execute(() ->
			{
				try
				{
					buyLimitClient.fetch(account, itemId);
				}
				catch (Exception e)
				{
					log.debug("FlipX setup buy limit fetch failed", e);
				}
				clientThread.invokeLater(() ->
				{
					if (itemId != pendingBuyLimitItemId)
					{
						return;
					}
					pendingBuyLimitItemId = -1;
					Widget liveSetup = client.getWidget(InterfaceID.GeOffers.SETUP);
					int bought = GeOfferSetupBuyProgress.parseBoughtSoFar(liveSetup);
					BuyLimitRemaining fresh = buyLimitClient.peek(account, itemId);
					applyBuyLimitQuantity(itemId, fresh, bought);
				});
			});
			return;
		}

		applyBuyLimitQuantity(itemId, cached, clientBought);
	}

	private void applyBuyLimitQuantity(int itemId, BuyLimitRemaining synced, int clientBoughtSoFar)
	{
		ItemStats stats = itemManager.getItemStats(itemId);
		coinBalanceService.refresh();
		int offerPriceGp = GeOfferSetupScripts.readOfferPriceGp(client);
		long inventoryCoins = coinBalanceService.getCoins();
		int qty = GeFlipxBuyLimit.quantityToApply(
			synced,
			itemId,
			stats,
			clientBoughtSoFar,
			offerPriceGp,
			inventoryCoins
		);
		if (qty <= 0)
		{
			return;
		}
		applyOfferQuantity(qty);
	}

	private void onFlipxPriceClicked(int itemId, boolean buyOffer)
	{
		ItemDetailResponse detail = itemsClient.peek(itemId);
		boolean missing = detail == null || detail.getOpportunity() == null;
		if (!missing)
		{
			applyFlipxPrice(itemId, buyOffer);
		}
		if (missing || itemsClient.isStale(itemId))
		{
			final boolean applyAfterFetch = missing;
			pendingPriceItemId = itemId;
			executorService.execute(() ->
			{
				try
				{
					itemsClient.fetch(itemId);
				}
				catch (Exception e)
				{
					log.debug("FlipX setup price fetch failed", e);
				}
				if (!applyAfterFetch)
				{
					pendingPriceItemId = -1;
					return;
				}
				clientThread.invokeLater(() ->
				{
					if (itemId != pendingPriceItemId && pendingPriceItemId != -1)
					{
						return;
					}
					pendingPriceItemId = -1;
					applyFlipxPrice(itemId, buyOffer);
				});
			});
		}
	}

	private void applyFlipxPrice(int itemId, boolean buyOffer)
	{
		ItemDetailResponse detail = itemsClient.peek(itemId);
		if (detail == null || detail.getOpportunity() == null)
		{
			return;
		}
		GeAssistPricing.ResolvedPrice resolved = GeAssistPricing.resolve(
			detail,
			buyOffer,
			opportunitiesClient.getEntitlements()
		);
		if (resolved == null || resolved.priceGp <= 0)
		{
			return;
		}
		long price = Math.min(
			GeAssistPricing.geOfferPriceGp(resolved, buyOffer, detail.getOpportunity()),
			Integer.MAX_VALUE
		);
		applyOfferPrice((int) price);
	}

	private void applyOfferQuantity(int quantity)
	{
		pendingPriceTarget = -1;
		priceApplyState.phase = GeOfferSetupExactPrice.Phase.IDLE;
		cancelQuantityApply();

		Widget setup = client.getWidget(InterfaceID.GeOffers.SETUP);
		if (setup == null || setup.isSelfHidden())
		{
			return;
		}
		if (GeOfferSetupExactQuantity.isExactMatch(client, quantity))
		{
			return;
		}
		if (GeOfferSetupExactPrice.isMeslayerBusyForUser(client))
		{
			return;
		}
		if (GeOfferSetupScripts.applyQuantityViaScript(client, setup, quantity))
		{
			return;
		}

		pendingQuantityTarget = quantity;
		quantityNativeFallback = false;
		GeOfferSetupExactQuantity.startApply(quantityApplyState, client);
		if (quantityApplyState.phase == GeOfferSetupExactQuantity.Phase.IDLE)
		{
			startQuantityNativeFallback(quantity);
			return;
		}
		quantityTicksRemaining = MAX_APPLY_TICKS;
		continueQuantityApplyOneShot();
	}

	private void startQuantityNativeFallback(int quantity)
	{
		pendingQuantityTarget = quantity;
		quantityNativeFallback = true;
		quantityTicksRemaining = MAX_APPLY_TICKS;
		runQuantityNativeFallbackOnce();
	}

	private void continueQuantityApplyOneShot()
	{
		if (pendingQuantityTarget <= 0)
		{
			return;
		}
		Widget setup = client.getWidget(InterfaceID.GeOffers.SETUP);
		if (setup == null || setup.isSelfHidden())
		{
			cancelQuantityApply();
			return;
		}
		boolean done = GeOfferSetupExactQuantity.advanceUntilWait(
			client,
			setup,
			pendingQuantityTarget,
			quantityApplyState
		);
		if (done)
		{
			if (GeOfferSetupExactQuantity.isExactMatch(client, pendingQuantityTarget))
			{
				pendingQuantityTarget = -1;
				return;
			}
			if (quantityApplyState.phase == GeOfferSetupExactQuantity.Phase.IDLE)
			{
				startQuantityNativeFallback(pendingQuantityTarget);
			}
			return;
		}
		if (GeOfferSetupExactQuantity.needsAsyncContinuation(quantityApplyState))
		{
			if (--quantityTicksRemaining <= 0)
			{
				GeOfferSetupExactQuantity.finishFailed(client, quantityApplyState);
				startQuantityNativeFallback(pendingQuantityTarget);
			}
		}
	}

	private void runQuantityNativeFallbackOnce()
	{
		if (pendingQuantityTarget <= 0 || !quantityNativeFallback)
		{
			return;
		}
		Widget setup = client.getWidget(InterfaceID.GeOffers.SETUP);
		if (setup == null || setup.isSelfHidden())
		{
			cancelQuantityApply();
			return;
		}
		if (GeOfferSetupScripts.applyQuantityNativeBurst(
			client,
			setup,
			pendingQuantityTarget
		))
		{
			pendingQuantityTarget = -1;
			quantityNativeFallback = false;
		}
	}

	private void cancelQuantityApply()
	{
		if (quantityApplyState.openedMeslayer)
		{
			GeOfferSetupMeslayer.abortIfOpen(client);
		}
		pendingQuantityTarget = -1;
		quantityNativeFallback = false;
		quantityApplyState.phase = GeOfferSetupExactQuantity.Phase.IDLE;
		quantityApplyState.waitTicks = 0;
		quantityApplyState.openedMeslayer = false;
	}

	private void applyOfferPrice(int priceGp)
	{
		if (GeOfferSetupExactPrice.isExactMatch(client, priceGp))
		{
			if (client.getVarcIntValue(VarClientID.MESLAYERMODE)
				== GeOfferSetupExactPrice.MODE_PRICE_INPUT)
			{
				GeOfferSetupMeslayer.abortIfOpen(client);
			}
			pendingPriceTarget = -1;
			priceApplyState.phase = GeOfferSetupExactPrice.Phase.IDLE;
			priceApplyState.openedMeslayer = false;
			return;
		}
		if (GeOfferSetupExactPrice.isMeslayerBusyForUser(client))
		{
			return;
		}
		if (priceApplyState.phase != GeOfferSetupExactPrice.Phase.IDLE)
		{
			GeOfferSetupExactPrice.finishFailed(client, priceApplyState);
		}
		cancelQuantityApply();

		Widget setup = client.getWidget(InterfaceID.GeOffers.SETUP);
		if (setup != null && !setup.isSelfHidden()
			&& GeOfferSetupScripts.applyPriceOneShot(client, setup, priceGp))
		{
			pendingPriceTarget = -1;
			priceApplyState.phase = GeOfferSetupExactPrice.Phase.IDLE;
			priceApplyState.openedMeslayer = false;
			return;
		}

		pendingPriceTarget = priceGp;
		GeOfferSetupExactPrice.startApply(priceApplyState, client);
		if (priceApplyState.phase == GeOfferSetupExactPrice.Phase.IDLE)
		{
			pendingPriceTarget = -1;
			return;
		}
		priceTicksRemaining = MAX_APPLY_TICKS;
		continuePriceApplyOneShot();
	}

	private void continuePriceApplyOneShot()
	{
		if (pendingPriceTarget <= 0)
		{
			return;
		}
		Widget setup = client.getWidget(InterfaceID.GeOffers.SETUP);
		if (setup == null || setup.isSelfHidden())
		{
			pendingPriceTarget = -1;
			GeOfferSetupExactPrice.finishFailed(client, priceApplyState);
			return;
		}
		boolean done = GeOfferSetupExactPrice.advanceUntilWait(
			client,
			setup,
			pendingPriceTarget,
			priceApplyState
		);
		if (done)
		{
			pendingPriceTarget = -1;
			return;
		}
		if (GeOfferSetupExactPrice.needsAsyncContinuation(priceApplyState))
		{
			if (--priceTicksRemaining <= 0)
			{
				GeOfferSetupExactPrice.finishFailed(client, priceApplyState);
				pendingPriceTarget = -1;
			}
		}
	}

	private void runPriceStepOnce()
	{
		continuePriceApplyOneShot();
	}

	private void clearAppliedOfferState()
	{
		if (priceApplyState.openedMeslayer)
		{
			GeOfferSetupMeslayer.abortIfOpen(client);
		}
		cancelQuantityApply();
		lastSetupItemId = -1;
		pendingPriceTarget = -1;
		priceApplyState.phase = GeOfferSetupExactPrice.Phase.IDLE;
		priceApplyState.waitTicks = 0;
		priceApplyState.openedMeslayer = false;
		GeSetupScriptTail.clear();
	}

	private boolean isBuyOfferSetup()
	{
		return client.getVarbitValue(VarbitID.GE_NEWOFFER_TYPE) != 1
			&& client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH) > 0;
	}

	private boolean isFeatureEnabled()
	{
		return config.enableGePriceAssist()
			&& config.apiKey() != null
			&& !config.apiKey().isBlank();
	}

	private boolean ensureFlipxSprite()
	{
		Map<Integer, net.runelite.api.SpritePixels> overrides = client.getSpriteOverrides();
		if (overrides.containsKey(FLIPX_GE_BUTTON_SPRITE))
		{
			return true;
		}
		try
		{
			BufferedImage raw = ImageUtil.loadImageResource(OsrsFlipFinderPlugin.class, "icon.png");
			BufferedImage scaled = ImageUtil.resizeImage(raw, ICON_W, ICON_H);
			overrides.put(FLIPX_GE_BUTTON_SPRITE, ImageUtil.getImageSpritePixels(scaled, client));
			return true;
		}
		catch (RuntimeException e)
		{
			log.debug("FlipX setup sprite failed", e);
			return false;
		}
	}

	private void hideButtons()
	{
		if (quantityButton != null)
		{
			quantityButton.setHidden(true);
			quantityButton = null;
		}
		if (priceButton != null)
		{
			priceButton.setHidden(true);
			priceButton = null;
		}
	}
}
