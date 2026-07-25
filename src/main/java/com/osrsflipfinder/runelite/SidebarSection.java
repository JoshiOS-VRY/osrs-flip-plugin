package com.osrsflipfinder.runelite;

/** Top-level FlipX sidebar views selected from the section dropdown. */
enum SidebarSection
{
	CONNECTION("Connection"),
	MY_SLOTS("Flip manager"),
	SESSION("Session stats"),
	MARKET("Market"),
	GE_SETUP("GE copilot"),
	IMPORT("Import history"),
	RECIPE_FLIPS("Recipe flips");

	private final String label;

	SidebarSection(String label)
	{
		this.label = label;
	}

	String label()
	{
		return label;
	}

	static SidebarSection[] navOrder()
	{
		return values();
	}
}
