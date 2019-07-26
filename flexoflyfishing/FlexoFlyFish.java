package net.runelite.client.plugins.flexoflyfishing;

import com.google.inject.Provides;
import java.awt.AWTException;
import java.awt.Rectangle;
import static java.awt.event.KeyEvent.VK_1;
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
import net.runelite.api.AnimationID;
import static net.runelite.api.AnimationID.COOKING_FIRE;
import static net.runelite.api.AnimationID.IDLE;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.GraphicID;
import net.runelite.api.Hitsplat;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
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
import net.runelite.client.plugins.idlenotifier.OutOfItemsMapping;
import net.runelite.client.plugins.stretchedmode.StretchedModeConfig;
import net.runelite.client.ui.ClientUI;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.HotkeyListener;

@PluginDescriptor(
	name = "Flexo Fly Fishing",
	type = PluginType.EXTERNAL
)
@Slf4j
@Singleton
public class FlexoFlyFish extends Plugin
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
	private FlexoFlyFishConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private FlexoFlyFishOverlay overlay;

	@Inject
	private EventBus eventBus;

	private int lastXaxisPos = 0;
	private int lastYaxisPos = 0;
	private Flexo flexo;
	private boolean flexoIsRunning = false;

	@Getter(AccessLevel.PACKAGE)
	private boolean enabled;

	@Getter(AccessLevel.PACKAGE)
	private FlexoFlyFishState state;

	private static final String FISHING_SPOT = "Fishing spot";
	private static final int FIRE_ID = 26185;
	private static final int FISHING_SPOT_ID = 1526;
	// Cook: 270.1

	private GameObject fire = null;
	private List<NPC> fishingSpots = new ArrayList<>();

	private Instant lastTimeItemsUsedUp;
	private List<Integer> itemIdsPrevious = new ArrayList<>();
	private List<Integer> itemQuantitiesPrevious = new ArrayList<>();
	private final List<Integer> itemQuantitiesChange = new ArrayList<>();

	private Instant lastAnimating;
	private int lastInvUpdate;
	private int lastAnimation = AnimationID.IDLE;
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
	FlexoFlyFishConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(FlexoFlyFishConfig.class);
	}

	protected void startUp()
	{
		eventBus.subscribe(OverlayMenuClicked.class, this, this::onOverlayMenuClicked);
		eventBus.subscribe(NpcSpawned.class, this, this::onNpcSpawned);
		eventBus.subscribe(NpcDespawned.class, this, this::onNpcDespawned);
		eventBus.subscribe(GameObjectSpawned.class, this, this::onGameObjectSpawned);
		eventBus.subscribe(GameObjectDespawned.class, this, this::onGameObjectDespawned);
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
			state = FlexoFlyFishState.CLICK_SPOT;
		}
	}

	private List<WidgetItem> getFish()
	{
		return getItems(335, 331);
	}

	private List<WidgetItem> getDrop()
	{
		return getItems(333, 343, 329, 13648, 13649, 13650, 13651, 15484, 15485, 15486, 15487, 23129, 23130);
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
				state = FlexoFlyFishState.CLICK_SPOT;
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

	private void onNpcSpawned(NpcSpawned npcSpawned)
	{
		final NPC npc = npcSpawned.getNpc();

		if (npc.getId() == FISHING_SPOT_ID)
		{
			fishingSpots.add(npc);
		}
	}

	private void onNpcDespawned(NpcDespawned npcDespawned)
	{
		final NPC npc = npcDespawned.getNpc();

		fishingSpots.remove(npc);
	}

	private void onGameObjectSpawned(GameObjectSpawned gameObjectSpawned)
	{
		final GameObject gameObject = gameObjectSpawned.getGameObject();
		final int eventObjectId = gameObject.getId();

		if (eventObjectId == FIRE_ID)
		{
			fire = gameObject;
		}
	}

	private void onGameObjectDespawned(GameObjectDespawned gameObjectDespawned)
	{
		final GameObject gameObject = gameObjectDespawned.getGameObject();
		final int eventObjectId = gameObject.getId();

		if (eventObjectId == FIRE_ID)
		{
			fire = null;
		}
	}

	private void onGameTick(GameTick gameTick)
	{
		if (!enabled)
		{
			return;
		}

		lastInvUpdate++;

		if (lastInvUpdate > 30)
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
			resetOutOfItemsIdleChecks();
			return;
		}

		if (checkIdleLogout())
		{
			state = FlexoFlyFishState.CLICK_SPOT;
		}

		if (checkOutOfItemsIdle(waitDuration))
		{
			if (state == FlexoFlyFishState.COOKING)
			{
				state = FlexoFlyFishState.CLICK_FISH;
			}

			// If this triggers, don't also trigger animation idle notification afterwards.
			lastAnimation = IDLE;
		}

		if (checkAnimationIdle(waitDuration, local))
		{
			if (state == FlexoFlyFishState.COOKING)
			{
				state = FlexoFlyFishState.CLICK_FISH;
			}
		}

		if (checkInteractionIdle(waitDuration, local))
		{
			if (state == FlexoFlyFishState.FISHING)
			{
				state = FlexoFlyFishState.CLICK_SPOT;
			}
		}

		if (timeout > 0)
		{
			timeout--;
			return;
		}

		try
		{
			if (state == FlexoFlyFishState.CLICK_SPOT)
			{
				if (invItems == 27 || invItems == 28)
				{
					state = FlexoFlyFishState.CLICK_FISH;
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
						}
					}

					if (spot == null)
					{
						spot = fishingSpots.get(0);
					}

					leftClick(centerBounds(FlexoMouse.getClickArea(spot.getConvexHull().getBounds())));
					state = FlexoFlyFishState.FISHING;
				}
			}
			else if (state == FlexoFlyFishState.CLICK_FISH)
			{
				if (getFish().size() == 0)
				{
					state = FlexoFlyFishState.DROPPING;
					return;
				}

				leftClick(centerBounds(FlexoMouse.getClickArea(getFish().get(0).getCanvasBounds())));
				state = FlexoFlyFishState.CLICK_FIRE;
				timeout = 1;
			}
			else if (state == FlexoFlyFishState.CLICK_FIRE)
			{
				leftClick(centerBounds(FlexoMouse.getClickArea(fire.getConvexHull().getBounds())));
				state = FlexoFlyFishState.WAIT_COOK;
			}
			else if (state == FlexoFlyFishState.DROPPING)
			{
				if (getDrop().size() > 0)
				{
					state = FlexoFlyFishState.DROPPING_STUB;
					dropFish();
				}
				else
				{
					state = FlexoFlyFishState.CLICK_SPOT;
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
				state = FlexoFlyFishState.DROPPING;
			}
			else
			{
				state = FlexoFlyFishState.CLICK_FISH;
			}
		}
		else
		{
			state = FlexoFlyFishState.CLICK_SPOT;
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
			state = FlexoFlyFishState.DROPPING;
		});
	}

	private void onWidgetLoaded(WidgetLoaded widget)
	{
		if (widget.getGroupId() == 270 && state == FlexoFlyFishState.WAIT_COOK)
		{
			sendKey(VK_1);
			state = FlexoFlyFishState.COOKING;
		}
		else if (widget.getGroupId() == LEVEL_UP_GROUP_ID)
		{
			if (state == FlexoFlyFishState.FISHING)
			{
				state = FlexoFlyFishState.CLICK_SPOT;
			}
			else if (state == FlexoFlyFishState.COOKING)
			{
				state = FlexoFlyFishState.CLICK_FISH;
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

		ArrayList<Integer> itemQuantities = new ArrayList<>();
		ArrayList<Integer> itemIds = new ArrayList<>();

		// Populate list of items in inventory without duplicates
		for (Item value : items)
		{
			if (value.getId() != -1)
			{
				itemCount++;
			}
			int itemId = OutOfItemsMapping.mapFirst(value.getId());
			if (itemIds.indexOf(itemId) == -1) // -1 if item not yet in list
			{
				itemIds.add(itemId);
			}
		}

		invItems = itemCount;

		// Populate quantity of each item in inventory
		for (int j = 0; j < itemIds.size(); j++)
		{
			itemQuantities.add(0);
			for (Item item : items)
			{
				if (itemIds.get(j) == OutOfItemsMapping.mapFirst(item.getId()))
				{
					itemQuantities.set(j, itemQuantities.get(j) + item.getQuantity());
				}
			}
		}

		itemQuantitiesChange.clear();

		// Calculate the quantity of each item consumed by the last action
		if (!itemIdsPrevious.isEmpty())
		{
			for (int i = 0; i < itemIdsPrevious.size(); i++)
			{
				int id = itemIdsPrevious.get(i);
				int currentIndex = itemIds.indexOf(id);
				int currentQuantity;
				if (currentIndex != -1) // -1 if item is no longer in inventory
				{
					currentQuantity = itemQuantities.get(currentIndex);
				}
				else
				{
					currentQuantity = 0;
				}
				itemQuantitiesChange.add(currentQuantity - itemQuantitiesPrevious.get(i));
			}
		}
		else
		{
			itemIdsPrevious = itemIds;
			itemQuantitiesPrevious = itemQuantities;
			return;
		}

		// Check we have enough items left for another action.
		for (int i = 0; i < itemQuantitiesPrevious.size(); i++)
		{
			if (-itemQuantitiesChange.get(i) * 2 > itemQuantitiesPrevious.get(i))
			{
				lastTimeItemsUsedUp = Instant.now();
				return;
			}
		}
		itemIdsPrevious = itemIds;
		itemQuantitiesPrevious = itemQuantities;

		if (state == FlexoFlyFishState.FISHING && (invItems == 27 || invItems == 28))
		{
			state = FlexoFlyFishState.CLICK_FISH;
			timeout = 1;
		}
		else if (state == FlexoFlyFishState.COOKING && getFish().size() == 0)
		{
			state = FlexoFlyFishState.DROPPING;
			timeout = 1;
		}
		else if (state == FlexoFlyFishState.DROPPING && getDrop().size() == 0)
		{
			state = FlexoFlyFishState.CLICK_SPOT;
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

	public void onAnimationChanged(AnimationChanged animationChanged)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		Player localPlayer = client.getLocalPlayer();
		if (localPlayer != animationChanged.getActor())
		{
			return;
		}

		int animation = localPlayer.getAnimation();
		switch (animation)
		{
			case COOKING_FIRE:
				resetTimers();
				lastAnimation = animation;
				lastAnimating = Instant.now();
				break;
			case IDLE:
				lastAnimating = Instant.now();
				break;
			default:
				// On unknown animation simply assume the animation is invalid and dont throw notification
				lastAnimation = IDLE;
				lastAnimating = null;
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

		// Reset animation idle timer
		lastAnimating = null;
		if (client.getGameState() == GameState.LOGIN_SCREEN || local == null || local.getAnimation() != lastAnimation)
		{
			lastAnimation = IDLE;
		}

		// Reset interaction idle timer
		lastInteracting = null;
		if (client.getGameState() == GameState.LOGIN_SCREEN || local == null || local.getInteracting() != lastInteract)
		{
			lastInteract = null;
		}
	}

	private boolean checkAnimationIdle(Duration waitDuration, Player local)
	{
		if (lastAnimation == IDLE)
		{
			return false;
		}

		final int animation = local.getAnimation();

		if (animation == IDLE)
		{
			if (lastAnimating != null && Instant.now().compareTo(lastAnimating.plus(waitDuration)) >= 0)
			{
				lastAnimation = IDLE;
				lastAnimating = null;
				return true;
			}
		}
		else
		{
			lastAnimating = Instant.now();
		}

		return false;
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

	private boolean checkOutOfItemsIdle(Duration waitDuration)
	{
		if (lastTimeItemsUsedUp == null)
		{
			return false;
		}

		if (Instant.now().compareTo(lastTimeItemsUsedUp.plus(waitDuration)) >= 0)
		{
			resetTimers();
			resetOutOfItemsIdleChecks();
			return true;
		}

		return false;
	}

	private void resetOutOfItemsIdleChecks()
	{
		lastTimeItemsUsedUp = null;
		itemQuantitiesChange.clear();
		itemIdsPrevious.clear();
		itemQuantitiesPrevious.clear();
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

