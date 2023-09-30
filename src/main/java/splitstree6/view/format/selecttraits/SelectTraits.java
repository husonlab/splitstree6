/*
 *  SelectTraitsController.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.view.format.selecttraits;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.scene.layout.Pane;
import jloda.fx.selection.SelectionModel;
import jloda.fx.util.ExtendedFXMLLoader;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TraitsBlock;
import splitstree6.data.parts.Taxon;
import splitstree6.window.MainWindow;

public class SelectTraits extends Pane {
	private final MainWindow mainWindow;
	private final SelectTraitsController controller;
	private final SelectTraitsPresenter presenter;
	private final ChangeListener<Boolean> validListener;

	private final ObjectProperty<TaxaBlock> workingTaxa = new SimpleObjectProperty<>();
	private final ObjectProperty<TraitsBlock> traitsBlock = new SimpleObjectProperty<>();


	public SelectTraits(MainWindow mainWindow) {
		this.mainWindow = mainWindow;
		var loader = new ExtendedFXMLLoader<SelectTraitsController>(SelectTraitsController.class);
		controller = loader.getController();
		getChildren().add(loader.getRoot());

		validListener = (v, o, n) -> {
			workingTaxa.set(mainWindow.getWorkflow().getWorkingTaxaBlock());
			if (n)
				traitsBlock.set(mainWindow.getWorkflow().getWorkingTaxaBlock().getTraitsBlock());
		};
		workingTaxa.set(mainWindow.getWorkflow().getWorkingTaxaBlock());
		mainWindow.getWorkflow().validProperty().addListener(new WeakChangeListener<>(validListener));

		presenter = new SelectTraitsPresenter(this);
	}

	public SelectTraitsController getController() {
		return controller;
	}

	public SelectionModel<Taxon> getTaxonSelectionModel() {
		return mainWindow.getTaxonSelectionModel();
	}

	public TaxaBlock getWorkingTaxa() {
		return workingTaxa.get();
	}

	public ObjectProperty<TaxaBlock> workingTaxaProperty() {
		return workingTaxa;
	}

	public TraitsBlock getTraitsBlock() {
		return traitsBlock.get();
	}

	public ObjectProperty<TraitsBlock> traitsBlockProperty() {
		return traitsBlock;
	}
}
