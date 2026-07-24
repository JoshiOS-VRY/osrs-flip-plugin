package com.osrsflipfinder.runelite;

import java.util.List;
import lombok.Data;

/** Response of {@code POST /api/plugin/copilot/items}. */
@Data
public class CopilotItemsResponse
{
	private List<CopilotItem> items;
}
