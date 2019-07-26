package net.runelite.client.plugins.flexobarbfishing;

public enum FlexoBarbFishState
{
	CLICK_SPOT("Click spot"),
	FISHING("Fishing"),

	DROPPING("Dropping fish"),
	DROPPING_STUB("Dropping fish"),

	FATAL("Fatal error");


	private final String name;

	FlexoBarbFishState(String name)
	{
		this.name = name;
	}

	@Override
	public String toString()
	{
		return name;
	}
}
