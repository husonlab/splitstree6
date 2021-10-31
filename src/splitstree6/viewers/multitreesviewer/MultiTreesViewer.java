/*
 *  MultiTreesViewer.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.viewers.multitreesviewer;

import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import jloda.fx.util.ExtendedFXMLLoader;
import jloda.phylo.PhyloTree;
import splitstree6.algorithms.trees.trees2sink.MultiTreeDisplay;
import splitstree6.tabs.viewer.ViewerTab;
import splitstree6.window.MainWindow;

public class MultiTreesViewer extends ViewerTab {

	private final MultiTreesViewerController controller;
	private final MultiTreesViewerPresenter presenter;

	private final ObservableList<PhyloTree> trees = FXCollections.observableArrayList();
	private final BooleanProperty isEmpty = new SimpleBooleanProperty(true);

	private final StringProperty optionGrid = new SimpleStringProperty("1 x 1");

	private final IntegerProperty rows = new SimpleIntegerProperty(1);
	private final IntegerProperty cols = new SimpleIntegerProperty(1);

	private final IntegerProperty pageNumber = new SimpleIntegerProperty(1);

	private final ObjectProperty<MultiTreeDisplay.Diagram> optionDiagram = new SimpleObjectProperty<>(this, "diagramOption", MultiTreeDisplay.Diagram.Unrooted);
	private final ObjectProperty<MultiTreeDisplay.RootSide> optionRootSide = new SimpleObjectProperty<>(this, "rootSideOption", MultiTreeDisplay.RootSide.Left);

	public MultiTreesViewer(MainWindow mainWindow) {
		var loader = new ExtendedFXMLLoader<MultiTreesViewerController>(MultiTreesViewerController.class);
		controller = loader.getController();


		presenter = new MultiTreesViewerPresenter(mainWindow, this, trees);

		isEmpty.bind(Bindings.isEmpty(trees));

		setContent(controller.getAnchorPane());
	}

	public MultiTreesViewerController getController() {
		return controller;
	}

	public MultiTreesViewerPresenter getPresenter() {
		return presenter;
	}

	public ObservableList<PhyloTree> getTrees() {
		return trees;
	}

	public int getRows() {
		return rows.get();
	}

	public IntegerProperty rowsProperty() {
		return rows;
	}

	public void setRows(int rows) {
		this.rows.set(Math.max(1, rows));
	}

	public int getCols() {
		return cols.get();
	}

	public IntegerProperty colsProperty() {
		return cols;
	}

	public void setCols(int cols) {
		this.cols.set(Math.max(1, cols));
	}

	public int getPageNumber() {
		return pageNumber.get();
	}

	public IntegerProperty pageNumberProperty() {
		return pageNumber;
	}

	public void setPageNumber(int pageNumber) {
		this.pageNumber.set(Math.max(1, pageNumber));
	}

	public MultiTreeDisplay.Diagram getOptionDiagram() {
		return optionDiagram.get();
	}

	public ObjectProperty<MultiTreeDisplay.Diagram> optionDiagramProperty() {
		return optionDiagram;
	}

	public void setOptionDiagram(MultiTreeDisplay.Diagram optionDiagram) {
		this.optionDiagram.set(optionDiagram);
	}

	public MultiTreeDisplay.RootSide getOptionRootSide() {
		return optionRootSide.get();
	}

	public ObjectProperty<MultiTreeDisplay.RootSide> optionRootSideProperty() {
		return optionRootSide;
	}

	public void setOptionRootSide(MultiTreeDisplay.RootSide optionRootSide) {
		this.optionRootSide.set(optionRootSide);
	}


	public String getOptionGrid() {
		return optionGrid.get();
	}

	public StringProperty optionGridProperty() {
		return optionGrid;
	}

	public void setOptionGrid(String optionGrid) {
		this.optionGrid.set(optionGrid);
	}

	@Override
	public ReadOnlyBooleanProperty isEmptyProperty() {
		return isEmpty;
	}

	@Override
	public Node getImageNode() {
		return controller.getPagination();
	}


}
