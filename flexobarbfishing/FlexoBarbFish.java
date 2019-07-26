package net.runelite.client.plugins.flexobarbfishing;

import com.google.inject.Provides;
import java.awt.AWTException;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.GameState;
import net.runelite.api.GraphicID;
import net.runelite.api.Hitsplat;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.SpotAnimationChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.Widget;
import static net.runelite.api.widgets.WidgetID.LEVEL_UP_GROUP_ID;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.flexo.Flexo;
import net.runelite.client.flexo.FlexoMouse;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginType;
import net.runelite.client.plugins.stretchedmode.StretchedModeConfig;
import net.runelite.client.ui.ClientUI;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.HotkeyListener;

@PluginDescriptor(
	name = "Flexo Barb Fishing",
	type = PluginType.EXTERNAL
)
@Slf4j
@Singleton
public class FlexoBarbFish extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientUI clientUI;

	@Inject
	private ConfigManager configManager;

	@Inject
	private KeyManager keyManager;

	@Inject
	private FlexoBarbFishConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private FlexoBarbFishOverlay overlay;

	@Inject
	private EventBus eventBus;

	private int lastXaxisPos = 0;
	private int lastYaxisPos = 0;
	private Flexo flexo;
	private boolean flexoIsRunning = false;

	@Getter(AccessLevel.PACKAGE)
	private boolean enabled;

	@Getter(AccessLevel.PACKAGE)
	private FlexoBarbFishState state;

	private static final String FISHING_SPOT = "Fishing spot";
	private static final int FISHING_SPOT_ID = 1542;

	private int lastInvUpdate;
	private Instant lastInteracting;
	private Actor lastInteract;

	private static final int LOGOUT_WARNING_MILLIS = (4 * 60 + 40) * 1000; // 4 minutes and 40 seconds
	private static final int COMBAT_WARNING_MILLIS = 19 * 60 * 1000; // 19 minutes
	private static final int LOGOUT_WARNING_CLIENT_TICKS = LOGOUT_WARNING_MILLIS / Constants.CLIENT_TICK_LENGTH;
	private static final int COMBAT_WARNING_CLIENT_TICKS = COMBAT_WARNING_MILLIS / Constants.CLIENT_TICK_LENGTH;
	private static final int HIGHEST_MONSTER_ATTACK_SPEED = 8; // Except Scarab Mage, but they are with other monsters
	private boolean notifyIdleLogout = true;
	private int lastCombatCountdown = 0;

	private BlockingQueue queue = new ArrayBlockingQueue(1);
	private ThreadPoolExecutor executorService = new ThreadPoolExecutor(1, 1, 25, TimeUnit.SECONDS, queue,
		new ThreadPoolExecutor.DiscardPolicy());

	private int invItems = 2;

	private int timeout = 0;

	@Provides
	FlexoBarbFishConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(FlexoBarbFishConfig.class);
	}

	protected void startUp()
	{
		eventBus.subscribe(OverlayMenuClicked.class, this, this::onOverlayMenuClicked);
		eventBus.subscribe(GameTick.class, this, this::onGameTick);
		eventBus.subscribe(ItemContainerChanged.class, this, this::onItemContainerChanged);
		eventBus.subscribe(InteractingChanged.class, this, this::onInteractingChanged);
		eventBus.subscribe(GameStateChanged.class, this, this::onGameStateChanged);
		eventBus.subscribe(WidgetLoaded.class, this, this::onWidgetLoaded);
		eventBus.subscribe(SpotAnimationChanged.class, this, this::onSpotAnimationChanged);
		eventBus.subscribe(HitsplatApplied.class, this, this::onHitsplatApplied);

		overlayManager.add(overlay);

		try
		{
			flexo = new Flexo();
		}
		catch (AWTException e)
		{
			e.printStackTrace();
		}

		keyManager.registerKeyListener(hotkeyListener);
	}

	protected void shutDown()
	{
		eventBus.unregister(this);

		overlayManager.remove(overlay);

		keyManager.unregisterKeyListener(hotkeyListener);
	}

	private void onOverlayMenuClicked(OverlayMenuClicked c)
	{
		if (!c.getOverlay().equals(overlay))
		{
			return;
		}

		if ("Reset state".equals(c.getEntry().getOption()))
		{
			state = FlexoBarbFishState.CLICK_SPOT;
		}
	}

	private List<WidgetItem> getFish()
	{
		return getItems(11328, 11330, 11332, 15491, 15492, 15493);
	}

	private List<WidgetItem> getDrop()
	{
		return getItems(11328, 11330, 11332, 15491, 15492, 15493, 13648, 13649, 13650, 13651, 15484, 15485, 15486, 15487, 23129, 23130);
	}

	private List<WidgetItem> getItems(int... itemIds)
	{
		Widget inventoryWidget = client.getWidget(WidgetInfo.INVENTORY);
		ArrayList<Integer> itemIDs = new ArrayList<>();
		for (int i : itemIds)
		{
			itemIDs.add(i);
		}

		List<WidgetItem> listToReturn = new ArrayList<>();

		for (WidgetItem item : inventoryWidget.getWidgetItems())
		{
			if (itemIDs.contains(item.getId()))
			{
				listToReturn.add(item);
			}
		}

		return listToReturn;
	}

	private long getMillis()
	{
		return (long) (Math.random() * config.randLow() + config.randHigh());
	}

	private final HotkeyListener hotkeyListener = new HotkeyListener(
		() -> config.hotkeyToggle()
	)
	{
		@Override
		public void hotkeyPressed()
		{
			enabled = !enabled;

			if (enabled && state == null)
			{
				state = FlexoBarbFishState.CLICK_SPOT;
			}
		}
	};

	private static int getRandomIntBetweenRange(int min, int max)
	{
		return (int) ((Math.random() * ((max - min) + 1)) + min);
	}

	private Rectangle centerBounds(Rectangle clickArea)
	{
		clickArea.x += clickArea.getWidth() / 2;
		clickArea.y += clickArea.getHeight() / 2;

		return clickArea;
	}

	private Point modClickArea(Point point)
	{
		int xdiff = lastXaxisPos - point.getX();
		int ydiff = lastYaxisPos - point.getY();

		if (xdiff < 32 && xdiff >= -32 && ydiff <= 32 && ydiff >= -32)
		{
			return new Point(lastXaxisPos, lastYaxisPos);
		}

		return point;
	}

	private void flexoSetup()
	{
		if (Flexo.client == null)
		{
			Flexo.client = client;
			Flexo.clientUI = clientUI;
			Flexo.minDelay = getRandomIntBetweenRange(90, 290);
		}

		Flexo.isStretched = client.isStretchedEnabled();
		Flexo.scale = configManager.getConfig(StretchedModeConfig.class).scalingFactor();
	}

	private Point getClickPoint(Rectangle rect)
	{
		int x = (int) (rect.getX() + getRandomIntBetweenRange((int) rect.getWidth() / 6 * -1, (int) rect.getWidth() / 6) + rect.getWidth() / 2);
		int y = (int) (rect.getY() + getRandomIntBetweenRange((int) rect.getHeight() / 6 * -1, (int) rect.getHeight() / 6) + rect.getHeight() / 2);

		if (client.isStretchedEnabled())
		{
			double scale = 1 + ((double) configManager.getConfig(StretchedModeConfig.class).scalingFactor() / 100);
			return new Point((int) (x * scale), (int) (y * scale));
		}

		return new Point(x, y);
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
				lastInvUpdate = 0;

				flexo.delay((int) getMillis());

				Point point = modClickArea(getClickPoint(rectangle));

				flexo.mouseMove(point.getX(), point.getY());
				lastXaxisPos = point.getX();
				lastYaxisPos = point.getY();
				flexo.mousePressAndRelease(MouseEvent.BUTTON1);

				flexoIsRunning = false;
			};

			Thread thread = new Thread(runnable);
			thread.start();
		}
	}

	private void onGameTick(GameTick gameTick)
	{
		if (!enabled)
		{
			return;
		}

		lastInvUpdate++;

		if (lastInvUpdate > getRandomIntBetweenRange(30, 50))
		{
			reset();
			return;
		}

		final Player local = client.getLocalPlayer();
		final Duration waitDuration = Duration.ofMillis(1500);
		lastCombatCountdown = Math.max(lastCombatCountdown - 1, 0);

		if (client.getGameState() != GameState.LOGGED_IN
			|| local == null
			// If user has clicked in the last second then they're not idle so don't send idle notification
			|| System.currentTimeMillis() - client.getMouseLastPressedMillis() < 1000
			|| client.getKeyboardIdleTicks() < 10)
		{
			resetTimers();
			return;
		}

		if (checkIdleLogout())
		{
			state = FlexoBarbFishState.CLICK_SPOT;
		}

		if (checkInteractionIdle(waitDuration, local))
		{
			if (state == FlexoBarbFishState.FISHING)
			{
				state = FlexoBarbFishState.CLICK_SPOT;
			}
		}

		if (timeout > 0)
		{
			timeout--;
			return;
		}

		try
		{
			if (state == FlexoBarbFishState.CLICK_SPOT)
			{
				if (invItems == 27 || invItems == 28)
				{
					state = FlexoBarbFishState.DROPPING;
				}

				List<NPC> npcs = client.getNpcs();
				List<NPC> fishingSpots = new ArrayList<>();

				for (NPC npc : npcs)
				{
					if (npc.getId() == FISHING_SPOT_ID)
					{
						fishingSpots.add(npc);
					}
				}

				if (fishingSpots.size() > 0)
				{
					int distance = Integer.MAX_VALUE;
					NPC spot = null;

					for (NPC fishSpot : fishingSpots)
					{
						int dis = fishSpot.getWorldLocation().distanceTo(client.getLocalPlayer().getWorldArea());
						if (dis < distance)
						{
							spot = fishSpot;
							distance = dis;
						}
					}

					if (spot == null)
					{
						spot = fishingSpots.get(0);
					}

					leftClick(centerBounds(FlexoMouse.getClickArea(spot.getConvexHull().getBounds())));
					state = FlexoBarbFishState.FISHING;
				}
			}
			else if (state == FlexoBarbFishState.DROPPING)
			{
				if (getDrop().size() > 0)
				{
					state = FlexoBarbFishState.DROPPING_STUB;
					dropFish();
				}
				else
				{
					state = FlexoBarbFishState.CLICK_SPOT;
				}
			}
		}
		catch (NullPointerException | ArrayIndexOutOfBoundsException ex)
		{
			reset();
		}
	}

	private void reset()
	{
		if (invItems == 27 || invItems == 28)
		{
			if (getFish().size() == 0)
			{
				state = FlexoBarbFishState.DROPPING;
			}
		}
		else
		{
			state = FlexoBarbFishState.CLICK_SPOT;
		}

		lastInvUpdate = 0;
	}

	private void dropFish()
	{
		executorService.submit(() -> {
			for (WidgetItem fish : getDrop())
			{
				try
				{
					leftClick(centerBounds(FlexoMouse.getClickArea(fish.getCanvasBounds())));
				}
				catch (NullPointerException ex)
				{
					log.error(ex.getMessage());
				}
			}
			state = FlexoBarbFishState.DROPPING;
		});
	}

	private void onWidgetLoaded(WidgetLoaded widget)
	{
		if (widget.getGroupId() == LEVEL_UP_GROUP_ID)
		{
			if (state == FlexoBarbFishState.FISHING)
			{
				state = FlexoBarbFishState.CLICK_SPOT;
			}
		}
	}

	private void onItemContainerChanged(ItemContainerChanged itemContainerChanged)
	{
		ItemContainer itemContainer = itemContainerChanged.getItemContainer();

		if (itemContainer != client.getItemContainer(InventoryID.INVENTORY))
		{
			return;
		}

		Item[] items = itemContainer.getItems();

		int itemCount = 0;
		lastInvUpdate = 0;

		// Populate list of items in inventory without duplicates
		for (Item value : items)
		{
			if (value.getId() != -1)
			{
				itemCount++;
			}
		}

		invItems = itemCount;

		if (state == FlexoBarbFishState.FISHING && (invItems == 27 || invItems == 28))
		{
			state = FlexoBarbFishState.DROPPING;
			timeout = 1;
		}
		else if (state == FlexoBarbFishState.DROPPING && getDrop().size() == 0)
		{
			state = FlexoBarbFishState.CLICK_SPOT;
			timeout = 1;
		}
	}

	private void onInteractingChanged(InteractingChanged interactingChanged)
	{
		final Actor source = interactingChanged.getSource();
		if (source != client.getLocalPlayer())
		{
			return;
		}

		final Actor target = interactingChanged.getTarget();

		// Reset last interact
		if (target != null)
		{
			lastInteract = null;
		}
		else
		{
			lastInteracting = Instant.now();
		}

		final boolean isNpc = target instanceof NPC;

		// If this is not NPC, do not process as we are not interested in other entities
		if (!isNpc)
		{
			return;
		}

		if (target.getName() != null && target.getName().contains(FISHING_SPOT))
		{
			resetTimers();
			lastInteract = target;
			lastInteracting = Instant.now();
		}
	}

	private void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		lastInteracting = null;

		GameState state = gameStateChanged.getGameState();

		switch (state)
		{
			case LOGIN_SCREEN:
			case LOGGED_IN:
				resetTimers();
				break;
		}
	}

	private void resetTimers()
	{
		final Player local = client.getLocalPlayer();

		// Reset interaction idle timer
		lastInteracting = null;
		if (client.getGameState() == GameState.LOGIN_SCREEN || local == null || local.getInteracting() != lastInteract)
		{
			lastInteract = null;
		}
	}

	private boolean checkInteractionIdle(Duration waitDuration, Player local)
	{
		if (lastInteract == null)
		{
			return false;
		}

		final Actor interact = local.getInteracting();

		if (interact == null)
		{
			if (lastInteracting != null
				&& Instant.now().compareTo(lastInteracting.plus(waitDuration)) >= 0
				&& lastCombatCountdown == 0)
			{
				lastInteract = null;
				lastInteracting = null;
				return true;
			}
		}
		else
		{
			lastInteracting = Instant.now();
		}

		return false;
	}

	private boolean checkIdleLogout()
	{
		// Check clientside AFK first, because this is required for the server to disconnect you for being first
		int idleClientTicks = client.getKeyboardIdleTicks();
		if (client.getMouseIdleTicks() < idleClientTicks)
		{
			idleClientTicks = client.getMouseIdleTicks();
		}

		if (idleClientTicks < LOGOUT_WARNING_CLIENT_TICKS)
		{
			notifyIdleLogout = true;
			return false;
		}

		// If we are not receiving hitsplats then we can be afk kicked
		if (lastCombatCountdown <= 0)
		{
			boolean warn = notifyIdleLogout;
			notifyIdleLogout = false;
			return warn;
		}

		// We are in combat, so now we have to check for the timer that knocks you out of combat
		// I think there are other conditions that I don't know about, because during testing I just didn't
		// get removed from combat sometimes.
		final long lastInteractionAgo = System.currentTimeMillis() - client.getMouseLastPressedMillis();
		if (lastInteractionAgo < COMBAT_WARNING_MILLIS || client.getKeyboardIdleTicks() < COMBAT_WARNING_CLIENT_TICKS)
		{
			notifyIdleLogout = true;
			return false;
		}

		boolean warn = notifyIdleLogout;
		notifyIdleLogout = false;
		return warn;
	}

	private void onHitsplatApplied(HitsplatApplied event)
	{
		if (event.getActor() != client.getLocalPlayer())
		{
			return;
		}

		final Hitsplat hitsplat = event.getHitsplat();

		if (hitsplat.getHitsplatType() == Hitsplat.HitsplatType.DAMAGE
			|| hitsplat.getHitsplatType() == Hitsplat.HitsplatType.BLOCK)
		{
			lastCombatCountdown = HIGHEST_MONSTER_ATTACK_SPEED;
		}
	}

	private void onSpotAnimationChanged(SpotAnimationChanged event)
	{
		Actor actor = event.getActor();

		if (actor != client.getLocalPlayer())
		{
			return;
		}

		if (actor.getSpotAnimation() == GraphicID.SPLASH)
		{
			lastCombatCountdown = HIGHEST_MONSTER_ATTACK_SPEED;
		}
	}
}

