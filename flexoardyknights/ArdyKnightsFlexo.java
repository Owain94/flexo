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
package net.runelite.client.plugins.flexoardyknights;

import com.google.common.collect.ImmutableList;
import com.google.inject.Provides;
import java.awt.AWTException;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.inject.Inject;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Skill;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
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
import net.runelite.client.util.Text;

@PluginDescriptor(
	name = "Flexo Ardy Knights",
	description = " Ardy Knights Assistant",
	tags = {"Ardy", "knigts", "helper"},
	type = PluginType.EXTERNAL
)
@Slf4j
public class ArdyKnightsFlexo extends Plugin
{
	// Option added to NPC menu
	private static final String TAG = "Select";
	private static final String UNTAG = "Deselect";

	private static final int ARDY_SOUTH_BANK = 10547;
	private static final int COIN_POUCH = 22531;

	private static final ImmutableList<String> PET_MESSAGES = ImmutableList.of("You have a funny feeling like you're being followed",
		"You feel something weird sneaking into your backpack",
		"You have a funny feeling like you would have been followed");
	private final Set<Integer> npcTags = new HashSet<>();

	@Inject
	private Client client;

	@Inject
	private ClientUI clientUI;

	@Inject
	private ConfigManager configManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ArdyKnightsFlexoOverlay overlay;

	@Inject
	private ArdyKnightsFlexoNpcOverlay npcOverlay;

	@Inject
	private ArdyKnightsFlexoConfig config;

	@Inject
	private KeyManager keyManager;

	@Inject
	private TabUtils tabUtils;

	@Getter(AccessLevel.PACKAGE)
	private boolean enabled;

	@Getter(AccessLevel.PACKAGE)
	private int pouches;

	@Getter(AccessLevel.PACKAGE)
	private int doubleLoot;

	@Getter(AccessLevel.PACKAGE)
	private NPC ardyKnight = null;

	@Getter(AccessLevel.PACKAGE)
	private boolean pet = false;

	private int lastXaxisPos = 0;
	private int lastYaxisPos = 0;
	private Flexo flexo;
	private boolean flexoIsRunning = false;

