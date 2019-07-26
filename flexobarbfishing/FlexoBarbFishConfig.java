package net.runelite.client.plugins.flexobarbfishing;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Keybind;

@ConfigGroup("FlexoBarbFishConfig")
public interface FlexoBarbFishConfig extends Config
{
	@ConfigItem(
		keyName = "hotkeyToggleArdyKnights",
		name = "Toggle automation",
		description = "Toggle flexo mouse control",
		position = 0
	)
	default Keybind hotkeyToggle()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
		keyName = "randLow",
		name = "Minimum MS",
		description = "Dont be greedy",
		position = 1
	)
	default int randLow()
	{
		return 200;
	}

	@ConfigItem(
		keyName = "randLower",
		name = "Maximum MS",
		description = "Dont be greedy",
		position = 2
	)
	default int randHigh()
	{
		return 600;
	}
}
