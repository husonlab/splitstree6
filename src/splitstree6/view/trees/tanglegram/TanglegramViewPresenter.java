/*
 *  TanglegramViewPresenter.java Copyright (C) 2021 Daniel H. Huson
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

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import javafx.geometry.Bounds;
import javafx.geometry.Dimension2D;
import javafx.scene.control.SelectionMode;
import javafx.scene.paint.Color;
import jloda.fx.find.FindToolBar;
import jloda.fx.find.Searcher;
import jloda.fx.util.BasicFX;
import jloda.phylo.PhyloTree;
import splitstree6.data.parts.Taxon;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.view.splits.viewer.ComboBoxUtils;
import splitstree6.view.trees.layout.LayoutUtils;
import splitstree6.view.trees.layout.TreeDiagramType;
import splitstree6.view.trees.ordering.CircularOrdering;
import splitstree6.view.trees.treepages.LayoutOrientation;
import splitstree6.window.MainWindow;

import java.util.function.Function;

import static splitstree6.view.trees.layout.TreeDiagramType.*;
import static splitstree6.view.trees.treepages.LayoutOrientation.*;

/**
 * tanglegram view presenter
 * Daniel Huson, 12.2021
 */
public class TanglegramViewPresenter implements IDisplayTabPresenter {
	private final MainWindow mainWindow;
	private final TanglegramView tanglegramView;
	private final TanglegramViewController controller;

	private final FindToolBar findToolBar;

	private final ObjectProperty<Dimension2D> treePaneDimensions = new SimpleObjectProperty<>(new Dimension2D(0, 0));

