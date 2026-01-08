/*
 *  TreeView.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.view.trees.treeview;

import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.collections.SetChangeListener;
import javafx.geometry.Bounds;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Separator;
import javafx.scene.layout.AnchorPane;
import jloda.fx.selection.SelectionModel;
import jloda.fx.selection.SetSelectionModel;
import jloda.fx.undo.UndoManager;
import jloda.fx.util.DraggableLabel;
import jloda.fx.util.ExtendedFXMLLoader;
import jloda.fx.util.FuzzyBoolean;
import jloda.fx.util.ProgramProperties;
import jloda.graph.Edge;
import jloda.phylo.PhyloTree;
import jloda.phylogeny.layout.Averaging;
import splitstree6.layout.tree.LabeledEdgeShape;
import splitstree6.layout.tree.LabeledNodeShape;
import splitstree6.layout.tree.PaneLabel;
import splitstree6.layout.tree.TreeDiagramType;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.tabs.viewtab.ViewTab;
import splitstree6.view.format.edgelabel.EdgeLabelFormat;
import splitstree6.view.format.edgelabel.LabelEdgesBy;
import splitstree6.view.format.edges.EdgesFormat;
import splitstree6.view.format.selecttraits.SelectTraits;
import splitstree6.view.format.taxlabel.TaxonLabelFormat;
import splitstree6.view.format.taxmark.TaxonMark;
import splitstree6.view.format.traits.TraitsFormat;
import splitstree6.view.utils.IView;
import splitstree6.window.MainWindow;

import java.util.List;

/**
 * show a single tree
 * Daniel Huson, 3.2022
 */
public class TreeView implements IView {

	private final UndoManager undoManager = new UndoManager();

	private final TreeViewController controller;
	private final TreeViewPresenter presenter;

	private final ObjectProperty<ViewTab> viewTab = new SimpleObjectProperty<>(this, "viewTab");

	private final StringProperty name = new SimpleStringProperty(this, "name");

	private final ObservableList<PhyloTree> trees = FXCollections.observableArrayList();
	private final BooleanProperty reticulated = new SimpleBooleanProperty(this, "reticulated", false);

	private final BooleanProperty empty = new SimpleBooleanProperty(this, "empty", true);

	private final IntegerProperty optionTree = new SimpleIntegerProperty(this, "optionTree", 0); // 1-based
	private final ObjectProperty<PhyloTree> tree = new SimpleObjectProperty<>(this, "tree");

	private final ObjectProperty<TreeDiagramType> optionDiagram = new SimpleObjectProperty<>(this, "optionDiagram", TreeDiagramType.RectangularPhylogram);
	private final ObjectProperty<Averaging> optionAveraging = new SimpleObjectProperty<>(this, "optionAveraging");

	private final StringProperty optionOrientation = new SimpleStringProperty(this, "optionOrientation", "Rotate0Deg");
	private final DoubleProperty optionHorizontalZoomFactor = new SimpleDoubleProperty(this, "optionHorizontalZoomFactor", 1.0);
	private final DoubleProperty optionVerticalZoomFactor = new SimpleDoubleProperty(this, "optionVerticalZoomFactor", 1.0);
	private final DoubleProperty optionFontScaleFactor = new SimpleDoubleProperty(this, "optionFontScaleFactor", 1.0);

	private final ObjectProperty<PaneLabel> optionPaneLabel = new SimpleObjectProperty<>(this, "optionTreeLabels");

	private final ObjectProperty<LabelEdgesBy> optionLabelEdgesBy = new SimpleObjectProperty<>(this, "optionLabelEdgesBy", LabelEdgesBy.None);

	private final ObjectProperty<String[]> optionActiveTraits = new SimpleObjectProperty<>(this, "optionActiveTraits", new String[0]);
	private final ObjectProperty<FuzzyBoolean> optionTraitLegend = new SimpleObjectProperty<>(this, "optionTraitLegend", FuzzyBoolean.False);
	private final IntegerProperty optionTraitSize = new SimpleIntegerProperty(this, "optionTraitSize", 64);

