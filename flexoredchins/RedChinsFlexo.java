/*
 * Copyright (c) 2019, ganom <https://github.com/Ganom>
 * Copyright (c) 2019, Owain van Brakel <owain.vanbrakel@gmail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.flexoredchins;

import com.google.common.collect.ImmutableList;
import com.google.inject.Provides;
import java.awt.AWTException;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javafx.scene.input.KeyCode;
import javax.inject.Inject;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemID;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.NpcID;
import net.runelite.api.ObjectID;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.Skill;
import net.runelite.api.Tile;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.flexo.Flexo;
import net.runelite.client.flexo.FlexoMouse;
import net.runelite.client.game.ItemManager;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginType;
import net.runelite.client.plugins.stretchedmode.StretchedModeConfig;
import net.runelite.client.ui.ClientUI;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.util.Text;

@PluginDescriptor(
	name = "Flexo Red Chins",
	description = " Red Chins Assistant",
	tags = {"red", "chins", "helper"},
	type = PluginType.EXTERNAL
)
@Slf4j
public class RedChinsFlexo extends Plugin
{

	private static final int RED_CHIN_AREA = 10129;

	private static final String TRAP_SET_MESSAGE = "You begin setting up";

	private static final ImmutableList<String> PET_MESSAGES = ImmutableList.of("You have a funny feeling like you're being followed",
		"You feel something weird sneaking into your backpack",
		"You have a funny feeling like you would have been followed");

	@Inject
	private Client client;

	@Inject
	private ClientUI clientUI;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ConfigManager configManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private RedChinsFlexoOverlay overlay;

	@Inject
	private RedChinsFlexoNpcTileOverlay npcOverlay;

	@Inject
	private RedChinsFlexoConfig config;

	@Inject
	private KeyManager keyManager;

	@Inject
	private TabUtils tabUtils;

	@Getter(AccessLevel.PACKAGE)
	private boolean enabled;

	@Getter(AccessLevel.PACKAGE)
	private final List<NPC> chins = new ArrayList<>();

	@Getter(AccessLevel.PACKAGE)
	private int chinsCaught;

	@Getter(AccessLevel.PACKAGE)
	private boolean pet = false;

	@Getter(AccessLevel.PACKAGE)
	private final Map<WorldPoint, HunterTrap> traps = new HashMap<>();

	@Getter(AccessLevel.PACKAGE)
	private RedChinFlexoState state = RedChinFlexoState.DISABLED;

	@Getter
	private Instant lastActionTime = Instant.ofEpochMilli(0);

	@Getter
	private ArrayList<WorldPoint> traplocations = new ArrayList<>();

	private final HashSet<WorldPoint> ourTrapLocs = new HashSet<>();
	private final HashSet<WorldPoint> trapItemPoints = new HashSet<>();
	private int lastChatTick;
	private WorldPoint lastTickLocalPlayerLocation;
	private WorldPoint validPoint;

	private int timeout = 0;
	private int lastXaxisPos = 0;
	private int lastYaxisPos = 0;
	private Flexo flexo;
	private boolean flexoIsRunning = false;
	private boolean setup = true;
	private int startChins = 0;
	private WorldPoint tickCheck;

	private ArrayList<TrapResetAction> trapsReset = new ArrayList<>();
	private ArrayList<Tile> layReset = new ArrayList<>();

	class TrapResetAction
	{
		GameObject gameObject;
		WorldPoint worldPoint;

		TrapResetAction(GameObject gameObject, WorldPoint worldPoint)
		{
			this.gameObject = gameObject;
			this.worldPoint = worldPoint;
		}
	}


	@Provides
	RedChinsFlexoConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(RedChinsFlexoConfig.class);
	}

	protected void startUp()
	{
		overlayManager.add(overlay);
		overlayManager.add(npcOverlay);
		keyManager.registerKeyListener(hotkeyListener);

		try
		{
			flexo = new Flexo();
		}
		catch (AWTException e)
		{
			e.printStackTrace();
		}
	}

	protected void shutDown()
	{
		overlayManager.remove(overlay);
		overlayManager.remove(npcOverlay);
		keyManager.unregisterKeyListener(hotkeyListener);
		chins.clear();
		lastActionTime = Instant.ofEpochMilli(0);
		ourTrapLocs.clear();
		traps.clear();
		trapItemPoints.clear();
		validPoint = null;
		traplocations.clear();
	}

	private List<WidgetItem> getBoxtraps()
	{
		return getItems(ItemID.BOX_TRAP);
	}

	private List<WidgetItem> getChinchompas()
	{
		return getItems(ItemID.RED_CHINCHOMPA_10034);
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

	private final HotkeyListener hotkeyListener = new HotkeyListener(
		() -> config.hotkeyToggleRedChins()
	)
	{
		@Override
		public void hotkeyPressed()
		{
			enabled = !enabled;

			if (enabled)
			{
				state = RedChinFlexoState.COMPASS;

				if (startChins == 0)
				{
					startChins = getChinchompas().get(0).getQuantity();
				}

				chins.clear();
				ourTrapLocs.clear();
				traps.clear();
				trapItemPoints.clear();
				validPoint = null;
				lastTickLocalPlayerLocation = null;
				traplocations.clear();
				setup = true;
				trapsReset.clear();
				layReset.clear();
			}
			else
			{
				state = RedChinFlexoState.DISABLED;
			}
		}
	};

	@Subscribe
	public void onOverlayMenuClicked(OverlayMenuClicked c)
	{
		if (!c.getOverlay().equals(overlay))
		{
			return;
		}

		if ("Reset".equals(c.getEntry().getOption()))
		{
			chinsCaught = 0;
			state = RedChinFlexoState.DISABLED;
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		GameState gameState = gameStateChanged.getGameState();

		if (gameState == GameState.CONNECTION_LOST || gameState == GameState.LOGIN_SCREEN || gameState == GameState.HOPPING)
		{
			chins.clear();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		Iterator<Map.Entry<WorldPoint, HunterTrap>> it = traps.entrySet().iterator();
		Tile[][][] tiles = client.getScene().getTiles();

		Instant expire = Instant.now().minus(HunterTrap.TRAP_TIME.multipliedBy(2));

		while (it.hasNext())
		{
			Map.Entry<WorldPoint, HunterTrap> entry = it.next();
			HunterTrap trap = entry.getValue();
			WorldPoint world = entry.getKey();
			LocalPoint local = LocalPoint.fromWorld(client, world);

			if (local == null)
			{
				if (trap.getPlacedOn().isBefore(expire))
				{
					it.remove();
					continue;
				}
				continue;
			}

			Tile tile = tiles[world.getPlane()][local.getSceneX()][local.getSceneY()];
			GameObject[] objects = tile.getGameObjects();

			boolean containsAnything = false;
			for (GameObject object : objects)
			{
				if (object != null)
				{
					containsAnything = true;
				}
			}

			if (!containsAnything)
			{
				it.remove();
			}
		}

		lastTickLocalPlayerLocation = client.getLocalPlayer().getWorldLocation();

		client.getLocalPlayer().setAnimation(-1);

		if (timeout > 0)
		{
			timeout--;
		}
		else if (enabled && isInsideRedChinArea() && timeout <= 0)
		{
			if (client.getWidget(WidgetInfo.INVENTORY).isHidden())
			{
				sendKey(tabUtils.getTabHotkey(Tab.INVENTORY));
			}

			// Camera
			else if (state == RedChinFlexoState.COMPASS)
			{
				Widget resizableCompass = client.getWidget(161, 24);
				Widget staticCompass = client.getWidget(548, 7);

				if (resizableCompass != null)
				{
					flexoMouseAction(wrapFlexo(resizableCompass.getBounds()));
				}
				else if (staticCompass != null)
				{
					flexoMouseAction(wrapFlexo(staticCompass.getBounds()));
				}

				state = RedChinFlexoState.CAMERA;
				timeout = 2;
			}
			else if (state == RedChinFlexoState.CAMERA)
			{
				holdKey(KeyEvent.VK_UP, 2500);

				state = RedChinFlexoState.GET_TRAP_LOCATIONS;
				timeout = 4;
			}

			// Trap locations
			else if (state == RedChinFlexoState.GET_TRAP_LOCATIONS)
			{
				WorldPoint playerlocation = client.getLocalPlayer().getWorldLocation();

				traplocations.add(new WorldPoint(playerlocation.getX() - 1, playerlocation.getY() + 1, playerlocation.getPlane()));
				traplocations.add(new WorldPoint(playerlocation.getX() + 1, playerlocation.getY() + 1, playerlocation.getPlane()));
				traplocations.add(playerlocation);
				traplocations.add(new WorldPoint(playerlocation.getX() - 1, playerlocation.getY() - 1, playerlocation.getPlane()));
				traplocations.add(new WorldPoint(playerlocation.getX() + 1, playerlocation.getY() - 1, playerlocation.getPlane()));

				state = RedChinFlexoState.WALK_FIRST;
			}

			// Walking
			else if (state == RedChinFlexoState.WALK_FIRST)
			{
				flexoMouseAction(wrapFlexo(worldPointBounds(traplocations.get(0))));

				state = RedChinFlexoState.SETUP_FIRST;
				timeout = 2;
			}
			else if (state == RedChinFlexoState.WALK_SECOND)
			{
				flexoMouseAction(wrapFlexo(worldPointBounds(traplocations.get(1))));

				state = RedChinFlexoState.SETUP_SECOND;
				timeout = 2;
			}
			else if (state == RedChinFlexoState.WALK_THIRD)
			{
				flexoMouseAction(wrapFlexo(worldPointBounds(traplocations.get(2))));

				state = RedChinFlexoState.SETUP_THIRD;
				timeout = 2;
			}
			else if (state == RedChinFlexoState.WALK_FOURTH)
			{
				flexoMouseAction(wrapFlexo(worldPointBounds(traplocations.get(3))));

				state = RedChinFlexoState.SETUP_FOURTH;
				timeout = 2;
			}
			else if (state == RedChinFlexoState.WALK_FIFTH)
			{
				flexoMouseAction(wrapFlexo(worldPointBounds(traplocations.get(4))));

				state = RedChinFlexoState.SETUP_FIFTH;
				timeout = 2;
			}

			// Set up box traps
			else if (state == RedChinFlexoState.SETUP_FIRST)
			{
				if (client.getLocalPlayer().getWorldLocation().distanceTo(traplocations.get(0)) == 0)
				{
					executeBoxTrap();

					state = setup ? RedChinFlexoState.WALK_SECOND : RedChinFlexoState.HUNTING_CHINS;
					timeout = 4;
				}
				else
				{
					state = RedChinFlexoState.WALK_FIRST;
				}
			}
			else if (state == RedChinFlexoState.SETUP_SECOND)
			{
				if (client.getLocalPlayer().getWorldLocation().distanceTo(traplocations.get(1)) == 0)
				{
					executeBoxTrap();

					state = setup ? RedChinFlexoState.WALK_THIRD : RedChinFlexoState.HUNTING_CHINS;
					timeout = 4;
				}
				else
				{
					state = RedChinFlexoState.WALK_SECOND;
				}
			}
			else if (state == RedChinFlexoState.SETUP_THIRD)
			{
				if (client.getLocalPlayer().getWorldLocation().distanceTo(traplocations.get(2)) == 0)
				{
					executeBoxTrap();

					state = setup ? RedChinFlexoState.WALK_FOURTH : RedChinFlexoState.HUNTING_CHINS;
					timeout = 4;
				}
				else
				{
					state = RedChinFlexoState.WALK_THIRD;
				}
			}
			else if (state == RedChinFlexoState.SETUP_FOURTH)
			{
				if (client.getLocalPlayer().getWorldLocation().distanceTo(traplocations.get(3)) == 0)
				{
					executeBoxTrap();

					state = setup ? RedChinFlexoState.WALK_FIFTH : RedChinFlexoState.HUNTING_CHINS;
					timeout = 4;
				}
				else
				{
					state = RedChinFlexoState.WALK_FOURTH;
				}
			}
			else if (state == RedChinFlexoState.SETUP_FIFTH)
			{
				if (client.getLocalPlayer().getWorldLocation().distanceTo(traplocations.get(4)) == 0)
				{
					executeBoxTrap();

					state = RedChinFlexoState.STUB_FIFTH;
					timeout = 4;
					setup = false;
				}
				else
				{
					state = RedChinFlexoState.WALK_FIFTH;
				}
			}

			// Normal operation
			else if (state == RedChinFlexoState.HUNTING_CHINS || state == RedChinFlexoState.HUNTING_MISCLICK)
			{
				if (!layReset.isEmpty())
				{
					tickCheck = layReset.get(0).getWorldLocation();
					flexoMouseAction(wrapFlexo(worldPointBounds(tickCheck)));
					timeout = 2;
					state = RedChinFlexoState.STUB_FALLEN_TRAP;
				}
				else if (!trapsReset.isEmpty())
				{
					tickCheck = trapsReset.get(0).worldPoint;
					flexoMouseAction(wrapFlexo(trapsReset.get(0).gameObject.getConvexHull().getBounds()));
					timeout = 5;
					state = RedChinFlexoState.TRAP_RESET_CHECK;
				}
				else if (traps.size() != 5)
				{
					state = RedChinFlexoState.LOST_TRAPS;
				}
			}
			else if (state == RedChinFlexoState.TRAP_RESET_CHECK)
			{
				if (client.getLocalPlayer().getWorldLocation().distanceTo(tickCheck) == 0)
				{
					boolean anyMatch = false;

					for (Map.Entry<WorldPoint, HunterTrap> entry : traps.entrySet())
					{
						WorldPoint worldPoint = entry.getKey();
						HunterTrap hunterTrap = entry.getValue();

						if (client.getLocalPlayer().getWorldLocation().distanceTo(worldPoint) == 0)
						{
							if (hunterTrap.getState() != HunterTrap.State.FULL && hunterTrap.getState() != HunterTrap.State.EMPTY)
							{
								trapsReset.remove(0);
								state = RedChinFlexoState.RESET_TRAP;
							}
							anyMatch = true;

							break;
						}
						else
						{
							anyMatch = false;
						}
					}

					if (!anyMatch)
					{
						trapsReset.remove(0);
						state = RedChinFlexoState.RESET_TRAP;
					}
				}

				if (state != RedChinFlexoState.RESET_TRAP)
				{
					state = RedChinFlexoState.HUNTING_MISCLICK;
				}
			}
			else if (state == RedChinFlexoState.RESET_TRAP)
			{
				if (!layReset.isEmpty())
				{
					flexoMouseAction(wrapFlexo(worldPointBounds(layReset.get(0).getWorldLocation())), false);
				}
				else if (!trapsReset.isEmpty())
				{
					flexoMouseAction(wrapFlexo(trapsReset.get(0).gameObject.getConvexHull().getBounds()), false);
				}

				state = RedChinFlexoState.TRAP_SETUP_CHECK;
				timeout = 2;
			}
			else if (state == RedChinFlexoState.TRAP_SETUP_CHECK)
			{
				if (traps.keySet().stream().noneMatch(worldPoint -> client.getLocalPlayer().getWorldLocation().distanceTo(worldPoint) == 0))
				{
					timeout = 1;
				}

				state = RedChinFlexoState.HUNTING_CHINS;
			}

			// Stubs
			else if (state == RedChinFlexoState.STUB_FIFTH)
			{
				state = RedChinFlexoState.HUNTING_CHINS;
			}
			else if (state == RedChinFlexoState.STUB_FALLEN_TRAP)
			{
				state = RedChinFlexoState.HUNTING_CHINS;
			}

			// Same tick execution
			if (state == RedChinFlexoState.LOST_TRAPS)
			{
				Boolean[] trapsFound = new Boolean[5];
				Arrays.fill(trapsFound, Boolean.FALSE);

				for (WorldPoint worldPoint : traps.keySet())
				{
					for (int i = 0; i < trapsFound.length; i++)
					{
						if (traplocations.get(i).distanceTo(worldPoint) == 0)
						{
							trapsFound[i] = true;
						}
					}
				}

				for (int i = 0; i < trapsFound.length; i++)
				{
					if (!trapsFound[i])
					{
						switch (i)
						{
							case 0:
								state = RedChinFlexoState.WALK_FIRST;
								return;
							case 1:
								state = RedChinFlexoState.WALK_SECOND;
								return;
							case 2:
								state = RedChinFlexoState.WALK_THIRD;
								return;
							case 3:
								state = RedChinFlexoState.WALK_FOURTH;
								return;
							case 4:
								state = RedChinFlexoState.WALK_FIFTH;
								return;
						}
					}
				}
			}

		}
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned npcSpawned)
	{
		final NPC npc = npcSpawned.getNpc();

		if (npc.getId() == NpcID.CARNIVOROUS_CHINCHOMPA)
		{
			chins.add(npc);
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned npcDespawned)
	{
		final NPC npc = npcDespawned.getNpc();

		if (npc.getId() == NpcID.CARNIVOROUS_CHINCHOMPA)
		{
			try
			{
				chins.remove(npc.getId());
			}
			catch (IndexOutOfBoundsException ex)
			{
			}
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage chatMessageEvent)
	{
		ChatMessageType chatMessageType = chatMessageEvent.getType();

		if (chatMessageType != ChatMessageType.GAMEMESSAGE && chatMessageType != ChatMessageType.SPAM)
		{
			return;
		}

		String chatMessage = chatMessageEvent.getMessage();
		if (PET_MESSAGES.stream().anyMatch(chatMessage::contains))
		{
			pet = true;
		}
		else if (chatMessage.contains(TRAP_SET_MESSAGE))
		{
			lastChatTick = client.getTickCount();
		}
	}

	@Subscribe
	public void onItemSpawned(ItemSpawned itemSpawned)
	{
		final Item item = itemSpawned.getItem();
		final Tile tile = itemSpawned.getTile();

		if (item.getId() == ItemID.BOX_TRAP)
		{
			trapItemPoints.add(tile.getWorldLocation());

			if (client.getTickCount() == lastChatTick
				&& lastTickLocalPlayerLocation.distanceTo(tile.getWorldLocation()) <= 2)
			{
				validPoint = tile.getWorldLocation();
			}
			else
			{
				if (traplocations.stream().anyMatch(worldPoint -> worldPoint.distanceTo(tile.getWorldLocation()) == 0))
				{
					layReset.add(tile);
				}
			}
		}
	}

	@Subscribe
	public void onItemDespawned(ItemDespawned itemDespawned)
	{
		final Item item = itemDespawned.getItem();
		final Tile tile = itemDespawned.getTile();

		if (trapItemPoints.contains(tile.getWorldLocation())
			&& item.getId() == ItemID.BOX_TRAP)
		{
			if (tile.getWorldLocation().equals(validPoint))
			{
				ourTrapLocs.add(validPoint);

			}
			else if (validPoint == null
				&& client.getTickCount() - lastChatTick <= 3
				&& lastTickLocalPlayerLocation.distanceTo(tile.getWorldLocation()) <= 2)
			{
				validPoint = tile.getWorldLocation();
			}

			trapItemPoints.remove(tile.getWorldLocation());
			layReset.remove(tile);
		}
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		final GameObject gameObject = event.getGameObject();
		final WorldPoint trapLocation = gameObject.getWorldLocation();
		final HunterTrap myTrap = traps.get(trapLocation);

		switch (gameObject.getId())
		{
			/*
			 * Placing traps
			 */
			case ObjectID.BOX_TRAP_9380:
				if (trapLocation.equals(validPoint))
				{
					traps.put(trapLocation, new HunterTrap(gameObject));
					lastActionTime = Instant.now();
					validPoint = null;
				}
				break;

			/*
			 * Failed catch
			 */
			case ObjectID.BOX_TRAP_9385:
				if (myTrap != null)
				{
					myTrap.setState(HunterTrap.State.EMPTY);
					myTrap.resetTimer();
					lastActionTime = Instant.now();
					trapsReset.add(new TrapResetAction(gameObject, trapLocation));
				}

				break;
			/*
			 * Transitions
			 */
			case ObjectID.BOX_TRAP_9381:
			case ObjectID.BOX_TRAP_9390:
			case ObjectID.BOX_TRAP_9391:
			case ObjectID.BOX_TRAP_9392:
			case ObjectID.BOX_TRAP_9393:
				if (myTrap != null)
				{
					myTrap.setState(HunterTrap.State.TRANSITION);
				}
				break;

			/*
			 * Catching stuff
			 */
			case ObjectID.SHAKING_BOX_9383:
				if (myTrap != null)
				{
					myTrap.setState(HunterTrap.State.FULL);
					myTrap.resetTimer();
					lastActionTime = Instant.now();

					trapsReset.add(new TrapResetAction(gameObject, trapLocation));
				}
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (!enabled || !isInsideRedChinArea())
		{
			return;
		}

		ItemContainer container = event.getItemContainer();

		if (container == client.getItemContainer(InventoryID.INVENTORY))
		{
			chinsCaught = getChinchompas().get(0).getQuantity() - startChins;
		}
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded menuEntryAdded)
	{
		if (client.getGameState() != GameState.LOGGED_IN || !isInsideRedChinArea())
		{
			return;
		}

		final String option = Text.removeTags(menuEntryAdded.getOption()).toLowerCase();
		final String target = Text.removeTags(menuEntryAdded.getTarget()).toLowerCase();
		final WorldPoint objectLoc = WorldPoint.fromScene(
			client, menuEntryAdded.getActionParam0(), menuEntryAdded.getActionParam1(), client.getPlane());

		if (!ourTrapLocs.contains(objectLoc) && !option.equals("take")
			&& target.equals("box trap"))
		{
			MenuEntry[] entries = client.getMenuEntries();

			int walkIdx = searchEntries(entries, "walk here", "");
			int hideIdx = searchEntries(entries, option, target);

			if (walkIdx >= 0 && hideIdx >= 0)
			{
				MenuEntry menuEntry = entries[walkIdx];
				entries[walkIdx] = entries[hideIdx];
				entries[hideIdx] = menuEntry;

				client.setMenuEntries(entries);
			}
		}
	}

	private int searchEntries(MenuEntry[] entries, String option, String target)
	{
		for (int i = entries.length - 1; i >= 0; i--)
		{
			MenuEntry entry = entries[i];
			String entryOption = Text.removeTags(entry.getOption()).toLowerCase();
			String entryTarget = Text.removeTags(entry.getTarget()).toLowerCase();

			if (entryOption.equals(option) && entryTarget.equals(target)
				|| entryOption.equals(option) && target.equals(""))
			{
				return i;
			}
		}
		return -1;
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked click)
	{
		if (Text.removeTags(click.getMenuOption()).toLowerCase().equals("ignore"))
		{
			click.consume();
		}
	}

	private Rectangle worldPointBounds(WorldPoint point)
	{
		LocalPoint lp = LocalPoint.fromWorld(client, point);
		if (lp == null)
		{
			return null;
		}

		Polygon poly = Perspective.getCanvasTilePoly(client, lp);
		if (poly == null)
		{
			return null;
		}

		return poly.getBounds();
	}

	private Rectangle wrapFlexo(Rectangle rec)
	{
		return FlexoMouse.getClickArea(rec);
	}

	private void executeBoxTrap()
	{
		List<WidgetItem> inventory = getBoxtraps();

		if (inventory.size() > 0)
		{
			Rectangle clickArea = FlexoMouse.getClickArea(inventory.get(setup ? inventory.size() - 1 : 0).getCanvasBounds());

			if (clickArea != null)
			{
				flexoMouseAction(clickArea);
			}
		}
	}

	private long getMillis()
	{
		return (long) (Math.random() * config.randLowRedChins() + config.randHighRedChins());
	}

	boolean isInsideRedChinArea()
	{
		return client.getLocalPlayer().getWorldLocation().getRegionID() == RED_CHIN_AREA;
	}

	int chinPrice()
	{
		return itemManager.getItemPrice(ItemID.RED_CHINCHOMPA_10034);
	}

	private int getPetRate()
	{
		int baseRate = 98373;

		try
		{
			return baseRate - (client.getRealSkillLevel(Skill.HUNTER) * 25);
		}
		catch (NullPointerException ex)
		{
			return baseRate;
		}
	}

	String calculatePetChance()
	{
		DecimalFormat df = new DecimalFormat("0", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
		df.setMaximumFractionDigits(4);

		double petChance = (double) chinsCaught / getPetRate() * 100;

		return df.format(petChance);
	}

	private static int getRandomIntBetweenRange(int min, int max)
	{
		return (int) ((Math.random() * ((max - min) + 1)) + min);
	}

	private Point modClickArea(Point point)
	{
		int xdiff = lastXaxisPos - point.getX();
		int ydiff = lastYaxisPos - point.getY();

		if (xdiff < 16 && xdiff >= -16 && ydiff <= 16 && ydiff >= -16)
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
		int x = (int) (rect.getX() + getRandomIntBetweenRange((int) rect.getWidth() / 10 * -1, (int) rect.getWidth() / 10) + rect.getWidth() / 2);
		int y = (int) (rect.getY() + getRandomIntBetweenRange((int) rect.getWidth() / 10 * -1, (int) rect.getWidth() / 10) + rect.getHeight() / 2);

		if (client.isStretchedEnabled())
		{
			double scale = 1 + ((double) configManager.getConfig(StretchedModeConfig.class).scalingFactor() / 100);
			return new Point((int) (x * scale), (int) (y * scale));
		}

		return new Point(x, y);
	}

	private void flexoMouseAction(Rectangle rectangle)
	{
		flexoMouseAction(rectangle, true);
	}

	private void flexoMouseAction(Rectangle rectangle, boolean click)
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

				Point point = modClickArea(getClickPoint(rectangle));

				lastXaxisPos = point.getX();
				lastYaxisPos = point.getY();

				flexo.mouseMove(point.getX(), point.getY());
				if (click)
				{
					flexo.mousePressAndRelease(MouseEvent.BUTTON1);
				}

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

	private void holdKey(int keyEvent, int time)
	{
		flexoSetup();

		if (flexo != null)
		{
			Runnable runnable = () ->
			{
				flexo.delay((int) getMillis());
				flexo.holdKey(keyEvent, time);;
			};

			Thread thread = new Thread(runnable);
			thread.start();
		}
	}
}
