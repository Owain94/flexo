package net.runelite.client.plugins.flexologin;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("FlexoLogin")
public interface FlexoLoginConfig extends Config
{
	@ConfigItem(
		keyName = "email",
		name = "Email",
		description = "",
		position = 0
	)
	default String email()
	{
		return "";
	}

	@ConfigItem(
		keyName = "password",
		name = "Password",
		description = "",
		position = 1
	)
	default String password()
	{
		return "";
	}

	@ConfigItem(
		keyName = "randLow",
		name = "Minimum MS",
		description = "Dont be greedy",
		position = 2
	)
	default int randLow()
	{
		return 300;
	}

	@ConfigItem(
		keyName = "randLower",
		name = "Maximum MS",
		description = "Dont be greedy",
		position = 3
	)
	default int randHigh()
	{
		return 400;
	}
}
