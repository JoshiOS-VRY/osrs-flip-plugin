package com.osrsflipfinder.runelite;

import java.util.List;
import lombok.Value;

/** Body for {@code POST /api/plugin/copilot/items}. */
@Value
public class CopilotItemsRequest
{
	List<Integer> itemIds;
}
