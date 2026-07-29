package com.osrsflipfinder.runelite;

import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.VarClientInt;
import net.runelite.api.VarClientStr;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.VarClientIntChanged;
import net.runelite.api.events.VarClientStrChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetSizeMode;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;

/**
 * GE chatbox assist: FlipX buy/sell prices on the price step; remaining buy limit on
 * the quantity step (buy offers only). Sets chat input only — you still confirm.
 */
@Slf4j
@Singleton
class GeFlipxPriceAssist
{
	private static final int GE_SETUP_OFFER_LABEL_CHILD = 20;
	/** OSRS uses the same chat input type for GE price and quantity entry. */
	private static final int CHAT_INPUT_TYPE_GE = 7;

	enum GeOfferChatStep
	{
		NONE,
		PRICE,
		QUANTITY_BUY,
		QUANTITY_SELL
	}

	private final Client client;
	private final ClientThread clientThread;
	private final FlipFinderConfig config;
	private final ItemsClient itemsClient;
	private final BuyLimitClient buyLimitClient;
	private final OpportunitiesClient opportunitiesClient;
	private final ItemManager itemManager;
	private final CoinBalanceService coinBalanceService;
	private final ScheduledExecutorService executorService;

	private volatile boolean geChatOpen;
	private volatile int attachedItemId = -1;
	private volatile GeOfferChatStep attachedStep = GeOfferChatStep.NONE;
	private String lastChatPrompt = "";
	private Widget assistLine;

	@Inject
	GeFlipxPriceAssist(
		Client client,
		ClientThread clientThread,
		FlipFinderConfig config,
		ItemsClient itemsClient,
		BuyLimitClient buyLimitClient,
		OpportunitiesClient opportunitiesClient,
		ItemManager itemManager,
		CoinBalanceService coinBalanceService,
		ScheduledExecutorService executorService
	)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.config = config;
		this.itemsClient = itemsClient;
		this.buyLimitClient = buyLimitClient;
		this.opportunitiesClient = opportunitiesClient;
		this.itemManager = itemManager;
		this.coinBalanceService = coinBalanceService;
		this.executorService = executorService;

