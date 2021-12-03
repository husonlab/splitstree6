/*
 *  TanglegramView.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.view.trees.tanglegram;

import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.TabPane;
import jloda.fx.undo.UndoManager;
import jloda.fx.util.ExtendedFXMLLoader;
import jloda.phylo.PhyloTree;
import jloda.util.ProgramProperties;
import splitstree6.tabs.viewtab.ViewTab;
import splitstree6.view.IView;
import splitstree6.view.trees.treepages.ComputeTreeEmbedding;
import splitstree6.view.trees.treepages.TreePane;
import splitstree6.window.MainWindow;

import java.util.List;

public class TanglegramView implements IView {
	private final UndoManager undoManager = new UndoManager();

	private final TanglegramViewController controller;
	private final TanglegramViewPresenter presenter;

	private final ObjectProperty<ViewTab> viewTab = new SimpleObjectProperty<>();

	private final StringProperty name = new SimpleStringProperty();

	private final ObjectProperty<TabPane> tabPane = new SimpleObjectProperty<>(null);

	private final ObservableList<PhyloTree> trees = FXCollections.observableArrayList();
	private final BooleanProperty empty = new SimpleBooleanProperty(true);

	private final ObjectProperty<Node> imageNode = new SimpleObjectProperty<>(null);

	private final IntegerProperty optionTree1 = new SimpleIntegerProperty(this, "optionTree1", 1); // 1-based
	private final ObjectProperty<ComputeTreeEmbedding.Diagram> optionDiagram1 = new SimpleObjectProperty<>(this, "optionDiagram1", ComputeTreeEmbedding.Diagram.RectangularPhylogram);

	private final IntegerProperty optionTree2 = new SimpleIntegerProperty(this, "optionTree2", 2); // 1-based
	private final ObjectProperty<ComputeTreeEmbedding.Diagram> optionDiagram2 = new SimpleObjectProperty<>(this, "optionDiagram2", ComputeTreeEmbedding.Diagram.RectangularPhylogram);

	private final ObjectProperty<TreePane.Orientation> optionOrientation = new SimpleObjectProperty<>(this, "optionOrientation1", TreePane.Orientation.Rotate0Deg);

	private final BooleanProperty optionShowTreeNames = new SimpleBooleanProperty(this, "optionShowTreeNames", ProgramProperties.get("TanglegramShowTreeNames", true));

	private final DoubleProperty optionZoomFactor = new SimpleDoubleProperty(this, "optionZoomFactor", 1.0);
	private final DoubleProperty optionFontScaleFactor = new SimpleDoubleProperty(this, "optionFontScaleFactor", 1.0);

	public List<String> listOptions() {
		return List.of(optionTree1.getName(), optionDiagram1.getName(),
				optionTree2.getName(), optionDiagram2.getName(), optionOrientation.getName(),
				optionZoomFactor.getName(), optionFontScaleFactor.getName());
	}

	public TanglegramView(MainWindow mainWindow, String name, ViewTab viewTab) {
		this.name.set(name);
		var loader = new ExtendedFXMLLoader<TanglegramViewController>(TanglegramViewController.class);
		controller = loader.getController();

		// this is the target area for the tree page:
		final ObjectProperty<Bounds> targetBounds = new SimpleObjectProperty<>();
		presenter = new TanglegramViewPresenter(mainWindow, this, targetBounds, getTrees());

		this.viewTab.addListener((v, o, n) -> {
			targetBounds.unbind();
			if (n != null)
				targetBounds.bind(n.layoutBoundsProperty());
		});
		setViewTab(viewTab);

		empty.bind(Bindings.isEmpty(getTrees()));

		optionShowTreeNames.addListener((v, o, n) -> ProgramProperties.put("TanglegramShowTreeNames", n));
	}

	public void setViewTab(ViewTab viewTab) {
		this.viewTab.set(viewTab);
	}

	public UndoManager getUndoManager() {
		return undoManager;
	}

	public TanglegramViewController getController() {
		return controller;
	}

	public TanglegramViewPresenter getPresenter() {
		return presenter;
	}

	public ViewTab getViewTab() {
		return viewTab.get();
	}

	public ObjectProperty<ViewTab> viewTabProperty() {
		return viewTab;
	}

	public String getName() {
		return name.get();
	}

	public StringProperty nameProperty() {
		return name;
	}

	public TabPane getTabPane() {
		return tabPane.get();
	}

	public ObjectProperty<TabPane> tabPaneProperty() {
		return tabPane;
	}

	public ObservableList<PhyloTree> getTrees() {
		return trees;
	}

	public boolean isEmpty() {
		return empty.get();
	}

	public BooleanProperty emptyProperty() {
		return empty;
	}

	public Node getImageNode() {
		return imageNode.get();
	}

	public ObjectProperty<Node> imageNodeProperty() {
		return imageNode;
	}

	public ComputeTreeEmbedding.Diagram getOptionDiagram1() {
		return optionDiagram1.get();
	}

	public ObjectProperty<ComputeTreeEmbedding.Diagram> optionDiagram1Property() {
		return optionDiagram1;
	}

	public TreePane.Orientation getOptionOrientation() {
		return optionOrientation.get();
	}

	public ObjectProperty<TreePane.Orientation> optionOrientationProperty() {
		return optionOrientation;
	}

	public ComputeTreeEmbedding.Diagram getOptionDiagram2() {
		return optionDiagram2.get();
	}

	public ObjectProperty<ComputeTreeEmbedding.Diagram> optionDiagram2Property() {
		return optionDiagram2;
	}

	public int getOptionTree1() {
		return optionTree1.get();
	}

	public IntegerProperty optionTree1Property() {
		return optionTree1;
	}

	public int getOptionTree2() {
		return optionTree2.get();
	}

	public IntegerProperty optionTree2Property() {
		return optionTree2;
	}

	public boolean isOptionShowTreeNames() {
		return optionShowTreeNames.get();
	}

	public BooleanProperty optionShowTreeNamesProperty() {
		return optionShowTreeNames;
	}

	public double getOptionZoomFactor() {
		return optionZoomFactor.get();
	}

	public DoubleProperty optionZoomFactorProperty() {
		return optionZoomFactor;
	}

	public double getOptionFontScaleFactor() {
		return optionFontScaleFactor.get();
	}

	public DoubleProperty optionFontScaleFactorProperty() {
		return optionFontScaleFactor;
	}

	public void setOptionZoomFactor(double optionZoomFactor) {
		this.optionZoomFactor.set(optionZoomFactor);
	}

	public void setOptionFontScaleFactor(double optionFontScaleFactor) {
		this.optionFontScaleFactor.set(optionFontScaleFactor);
	}

	@Override
	public Node getRoot() {
		return controller.getAnchorPane();
	}

	@Override
	public void setupMenuItems() {
		presenter.setupMenuItems();

	}

	@Override
	public int size() {
		return (controller.getTree1CBox().getValue() != null ? 1 : 0) + (controller.getTree2CBox().getValue() != null ? 1 : 0);
	}
}
