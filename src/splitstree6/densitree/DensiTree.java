/*
 *  DensiTree.java Copyright (C) 2021 Daniel H. Huson
 *
 *  (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package splitstree6.densitree;

import javafx.scene.canvas.Canvas;
import javafx.scene.text.Font;
import jloda.util.StringUtils;

/**
 * draw the densi-tree
 */
public class DensiTree {

	public static void draw(Parameters parameters, Model model, Canvas canvas) {
		System.err.println("Width: " + canvas.getWidth());
		System.err.println("Height: " + canvas.getHeight());

		var gc = canvas.getGraphicsContext2D();
		gc.setFont(Font.font("Courier New", 11));
		gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

		if (model.getTreesBlock().size() > 0) {
			gc.strokeText("nTax: " + model.getTaxaBlock().getNtax(), 20, 20);

			var cx = 20;
			var cy = 40;

			for (int value : model.getCircularOrdering()) {
				if (value > 0) {
					gc.strokeText(String.valueOf(value), cx, cy);
					cx += 20;
					if (cx > canvas.getWidth())
						break;
				}
			}

			var tree = model.getTreesBlock().getTree(1);
			var x = 20;
			var y = 60;
			for (var node : tree.nodes()) {
				if (node.getLabel() != null) {
					gc.strokeText(StringUtils.toString(tree.getTaxa(node), " ") + ": " + node.getLabel(), x, y);
					y += 30;
					if (y > canvas.getHeight())
						break;
				}
			}
		}
	}

	/**
	 * this contains all the parameters used for drawing
	 */
	public record Parameters(boolean toScale) {
	}
}
