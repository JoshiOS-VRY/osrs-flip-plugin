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
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetSizeMode;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.util.ImageUtil;

/**
 * FlipX icons on the GE setup panel: buy-limit on the quantity row (buy only) and
 * suggested price on the Guide price slot (buy and sell). Clicking an icon prefills
 * the native Enter quantity / Enter price chatbox if that dialog is already open,
 * or remembers the value until you open it. You still press Enter and Confirm.
 */
@Slf4j
@Singleton
class GeFlipxSetupAssist
{
	private static final int GE_OFFERS_GROUP = 465;
	/** Clientscript wrapper; {@link ScriptID#GE_OFFERS_SETUP_BUILD} is the proc it calls. */
	private static final int GE_OFFERS_SETUP_DRAW_CLIENTSCRIPT = 776;
	/** Fired when GE price/count meslayer chat opens after Enter price. */
	private static final int CS_MESLAYER_CHAT_OPEN = 108;
	private static final int GE_BTN = 35;
	private static final int ICON_W = 14;
	private static final int ICON_H = 12;
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

	private Widget quantityButton;
	private Widget quantityLabel;
	private Widget priceButton;
	private Widget priceLabel;
	private volatile int pendingBuyLimitItemId = -1;
	private volatile int lastSetupItemId = -1;
	private volatile int pendingPriceItemId = -1;
	private volatile int pendingChatValue = -1;
	private volatile GeOfferChatInput.Step pendingChatStep = GeOfferChatInput.Step.NONE;

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
	public void onConfigChanged(ConfigChanged event)
	{
		if (!FlipFinderConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		clientThread.invokeLater(() ->
		{
			if (!isFeatureEnabled())
			{
				hideButtons();
				clearAppliedOfferState();
				return;
			}
			scheduleAttachButtons();
		});
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (isSetupDrawScript(event.getScriptId()))
		{
			scheduleAttachButtons();
			return;
		}
		if (event.getScriptId() == CS_MESLAYER_CHAT_OPEN && pendingChatValue > 0)
		{
			clientThread.invokeLater(this::tryFinishPendingChatPrefill);
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
			hideButtons();
			clearAppliedOfferState();
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
			clearPendingChat();
			scheduleAttachButtons();
			return;
		}
		if (!hasLiveButtons(setup))
		{
			scheduleAttachButtons();
		}
		tryFinishPendingChatPrefill();
	}

	private boolean hasLiveButtons(Widget setup)
	{
		boolean buyOffer = isBuyOfferSetup();
		boolean priceLive = isLiveButton(priceButton, setup);
		if (!priceLive)
		{
			return false;
		}
		if (buyOffer)
		{
			return isLiveButton(quantityButton, setup);
		}
		return true;
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
		lastSetupItemId = itemId;

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
		if (!buyOffer && isLiveButton(quantityButton, setup))
		{
			hideWidget(quantityButton);
			hideWidget(quantityLabel);
			quantityButton = null;
			quantityLabel = null;
		}

		if (buyOffer)
		{
			Widget qtyAnchor = findQuantityPluginAnchor(setup);
			if (qtyAnchor != null)
			{
				stealNativeSlot(qtyAnchor);
				hidePlus1kLabel(setup, qtyAnchor);
				quantityButton = ensureIconButton(
					setup,
					quantityButton,
					qtyAnchor,
					true,
					"FlipX buy limit",
					() -> onBuyLimitClicked(itemId)
				);
				quantityLabel = ensureIconLabel(setup, quantityLabel, true);
			}
			else
			{
				quantityButton = null;
			}
		}

		Widget priceAnchor = findGuidePriceAnchor(setup);
		if (priceAnchor != null)
		{
			String action = buyOffer ? "FlipX buy price" : "FlipX sell price";
			priceButton = ensureIconButton(
				setup,
				priceButton,
				priceAnchor,
				false,
				action,
				() -> onFlipxPriceClicked(itemId, buyOffer)
			);
			priceLabel = ensureIconLabel(setup, priceLabel, false);
		}
		else
		{
			priceButton = null;
		}
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

	private Widget ensureIconButton(
		Widget setup,
		Widget existing,
		Widget anchor,
		boolean quantitySlot,
		String action,
		Runnable onClick
	)
	{
		if (isLiveButton(existing, setup))
		{
			applyNativeButtonBounds(existing, setup, anchor, quantitySlot);
			bindButton(existing, action, onClick);
			existing.setHidden(false);
			existing.revalidate();
			return existing;
		}
		return attachIconButton(setup, anchor, quantitySlot, action, onClick);
	}

	private Widget attachIconButton(
		Widget setup,
		Widget anchor,
		boolean quantitySlot,
		String action,
		Runnable onClick
	)
	{
		Widget btn = setup.createChild(-1, WidgetType.GRAPHIC);
		Widget peer = findSizePeer(setup, quantitySlot);
		int spriteId = peer != null ? peer.getSpriteId() : -1;
		btn.setSpriteId(spriteId > 0 ? spriteId : FLIPX_GE_BUTTON_SPRITE);
		btn.setHasListener(true);
		btn.setNoClickThrough(true);
		applyNativeButtonBounds(btn, setup, anchor, quantitySlot);
		bindButton(btn, action, onClick);
		btn.revalidate();
		return btn;
	}

	private Widget ensureIconLabel(Widget setup, Widget existing, boolean quantitySlot)
	{
		ButtonMetrics metrics = nativeButtonMetrics(
			setup,
			quantitySlot ? findQuantityPluginAnchor(setup) : findGuidePriceAnchor(setup),
			quantitySlot
		);
		if (metrics == null)
		{
			return existing;
		}
		Widget label = existing;
		if (!isLiveButton(label, setup))
		{
			label = setup.createChild(-1, WidgetType.TEXT);
		}
		Widget style = findPeerLabel(setup, quantitySlot);
		label.setText("X");
		label.setHidden(false);
		label.setNoClickThrough(false);
		label.setHasListener(false);
		if (style != null)
		{
			label.setFontId(style.getFontId());
			label.setTextColor(style.getTextColor());
			label.setXTextAlignment(style.getXTextAlignment());
			label.setYTextAlignment(style.getYTextAlignment());
		}
		label.setWidthMode(WidgetSizeMode.ABSOLUTE);
		label.setHeightMode(WidgetSizeMode.ABSOLUTE);
		label.setXPositionMode(WidgetPositionMode.ABSOLUTE_LEFT);
		label.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
		label.setOriginalX(metrics.x);
		label.setOriginalY(metrics.y);
		label.setOriginalWidth(metrics.w);
		label.setOriginalHeight(metrics.h);
		label.revalidate();
		return label;
	}

	private void bindButton(Widget btn, String action, Runnable onClick)
	{
		btn.setAction(0, action);
		btn.setOnOpListener((JavaScriptCallback) ev ->
			clientThread.invoke(onClick)
		);
	}

	private static void applyNativeButtonBounds(
		Widget btn,
		Widget setup,
		Widget slot,
		boolean quantitySlot
	)
	{
		ButtonMetrics metrics = nativeButtonMetrics(setup, slot, quantitySlot);
		if (metrics == null)
		{
			return;
		}
		btn.setWidthMode(WidgetSizeMode.ABSOLUTE);
		btn.setHeightMode(WidgetSizeMode.ABSOLUTE);
		btn.setXPositionMode(WidgetPositionMode.ABSOLUTE_LEFT);
		btn.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
		btn.setOriginalWidth(metrics.w);
		btn.setOriginalHeight(metrics.h);
		btn.setOriginalX(metrics.x);
		btn.setOriginalY(metrics.y);
	}

	static ButtonMetrics nativeButtonMetrics(Widget setup, Widget slot, boolean quantitySlot)
	{
		if (slot == null)
		{
			return null;
		}
		Widget sizePeer = findSizePeer(setup, quantitySlot);
		int w = firstPositive(
			sizePeer != null ? sizePeer.getWidth() : 0,
			sizePeer != null ? sizePeer.getOriginalWidth() : 0,
			slot.getWidth(),
			slot.getOriginalWidth(),
			GE_BTN
		);
		int h = firstPositive(
			sizePeer != null ? sizePeer.getHeight() : 0,
			sizePeer != null ? sizePeer.getOriginalHeight() : 0,
			slot.getHeight(),
			slot.getOriginalHeight(),
			GE_BTN
		);
		int slotW = firstPositive(slot.getWidth(), slot.getOriginalWidth(), w);
		int x = slot.getOriginalX();
		int y = sizePeer != null ? sizePeer.getOriginalY() : slot.getOriginalY();
		if (slotW > w)
		{
			x += (slotW - w) / 2;
		}
		return new ButtonMetrics(x, y, w, h);
	}

	static Widget findSizePeer(Widget setup, boolean quantitySlot)
	{
		String[] peers = quantitySlot
			? new String[] { "+100", "+10", "+1" }
			: new String[] { "-5%", "+5%", "-99%", "+99%" };
		for (String action : peers)
		{
			Widget peer = GeSetupWidgetSearch.findByAction(setup, action);
			if (peer != null && firstPositive(peer.getWidth(), peer.getOriginalWidth(), 0) > 0)
			{
				return peer;
			}
		}
		return null;
	}

	static Widget findPeerLabel(Widget setup, boolean quantitySlot)
	{
		if (setup == null)
		{
			return null;
		}
		String[] texts = quantitySlot
			? new String[] { "+100", "+10", "+1" }
			: new String[] { "-5%", "+5%", "-99%", "+99%" };
		Widget[] children = setup.getDynamicChildren();
		if (children == null)
		{
			return null;
		}
		for (String text : texts)
		{
			for (Widget child : children)
			{
				if (child != null
					&& child.getType() == WidgetType.TEXT
					&& text.equals(child.getText()))
				{
					return child;
				}
			}
		}
		return null;
	}

	static int firstPositive(int... values)
	{
		for (int value : values)
		{
			if (value > 0)
			{
				return value;
			}
		}
		return GE_BTN;
	}

	/**
	 * Take over a native GE slot (+1K / Guide price) so leftover clicks cannot
	 * fire the original op (notably +1K → quantity 1000).
	 */
	static void stealNativeSlot(Widget nativeBtn)
	{
		if (nativeBtn == null)
		{
			return;
		}
		nativeBtn.setHasListener(false);
		String[] actions = nativeBtn.getActions();
		if (actions != null)
		{
			for (int i = 0; i < actions.length; i++)
			{
				nativeBtn.setAction(i, null);
			}
		}
		nativeBtn.setHidden(true);
	}

	static boolean isLiveButton(Widget button, Widget setup)
	{
		if (button == null || setup == null)
		{
			return false;
		}
		try
		{
			return !button.isHidden() && button.getParent() == setup;
		}
		catch (RuntimeException e)
		{
			return false;
		}
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
		if (!isCurrentSetupItem(itemId))
		{
			return;
		}
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
					if (itemId != pendingBuyLimitItemId || !isCurrentSetupItem(itemId))
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
		if (!isCurrentSetupItem(itemId))
		{
			return;
		}
		ItemStats stats = itemManager.getItemStats(itemId);
		coinBalanceService.refresh();
		long offerPriceGp = GeOfferSetupScripts.readOfferPriceGp(client);
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
			log.debug("FlipX buy-limit qty is 0 for item {}", itemId);
			return;
		}
		offerQuantity(qty);
	}

	private void onFlipxPriceClicked(int itemId, boolean buyOffer)
	{
		if (!isCurrentSetupItem(itemId))
		{
			return;
		}
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
					if (itemId == pendingPriceItemId)
					{
						pendingPriceItemId = -1;
					}
					return;
				}
				clientThread.invokeLater(() ->
				{
					if (itemId != pendingPriceItemId || !isCurrentSetupItem(itemId))
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
		if (!isCurrentSetupItem(itemId))
		{
			return;
		}
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
		offerPrice((int) price);
	}

	private void offerQuantity(int quantity)
	{
		offerChatValue(quantity, GeOfferChatInput.Step.QUANTITY_BUY);
	}

	private void offerPrice(int priceGp)
	{
		offerChatValue(priceGp, GeOfferChatInput.Step.PRICE);
	}

	private void offerChatValue(int value, GeOfferChatInput.Step step)
	{
		if (value <= 0)
		{
			return;
		}
		Widget setup = client.getWidget(InterfaceID.GeOffers.SETUP);
		if (setup == null || setup.isSelfHidden())
		{
			return;
		}
		if (GeOfferChatInput.isOtherInputOpen(client))
		{
			return;
		}

		boolean wantPrice = step == GeOfferChatInput.Step.PRICE;
		if (wantPrice ? GeOfferChatInput.isQuantityOpen(client) : GeOfferChatInput.isPriceOpen(client))
		{
			return;
		}
		if (GeOfferChatInput.prefillIfStep(client, value, step))
		{
			clearPendingChat();
			return;
		}

		pendingChatValue = value;
		pendingChatStep = step;
	}

	private void tryFinishPendingChatPrefill()
	{
		if (pendingChatValue <= 0 || pendingChatStep == GeOfferChatInput.Step.NONE)
		{
			return;
		}
		if (GeOfferChatInput.prefillIfStep(client, pendingChatValue, pendingChatStep))
		{
			clearPendingChat();
		}
	}

	private boolean isCurrentSetupItem(int itemId)
	{
		return itemId > 0 && itemId == GeItemResolver.resolve(client);
	}

	private void clearPendingChat()
	{
		pendingChatValue = -1;
		pendingChatStep = GeOfferChatInput.Step.NONE;
	}

	private void clearAppliedOfferState()
	{
		clearPendingChat();
		lastSetupItemId = -1;
		pendingBuyLimitItemId = -1;
		pendingPriceItemId = -1;
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
		hideWidget(quantityButton);
		hideWidget(quantityLabel);
		hideWidget(priceButton);
		hideWidget(priceLabel);
		quantityButton = null;
		quantityLabel = null;
		priceButton = null;
		priceLabel = null;
	}

	private static void hideWidget(Widget widget)
	{
		if (widget == null)
		{
			return;
		}
		try
		{
			widget.setHidden(true);
		}
		catch (RuntimeException ignored)
		{
		}
	}

	static final class ButtonMetrics
	{
		final int x;
		final int y;
		final int w;
		final int h;

		ButtonMetrics(int x, int y, int w, int h)
		{
			this.x = x;
			this.y = y;
			this.w = w;
			this.h = h;
		}
	}
}