	private final ObjectProperty<String[]> optionEdits = new SimpleObjectProperty<>(this, "optionEdits", new String[0]);

	private final BooleanProperty optionShowQRCode = new SimpleBooleanProperty(this, "optionShowQRCode");

	private final ObjectProperty<Bounds> targetBounds = new SimpleObjectProperty<>(this, "targetBounds");

	private final ObservableMap<jloda.graph.Node, LabeledNodeShape> nodeShapeMap = FXCollections.observableHashMap();
	private final ObservableMap<jloda.graph.Edge, LabeledEdgeShape> edgeShapeMap = FXCollections.observableHashMap();
	private final SelectionModel<Edge> edgeSelectionModel = new SetSelectionModel<>();

	// create properties:
	{
		ProgramProperties.track(optionDiagram, TreeDiagramType::valueOf, TreeDiagramType.RectangularPhylogram);
		ProgramProperties.track(optionLabelEdgesBy, LabelEdgesBy::valueOf, LabelEdgesBy.None);
		ProgramProperties.track(optionAveraging, Averaging::valueOf, Averaging.ChildAverage);
		ProgramProperties.track(optionPaneLabel, PaneLabel::valueOf, PaneLabel.ScaleBarNone);
		ProgramProperties.track(optionShowQRCode, false);
	}

	public List<String> listOptions() {
		return List.of(optionTree.getName(), optionDiagram.getName(), optionAveraging.getName(), optionOrientation.getName(),
				optionHorizontalZoomFactor.getName(), optionVerticalZoomFactor.getName(),
				optionFontScaleFactor.getName(), optionEdits.getName(),
				optionPaneLabel.getName(), optionLabelEdgesBy.getName(),
				optionActiveTraits.getName(), optionTraitLegend.getName(),
				optionTraitSize.getName(), optionShowQRCode.getName());
	}

