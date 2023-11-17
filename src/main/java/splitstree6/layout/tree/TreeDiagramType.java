/*
 * TreeDiagramType.java Copyright (C) 2023 Daniel H. Huson
 *
 * (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package splitstree6.layout.tree;

import javafx.scene.Node;
import javafx.scene.effect.BlendMode;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Effect;
import javafx.scene.paint.Color;
import jloda.fx.util.ResourceManagerFX;

/**
 * the tree diagram type
 * Daniel Huson, 12.2021
 */
public enum TreeDiagramType {
	RectangularCladogram, RectangularPhylogram, CircularCladogram, CircularPhylogram, TriangularCladogram, RadialCladogram, RadialPhylogram;

	public boolean isRadialOrCircular() {
		return this == RadialPhylogram || this == RadialCladogram || this == CircularPhylogram || this == CircularCladogram;
	}

	public boolean isPhylogram() {
		return this == RadialPhylogram || this == CircularPhylogram || this == RectangularPhylogram;
	}


	private static Effect effect;

	public Node icon() {
		if (effect == null) {
			var dropShadow = new DropShadow();
			dropShadow.setColor(Color.WHITE);
			dropShadow.setRadius(2);
			dropShadow.setSpread(0);
			effect = dropShadow;
		}
		var node = ResourceManagerFX.getIconAsImageView(name() + ".png", 16);
		node.setEffect(effect);
		node.setBlendMode(BlendMode.SRC_ATOP);

		return node;
	}

	/**
	 * this is not currently one of the supported tree drawing modes (only used in Densitree)
	 *
	 * @return icon
	 */
	public static Node iconForRoundedPhylogram() {
		var node = ResourceManagerFX.getIconAsImageView("RoundedPhylogram.png", 16);
		node.setEffect(effect);
		return node;
	}
}