	public TanglegramViewPresenter(MainWindow mainWindow, TanglegramView tanglegramView, ObjectProperty<Bounds> targetBounds, ObservableList<PhyloTree> trees) {
		this.mainWindow = mainWindow;
		this.tanglegramView = tanglegramView;
		controller = tanglegramView.getController();

		var taxonOrdering1 = new SimpleObjectProperty<int[]>();
		var taxonOrdering2 = new SimpleObjectProperty<int[]>();

		var tree1 = new SimpleObjectProperty<PhyloTree>(this, "tree1");
		tree1.addListener((v, o, n) -> {
					controller.getTree1CBox().setValue(tree1.get());
					controller.getTree1Label().setText(tree1.get().getName());
				}
		);
		tree1.bind(Bindings.createObjectBinding(() -> tanglegramView.getOptionTree1() >= 1 && tanglegramView.getOptionTree1() <= trees.size() ? trees.get(tanglegramView.getOptionTree1() - 1) : null, tanglegramView.optionTree1Property(), trees));
		controller.getTree1Label().visibleProperty().bind(tanglegramView.optionShowTreeNamesProperty());

		var tree1Pane = new TanglegramTreePane(mainWindow.getWorkflow().getWorkingTaxaBlock(), mainWindow.getTaxonSelectionModel(), tree1, taxonOrdering1, treePaneDimensions,
				tanglegramView.optionDiagram1Property(), tanglegramView.optionOrientationProperty(), tanglegramView.optionFontScaleFactorProperty());
		controller.getLeftPane().getChildren().add(tree1Pane);

		var tree2 = new SimpleObjectProperty<PhyloTree>(this, "tree2");
		tree2.addListener((v, o, n) -> {
			controller.getTree2CBox().setValue(tree2.get());
			controller.getTree2Label().setText(tree2.get().getName());
		});
		tree2.bind(Bindings.createObjectBinding(() -> tanglegramView.getOptionTree2() >= 1 && tanglegramView.getOptionTree2() <= trees.size() ? trees.get(tanglegramView.getOptionTree2() - 1) : null, tanglegramView.optionTree2Property(), trees));
		controller.getTree2Label().visibleProperty().bind(tanglegramView.optionShowTreeNamesProperty());

		var orientation2Property = new SimpleObjectProperty<LayoutOrientation>();
		BasicFX.reportChanges("orientation2", orientation2Property);
		tanglegramView.optionOrientationProperty().addListener((v, o, n) -> {
			if (n == Rotate0Deg)
				orientation2Property.set(FlipRotate0Deg);
			else
				orientation2Property.set(Rotate180Deg);
		});
		orientation2Property.set(tanglegramView.getOptionOrientation() == Rotate0Deg ? FlipRotate0Deg : Rotate180Deg);

		var tree2Pane = new TanglegramTreePane(mainWindow.getWorkflow().getWorkingTaxaBlock(), mainWindow.getTaxonSelectionModel(), tree2, taxonOrdering2, treePaneDimensions,
				tanglegramView.optionDiagram2Property(), orientation2Property, tanglegramView.optionFontScaleFactorProperty());
		controller.getRightPane().getChildren().add(tree2Pane);

		tree1.addListener(e -> {
			if (tree1.get() != null) {
				var cycle = CircularOrdering.apply(mainWindow.getWorkflow().getWorkingTaxaBlock(), tree1.get(), tree2.get());
				taxonOrdering1.set(CircularOrdering.computeRealizableCycle(tree1.get(), cycle));
				if (tree2.get() != null)
					taxonOrdering2.set(CircularOrdering.computeRealizableCycle(tree2.get(), cycle));
			}
		});
		tree2.addListener(e -> {
			if (tree1.get() != null) {
				var cycle = CircularOrdering.apply(mainWindow.getWorkflow().getWorkingTaxaBlock(), tree1.get(), tree2.get());
				taxonOrdering1.set(CircularOrdering.computeRealizableCycle(tree1.get(), cycle));
				if (tree2.get() != null)
					taxonOrdering2.set(CircularOrdering.computeRealizableCycle(tree2.get(), cycle));
			}
		});

		{
			var connectors = new Connectors(mainWindow, controller.getMiddlePane(), controller.getLeftPane(), controller.getRightPane(), new SimpleObjectProperty<>(Color.DARKGRAY), new SimpleDoubleProperty(1.0));
			tree1Pane.setRunAfterUpdate(connectors::update);
			tree2Pane.setRunAfterUpdate(connectors::update);
			tanglegramView.optionFontScaleFactorProperty().addListener(e -> connectors.update());
		}

		{
			final ObservableSet<TreeDiagramType> disabledDiagrams1 = FXCollections.observableSet();
			tree1.addListener((v, o, n) -> {
				disabledDiagrams1.clear();
				if (tree1.get() != null && tree1.get().isReticulated()) {
					disabledDiagrams1.add(TreeDiagramType.TriangularCladogram);
				}
			});

			controller.getDiagram1CBox().setButtonCell(ComboBoxUtils.createButtonCell(disabledDiagrams1, TreeDiagramType::createNode, false));
			controller.getDiagram1CBox().setCellFactory(ComboBoxUtils.createCellFactory(disabledDiagrams1, TreeDiagramType::createNode, false));
			controller.getDiagram1CBox().getItems().addAll(RectangularPhylogram, RectangularCladogram, TriangularCladogram);
			controller.getDiagram1CBox().setValue(tanglegramView.getOptionDiagram1());
			tanglegramView.optionDiagram1Property().addListener((v, o, n) -> controller.getDiagram1CBox().setValue(n));
			controller.getDiagram1CBox().valueProperty().addListener((v, o, n) -> tanglegramView.optionDiagram1Property().set(n));
		}
		{
			final ObservableSet<TreeDiagramType> disabledDiagrams2 = FXCollections.observableSet();
			tree2.addListener((v, o, n) -> {
				disabledDiagrams2.clear();
				if (tree2.get() != null && tree2.get().isReticulated()) {
					disabledDiagrams2.add(TreeDiagramType.TriangularCladogram);
				}
			});

			controller.getDiagram2CBox().setButtonCell(ComboBoxUtils.createButtonCell(disabledDiagrams2, TreeDiagramType::createNode, true));
			controller.getDiagram2CBox().setCellFactory(ComboBoxUtils.createCellFactory(disabledDiagrams2, TreeDiagramType::createNode, true));
			controller.getDiagram2CBox().getItems().addAll(RectangularPhylogram, RectangularCladogram, TriangularCladogram);
			controller.getDiagram2CBox().setValue(tanglegramView.getOptionDiagram2());
			tanglegramView.optionDiagram2Property().addListener((v, o, n) -> controller.getDiagram2CBox().setValue(n));
			controller.getDiagram2CBox().valueProperty().addListener((v, o, n) -> tanglegramView.optionDiagram2Property().set(n));
		}

		controller.getOrientationCBox().setButtonCell(ComboBoxUtils.createButtonCell(null, LayoutOrientation::createIconLabel));
		controller.getOrientationCBox().setCellFactory(ComboBoxUtils.createCellFactory(null, LayoutOrientation::createIconLabel));
		controller.getOrientationCBox().getItems().addAll(Rotate0Deg, FlipRotate180Deg);
		controller.getOrientationCBox().setValue(tanglegramView.getOptionOrientation());
		controller.getOrientationCBox().valueProperty().addListener((v, o, n) -> tanglegramView.optionOrientationProperty().set(n));
		tanglegramView.optionOrientationProperty().addListener((v, o, n) -> controller.getOrientationCBox().setValue(n));

		tanglegramView.optionOrientationProperty().addListener((v, o, n) -> {
			LayoutUtils.applyOrientation(o, n, true, controller.getMiddlePane().getChildren().get(0));
		});

		controller.getShowTreeNamesToggleButton().selectedProperty().bindBidirectional(tanglegramView.optionShowTreeNamesProperty());

		controller.getTree1CBox().setItems(trees);
		controller.getTree1CBox().disableProperty().bind(Bindings.isEmpty(trees));
		if (tanglegramView.getOptionTree1() <= trees.size())
			controller.getTree1CBox().setValue(trees.get(tanglegramView.getOptionTree1() - 1));
		controller.getTree1CBox().valueProperty().addListener((v, o, n) -> {
			if (n != null)
				tanglegramView.optionTree1Property().set(trees.indexOf(n) + 1);
		});

		controller.getTree2CBox().setItems(trees);
		controller.getTree2CBox().disableProperty().bind(Bindings.isEmpty(trees));
		if (tanglegramView.getOptionTree2() <= trees.size())
			controller.getTree1CBox().setValue(trees.get(tanglegramView.getOptionTree2() - 1));
		controller.getTree2CBox().valueProperty().addListener((v, o, n) -> {
			if (n != null)
				tanglegramView.optionTree2Property().set(trees.indexOf(n) + 1);
		});

		trees.addListener((InvalidationListener) e -> {
			if (controller.getTree1CBox().getValue() == null) {
				if (tanglegramView.getOptionTree1() >= 1 && tanglegramView.getOptionTree1() <= trees.size())
					controller.getTree1CBox().setValue(trees.get(tanglegramView.getOptionTree1() - 1));
				else if (trees.size() >= 1)
					controller.getTree1CBox().setValue(trees.get(0));
			}
			if (controller.getTree2CBox().getValue() == null) {
				if (tanglegramView.getOptionTree2() >= 1 && tanglegramView.getOptionTree2() <= trees.size())
					controller.getTree2CBox().setValue(trees.get(tanglegramView.getOptionTree2() - 1));
				else if (trees.size() >= 2)
					controller.getTree2CBox().setValue(trees.get(1));
			}
		});

		controller.getPreviousButton().setOnAction(e -> {
			tanglegramView.getUndoManager().setRecordChanges(false);
			try {
				tanglegramView.setOptionTree1(tanglegramView.getOptionTree1() - 1);
				tanglegramView.setOptionTree2(tanglegramView.getOptionTree2() - 1);
			} finally {
				tanglegramView.getUndoManager().setRecordChanges(true);
				tanglegramView.getUndoManager().add("Previous", () -> {
					tanglegramView.setOptionTree1(tanglegramView.getOptionTree1() + 1);
					tanglegramView.setOptionTree2(tanglegramView.getOptionTree2() + 1);
				}, () -> {
					tanglegramView.setOptionTree1(tanglegramView.getOptionTree1() - 1);
					tanglegramView.setOptionTree2(tanglegramView.getOptionTree2() - 1);
				});
			}
		});
		controller.getPreviousButton().disableProperty().bind(tanglegramView.optionTree1Property().lessThanOrEqualTo(1).or(tanglegramView.optionTree2Property().lessThanOrEqualTo(1)));

		controller.getNextButton().setOnAction(e -> {
			try {
				tanglegramView.getUndoManager().setRecordChanges(false);
				tanglegramView.setOptionTree1(tanglegramView.getOptionTree1() + 1);
				tanglegramView.setOptionTree2(tanglegramView.getOptionTree2() + 1);
			} finally {
				tanglegramView.getUndoManager().setRecordChanges(true);
				tanglegramView.getUndoManager().add("Next", () -> {
					tanglegramView.setOptionTree1(tanglegramView.getOptionTree1() - 1);
					tanglegramView.setOptionTree2(tanglegramView.getOptionTree2() - 1);
				}, () -> {
					tanglegramView.setOptionTree1(tanglegramView.getOptionTree1() + 1);
					tanglegramView.setOptionTree2(tanglegramView.getOptionTree2() + 1);
				});
			}

		});
		controller.getNextButton().disableProperty().bind(tanglegramView.optionTree1Property().greaterThanOrEqualTo(Bindings.size(trees)).or(tanglegramView.optionTree2Property().greaterThanOrEqualTo(Bindings.size(trees))));

		InvalidationListener updateDimensions = e -> {
			var bounds = targetBounds.get();
			var middleWidth = Math.min(300, Math.max(40, 0.2 * bounds.getWidth()));
			var treePaneWidth = (0.5 * bounds.getWidth() - middleWidth);
			var height = bounds.getHeight() - 200;
			controller.getMiddlePane().setPrefWidth(middleWidth);
			controller.getMiddlePane().setPrefHeight(height);
			treePaneDimensions.set(new Dimension2D(tanglegramView.getOptionHorizontalZoomFactor() * treePaneWidth, tanglegramView.getOptionVerticalZoomFactor() * height));

		};
		targetBounds.addListener(updateDimensions);
		tanglegramView.optionVerticalZoomFactorProperty().addListener(updateDimensions);
		tanglegramView.optionHorizontalZoomFactorProperty().addListener(updateDimensions);

		Function<Integer, Taxon> t2taxon = t -> mainWindow.getActiveTaxa().get(t);

		findToolBar = new FindToolBar(mainWindow.getStage(), new Searcher<>(mainWindow.getActiveTaxa(),
				t -> mainWindow.getTaxonSelectionModel().isSelected(t2taxon.apply(t)),
				(t, s) -> mainWindow.getTaxonSelectionModel().setSelected(t2taxon.apply(t), s),
				new SimpleObjectProperty<>(SelectionMode.MULTIPLE),
				t -> t2taxon.apply(t).getNameAndDisplayLabel("===="),
				label -> label.replaceAll(".*====", ""),
				null));
		findToolBar.setShowFindToolBar(false);

		controller.getvBox().getChildren().add(findToolBar);
		controller.getFindButton().setOnAction(e -> findToolBar.setShowFindToolBar(!findToolBar.isShowFindToolBar()));

		var undoManager = tanglegramView.getUndoManager();
		tanglegramView.optionTree1Property().addListener((v, o, n) -> undoManager.add("Set Tree 1", tanglegramView.optionTree1Property(), o, n));
		tanglegramView.optionTree2Property().addListener((v, o, n) -> undoManager.add("Set Tree 2", tanglegramView.optionTree2Property(), o, n));
		tanglegramView.optionDiagram1Property().addListener((v, o, n) -> undoManager.add("Set TreeDiagramType 1", tanglegramView.optionDiagram1Property(), o, n));
		tanglegramView.optionDiagram2Property().addListener((v, o, n) -> undoManager.add("Set TreeDiagramType 2", tanglegramView.optionDiagram2Property(), o, n));
		tanglegramView.optionOrientationProperty().addListener((v, o, n) -> undoManager.add("Set LayoutOrientation", tanglegramView.optionOrientationProperty(), o, n));
		tanglegramView.optionFontScaleFactorProperty().addListener((v, o, n) -> undoManager.add((n.doubleValue() > 1 ? "Increase" : "Decrease ") + " Font Size", tanglegramView.optionFontScaleFactorProperty(), o, n));
		tanglegramView.optionHorizontalZoomFactorProperty().addListener((v, o, n) -> undoManager.add((n.doubleValue() > 1 ? "Increase" : "Decrease ") + " Horizontal Zoom", tanglegramView.optionHorizontalZoomFactorProperty(), o, n));
		tanglegramView.optionVerticalZoomFactorProperty().addListener((v, o, n) -> undoManager.add((n.doubleValue() > 1 ? "Increase" : "Decrease ") + " Vertical Zoom", tanglegramView.optionVerticalZoomFactorProperty(), o, n));
		tanglegramView.optionShowTreeNamesProperty().addListener((v, o, n) -> undoManager.add((n ? "Show" : "Hide") + " Tree names", tanglegramView.optionShowTreeNamesProperty(), o, n));

		controller.getPrintButton().setOnAction(mainWindow.getController().getPrintMenuItem().getOnAction());
		controller.getPrintButton().disableProperty().bind(mainWindow.getController().getPrintMenuItem().disableProperty());

		controller.getContractHorizontallyButton().setOnAction(e -> tanglegramView.setOptionHorizontalZoomFactor((1.0 / 1.1) * tanglegramView.getOptionHorizontalZoomFactor()));
		controller.getContractHorizontallyButton().disableProperty().bind(tanglegramView.emptyProperty().or(tanglegramView.optionHorizontalZoomFactorProperty().greaterThan(8.0 / 1.1)));

		controller.getExpandHorizontallyButton().setOnAction(e -> tanglegramView.setOptionHorizontalZoomFactor(1.1 * tanglegramView.getOptionHorizontalZoomFactor()));
		controller.getExpandHorizontallyButton().disableProperty().bind(tanglegramView.emptyProperty());

		controller.getExpandVerticallyButton().setOnAction(e -> tanglegramView.setOptionVerticalZoomFactor(1.1 * tanglegramView.getOptionVerticalZoomFactor()));
		controller.getExpandVerticallyButton().disableProperty().bind(tanglegramView.emptyProperty().or(tanglegramView.optionVerticalZoomFactorProperty().greaterThan(8.0 / 1.1)));

		controller.getContractVerticallyButton().setOnAction(e -> tanglegramView.setOptionVerticalZoomFactor((1.0 / 1.1) * tanglegramView.getOptionVerticalZoomFactor()));
		controller.getContractVerticallyButton().disableProperty().bind(tanglegramView.emptyProperty());

		Platform.runLater(this::setupMenuItems);
	}