	public TreeView(MainWindow mainWindow, String name, ViewTab viewTab) {
		this.name.set(name);
		var loader = new ExtendedFXMLLoader<TreeViewController>(TreeViewController.class);
		controller = loader.getController();

		// this is the target area for the tree page:
		presenter = new TreeViewPresenter(mainWindow, this, targetBounds);

		this.viewTab.addListener((v, o, n) -> {
			targetBounds.unbind();
			if (n != null)
				targetBounds.bind(n.layoutBoundsProperty());
		});

		setViewTab(viewTab);

		var taxLabelFormatter = new TaxonLabelFormat(mainWindow, undoManager);

		var traitsFormatter = new TraitsFormat(mainWindow, undoManager);
		traitsFormatter.setNodeShapeMap(nodeShapeMap);

		traitsFormatter.optionActiveTraitsProperty().bindBidirectional(optionActiveTraits);
		traitsFormatter.optionTraitLegendProperty().bindBidirectional(optionTraitLegend);
		traitsFormatter.optionMaxCircleRadiusProperty().bindBidirectional(optionTraitSize);
		traitsFormatter.setRunAfterUpdateNodes(presenter::updateLabelLayout);
		presenter.updateCounterProperty().addListener(e -> traitsFormatter.updateNodes());

		var edgesFormatter = new EdgesFormat(undoManager, edgeSelectionModel, edgeShapeMap, optionEditsProperty());

		var edgeLabelFormat = new EdgeLabelFormat(undoManager);
		optionLabelEdgesBy.bindBidirectional(edgeLabelFormat.optionLabelEdgesByProperty());

		treeProperty().addListener((v, o, n) -> {
			edgeLabelFormat.getPresenter().updateMenus(n);
		});

		controller.getFormatVBox().getChildren().addAll(taxLabelFormatter, new TaxonMark(mainWindow, undoManager), traitsFormatter, new SelectTraits(mainWindow),
				new Separator(Orientation.HORIZONTAL), edgeLabelFormat, edgesFormatter);

		AnchorPane.setLeftAnchor(traitsFormatter.getLegend(), 5.0);
		AnchorPane.setTopAnchor(traitsFormatter.getLegend(), 35.0);
		controller.getInnerAnchorPane().getChildren().add(controller.getInnerAnchorPane().getChildren().size() - 1, traitsFormatter.getLegend());
		DraggableLabel.makeDraggable(traitsFormatter.getLegend());

		viewTab.emptyProperty().bind(empty);
		viewTabProperty().addListener((v, o, n) -> {
			if (o != null)
				o.emptyProperty().unbind();
			if (n != null)
				n.emptyProperty().bind(empty);
		});
		trees.addListener((InvalidationListener) e -> {
			empty.set(trees.isEmpty());
			if (getOptionTree() == 0 && !trees.isEmpty())
				setOptionTree(1);
			else if (getOptionTree() > trees.size())
				setOptionTree(trees.size());
		});

		undoManager.undoableProperty().addListener(e -> mainWindow.setDirty(true));
		optionDiagramProperty().addListener(e -> mainWindow.setDirty(true));

		InvalidationListener updateBreadCrumbs = e -> {
			var text = "";
			if (mainWindow.getWorkingTaxa() != null)
				text += "n: %,d".formatted(mainWindow.getWorkingTaxa().getNtax());
			if (getTree() != null) {
				var tree = getTree();
				text += " v: %,d e: %,d".formatted(tree.getNumberOfNodes(), tree.getNumberOfEdges());
				if (tree.isReticulated()) {
					var hybridNumber = tree.nodeStream().filter(v -> v.getInDegree() > 1).mapToInt(v -> v.getInDegree() - 1).sum();
					text += " h: %,d".formatted(hybridNumber);
				}
				if (getTrees().size() > 1) {
					text += " (%,d of %,d)".formatted(getOptionTree(), getTrees().size());
				}
			}
			viewTab.getAlgorithmBreadCrumbsToolBar().getInfoLabel().textProperty().unbind();
			viewTab.getAlgorithmBreadCrumbsToolBar().getInfoLabel().setText(text);
		};
		mainWindow.workingTaxaProperty().addListener(new WeakInvalidationListener(updateBreadCrumbs));
		treeProperty().addListener(updateBreadCrumbs);
	}

	@Override
	public void clear() {
	}

