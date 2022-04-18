/*
 *  TreeView.java Copyright (C) 2022 Daniel H. Huson
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
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.geometry.Bounds;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.AnchorPane;
import javafx.scene.shape.Shape;
import jloda.fx.selection.SelectionModel;
import jloda.fx.selection.SetSelectionModel;
import jloda.fx.undo.UndoManager;
import jloda.fx.util.DraggableLabel;
import jloda.fx.util.ExtendedFXMLLoader;
import jloda.fx.util.PrintUtils;
import jloda.phylo.PhyloTree;
import jloda.util.ProgramProperties;
import splitstree6.layout.splits.algorithms.EqualAngle;
import splitstree6.layout.tree.HeightAndAngles;
import splitstree6.layout.tree.LayoutOrientation;
import splitstree6.layout.tree.TreeDiagramType;
import splitstree6.layout.tree.TreeLabel;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.tabs.viewtab.ViewTab;
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

	private final SelectionModel<Integer> splitSelectionModel = new SetSelectionModel<>();

	private final TreeViewController controller;
	private final TreeViewPresenter presenter;

	private final ObjectProperty<ViewTab> viewTab = new SimpleObjectProperty<>(this, "viewTab");

	private final StringProperty name = new SimpleStringProperty(this, "name");

	private final ObservableList<PhyloTree> trees = FXCollections.observableArrayList();
	private final BooleanProperty reticulated = new SimpleBooleanProperty(this, "reticulated", false);

	private final BooleanProperty empty = new SimpleBooleanProperty(this, "empty", true);

	private final IntegerProperty optionTree = new SimpleIntegerProperty(this, "optionTree", 0); // 1-based

	private final ObjectProperty<TreeDiagramType> optionDiagram = new SimpleObjectProperty<>(this, "optionDiagram");
	private final ObjectProperty<HeightAndAngles.Averaging> optionAveraging = new SimpleObjectProperty<>(this, "optionAveraging");

	private final ObjectProperty<LayoutOrientation> optionOrientation = new SimpleObjectProperty<>(this, "optionOrientation");
	private final BooleanProperty optionShowConfidence = new SimpleBooleanProperty(this, "optionShowConfidence", true);
	private final DoubleProperty optionHorizontalZoomFactor = new SimpleDoubleProperty(this, "optionHorizontalZoomFactor", 1.0);
	private final DoubleProperty optionVerticalZoomFactor = new SimpleDoubleProperty(this, "optionVerticalZoomFactor", 1.0);
	private final DoubleProperty optionFontScaleFactor = new SimpleDoubleProperty(this, "optionFontScaleFactor", 1.0);
	private final ObjectProperty<TreeLabel> optionTreeLabels = new SimpleObjectProperty<>(this, "optionTreeLabels");
	private final BooleanProperty optionShowInternalLabels = new SimpleBooleanProperty(this, "optionShowInternalLabels");

	private final ObjectProperty<String[]> optionActiveTraits = new SimpleObjectProperty<>(this, "optionActiveTraits");
	private final BooleanProperty optionTraitLegend = new SimpleBooleanProperty(this, "optionTraitLegend");
	private final IntegerProperty optionTraitSize = new SimpleIntegerProperty(this, "optionTraitSize");

	private final ObjectProperty<String[]> optionEdits = new SimpleObjectProperty<>(this, "optionEdits", new String[0]);

	private final ObjectProperty<Bounds> targetBounds = new SimpleObjectProperty<>(this, "targetBounds");

	// create properties:
	{
		ProgramProperties.track(optionDiagram, TreeDiagramType::valueOf, TreeDiagramType.RadialPhylogram);
		ProgramProperties.track(optionOrientation, LayoutOrientation::valueOf, LayoutOrientation.Rotate0Deg);
		ProgramProperties.track(optionAveraging, HeightAndAngles.Averaging::valueOf, HeightAndAngles.Averaging.ChildAverage);
		ProgramProperties.track(optionTreeLabels, TreeLabel::valueOf, TreeLabel.Name);
		ProgramProperties.track(optionShowInternalLabels, true);
	}

	public List<String> listOptions() {
		return List.of(optionTree.getName(), optionDiagram.getName(), optionAveraging.getName(), optionOrientation.getName(),
				optionHorizontalZoomFactor.getName(), optionVerticalZoomFactor.getName(),
				optionFontScaleFactor.getName(), optionEdits.getName(), optionShowConfidence.getName(),
				optionTreeLabels.getName(), optionShowInternalLabels.getName(),
				optionActiveTraits.getName(), optionTraitLegend.getName(), optionTraitSize.getName());
	}

	public TreeView(MainWindow mainWindow, String name, ViewTab viewTab) {
		this.name.set(name);
		var loader = new ExtendedFXMLLoader<TreeViewController>(TreeViewController.class);
		controller = loader.getController();

		final ObservableMap<jloda.graph.Node, Group> nodeShapeMap = FXCollections.observableHashMap();
		final ObservableMap<Integer, Shape> splitShapeMap = FXCollections.observableHashMap();

		// this is the target area for the tree page:
		presenter = new TreeViewPresenter(mainWindow, this, targetBounds, nodeShapeMap, splitShapeMap);

		this.viewTab.addListener((v, o, n) -> {
			targetBounds.unbind();
			if (n != null)
				targetBounds.bind(n.layoutBoundsProperty());
		});

		setViewTab(viewTab);

		var taxLabelFormatter = new TaxonLabelFormat(mainWindow, undoManager);

		var traitsFormatter = new TraitsFormat(mainWindow, undoManager);
		traitsFormatter.setNodeShapeMap(nodeShapeMap);
		optionActiveTraits.bindBidirectional(traitsFormatter.optionActiveTraitsProperty());
		optionTraitLegend.bindBidirectional(traitsFormatter.optionTraitLegendProperty());
		optionTraitSize.bindBidirectional(traitsFormatter.optionTraitSizeProperty());
		traitsFormatter.getLegend().scaleProperty().bind(optionHorizontalZoomFactorProperty());
		traitsFormatter.setRunAfterUpdateNodes(presenter::updateLabelLayout);
		presenter.updateCounterProperty().addListener(e -> traitsFormatter.updateNodes());

		controller.getFormatVBox().getChildren().addAll(taxLabelFormatter, new TaxonMark(mainWindow, undoManager), traitsFormatter, new SelectTraits(mainWindow));

		AnchorPane.setLeftAnchor(traitsFormatter.getLegend(), 5.0);
		AnchorPane.setTopAnchor(traitsFormatter.getLegend(), 35.0);
		controller.getInnerAnchorPane().getChildren().add(controller.getInnerAnchorPane().getChildren().size() - 1, traitsFormatter.getLegend());
		DraggableLabel.makeDraggable(traitsFormatter.getLegend());

		trees.addListener((InvalidationListener) e -> {
			empty.set(trees.size() == 0);
			if (getOptionTree() == 0 && trees.size() > 0)
				setOptionTree(1);
			else if (getOptionTree() > trees.size())
				setOptionTree(trees.size());
		});

		undoManager.undoableProperty().addListener(e -> mainWindow.setDirty(true));
		optionDiagramProperty().addListener(e -> mainWindow.setDirty(true));
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
	public Node getImageNode() {
		return PrintUtils.createImage(controller.getInnerAnchorPane(), controller.getScrollPane());
	}

	@Override
	public IDisplayTabPresenter getPresenter() {
		return presenter;
	}

	@Override
	public String getCitation() {
		return EqualAngle.getCitation();
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

	public LayoutOrientation getOptionOrientation() {
		return optionOrientation.get();
	}

	public ObjectProperty<LayoutOrientation> optionOrientationProperty() {
		return optionOrientation;
	}

	public void setOptionOrientation(LayoutOrientation optionOrientation) {
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

	public HeightAndAngles.Averaging getOptionAveraging() {
		return optionAveraging.get();
	}

	public ObjectProperty<HeightAndAngles.Averaging> optionAveragingProperty() {
		return optionAveraging;
	}

	public void setOptionAveraging(HeightAndAngles.Averaging optionAveraging) {
		this.optionAveraging.set(optionAveraging);
	}

	public TreeLabel getOptionTreeLabels() {
		return optionTreeLabels.get();
	}

	public ObjectProperty<TreeLabel> optionTreeLabelsProperty() {
		return optionTreeLabels;
	}

	public void setOptionTreeLabels(TreeLabel optionTreeLabel) {
		this.optionTreeLabels.set(optionTreeLabel);
	}

	public boolean isOptionShowInternalLabels() {
		return optionShowInternalLabels.get();
	}

	public BooleanProperty optionShowInternalLabelsProperty() {
		return optionShowInternalLabels;
	}

	public void setOptionShowInternalLabels(boolean optionShowInternalLabels) {
		this.optionShowInternalLabels.set(optionShowInternalLabels);
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

	public boolean isOptionShowConfidence() {
		return optionShowConfidence.get();
	}

	public BooleanProperty optionShowConfidenceProperty() {
		return optionShowConfidence;
	}

	public void setOptionShowConfidence(boolean optionShowConfidence) {
		this.optionShowConfidence.set(optionShowConfidence);
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
}
