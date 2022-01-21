/*
 *  TaxaEditTab.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.tabs.algorithms.taxaedit;

import jloda.fx.util.ExtendedFXMLLoader;
import splitstree6.data.TaxaBlock;
import splitstree6.tabs.IDisplayTab;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.tabs.algorithms.AlgorithmTab;
import splitstree6.window.MainWindow;
import splitstree6.workflow.AlgorithmNode;

public class TaxaEditTab extends AlgorithmTab implements IDisplayTab {

	private final TaxaEditController taxaEditController;
	private final TaxaEditPresenter taxaEditPresenter;

	/**
	 * constructor
	 */
	public TaxaEditTab(MainWindow mainWindow, AlgorithmNode<TaxaBlock, TaxaBlock> taxaFilterNode) {
		super(mainWindow, taxaFilterNode);

		var loader = new ExtendedFXMLLoader<TaxaEditController>(TaxaEditTab.class);
		taxaEditController = loader.getController();
		taxaEditPresenter = new TaxaEditPresenter(mainWindow, this, taxaFilterNode);

		getController().getMainPane().getChildren().add(taxaEditController.getAnchorPane());
		taxaEditController.getAnchorPane().prefWidthProperty().bind(getController().getMainPane().widthProperty());
		taxaEditController.getAnchorPane().prefHeightProperty().bind(getController().getMainPane().heightProperty());
	}

	public TaxaEditController getTaxaFilterController() {
		return taxaEditController;
	}

	@Override
	public IDisplayTabPresenter getPresenter() {
		return taxaEditPresenter;
	}
}

