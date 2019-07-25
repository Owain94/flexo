package net.runelite.client.plugins.flexologin;

import com.google.inject.Inject;
import com.google.inject.Provides;
import java.awt.AWTException;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Point;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.flexo.Flexo;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginType;
import net.runelite.client.plugins.stretchedmode.StretchedModeConfig;
import net.runelite.client.ui.ClientUI;

@PluginDescriptor(
	name = "Flexo Login",
	description = " Enters your username and password",
	tags = {"login", "Flexo"},
	type = PluginType.EXTERNAL
)
@Slf4j
public class FlexoLoginPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientUI clientUI;

	@Inject
	private FlexoLoginConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private EventBus eventBus;

	private Flexo flexo;
	private boolean flexoIsRunning = false;

	private BlockingQueue queue = new ArrayBlockingQueue(1);
	@SuppressWarnings("unchecked")
	private ThreadPoolExecutor executorService = new ThreadPoolExecutor(1, 1, 25, TimeUnit.SECONDS, queue,
		new ThreadPoolExecutor.DiscardPolicy());

	@Provides
	FlexoLoginConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(FlexoLoginConfig.class);
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

		eventBus.subscribe(GameStateChanged.class, this, this::onGameStateChanged);
		eventBus.subscribe(GameTick.class, this, this::onGameTick);
	}

	protected void shutDown()
	{
		eventBus.unregister(this);
	}

	private void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		log.info("gamestate changed {}", gameStateChanged.getGameState());

		if (gameStateChanged.getGameState() == GameState.LOGIN_SCREEN)
		{
			executorService.submit(() -> {
				sendKey(KeyEvent.VK_ENTER);

				client.setUsername(config.email());
				client.setPassword(config.password());

				sendKey(KeyEvent.VK_ENTER);
				sendKey(KeyEvent.VK_ENTER);
			});
		}
	}

	private void onGameTick(GameTick ignored)
	{
		Widget login = client.getWidget(378, 81);
		if (login != null && !flexoIsRunning)
		{
			leftClick(login.getBounds());
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

	private Point getClickPoint(Rectangle rect)
	{
		int x = (int) (rect.getX() + getRandomIntBetweenRange((int) rect.getWidth() / 3 * -1, (int) rect.getWidth() / 3) + rect.getWidth() / 2);
		int y = (int) (rect.getY() + getRandomIntBetweenRange((int) rect.getHeight() / 3 * -1, (int) rect.getHeight() / 3) + rect.getHeight() / 2);

		if (client.isStretchedEnabled())
		{
			double scale = 1 + ((double) configManager.getConfig(StretchedModeConfig.class).scalingFactor() / 100);
			return new Point((int) (x * scale), (int) (y * scale));
		}

		return new Point(x, y);
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

	private void leftClick(Rectangle rectangle)
	{
		if (rectangle == null || flexoIsRunning)
		{
			return;
		}

		flexoSetup();

		if (flexo != null)
		{
			Runnable runnable = () -> {
				flexoIsRunning = true;

				flexo.delay((int) getMillis());

				Point point = getClickPoint(rectangle);

				flexo.mouseMove(point.getX(), point.getY());
				flexo.mousePressAndRelease(MouseEvent.BUTTON1);

				flexoIsRunning = false;
			};

			Thread thread = new Thread(runnable);
			thread.start();
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
