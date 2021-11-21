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
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Pane;
import jloda.fx.undo.UndoManager;
import jloda.fx.util.ExtendedFXMLLoader;
import jloda.phylo.PhyloTree;
import jloda.util.ProgramProperties;
import splitstree6.tabs.viewtab.ViewTab;
import splitstree6.view.IView;
import splitstree6.window.MainWindow;

import java.util.Collection;
import java.util.List;

public class TreePagesView implements IView {
	private final UndoManager undoManager = new UndoManager();

	private final TreePagesViewController controller;
	private final TreePagesViewPresenter presenter;

	private final ObjectProperty<ViewTab> viewTab = new SimpleObjectProperty<>();

	private final StringProperty nameProperty = new SimpleStringProperty();

	private final ObjectProperty<TabPane> tabPane = new SimpleObjectProperty<>(null);

	private final ObservableList<PhyloTree> trees = FXCollections.observableArrayList();
	private final BooleanProperty empty = new SimpleBooleanProperty(true);

	private final IntegerProperty optionRows = new SimpleIntegerProperty(this, "optionRows", ProgramProperties.get("TreePagesRows", 1));
	private final IntegerProperty optionCols = new SimpleIntegerProperty(this, "optionCols", ProgramProperties.get("TreePagesCols", 1));

	private final ObjectProperty<Node> imageNode = new SimpleObjectProperty<>(null);

	private final ObjectProperty<ComputeTreeEmbedding.Diagram> optionDiagram = new SimpleObjectProperty<>(this, "optionDiagram", ComputeTreeEmbedding.Diagram.getDefault());
	private final ObjectProperty<TreePane.RootSide> optionRootSide = new SimpleObjectProperty<>(this, "optionRootSide", TreePane.RootSide.getDefault());

	private final IntegerProperty pageNumber = new SimpleIntegerProperty(this, "pageNumber", 1); // 1-based

	private final BooleanProperty optionShowTreeNames = new SimpleBooleanProperty(this, "optionShowTreeNames", ProgramProperties.get("TreePagesShowTreeNames", true));

	private final DoubleProperty optionFontScaleFactor = new SimpleDoubleProperty(this, "optionFontScaleFactor", 1.0);

	public List<String> listOptions() {
		return List.of(optionDiagram.getName(), optionRootSide.getName(), optionRows.getName(), optionCols.getName(), pageNumber.getName(), optionFontScaleFactor.getName());
	}

	public TreePagesView(MainWindow mainWindow, String name, ViewTab viewTab) {
		nameProperty.set(name);
		var loader = new ExtendedFXMLLoader<TreePagesViewController>(TreePagesViewController.class);
		controller = loader.getController();

		// this is the target area for the tree page:
		final ObjectProperty<Bounds> targetBounds = new SimpleObjectProperty<>();
		presenter = new TreePagesViewPresenter(mainWindow, this, targetBounds, getTrees());

		this.viewTab.addListener((v, o, n) -> {
			targetBounds.unbind();
			if (n != null)
				targetBounds.bind(n.layoutBoundsProperty());
		});
		setViewTab(viewTab);

		empty.bind(Bindings.isEmpty(getTrees()));

		optionDiagram.addListener((v, o, n) -> ComputeTreeEmbedding.Diagram.setDefault(n));
		optionRootSide.addListener((v, o, n) -> TreePane.RootSide.setDefault(n));
		optionRows.addListener((v, o, n) -> ProgramProperties.put("TreePagesRows", n.intValue()));
		optionCols.addListener((v, o, n) -> ProgramProperties.put("TreePagesCols", n.intValue()));
		optionShowTreeNames.addListener((v, o, n) -> ProgramProperties.put("TreePagesShowTreeNames", n));
	}

	public void setViewTab(ViewTab viewTab) {
		this.viewTab.set(viewTab);
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

	public int getPageNumber() {
		return pageNumber.get();
	}

	public IntegerProperty pageNumberProperty() {
		return pageNumber;
	}

	public void setPageNumber(int pageNumber) {
		this.pageNumber.set(pageNumber);
	}

	public ComputeTreeEmbedding.Diagram getOptionDiagram() {
		return optionDiagram.get();
	}

	public ObjectProperty<ComputeTreeEmbedding.Diagram> optionDiagramProperty() {
		return optionDiagram;
	}

	public void setOptionDiagram(ComputeTreeEmbedding.Diagram optionDiagram) {
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

	public Pane getRoot() {
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

	public boolean isOptionShowTreeNames() {
		return optionShowTreeNames.get();
	}

	public BooleanProperty optionShowTreeNamesProperty() {
		return optionShowTreeNames;
	}

	public void setOptionShowTreeNames(boolean optionShowTreeNames) {
		this.optionShowTreeNames.set(optionShowTreeNames);
	}

	@Override
	public UndoManager getUndoManager() {
		return undoManager;
	}

	public void setTrees(Collection<PhyloTree> trees) {
		this.trees.setAll(trees);
	}

	@Override
	public Node getImageNode() {
		return controller.getPagination();
	}
}
