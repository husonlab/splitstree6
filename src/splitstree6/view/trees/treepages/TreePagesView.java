/*
 *  TreePagesView.java Copyright (C) 2021 Daniel H. Huson
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

import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.TabPane;
import jloda.fx.undo.UndoManager;
import jloda.fx.util.ExtendedFXMLLoader;
import jloda.phylo.PhyloTree;
import splitstree6.tabs.tab.ViewTab;
import splitstree6.view.IView;
import splitstree6.window.MainWindow;

import java.util.Collection;
import java.util.List;

public class TreePagesView implements IView {
	private final UndoManager undoManager = new UndoManager();

	private final TreePagesViewController controller;
	private final TreePagesViewPresenter presenter;

	private final StringProperty nameProperty = new SimpleStringProperty();

	private final ObjectProperty<TabPane> tabPane = new SimpleObjectProperty<>(null);

	private final ObservableList<PhyloTree> trees = FXCollections.observableArrayList();
	private final BooleanProperty empty = new SimpleBooleanProperty(true);

	private final IntegerProperty optionRows = new SimpleIntegerProperty(this, "optionRows", 1);
	private final IntegerProperty optionCols = new SimpleIntegerProperty(this, "optionCols", 1);

	private final ObjectProperty<Node> imageNode = new SimpleObjectProperty<>(null);

	private final ObjectProperty<ComputeTreeEmbedding.TreeDiagram> optionDiagram = new SimpleObjectProperty<>(this, "optionDiagram", ComputeTreeEmbedding.TreeDiagram.getDefault());
	private final ObjectProperty<TreePane.RootSide> optionRootSide = new SimpleObjectProperty<>(this, "optionRootSide", TreePane.RootSide.getDefault());

	private final IntegerProperty optionPageNumber = new SimpleIntegerProperty(this, "optionPageNumber", 1); // 1-based

	private final DoubleProperty optionFontScaleFactor = new SimpleDoubleProperty(this, "optionFontScaleFactor", 1.0);

	public List<String> listOptions() {
		return List.of(optionDiagram.getName(), optionRootSide.getName(), optionRows.getName(), optionCols.getName(), optionPageNumber.getName(), optionFontScaleFactor.getName());
	}

	public TreePagesView(MainWindow mainWindow, String name, ViewTab viewTab) {
		nameProperty.set(name);
		var loader = new ExtendedFXMLLoader<TreePagesViewController>(TreePagesViewController.class);
		controller = loader.getController();

		presenter = new TreePagesViewPresenter(mainWindow, this, viewTab, getTrees());

		empty.bind(Bindings.isEmpty(getTrees()));
	}

	public TreePagesViewController getController() {
		return controller;
	}

	public TreePagesViewPresenter getPresenter() {
		return presenter;
	}

	public ObservableList<PhyloTree> getTrees() {
		return trees;
	}

	public int getOptionRows() {
		return optionRows.get();
	}

	public IntegerProperty optionRowsProperty() {
		return optionRows;
	}

	public void setOptionRows(int optionRows) {
		this.optionRows.set(Math.max(1, optionRows));
	}

	public int getOptionCols() {
		return optionCols.get();
	}

	public IntegerProperty optionColsProperty() {
		return optionCols;
	}

	public void setOptionCols(int optionCols) {
		this.optionCols.set(Math.max(1, optionCols));
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

	public ComputeTreeEmbedding.TreeDiagram getOptionDiagram() {
		return optionDiagram.get();
	}

	public ObjectProperty<ComputeTreeEmbedding.TreeDiagram> optionDiagramProperty() {
		return optionDiagram;
	}

	public void setOptionDiagram(ComputeTreeEmbedding.TreeDiagram optionDiagram) {
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
		this.trees.setAll(trees);
		presenter.updatePageContent();
	}

	@Override
	public Node getImageNode() {
		return controller.getPagination();
	}
}