	@Override
	public void setupMenuItems() {
		var mainController = mainWindow.getController();

		mainController.getCutMenuItem().disableProperty().bind(new SimpleBooleanProperty(true));
		mainController.getPasteMenuItem().disableProperty().bind(new SimpleBooleanProperty(true));

		mainWindow.getController().getIncreaseFontSizeMenuItem().setOnAction(e -> tanglegramView.setOptionFontScaleFactor(1.2 * tanglegramView.getOptionFontScaleFactor()));
		mainWindow.getController().getIncreaseFontSizeMenuItem().disableProperty().bind(tanglegramView.emptyProperty());
		mainWindow.getController().getDecreaseFontSizeMenuItem().setOnAction(e -> tanglegramView.setOptionFontScaleFactor((1.0 / 1.2) * tanglegramView.getOptionFontScaleFactor()));
		mainWindow.getController().getDecreaseFontSizeMenuItem().disableProperty().bind(tanglegramView.emptyProperty());

		mainController.getZoomInMenuItem().setOnAction(controller.getExpandVerticallyButton().getOnAction());
		mainController.getZoomInMenuItem().disableProperty().bind(controller.getExpandVerticallyButton().disableProperty());

		mainController.getZoomOutMenuItem().setOnAction(controller.getContractVerticallyButton().getOnAction());
		mainController.getZoomOutMenuItem().disableProperty().bind(controller.getContractVerticallyButton().disableProperty());

		mainController.getZoomInHorizontalMenuItem().setOnAction(controller.getExpandHorizontallyButton().getOnAction());
		mainController.getZoomInHorizontalMenuItem().disableProperty().bind(controller.getExpandHorizontallyButton().disableProperty());

		mainController.getZoomOutHorizontalMenuItem().setOnAction(controller.getContractHorizontallyButton().getOnAction());
		mainController.getZoomOutHorizontalMenuItem().disableProperty().bind(controller.getContractHorizontallyButton().disableProperty());

		mainController.getFindMenuItem().setOnAction(controller.getFindButton().getOnAction());
		mainController.getFindAgainMenuItem().setOnAction(e -> findToolBar.findAgain());
		mainController.getFindAgainMenuItem().disableProperty().bind(findToolBar.canFindAgainProperty().not());

		mainController.getSelectAllMenuItem().setOnAction(e -> mainWindow.getTaxonSelectionModel().selectAll(mainWindow.getWorkflow().getWorkingTaxaBlock().getTaxa()));
		mainController.getSelectNoneMenuItem().setOnAction(e -> mainWindow.getTaxonSelectionModel().clearSelection());
		mainController.getSelectNoneMenuItem().disableProperty().bind(mainWindow.getTaxonSelectionModel().sizeProperty().isEqualTo(0));

	}
}
