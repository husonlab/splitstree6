/*
 * TreeLabel.java Copyright (C) 2022 Daniel H. Huson
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

import javafx.scene.control.TextInputControl;
import jloda.phylo.PhyloTree;
import jloda.phylo.algorithms.RootedNetworkProperties;

import java.util.Arrays;

/**
 * tree label: name, name and info, or none
 * Daniel Huson, 1.2022
 */
public enum TreeLabel {
	None, Name, Info;

	public String label() {
		return switch (this) {
			case None -> "-";
			case Name -> "n";
			case Info -> "i";
		};
	}

	public static String[] labels() {
		return Arrays.stream(values()).map(TreeLabel::label).toArray(String[]::new);
	}

	public static TreeLabel valueOfLabel(String label) {
		return switch (label) {
			case "-" -> None;
			case "n" -> Name;
			case "i" -> Info;
			default -> None;
		};
	}

	public static void setLabel(PhyloTree tree, TreeLabel treeLabel, TextInputControl label) {
		if (tree != null && (treeLabel == Name || treeLabel == Info)) {
			label.setText(tree.getName() + (treeLabel == Info ? " : " + RootedNetworkProperties.computeInfoString(tree) : ""));
		} else
			label.setText("");
	}
}
