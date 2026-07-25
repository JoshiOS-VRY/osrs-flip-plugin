package com.osrsflipfinder.runelite;

/**
 * Production API endpoints for FlipX. End users never configure these - only
 * developers may override {@link #BASE_URL_PROPERTY} when running locally.
 */
public final class FlipXConstants
{
	static final String BASE_URL_PROPERTY = "flipx.baseUrl";

	private static final String PRODUCTION_BASE_URL = "https://www.flipx.gg";

	private FlipXConstants()
	{
	}

	public static String baseUrl()
	{
		String override = System.getProperty(BASE_URL_PROPERTY);
		if (override != null && !override.isBlank())
		{
			return normalizeBaseUrl(override);
		}
		return PRODUCTION_BASE_URL;
	}

	static String normalizeBaseUrl(String baseUrl)
	{
		if (baseUrl == null)
		{
			return PRODUCTION_BASE_URL;
		}
		String trimmed = baseUrl.trim();
		while (trimmed.endsWith("/"))
		{
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		return trimmed.isEmpty() ? PRODUCTION_BASE_URL : trimmed;
	}

	/** Apex redirects to www on Vercel; treat both as the same API host. */
	static String canonicalBaseUrl(String baseUrl)
	{
		String normalized = normalizeBaseUrl(baseUrl);
		if ("https://flipx.gg".equals(normalized))
		{
			return PRODUCTION_BASE_URL;
		}
		return normalized;
	}
}
