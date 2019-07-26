package net.runelite.client.plugins.flexoflyfishing;

public enum FlexoFlyFishState
{
	CLICK_SPOT("Click spot"),
	FISHING("Fishing"),

	CLICK_FISH("Click fish"),
	CLICK_FIRE("Click fire"),
	WAIT_COOK("Waiting"),
	COOKING("Cooking"),

	DROPPING("Dropping fish"),
	DROPPING_STUB("Dropping fish"),

	FATAL("Fatal error");


	private final String name;

	FlexoFlyFishState(String name)
	{
		this.name = name;
	}

	@Override
	public String toString()
	{
		return name;
	}
}
