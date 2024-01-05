/*
 *  EdgesFormat.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.view.format.densitree;

import javafx.scene.Group;
import jloda.fx.util.ExtendedFXMLLoader;
import splitstree6.view.trees.densitree.DensiTreeView;

/**
 * edge formatter pane
 * Daniel Huson, 5.2022
 */
public class EdgesFormat extends Group {
	private final EdgesFormatController controller;
	private final EdgesFormatPresenter presenter;

	public EdgesFormat(DensiTreeView densiTreeView) {
		var loader = new ExtendedFXMLLoader<EdgesFormatController>(EdgesFormatController.class);
		controller = loader.getController();
		getChildren().add(loader.getRoot());

		presenter = new EdgesFormatPresenter(densiTreeView, controller);
	}

	public EdgesFormatPresenter getPresenter() {
		return presenter;
	}


	public EdgesFormatController getController() {
		return controller;
	}
}
