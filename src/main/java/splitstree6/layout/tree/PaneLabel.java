/*
 * PaneLabel.java Copyright (C) 2024 Daniel H. Huson
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
import jloda.util.StringUtils;
import splitstree6.data.SplitsBlock;

import java.util.Arrays;

/**
 * tree label: name, name and details, or none
 * Daniel Huson, 1.2022
 */
public enum PaneLabel {
	None, Name, Details, Fit, ScaleBarNone, ScaleBarName, ScaleBarFit, ScaleBarDetails;

	public String label() {
		return switch (this) {
			case None -> "-";
			case Name -> "n";
			case Fit -> "f";
			case Details -> "d";
			case ScaleBarNone -> "s";
			case ScaleBarName -> "N";
			case ScaleBarDetails -> "D";
			case ScaleBarFit -> "F";
		};
	}

	public static String[] labels() {
		return Arrays.stream(values()).map(PaneLabel::label).toArray(String[]::new);
	}

	public static PaneLabel valueOfLabel(String label) {
		return switch (label) {
			case "-" -> None;
			case "n" -> Name;
			case "d" -> Details;
			case "s" -> ScaleBarNone;
			case "N" -> ScaleBarName;
			case "D" -> ScaleBarDetails;
			default -> None;
		};
	}

	public boolean showScaleBar() {
		return label().equals("s") || label().equals("N") || label().equals("D");
	}

	public boolean showName() {
		return label().equalsIgnoreCase("n") || showNameAndDetails();
	}

	public boolean showNameAndDetails() {
		return label().equalsIgnoreCase("d");
	}

	public static void setLabel(PhyloTree tree, PaneLabel paneLabel, TextInputControl label) {
		String text;
		if (tree != null && (paneLabel.showName())) {
			text = (tree.getName() != null ? tree.getName() : "") + (paneLabel.showNameAndDetails() ? " : " + RootedNetworkProperties.computeInfoString(tree) : "");
		} else
			text = "   ";
		label.setText(text);
	}

	public static void setLabel(SplitsBlock splits, PaneLabel paneLabel, TextInputControl label) {
		String text;
		if (splits != null && paneLabel.showScaleBar() && splits.getFit() > 0.0) {
			text = "Fit: " + StringUtils.removeTrailingZerosAfterDot("%.1f", splits.getFit());
		} else
			text = "   ";
		label.setText(text);
	}
}
