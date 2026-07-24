package com.osrsflipfinder.runelite;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;

/**
 * Persists last-known API payloads under {@code ~/.runelite/flipx/} for offline
 * display when network requests fail.
 */
@Slf4j
@Singleton
public class LocalCacheStore
{
	static final long DEFAULT_MAX_AGE_MS = 24L * 60L * 60L * 1000L;

	private final Gson gson;
	private final Path cacheDir;

	@Inject
	LocalCacheStore(Gson gson)
	{
		this.gson = gson;
		this.cacheDir = RuneLite.RUNELITE_DIR.toPath().resolve("flipx");
	}

	boolean isEnabled(FlipFinderConfig config)
	{
		return config.enableOfflineCache();
	}

	<T> void write(String key, T payload, FlipFinderConfig config)
	{
		if (!isEnabled(config) || payload == null)
		{
			return;
		}
		try
		{
			Files.createDirectories(cacheDir);
			Envelope<T> envelope = new Envelope<>(payload, Instant.now().toString());
			Path target = cacheDir.resolve(safeKey(key) + ".json");
			Files.writeString(target, gson.toJson(envelope), StandardCharsets.UTF_8);
		}
		catch (IOException | RuntimeException ex)
		{
			log.debug("Failed to write cache entry {}", key, ex);
		}
	}

	<T> CachedEntry<T> read(String key, Class<T> type, FlipFinderConfig config)
	{
		if (!isEnabled(config))
		{
			return null;
		}
		try
		{
			Path target = cacheDir.resolve(safeKey(key) + ".json");
			if (!Files.isRegularFile(target))
			{
				return null;
			}
			String raw = Files.readString(target, StandardCharsets.UTF_8);
			Envelope<T> envelope = gson.fromJson(raw, Envelope.type(type));
			if (envelope == null || envelope.payload == null || envelope.cachedAt == null)
			{
				return null;
			}
			Instant cachedAt = Instant.parse(envelope.cachedAt);
			if (Instant.now().toEpochMilli() - cachedAt.toEpochMilli() > DEFAULT_MAX_AGE_MS)
			{
				return null;
			}
			return new CachedEntry<>(envelope.payload, cachedAt);
		}
		catch (IOException | RuntimeException ex)
		{
			log.debug("Failed to read cache entry {}", key, ex);
			return null;
		}
	}

	static String formatCachedAt(Instant cachedAt)
	{
		if (cachedAt == null)
		{
			return "unknown time";
		}
		return cachedAt.toString().replace('T', ' ').substring(0, Math.min(16, cachedAt.toString().length()));
	}

	private static String safeKey(String key)
	{
		return key.replaceAll("[^a-zA-Z0-9._-]", "_");
	}

	static final class CachedEntry<T>
	{
		private final T payload;
		private final Instant cachedAt;

		CachedEntry(T payload, Instant cachedAt)
		{
			this.payload = payload;
			this.cachedAt = cachedAt;
		}

		T getPayload()
		{
			return payload;
		}

		Instant getCachedAt()
		{
			return cachedAt;
		}
	}

	private static final class Envelope<T>
	{
		private T payload;
		private String cachedAt;

		Envelope(T payload, String cachedAt)
		{
			this.payload = payload;
			this.cachedAt = cachedAt;
		}

		static <T> java.lang.reflect.Type type(Class<T> clazz)
		{
			return com.google.gson.reflect.TypeToken.getParameterized(Envelope.class, clazz).getType();
		}
	}
}
