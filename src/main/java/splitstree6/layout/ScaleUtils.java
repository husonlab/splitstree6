/*
 *  ScaleUtils.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.layout;

import javafx.scene.Node;
import jloda.fx.util.BasicFX;

import java.util.function.Predicate;

/**
 * scale utils
 * Daniel Huson, 1.2022
 */
public class ScaleUtils {
	/**
	 * scale the translateX and translateY properties by the given values
	 *
	 * @param root      applied to this node and all descendants
	 * @param predicate determines whether node should be processed
	 * @param scaleX    scale x factor
	 * @param scaleY    scale y factor
	 */
	public static void scaleTranslate(javafx.scene.Node root, Predicate<Node> predicate, double scaleX, double scaleY) {
		for (var node : BasicFX.getAllRecursively(root, predicate)) {
			node.setTranslateX(node.getTranslateX() * scaleX);
			node.setTranslateY(node.getTranslateY() * scaleY);
		}
	}
}
