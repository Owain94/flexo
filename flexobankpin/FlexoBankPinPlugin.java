package net.runelite.client.plugins.flexobankpin;

import com.google.inject.Inject;
import com.google.inject.Provides;
import java.awt.AWTException;
import java.awt.event.KeyEvent;
import net.runelite.api.Client;
import net.runelite.api.events.GameTick;
import net.runelite.api.widgets.WidgetID;
import static net.runelite.api.widgets.WidgetInfo.BANK_PIN_INSTRUCTION_TEXT;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneLitePlusConfig;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.flexo.Flexo;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginType;
import net.runelite.client.ui.ClientUI;

@PluginDescriptor(
	name = "Flexo Bank Pin",
	description = " Enters your bank pin",
	tags = {"Bank", "Pin", "Flexo"},
	type = PluginType.EXTERNAL
)
public class FlexoBankPinPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientUI clientUI;

	@Inject
	private RuneLitePlusConfig plusConfig;

	@Inject
	private FlexoBankPinConfig config;

	@Inject
	private EventBus eventBus;

	private Flexo flexo;

	private boolean first;
	private boolean second;
	private boolean third;
	private boolean fourth;

	@Provides
	FlexoBankPinConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(FlexoBankPinConfig.class);
	}

	protected void startUp()
	{
		try
		{
			flexo = new Flexo();
		}
		catch (AWTException e)
		{
			e.printStackTrace();
		}

		eventBus.subscribe(GameTick.class, this, this::onGameTick);
	}

	protected void shutDown()
	{
		eventBus.unregister(this);
	}

	private void onGameTick(GameTick ignored)
	{
		if (client.getWidget(WidgetID.BANK_PIN_GROUP_ID, BANK_PIN_INSTRUCTION_TEXT.getChildId()) == null
			|| !client.getWidget(BANK_PIN_INSTRUCTION_TEXT).getText().equals("First click the FIRST digit.")
			&& !client.getWidget(BANK_PIN_INSTRUCTION_TEXT).getText().equals("Now click the SECOND digit.")
			&& !client.getWidget(BANK_PIN_INSTRUCTION_TEXT).getText().equals("Time for the THIRD digit.")
			&& !client.getWidget(BANK_PIN_INSTRUCTION_TEXT).getText().equals("Finally, the FOURTH digit."))
		{
			return;
		}

		if (!plusConfig.keyboardPin() || config.bankpin() < 1000)
		{
			return;
		}

		String number = String.valueOf(config.bankpin());

		char[] digits = number.toCharArray();
		int charCode = -1;

		switch (client.getWidget(BANK_PIN_INSTRUCTION_TEXT).getText())
		{
			case "First click the FIRST digit.":
				if (first)
				{
					return;
				}

				charCode = KeyEvent.getExtendedKeyCodeForChar(digits[0]);
				first = true;
				break;
			case "Now click the SECOND digit.":
				if (second)
				{
					return;
				}

				charCode = KeyEvent.getExtendedKeyCodeForChar(digits[1]);
				second = true;
				break;
			case "Time for the THIRD digit.":
				if (third)
				{
					return;
				}

				charCode = KeyEvent.getExtendedKeyCodeForChar(digits[2]);
				third = true;
				break;
			case "Finally, the FOURTH digit.":
				if (!first && !fourth)
				{
					return;
				}

				charCode = KeyEvent.getExtendedKeyCodeForChar(digits[3]);
				fourth = true;
				break;
		}

		if (charCode != -1)
		{
			sendKey(charCode);

			if (fourth)
			{
				first = false;
				second = false;
				third = false;
				fourth = false;
			}
		}
	}

	@SuppressWarnings("SameParameterValue")
	private static int getRandomIntBetweenRange(int min, int max)
	{
		return (int) ((Math.random() * ((max - min) + 1)) + min);
	}

	private long getMillis()
	{
		return (long) (Math.random() * config.randLow() + config.randHigh());
	}

	private void flexoSetup()
	{
		if (Flexo.client == null)
		{
			Flexo.client = client;
			Flexo.clientUI = clientUI;
			Flexo.minDelay = getRandomIntBetweenRange(90, 290);
		}
	}

	private void sendKey(int keyEvent)
	{
		flexoSetup();

		if (flexo != null)
		{
			Runnable runnable = () ->
			{
				flexo.delay((int) getMillis());
				flexo.keyPress(keyEvent);
				flexo.keyRelease(keyEvent);
			};

			Thread thread = new Thread(runnable);
			thread.start();
		}
	}
}
