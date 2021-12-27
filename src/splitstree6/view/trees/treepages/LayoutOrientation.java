/*
 *  Copyright (C) 2018. Daniel H. Huson
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

/*
 *  LayoutOrientation.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.view.trees.treepages;

import javafx.scene.control.Label;

/**
 * tree or network layout orientation
 * Daniel Huson,12.2021
 */
public enum LayoutOrientation {
	Rotate0Deg, Rotate90Deg, Rotate180Deg, Rotate270Deg, FlipRotate0Deg, FlipRotate90Deg, FlipRotate180Deg, FlipRotate270Deg;

	public boolean isWidthHeightSwitched() {
		return this == Rotate90Deg || this == FlipRotate90Deg || this == Rotate270Deg || this == FlipRotate270Deg;
	}

	public Label createNode() {
		var label = new Label("R");
		switch (this) {
			case Rotate90Deg -> label.setRotate(-90);
			case Rotate180Deg -> label.setRotate(180);
			case Rotate270Deg -> label.setRotate(-270);
			case FlipRotate0Deg -> label.setScaleX(-label.getScaleX());
			case FlipRotate90Deg -> {
				label.setScaleX(-label.getScaleX());
				label.setRotate(-90);
			}
			case FlipRotate180Deg -> {
				label.setScaleX(-label.getScaleX());
				label.setRotate(180);
			}
			case FlipRotate270Deg -> {
				label.setScaleX(-label.getScaleX());
				label.setRotate(-270);
			}
		}
		return label;
	}
}
