/*
 *  EdgeLabelFormat.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.view.format.edgelabel;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Group;
import jloda.fx.undo.UndoManager;
import jloda.fx.util.ExtendedFXMLLoader;

/**
 * edge formatter pane
 * Daniel Huson, 5.2022
 */
public class EdgeLabelFormat extends Group {

	private final EdgeLabelPresenter presenter;

	private final ObjectProperty<LabelEdgesBy> optionLabelEdgesBy = new SimpleObjectProperty<>(this, "optionLabelEdgesBy", LabelEdgesBy.None);

	public EdgeLabelFormat(UndoManager undoManager) {
		var loader = new ExtendedFXMLLoader<EdgeLabelController>(EdgeLabelController.class);
		EdgeLabelController controller = loader.getController();
		getChildren().add(loader.getRoot());

		presenter = new EdgeLabelPresenter(controller, optionLabelEdgesBy);

		optionLabelEdgesBy.addListener((v, o, n) -> undoManager.add("edge labels", optionLabelEdgesBy, o, n));
	}

	public EdgeLabelPresenter getPresenter() {
		return presenter;
	}

	public ObjectProperty<LabelEdgesBy> optionLabelEdgesByProperty() {
		return optionLabelEdgesBy;
	}
}
