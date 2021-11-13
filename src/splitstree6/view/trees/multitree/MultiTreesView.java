/*
 *  MultiTreesView.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.view.trees.multitree;

import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.TabPane;
import jloda.fx.undo.UndoManager;
import jloda.fx.util.ExtendedFXMLLoader;
import jloda.phylo.PhyloTree;
import jloda.util.ProgramProperties;
import splitstree6.view.IView;
import splitstree6.window.MainWindow;

import java.util.Collection;
import java.util.List;

public class MultiTreesView implements IView {
	private final UndoManager undoManager = new UndoManager();

	private final MultiTreesViewController controller;
	private final MultiTreesViewPresenter presenter;

	private final StringProperty nameProperty = new SimpleStringProperty();

	private final ObjectProperty<TabPane> tabPane = new SimpleObjectProperty<>(null);

	private final ObservableList<PhyloTree> trees = FXCollections.observableArrayList();
	private final BooleanProperty empty = new SimpleBooleanProperty(true);

	private final IntegerProperty rows = new SimpleIntegerProperty(1);
	private final IntegerProperty cols = new SimpleIntegerProperty(1);

	private final ObjectProperty<Node> imageNode = new SimpleObjectProperty<>(null);

	private final ObjectProperty<TreeEmbedding.TreeDiagram> optionDiagram = new SimpleObjectProperty<>(this, "optionDiagram", TreeEmbedding.TreeDiagram.getDefault());
	private final ObjectProperty<TreePane.RootSide> optionRootSide = new SimpleObjectProperty<>(this, "optionRootSide", TreePane.RootSide.getDefault());

	private final StringProperty optionGrid = new SimpleStringProperty(this, "optionGrid", ProgramProperties.get("OptionGrid", "1 x 1"));

	private final IntegerProperty optionPageNumber = new SimpleIntegerProperty(this, "optionPageNumber", 1); // 1-based

	private final DoubleProperty optionFontScaleFactor = new SimpleDoubleProperty(this, "optionFontScaleFactor", 1.0);

	public List<String> listOptions() {
		return List.of(optionDiagram.getName(), optionRootSide.getName(), optionGrid.getName(), optionPageNumber.getName(), optionFontScaleFactor.getName());
	}

	public MultiTreesView(MainWindow mainWindow, StringProperty titleProperty) {
		nameProperty.bind(titleProperty);
		var loader = new ExtendedFXMLLoader<MultiTreesViewController>(MultiTreesViewController.class);
		controller = loader.getController();

		presenter = new MultiTreesViewPresenter(mainWindow, this, getTrees());

		empty.bind(Bindings.isEmpty(getTrees()));

		optionGrid.addListener((v, o, n) -> ProgramProperties.put("OptionGrid", n));
	}

	public MultiTreesViewController getController() {
		return controller;
	}

	public MultiTreesViewPresenter getPresenter() {
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

	public int getOptionPageNumber() {
		return optionPageNumber.get();
	}

	public IntegerProperty optionPageNumberProperty() {
		return optionPageNumber;
	}

	public void setOptionPageNumber(int optionPageNumber) {
		this.optionPageNumber.set(optionPageNumber);
	}

	public TreeEmbedding.TreeDiagram getOptionDiagram() {
		return optionDiagram.get();
	}

	public ObjectProperty<TreeEmbedding.TreeDiagram> optionDiagramProperty() {
		return optionDiagram;
	}

	public void setOptionDiagram(TreeEmbedding.TreeDiagram optionDiagram) {
		this.optionDiagram.set(optionDiagram);
	}

	public TreePane.RootSide getOptionRootSide() {
		return optionRootSide.get();
	}

	public ObjectProperty<TreePane.RootSide> optionRootSideProperty() {
		return optionRootSide;
	}

	public void setOptionRootSide(TreePane.RootSide optionRootSide) {
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

	public ObjectProperty<Node> imageNodeProperty() {
		return imageNode;
	}

	public void setImageNode(Node imageNode) {
		this.imageNode.set(imageNode);
	}

	public Node getRoot() {
		return controller.getAnchorPane();
	}

	@Override
	public String getName() {
		return nameProperty.get();
	}

	@Override
	public void setupMenuItems() {
		presenter.setupMenuItems();
	}

	@Override
	public int size() {
		return trees.size();
	}

	public boolean getEmpty() {
		return empty.get();
	}

	public BooleanProperty emptyProperty() {
		return empty;
	}

	public TabPane getTabPane() {
		return tabPane.get();
	}

	public ObjectProperty<TabPane> tabPaneProperty() {
		return tabPane;
	}

	public void setTabPane(TabPane tabPane) {
		this.tabPane.set(tabPane);
	}

	public double getOptionFontScaleFactor() {
		return optionFontScaleFactor.get();
	}

	public DoubleProperty optionFontScaleFactorProperty() {
		return optionFontScaleFactor;
	}

	public void setOptionFontScaleFactor(double optionFontScaleFactor) {
		this.optionFontScaleFactor.set(optionFontScaleFactor);
	}

	@Override
	public UndoManager getUndoManager() {
		return undoManager;
	}

	public void setTrees(Collection<PhyloTree> trees) {
		if (false)
			this.trees.setAll(trees);
		else {
			var pageFactory = controller.getPagination().getPageFactory();
			controller.getPagination().setPageFactory(null);
			this.trees.setAll(trees);
			controller.getPagination().setPageFactory(pageFactory);
		}
		presenter.redraw();
	}
}