	@Provides
	ArdyKnightsFlexoConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ArdyKnightsFlexoConfig.class);
	}

	protected void startUp()
	{
		overlayManager.add(overlay);
		overlayManager.add(npcOverlay);

		try
		{
			flexo = new Flexo();
		}
		catch (AWTException e)
		{
			e.printStackTrace();
		}

		keyManager.registerKeyListener(hotkeyListener);
		npcTags.clear();
	}

	protected void shutDown()
	{
		overlayManager.remove(overlay);
		overlayManager.remove(npcOverlay);
		keyManager.unregisterKeyListener(hotkeyListener);
		ardyKnight = null;
	}

	private List<WidgetItem> getInventory()
	{
		return getItems(COIN_POUCH);
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
		() -> config.hotkeyToggleArdyKnights()
	)
	{
		@Override
		public void hotkeyPressed()
		{
			enabled = !enabled;

			if (enabled && ardyKnight == null)
			{
				enabled = false;
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
			pouches = 0;
			doubleLoot = 0;
			ardyKnight = null;
			npcTags.clear();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (enabled && isInsideArdySouthBank())
		{
			executeCoinPouch();

			Player player = client.getLocalPlayer();

			if (ardyKnight != null)
			{
				ardyKnight.setAnimation(-1);
				player.setAnimation(-1);

				executePickpocket(ardyKnight);
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
		if (chatMessage.startsWith("You pick the knight"))
		{
			pouches++;
		}
		else if (chatMessage.startsWith("Your rogue clothing"))
		{
			doubleLoot++;
		}
		else if (PET_MESSAGES.stream().anyMatch(chatMessage::contains))
		{
			pet = true;
		}
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (client.getGameState() != GameState.LOGGED_IN || !isInsideArdySouthBank())
		{
			return;
		}

		final String target = Text.removeTags(event.getTarget());

		if (!enabled)
		{
			MenuEntry[] menuEntries = client.getMenuEntries();
			int type = event.getType();

			if (type == MenuAction.EXAMINE_NPC.getId() && target.toLowerCase().contains("knight"))
			{
				// Add tag option
				menuEntries = Arrays.copyOf(menuEntries, menuEntries.length + 1);
				final MenuEntry tagEntry = menuEntries[menuEntries.length - 1] = new MenuEntry();
				tagEntry.setOption(npcTags.contains(event.getIdentifier()) ? UNTAG : TAG);
				tagEntry.setTarget(event.getTarget());
				tagEntry.setParam0(event.getActionParam0());
				tagEntry.setParam1(event.getActionParam1());
				tagEntry.setIdentifier(event.getIdentifier());
				tagEntry.setType(MenuAction.RUNELITE.getId());
				client.setMenuEntries(menuEntries);
			}
		}
		else
		{
			final String option = Text.removeTags(event.getOption()).toLowerCase();

			if (!option.toLowerCase().equals("pickpocket") && !target.toLowerCase().contains("knight") && !target.toLowerCase().equals("coin pouch"))
			{
				MenuEntry[] menuEntries = new MenuEntry[1];
				menuEntries[0] = new MenuEntry();
				menuEntries[0].setOption("Ignore");
				menuEntries[0].setTarget(event.getTarget());
				menuEntries[0].setType(MenuAction.CANCEL.getId());

				client.setMenuEntries(menuEntries);
			}
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked click)
	{
		if (Text.removeTags(click.getMenuOption()).toLowerCase().equals("ignore"))
		{
			click.consume();
		}

		if (click.getMenuAction() != MenuAction.RUNELITE
			|| (!click.getMenuOption().equals(TAG)
			&& !click.getMenuOption().equals(UNTAG)))
		{
			return;
		}

		final int id = click.getId();
		final boolean removed = npcTags.remove(id);
		final NPC[] cachedNPCs = client.getCachedNPCs();
		final NPC npc = cachedNPCs[id];

		if (npc == null || npc.getName() == null)
		{
			return;
		}

		if (removed)
		{
			npcTags.clear();
			ardyKnight = null;
		}
		else
		{
			npcTags.add(id);
			ardyKnight = npc;
		}

		click.consume();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN ||
			event.getGameState() == GameState.HOPPING)
		{
			npcTags.clear();
			ardyKnight = null;
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned npcDespawned)
	{
		final NPC npc = npcDespawned.getNpc();

		npcTags.remove(npc.getId());
	}

	private void executeCoinPouch()
	{
		if (client.getWidget(WidgetInfo.INVENTORY).isHidden())
		{
			sendKey(tabUtils.getTabHotkey(Tab.INVENTORY));
		}

		List<WidgetItem> inventory = getInventory();

		if (inventory.size() > 0 && inventory.get(0).getQuantity() == 28)
		{
			Rectangle clickArea = FlexoMouse.getClickArea(inventory.get(0).getCanvasBounds());

			if (clickArea != null)
			{
				leftClick(clickArea);
			}
		}
	}

	private void executePickpocket(NPC npc)
	{
		leftClick(FlexoMouse.getClickArea(npc.getConvexHull().getBounds()));
	}

	private long getMillis()
	{
		return (long) (Math.random() * config.randLowArdyKnights() + config.randHighArdyKnights());
	}

	boolean isInsideArdySouthBank()
	{
		return client.getLocalPlayer().getWorldLocation().getRegionID() == ARDY_SOUTH_BANK;
	}

	private int getPetRate()
	{
		int baseRate = 257211;

		try
		{
			return baseRate - (client.getRealSkillLevel(Skill.THIEVING) * 25);
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

		double petChance = (double) pouches / getPetRate() * 100;

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
}