	@Override
	public String getName() {
		return name.get();
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
	public void setViewTab(ViewTab viewTab) {
		this.viewTab.set(viewTab);
	}

	@Override
	public int size() {
		return getTrees().size();
	}

	@Override
	public UndoManager getUndoManager() {
		return undoManager;
	}

	@Override
	public ReadOnlyBooleanProperty emptyProperty() {
		return empty;
	}

	@Override
	public Node getMainNode() {
		return controller.getInnerAnchorPane();
	}

	@Override
	public IDisplayTabPresenter getPresenter() {
		return presenter;
	}

	@Override
	public String getCitation() {
		return null;
	}

	public ViewTab getViewTab() {
		return viewTab.get();
	}

	public ObjectProperty<ViewTab> viewTabProperty() {
		return viewTab;
	}

	public int getOptionTree() {
		return optionTree.get();
	}

	public IntegerProperty optionTreeProperty() {
		return optionTree;
	}

	public void setOptionTree(int optionTree) {
		this.optionTree.set(optionTree);
	}

	public String getOptionOrientation() {
		return optionOrientation.get();
	}

	public StringProperty optionOrientationProperty() {
		return optionOrientation;
	}

	public void setOptionOrientation(String optionOrientation) {
		this.optionOrientation.set(optionOrientation);
	}

	public TreeDiagramType getOptionDiagram() {
		return optionDiagram.get();
	}

	public ObjectProperty<TreeDiagramType> optionDiagramProperty() {
		return optionDiagram;
	}

	public void setOptionDiagram(TreeDiagramType optionDiagram) {
		this.optionDiagram.set(optionDiagram);
	}

	public Averaging getOptionAveraging() {
		return optionAveraging.get();
	}

	public ObjectProperty<Averaging> optionAveragingProperty() {
		return optionAveraging;
	}

	public void setOptionAveraging(Averaging optionAveraging) {
		this.optionAveraging.set(optionAveraging);
	}

	public PaneLabel getOptionPaneLabel() {
		return optionPaneLabel.get();
	}

	public ObjectProperty<PaneLabel> optionPaneLabelProperty() {
		return optionPaneLabel;
	}

	public void setOptionPaneLabel(PaneLabel optionPaneLabel) {
		this.optionPaneLabel.set(optionPaneLabel);
	}

	public LabelEdgesBy getOptionLabelEdgesBy() {
		return optionLabelEdgesBy.get();
	}

	public ObjectProperty<LabelEdgesBy> optionLabelEdgesByProperty() {
		return optionLabelEdgesBy;
	}

	public void setOptionLabelEdgesBy(LabelEdgesBy optionLabelEdgesBy) {
		this.optionLabelEdgesBy.set(optionLabelEdgesBy);
	}

	public String[] getOptionEdits() {
		return optionEdits.get();
	}

	public ObjectProperty<String[]> optionEditsProperty() {
		return optionEdits;
	}

	public void setOptionEdits(String[] optionEdits) {
		this.optionEdits.set(optionEdits);
	}

	public boolean isOptionShowQRCode() {
		return optionShowQRCode.get();
	}

	public BooleanProperty optionShowQRCodeProperty() {
		return optionShowQRCode;
	}

	public double getOptionHorizontalZoomFactor() {
		return optionHorizontalZoomFactor.get();
	}

	public DoubleProperty optionHorizontalZoomFactorProperty() {
		return optionHorizontalZoomFactor;
	}

	public void setOptionHorizontalZoomFactor(double optionHorizontalZoomFactor) {
		this.optionHorizontalZoomFactor.set(optionHorizontalZoomFactor);
	}

	public double getOptionVerticalZoomFactor() {
		return optionVerticalZoomFactor.get();
	}

	public DoubleProperty optionVerticalZoomFactorProperty() {
		return optionVerticalZoomFactor;
	}

	public void setOptionVerticalZoomFactor(double optionVerticalZoomFactor) {
		this.optionVerticalZoomFactor.set(optionVerticalZoomFactor);
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

	public Bounds getTargetBounds() {
		return targetBounds.get();
	}

	public ObjectProperty<Bounds> targetBoundsProperty() {
		return targetBounds;
	}

	public TreeViewController getController() {
		return controller;
	}

	public ObservableList<PhyloTree> getTrees() {
		return trees;
	}

	public boolean isReticulated() {
		return reticulated.get();
	}

	public BooleanProperty reticulatedProperty() {
		return reticulated;
	}

	public void setReticulated(boolean reticulated) {
		this.reticulated.set(reticulated);
	}

	public PhyloTree getTree() {
		return tree.get();
	}

	public ObjectProperty<PhyloTree> treeProperty() {
		return tree;
	}

	public ObservableMap<jloda.graph.Node, LabeledNodeShape> getNodeShapeMap() {
		return nodeShapeMap;
	}

	public ObservableMap<Edge, LabeledEdgeShape> getEdgeShapeMap() {
		return edgeShapeMap;
	}

	public SelectionModel<Edge> getEdgeSelectionModel() {
		return edgeSelectionModel;
	}

	public void setEdgeSelectionModel(SelectionModel<Edge> edgeSelectionModel) {
		this.edgeSelectionModel.clearSelection();
		this.edgeSelectionModel.selectAll(edgeSelectionModel.getSelectedItems());
		edgeSelectionModel.getSelectedItems().addListener((SetChangeListener<? super Edge>) e -> {
			if (e.wasAdded())
				this.edgeSelectionModel.select(e.getElementAdded());
			if (e.wasRemoved())
				this.edgeSelectionModel.clearSelection(e.getElementRemoved());
		});
	}


}
