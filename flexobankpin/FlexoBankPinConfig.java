package net.runelite.client.plugins.flexobankpin;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("FlexoBankPin")
public interface FlexoBankPinConfig extends Config
{
	@ConfigItem(
		keyName = "bankpin",
		name = "Bank Pin",
		description = "Bank pin that will be entered",
		position = 0
	)
	default int bankpin()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "randLow",
		name = "Minimum MS",
		description = "Dont be greedy",
		position = 1
	)
	default int randLow()
	{
		return 300;
	}

	@ConfigItem(
		keyName = "randLower",
		name = "Maximum MS",
		description = "Dont be greedy",
		position = 2
	)
	default int randHigh()
	{
		return 400;
	}
}
