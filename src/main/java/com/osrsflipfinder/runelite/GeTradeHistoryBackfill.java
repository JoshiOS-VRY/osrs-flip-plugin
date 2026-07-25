package com.osrsflipfinder.runelite;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;

/**
 * RuneLite's built-in Grand Exchange plugin stores completed trades per RS profile
 * in {@code grandexchange.tradeHistory}. We ingest missing rows after login to
 * recover flips that never uploaded during auth outages.
 */
@Slf4j
@Singleton
public class GeTradeHistoryBackfill
{
	private static final String RL_GE_GROUP = "grandexchange";
	private static final String RL_TRADE_HISTORY = "tradeHistory";
	private static final String WATERMARK_CONFIG_KEY = "geTradeHistoryWatermark";
	private static final long WATERMARK_OVERLAP_MINUTES = 10L;

	private static final Type TRADE_LIST_TYPE = new TypeToken<List<RuneliteHistoryTrade>>()
	{
	}.getType();

	private final Client client;
	private final ConfigManager configManager;
	private final FlipFinderConfig config;
	private final Gson gson;
	private final IngestClient ingestClient;
	private final ItemManager itemManager;

	@Inject
	GeTradeHistoryBackfill(
		Client client,
		ConfigManager configManager,
		FlipFinderConfig config,
		Gson gson,
		IngestClient ingestClient,
		ItemManager itemManager
	)
	{
		this.client = client;
		this.configManager = configManager;
		this.config = config;
		this.gson = gson;
		this.ingestClient = ingestClient;
		this.itemManager = itemManager;
	}

	void syncFromRuneliteProfileHistory()
	{
		if (!ingestClient.isConfigured())
		{
			return;
		}

		long accountHash = client.getAccountHash();
		if (accountHash == -1)
		{
			return;
		}

		String raw = configManager.getRSProfileConfiguration(RL_GE_GROUP, RL_TRADE_HISTORY);
		if (raw == null || raw.isBlank())
		{
			return;
		}

		List<RuneliteHistoryTrade> trades;
		try
		{
			trades = gson.fromJson(raw, TRADE_LIST_TYPE);
		}
		catch (JsonSyntaxException e)
		{
			log.debug("Could not parse RuneLite GE tradeHistory", e);
			return;
		}

		if (trades == null || trades.isEmpty())
		{
			return;
		}

		String accountHashValue = String.valueOf(accountHash);
		String displayName = client.getLocalPlayer() != null
			? client.getLocalPlayer().getName()
			: null;

		Instant watermark = readWatermark(accountHashValue);
		Instant cutoff;
		if (watermark.equals(Instant.EPOCH))
		{
			// First sync for this Jagex account on this RuneLite install: do not upload the
			// entire profile GE history into a newly paired FlipX account — only trades from
			// pairing onward (plus overlap for clock skew).
			cutoff = initialBackfillCutoff();
		}
		else
		{
			cutoff = watermark.minus(WATERMARK_OVERLAP_MINUTES, ChronoUnit.MINUTES);
		}
		Instant maxTradeTime = watermark;

		int enqueued = 0;
		for (RuneliteHistoryTrade trade : trades)
		{
			Instant occurredAt = trade.time != null ? trade.time : Instant.now();
			if (occurredAt.isBefore(cutoff))
			{
				continue;
			}
			if (occurredAt.isAfter(maxTradeTime))
			{
				maxTradeTime = occurredAt;
			}

			IngestGeEvent event = toIngestEvent(trade, accountHashValue, displayName);
			if (event != null)
			{
				ingestClient.enqueue(event);
				enqueued++;
			}
		}

		if (enqueued > 0)
		{
			log.debug("Enqueued {} RuneLite GE history trade(s) for ingest", enqueued);
			ingestClient.flushAsync();
		}

		if (maxTradeTime.isAfter(watermark))
		{
			writeWatermark(accountHashValue, maxTradeTime);
		}
	}

	private Instant initialBackfillCutoff()
	{
		String pairedAt = config.pairedAt();
		if (pairedAt != null && !pairedAt.isBlank())
		{
			try
			{
				return Instant.parse(pairedAt).minus(WATERMARK_OVERLAP_MINUTES, ChronoUnit.MINUTES);
			}
			catch (RuntimeException ignored)
			{
				// fall through
			}
		}
		return Instant.now().minus(WATERMARK_OVERLAP_MINUTES, ChronoUnit.MINUTES);
	}

	private Instant readWatermark(String accountHash)
	{
		String raw = configManager.getConfiguration(
			FlipFinderConfig.GROUP,
			WATERMARK_CONFIG_KEY + "." + accountHash
		);
		if (raw == null || raw.isBlank())
		{
			return Instant.EPOCH;
		}
		try
		{
			return Instant.parse(raw);
		}
		catch (RuntimeException e)
		{
			return Instant.EPOCH;
		}
	}

	private void writeWatermark(String accountHash, Instant instant)
	{
		configManager.setConfiguration(
			FlipFinderConfig.GROUP,
			WATERMARK_CONFIG_KEY + "." + accountHash,
			instant.toString()
		);
	}

	private IngestGeEvent toIngestEvent(
		RuneliteHistoryTrade trade,
		String accountHash,
		String displayName
	)
	{
		if (trade == null || trade.itemId <= 0 || trade.quantity <= 0 || trade.price <= 0)
		{
			return null;
		}

		Instant occurredAt = trade.time != null ? trade.time : Instant.now();
		long epochSecond = occurredAt.getEpochSecond();
		boolean buy = trade.buy;
		String side = buy ? "buy" : "sell";
		String state = buy ? "bought" : "sold";

		String itemName = null;
		try
		{
			itemName = itemManager.getItemComposition(trade.itemId).getName();
		}
		catch (RuntimeException ignored)
		{
			// item metadata optional
		}

		String idempotencyKey = IdempotencyKeyBuilder.importHistory(
			accountHash,
			trade.itemId,
			side,
			trade.price,
			trade.quantity,
			state,
			epochSecond
		);

		return IngestGeEvent.builder()
			.idempotencyKey(idempotencyKey)
			.accountHash(accountHash)
			.accountDisplayName(displayName)
			.itemId(trade.itemId)
			.itemName(itemName)
			.side(side)
			.price(trade.price)
			.quantity(trade.quantity)
			.quantityFilled(trade.quantity)
			.state(state)
			.occurredAt(occurredAt.toString())
			.source("import_runelite_ge")
			.build();
	}

	static final class RuneliteHistoryTrade
	{
		boolean buy;
		int itemId;
		int quantity;
		int price;
		Instant time;

		static List<RuneliteHistoryTrade> empty()
		{
			return Collections.emptyList();
		}
	}
}
