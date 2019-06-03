/*
 * Copyright (c) 2018, James Swindle <wilingua@gmail.com>
 * Copyright (c) 2018, Adam <Adam@sigterm.info>
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
 *
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
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.graphics.ModelOutlineRenderer;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

public class RedChinsFlexoNpcTileOverlay extends Overlay
{

	private final Client client;
	private final RedChinsFlexo plugin;
	private final RedChinsFlexoConfig config;
	private final ModelOutlineRenderer modelOutliner;

	@Inject
	RedChinsFlexoNpcTileOverlay(Client client, RedChinsFlexoConfig config, RedChinsFlexo plugin, ModelOutlineRenderer modelOutliner)
	{
		this.client = client;
		this.config = config;
		this.plugin = plugin;
		this.modelOutliner = modelOutliner;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (config.redChinOverlay())
		{
			List<NPC> chins = plugin.getChins();

			for (NPC chin : chins)
			{
				try
				{
					modelOutliner.drawOutline(chin, 1, Color.MAGENTA);
				}
				catch (NullPointerException ex)
				{
				}
			}
		}

		if (config.trapTileOverlay())
		{
			List<WorldPoint> trapLocations = plugin.getTraplocations();

			int i = 0;
			for (WorldPoint worldPoint : trapLocations)
			{
				i++;
				if (worldPoint.getPlane() != client.getPlane())
				{
					continue;
				}

				drawTile(graphics, worldPoint, i, plugin.getTraps().get(worldPoint));
			}
		}

		return null;
	}

	private void drawTile(Graphics2D graphics, WorldPoint point, int trap, HunterTrap hunterTrap)
	{
		LocalPoint lp = LocalPoint.fromWorld(client, point);
		if (lp == null)
		{
			return;
		}

		Polygon poly = Perspective.getCanvasTilePoly(client, lp);
		if (poly == null)
		{
			return;
		}

		Color color = Color.MAGENTA;

		if (config.trapTileOverlayColors())
		{
			if (hunterTrap != null)
			{
				switch (hunterTrap.getState())
				{
					case OPEN:
						color = Color.YELLOW;
						break;
					case FULL:
						color = Color.GREEN;
						break;
					case EMPTY:
						color = Color.RED;
						break;
					case TRANSITION:
						color = Color.ORANGE;
						break;
				}
			}
		}

		OverlayUtil.renderPolygon(graphics, poly, color);

		if (config.trapTileOverlayNumbers())
		{
			Point canvasPoint = Perspective.getCanvasTextLocation(client, graphics, lp, String.valueOf(trap), 0);
			renderTextLocation(graphics, String.valueOf(trap), canvasPoint);
		}
	}


	private void renderTextLocation(Graphics2D graphics, String txtString, Point canvasPoint)
	{
		graphics.setFont(new Font("Arial", Font.BOLD, 32));
		if (canvasPoint != null)
		{
			final Point canvasCenterPoint = new Point(
				canvasPoint.getX(),
				canvasPoint.getY());
			final Point canvasCenterPoint_shadow = new Point(
				canvasPoint.getX() + 1,
				canvasPoint.getY() + 1);
			OverlayUtil.renderTextLocation(graphics, canvasCenterPoint_shadow, txtString, Color.BLACK);
			OverlayUtil.renderTextLocation(graphics, canvasCenterPoint, txtString, Color.WHITE);
		}
	}
}
