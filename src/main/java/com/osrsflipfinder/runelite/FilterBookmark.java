package com.osrsflipfinder.runelite;

import lombok.Data;

/** Saved filter/sort bookmark synced locally and optionally via cloud API. */
@Data
public class FilterBookmark
{
	private String id;
	private String context;
	private String name;
	private String presetId;
	private MarketQueryRequest.MarketFilters filters;
	private java.util.List<MarketQueryRequest.Sort> sort;
	private Integer volumeEmphasis;
	private boolean localOnly;
}