		itemsClient.addUpdateListener(itemId ->
		{
			if (geChatOpen && itemId == attachedItemId)
			{
				clientThread.invokeLater(this::refreshAssistLine);
			}
		});
		opportunitiesClient.addEntitlementsListener(entitlements ->
			clientThread.invokeLater(this::refreshAssistLine)
		);
	}

	/** Below FU's second line (y≈20); above our previous placement for visibility. */
	private static final int FLIPX_LINE_Y = 26;
	private static final int COLOR_DEFAULT = 0x800000;
	private static final int COLOR_HOVER = 0x000000;

	@Subscribe
	public void onVarClientIntChanged(VarClientIntChanged event)
	{
		if (!isFeatureEnabled())
		{
			return;
		}

		if (event.getIndex() == VarClientInt.INPUT_TYPE
			&& client.getVarcIntValue(VarClientInt.INPUT_TYPE) == 0)
		{
			resetGeChatAssist();
			return;
		}

		if (event.getIndex() != VarClientInt.INPUT_TYPE)
		{
			return;
		}

		scheduleRefreshAssist();
	}

	@Subscribe
	public void onVarClientStrChanged(VarClientStrChanged event)
	{
		if (!isFeatureEnabled())
		{
			return;
		}
		if (client.getVarcIntValue(VarClientInt.INPUT_TYPE) != CHAT_INPUT_TYPE_GE)
		{
			return;
		}
		scheduleRefreshAssist();
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!isFeatureEnabled())
		{
			return;
		}
		if (client.getVarcIntValue(VarClientInt.INPUT_TYPE) == CHAT_INPUT_TYPE_GE
			&& client.getWidget(InterfaceID.GeOffers.SETUP_DESC) != null)
		{
			if (!geChatOpen)
			{
				scheduleRefreshAssist();
			}
		}
		if (!geChatOpen)
		{
			return;
		}
		if (client.getVarcIntValue(VarClientInt.INPUT_TYPE) != CHAT_INPUT_TYPE_GE)
		{
			return;
		}
		String prompt = readChatPrompt();
		if (prompt != null && !prompt.equals(lastChatPrompt))
		{
			lastChatPrompt = prompt;
			clientThread.invokeLater(this::refreshAssistLine);
		}
	}

	private boolean isFeatureEnabled()
	{
		return config.enableGePriceAssist()
			&& config.apiKey() != null
			&& !config.apiKey().isBlank();
	}

	private void scheduleRefreshAssist()
	{
		if (client.getWidget(InterfaceID.Chatbox.MES_TEXT) == null
			|| client.getWidget(InterfaceID.GeOffers.SETUP_DESC) == null
			|| client.getVarcIntValue(VarClientInt.INPUT_TYPE) != CHAT_INPUT_TYPE_GE)
		{
			return;
		}
		geChatOpen = true;
		String prompt = readChatPrompt();
		if (prompt != null)
		{
			lastChatPrompt = prompt;
		}
		clientThread.invokeLater(() -> clientThread.invokeLater(this::refreshAssistLine));
	}

	private void resetGeChatAssist()
	{
		geChatOpen = false;
		attachedItemId = -1;
		attachedStep = GeOfferChatStep.NONE;
		lastChatPrompt = "";
		clientThread.invokeLater(this::hideAssistLine);
	}

	private void refreshAssistLine()
	{
		if (!geChatOpen || !isFeatureEnabled())
		{
			return;
		}

		Widget mesLayer = client.getWidget(InterfaceID.Chatbox.MES_LAYER);
		if (mesLayer == null)
		{
			return;
		}

		String chatPrompt = readChatPrompt();
		Boolean buyOffer = readBuyOfferSide();
		GeOfferChatStep step = parseGeOfferChatStep(chatPrompt);

		if (step == GeOfferChatStep.NONE)
		{
			hideAssistLine();
			attachedStep = GeOfferChatStep.NONE;
			return;
		}

		if (step == GeOfferChatStep.PRICE && buyOffer == null)
		{
			return;
		}

		int itemId = GeItemResolver.resolve(client);
		if (itemId <= 0)
		{
			return;
		}

		attachedItemId = itemId;
		attachedStep = step;

		switch (step)
		{
			case PRICE:
				attachPriceAssist(mesLayer, itemId, buyOffer != null && buyOffer);
				break;
			case QUANTITY_BUY:
				attachQuantityBuyAssist(mesLayer, itemId);
				break;
			default:
				hideAssistLine();
				break;
		}
	}

	private void attachPriceAssist(Widget mesLayer, int itemId, boolean buyOffer)
	{
		ItemDetailResponse detail = itemsClient.peek(itemId);
		FlipOpportunity opp = detail != null ? detail.getOpportunity() : null;

		if (detail == null || opp == null || itemsClient.isStale(itemId))
		{
			showAssistLine(mesLayer, "FlipX price loading…", false, null);
			executorService.execute(() ->
			{
				try
				{
					itemsClient.fetch(itemId);
				}
				catch (Exception e)
				{
					log.debug("FlipX price assist fetch failed", e);
				}
				clientThread.invokeLater(this::refreshAssistLine);
			});
			return;
		}

		GeAssistPricing.ResolvedPrice resolved = GeAssistPricing.resolve(
			detail,
			buyOffer,
			opportunitiesClient.getEntitlements()
		);
		if (resolved == null)
		{
			showAssistLine(mesLayer, "FlipX has no price for this item yet.", false, null);
			return;
		}

		int price = clampGeInt(GeAssistPricing.geOfferPriceGp(resolved, buyOffer, opp));
		String label = GeAssistPricing.priceLineLabel(buyOffer, resolved, opp);
		showAssistLine(
			mesLayer,
			label,
			true,
			() -> applyNumericChatInput(price, buyOffer)
		);
	}

	private void attachQuantityBuyAssist(Widget mesLayer, int itemId)
	{
		ItemStats stats = itemManager.getItemStats(itemId);
		int geLimit = stats != null ? stats.getGeLimit() : 0;
		Widget setup = client.getWidget(InterfaceID.GeOffers.SETUP);
		int clientBought = GeOfferSetupBuyProgress.parseBoughtSoFar(setup);
		coinBalanceService.refresh();
		int offerPriceGp = GeOfferSetupScripts.readOfferPriceGp(client);
		long inventoryCoins = coinBalanceService.getCoins();

		long accountHash = client.getAccountHash();
		if (accountHash == -1)
		{
			int qty = GeFlipxBuyLimit.quantityToApply(
				null,
				itemId,
				stats,
				clientBought,
				offerPriceGp,
				inventoryCoins
			);
			showQuantityBuyAssistLine(mesLayer, qty, geLimit, clientBought, displayLimitFor(null, geLimit));
			return;
		}
		String account = String.valueOf(accountHash);

		BuyLimitRemaining cached = buyLimitClient.peek(account, itemId);
		if (cached == null)
		{
			showAssistLine(mesLayer, "FlipX buy limit loading…", false, null);
			executorService.execute(() ->
			{
				try
				{
					buyLimitClient.fetch(account, itemId);
				}
				catch (Exception e)
				{
					log.debug("FlipX buy limit fetch failed", e);
				}
				clientThread.invokeLater(this::refreshAssistLine);
			});
			return;
		}

		int qty = GeFlipxBuyLimit.quantityToApply(
			cached,
			itemId,
			stats,
			clientBought,
			offerPriceGp,
			inventoryCoins
		);
		int displayLimit = displayLimitFor(cached, geLimit);
		showQuantityBuyAssistLine(mesLayer, qty, geLimit, clientBought, displayLimit);
	}

	private static int displayLimitFor(BuyLimitRemaining cached, int geLimit)
	{
		if (cached != null && cached.getBuyLimit() != null && cached.getBuyLimit() > 0)
		{
			return cached.getBuyLimit();
		}
		return geLimit;
	}

	private void showQuantityBuyAssistLine(
		Widget mesLayer,
		int qty,
		int geLimit,
		int clientBought,
		int displayLimit
	)
	{
		if (qty <= 0)
		{
			if (displayLimit > 0 && clientBought >= displayLimit)
			{
				showAssistLine(mesLayer, "FlipX buy limit: 0 left (4h window)", false, null);
			}
			else
			{
				showAssistLine(mesLayer, "FlipX buy limit: not enough coins at this price", false, null);
			}
			return;
		}

		String label = String.format("FlipX buy qty: %,d", qty);
		showAssistLine(
			mesLayer,
			label,
			true,
			() -> applyNumericChatInput(qty, true)
		);
	}

	private String readChatPrompt()
	{
		Widget mesText = client.getWidget(InterfaceID.Chatbox.MES_TEXT);
		return mesText != null ? mesText.getText() : null;
	}

	static GeOfferChatStep parseGeOfferChatStep(String chatPrompt)
	{
		if (chatPrompt == null)
		{
			return GeOfferChatStep.NONE;
		}
		if ("Set a price for each item:".equals(chatPrompt))
		{
			return GeOfferChatStep.PRICE;
		}
		String lower = chatPrompt.toLowerCase();
		if (lower.contains("how many do you wish to buy"))
		{
			return GeOfferChatStep.QUANTITY_BUY;
		}
		if (lower.contains("how many do you wish to sell"))
		{
			return GeOfferChatStep.QUANTITY_SELL;
		}
		return GeOfferChatStep.NONE;
	}

	private Boolean readBuyOfferSide()
	{
		Widget setup = client.getWidget(InterfaceID.GeOffers.SETUP);
		if (setup == null)
		{
			return null;
		}
		Widget[] children = setup.getChildren();
		if (children == null || children.length <= GE_SETUP_OFFER_LABEL_CHILD)
		{
			return null;
		}
		Widget labelWidget = children[GE_SETUP_OFFER_LABEL_CHILD];
		if (labelWidget == null)
		{
			return null;
		}
		return parseBuyOfferSide(labelWidget.getText());
	}

	private static int clampGeInt(long value)
	{
		if (value <= 0)
		{
			return 0;
		}
		return (int) Math.min(value, Integer.MAX_VALUE);
	}

	private void showAssistLine(
		Widget parent,
		String label,
		boolean clickable,
		Runnable onClick
	)
	{
		Widget line = ensureAssistLine(parent);
		line.setHidden(false);
		line.setTextColor(COLOR_DEFAULT);
		line.setText(label);

		if (!clickable || onClick == null)
		{
			line.setHasListener(false);
			line.revalidate();
			return;
		}

		line.setHasListener(true);
		line.setAction(0, attachedStep == GeOfferChatStep.PRICE ? "Set FlipX price" : "Set FlipX quantity");
		line.setOnMouseRepeatListener((JavaScriptCallback) ev -> line.setTextColor(COLOR_HOVER));
		line.setOnMouseLeaveListener((JavaScriptCallback) ev -> line.setTextColor(COLOR_DEFAULT));
		line.setOnOpListener((JavaScriptCallback) ev -> onClick.run());
		line.revalidate();
	}

	private Widget ensureAssistLine(Widget parent)
	{
		if (assistLine != null && assistLine.getParent() == parent)
		{
			return assistLine;
		}
		hideAssistLine();
		assistLine = parent.createChild(-1, WidgetType.TEXT);
		assistLine.setFontId(FontID.VERDANA_11_BOLD);
		assistLine.setXPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
		assistLine.setOriginalX(5);
		assistLine.setOriginalY(FLIPX_LINE_Y);
		assistLine.setOriginalHeight(18);
		assistLine.setXTextAlignment(WidgetTextAlignment.LEFT);
		assistLine.setWidthMode(WidgetSizeMode.MINUS);
		return assistLine;
	}

	private void hideAssistLine()
	{
		if (assistLine != null)
		{
			assistLine.setHidden(true);
			assistLine.setText("");
		}
	}

	private void applyNumericChatInput(int value, boolean buySideUsesInputText)
	{
		Widget mesText2 = client.getWidget(InterfaceID.Chatbox.MES_TEXT2);
		if (mesText2 == null)
		{
			return;
		}
		String text = value + "*";
		mesText2.setText(text);
		if (buySideUsesInputText)
		{
			client.setVarcStrValue(VarClientStr.INPUT_TEXT, String.valueOf(value));
		}
		else
		{
			client.setVarcStrValue(VarClientID.MESLAYERINPUT, String.valueOf(value));
		}
	}

	static Boolean parseBuyOfferSide(String offerLabelText)
	{
		if (offerLabelText == null)
		{
			return null;
		}
		String lower = offerLabelText.toLowerCase();
		if (lower.contains("buy"))
		{
			return true;
		}
		if (lower.contains("sell"))
		{
			return false;
		}
		return null;
	}
}
