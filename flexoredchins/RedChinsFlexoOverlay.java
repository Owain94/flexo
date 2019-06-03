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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.text.NumberFormat;
import javax.inject.Inject;
import net.runelite.api.MenuAction;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentConstants;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.client.ui.overlay.components.table.TableAlignment;
import net.runelite.client.ui.overlay.components.table.TableComponent;
import net.runelite.client.util.StackFormatter;

class RedChinsFlexoOverlay extends Overlay
{
	private static final String TARGET = "Flexo Red Chins";

	private RedChinsFlexo plugin;
	private final PanelComponent panelComponent = new PanelComponent();

	private int chins;
	private String petChance = "0";
	private String profit = "0";

	@Inject
	private RedChinsFlexoOverlay(RedChinsFlexo plugin)
	{
		this.plugin = plugin;
		setPosition(OverlayPosition.ABOVE_CHATBOX_RIGHT);

		getMenuEntries().add(new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY, "Reset", TARGET));
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		boolean redChinArea = plugin.isInsideRedChinArea();
		int chinsCaught = plugin.getChinsCaught();

		if (redChinArea)
		{
			if (this.chins != chinsCaught)
			{
				this.chins = chinsCaught;

				petChance = plugin.calculatePetChance();
				profit = StackFormatter.quantityToStackSize(chinsCaught * plugin.chinPrice());
			}

			panelComponent.getChildren().clear();
			String overlayTitle = "Flexo Red Chins Info";

			panelComponent.getChildren().add(TitleComponent.builder()
				.text(overlayTitle)
				.color(Color.GREEN)
				.build());

			TableComponent tableComponent = new TableComponent();
			tableComponent.setColumnAlignments(TableAlignment.LEFT, TableAlignment.RIGHT);

			String[][] items = {
				{"Enabled (hotkey):", Boolean.toString(plugin.isEnabled())},
				{"State:", plugin.isEnabled() ? String.valueOf(plugin.getState()) : "Disabled"},
				{"Chins:", NumberFormat.getIntegerInstance().format(chinsCaught)},
				{"Profit:", profit + " gp"},
				{plugin.isPet() ? "Pet:" : "Pet chance:", plugin.isPet() ? "Gz" : petChance + "%"}
			};

			tableComponent.addRows(items);

			panelComponent.getChildren().add(tableComponent);

			final FontMetrics fontMetrics = graphics.getFontMetrics();
			int textWidth = Math.max(ComponentConstants.STANDARD_WIDTH, fontMetrics.stringWidth(overlayTitle));

			panelComponent.setPreferredSize(new Dimension(textWidth + 40, 0));
			return panelComponent.render(graphics);
		}
		else
		{
			return null;
		}

	}
}
