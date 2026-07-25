package com.osrsflipfinder.runelite;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Copilot overlay accessor - delegates to {@link ItemsClient} so GE overlay
 * numbers always match market detail and GE setup.
 */
@Singleton
public class CopilotClient
{
	private final ItemsClient itemsClient;

	@Inject
	CopilotClient(ItemsClient itemsClient)
	{
		this.itemsClient = itemsClient;
	}

	CopilotItem peek(int itemId)
	{
		return itemsClient.toCopilotItem(itemId);
	}

	CopilotItem fetch(int itemId) throws IOException
	{
		itemsClient.fetch(itemId);
		CopilotItem item = itemsClient.toCopilotItem(itemId);
		if (item == null)
		{
			throw new IOException("Item not found");
		}
		return item;
	}

	List<CopilotItem> fetchBulk(List<Integer> itemIds) throws IOException
	{
		for (int itemId : itemIds)
		{
			if (itemsClient.isStale(itemId))
			{
				itemsClient.fetch(itemId);
			}
		}

		List<CopilotItem> result = new ArrayList<>();
		for (int itemId : itemIds)
		{
			CopilotItem item = peek(itemId);
			if (item != null)
			{
				result.add(item);
			}
		}
		return result;
	}

	void clear()
	{
		itemsClient.clearMemory();
	}
}
