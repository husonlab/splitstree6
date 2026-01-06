/*
 *  TanglegramViewPresenter.java Copyright (C) 2024 Daniel H. Huson
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
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.collections.ObservableSet;
import javafx.geometry.Bounds;
import javafx.geometry.Dimension2D;
import javafx.geometry.Orientation;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;
import jloda.fx.control.RichTextLabel;
import jloda.fx.find.FindToolBar;
import jloda.fx.util.BasicFX;
import jloda.fx.util.ClipboardUtils;
import jloda.fx.util.RunAfterAWhile;
import jloda.fx.util.SwipeUtils;
import jloda.fx.window.NotificationManager;
import jloda.graph.Graph;
import jloda.graph.Node;
import jloda.phylo.PhyloTree;
import jloda.phylo.algorithms.RootedNetworkProperties;
import jloda.util.StringUtils;
import splitstree6.layout.tree.LabeledNodeShape;
import splitstree6.layout.tree.LayoutOrientation;
import splitstree6.layout.tree.LayoutUtils;
import splitstree6.layout.tree.TreeDiagramType;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.view.findreplace.FindReplaceTaxa;
import splitstree6.view.utils.ComboBoxUtils;
import splitstree6.view.utils.ExportUtils;
import splitstree6.window.MainWindow;

import java.util.ArrayList;
import java.util.stream.Collectors;

import static splitstree6.layout.tree.TreeDiagramType.*;

/**
 * tanglegram view presenter
 * Daniel Huson, 12.2021
 */
public class TanglegramViewPresenter implements IDisplayTabPresenter {
	private final MainWindow mainWindow;
	private final TanglegramView view;
	private final TanglegramViewController controller;

	private final ObjectProperty<PhyloTree> tree1 = new SimpleObjectProperty<>(this, "tree1");
	private final ObjectProperty<PhyloTree> tree2 = new SimpleObjectProperty<>(this, "tree2");

	private final BooleanProperty changingOrientation = new SimpleBooleanProperty(this, "changingOrientation", false);

	private final LongProperty updateRequested1 = new SimpleLongProperty(this, "updateRequested1", 0L);
	private final LongProperty updateRequested2 = new SimpleLongProperty(this, "updateRequested2", 0L);

	private final FindToolBar findToolBar;

	private final ObjectProperty<Dimension2D> treePaneDimensions = new SimpleObjectProperty<>(new Dimension2D(0, 0));

	public TanglegramViewPresenter(MainWindow mainWindow, TanglegramView view, ObjectProperty<Bounds> targetBounds, ObservableList<PhyloTree> trees) {
		this.mainWindow = mainWindow;
		this.view = view;
		controller = view.getController();

		view.getUndoManager().undoableProperty().addListener((v, o, n) -> {
			if (n)
				mainWindow.setDirty(true);
		});

		tree1.addListener((v, o, n) -> {
					controller.getTree1CBox().setValue(n == null ? null : n.getName());
			Platform.runLater(() -> setLabel(n, view.isOptionShowTreeNames(), view.isOptionShowTreeInfo(), controller.getTree1NameLabel()));
				}
		);

		final ObservableMap<Node, LabeledNodeShape> nodeShapeMap1 = FXCollections.observableHashMap();
		var tree1Pane = new TanglegramTreePane(mainWindow, view.getUndoManager(), mainWindow.getWorkflow().getWorkingTaxaBlock(), mainWindow.getTaxonSelectionModel(), tree1, treePaneDimensions,
				view.optionDiagram1Property(), view.optionLabelEdgesByProperty(), view.optionAveraging1Property(), view.optionOrientationProperty(), view.optionFontScaleFactorProperty(),
				nodeShapeMap1, updateRequested1);

		controller.getLeftPane().getChildren().add(tree1Pane);

		tree2.addListener((v, o, n) -> {
			controller.getTree2CBox().setValue(n == null ? null : n.getName());
			Platform.runLater(() -> setLabel(n, view.isOptionShowTreeNames(), view.isOptionShowTreeInfo(), controller.getTree2NameLabel()));
		});

		var orientation2Property = new SimpleStringProperty(this, "orientation2Property");
		view.optionOrientationProperty().addListener((v, o, n) -> {
			if (n.equals("Rotate0Deg")) {
				orientation2Property.set("FlipRotate0Deg");
			} else {
				orientation2Property.set("Rotate180Deg");
			}
		});
		orientation2Property.set(view.getOptionOrientation().equals("Rotate0Deg") ? "FlipRotate0Deg" : "Rotate180Deg");
		if (false)
			orientation2Property.addListener((v, o, n) -> {
			System.err.println("orientation2Property " + o + " -> " + n);
		});

		ObservableMap<Node, LabeledNodeShape> nodeShapeMap2 = FXCollections.observableHashMap();
		var tree2Pane = new TanglegramTreePane(mainWindow, view.getUndoManager(), mainWindow.getWorkflow().getWorkingTaxaBlock(), mainWindow.getTaxonSelectionModel(), tree2, treePaneDimensions,
				view.optionDiagram2Property(), view.optionLabelEdgesByProperty(), view.optionAveraging2Property(), orientation2Property, view.optionFontScaleFactorProperty(),
				nodeShapeMap2, updateRequested2);

		controller.getRightPane().getChildren().add(tree2Pane);

		final ObservableList<String> treeNames = FXCollections.observableArrayList();
		trees.addListener((InvalidationListener) e -> treeNames.setAll(trees.stream().map(Graph::getName).collect(Collectors.toList())));

		changingOrientation.bind(tree1Pane.changingOrientationProperty().or(tree2Pane.changingOrientationProperty()));

		{
			controller.getTree1CBox().setItems(treeNames);
			controller.getTree1CBox().disableProperty().bind(Bindings.isEmpty(trees));
			if (view.getOptionTree1() <= trees.size())
				controller.getTree1CBox().setValue(trees.get(view.getOptionTree1() - 1).getName());
			controller.getTree1CBox().valueProperty().addListener((v, o, n) -> {
				if (n != null)
					view.optionTree1Property().set(controller.getTree1CBox().getItems().indexOf(n) + 1);
			});

			controller.getTree2CBox().setItems(treeNames);
			controller.getTree2CBox().disableProperty().bind(Bindings.isEmpty(trees));
			if (view.getOptionTree2() <= trees.size())
				controller.getTree1CBox().setValue(trees.get(view.getOptionTree2() - 1).getName());
			controller.getTree2CBox().valueProperty().addListener((v, o, n) -> {
				if (n != null)
					view.optionTree2Property().set(controller.getTree2CBox().getItems().indexOf(n) + 1);
			});

			trees.addListener((InvalidationListener) e -> {
				if (controller.getTree1CBox().getValue() == null) {
					if (view.getOptionTree1() >= 1 && view.getOptionTree1() <= trees.size())
						controller.getTree1CBox().setValue(treeNames.get(view.getOptionTree1() - 1));
					else if (!trees.isEmpty())
						controller.getTree1CBox().setValue(treeNames.get(0));
				}
				if (controller.getTree2CBox().getValue() == null) {
					if (view.getOptionTree2() >= 1 && view.getOptionTree2() <= trees.size())
						controller.getTree2CBox().setValue(treeNames.get(view.getOptionTree2() - 1));
					else if (trees.size() >= 2)
						controller.getTree2CBox().setValue(treeNames.get(1));
				}
			});
		}


		controller.getTaxonDisplacementFirstCBox().selectedProperty().bindBidirectional(view.optionOptimizeTanglegramCrossings1Property());
		controller.getReticulateDisplacementFirstCBox().selectedProperty().bindBidirectional(view.optionOptimizeReticulateCrossings1Property());
		controller.getTaxonDisplacementSecondCBox().selectedProperty().bindBidirectional(view.optionOptimizeTanglegramCrossings2Property());
		controller.getReticulateDisplacementSecondCBox().selectedProperty().bindBidirectional(view.optionOptimizeReticulateCrossings2Property());
		controller.getUseNNPresortingCBox().selectedProperty().bindBidirectional(view.optionUsePQTreeProperty());
		tree1.addListener((v, o, n) -> {
			controller.getTaxonDisplacementFirstCBox().setDisable(n == null || n == tree2.get());
			controller.getReticulateDisplacementFirstCBox().setDisable(n == null || n == tree2.get() || !n.hasReticulateEdges());
		});
		tree2.addListener((v, o, n) -> {
			controller.getTaxonDisplacementSecondCBox().setDisable(n == null || n == tree1.get());
			controller.getReticulateDisplacementSecondCBox().setDisable(n == null || n == tree1.get() || !n.hasReticulateEdges());
		});

		// todo: don't run optimization when opening a previously saved file

		{
			Runnable runOptimization = () -> {
				var first = tree1.get();
				var second = tree2.get();
				if (first != null && second != null && first != second) {
					first.getLSAChildrenMap().clear();
					second.getLSAChildrenMap().clear();
					DoTanglegram.apply(mainWindow.getController().getBottomFlowPane(), first, second,
							view.getOptionOptimizeTanglegramCrossings1(),
							view.getOptionOptimizeReticulateCrossings1(),
							view.getOptionOptimizeTanglegramCrossings2(),
							view.getOptionOptimizeReticulateCrossings2(),
							view.getOptionUsePQTree(),
							r -> controller.getBorderPane().setDisable(r), () -> {
								updateRequested1.set(updateRequested1.get() + 1);
								updateRequested2.set(updateRequested2.get() + 1);
							},
							a -> NotificationManager.showError("Layout failed: " + a.getMessage()));
				}
			};

			{
				InvalidationListener listener1 = e -> {
					var t = view.getOptionTree1();
					if (t >= 1 && t <= trees.size()) {
						tree1.set(trees.get(t - 1));
						RunAfterAWhile.applyInFXThread(runOptimization, runOptimization);
					}
				};

				view.optionTree1Property().addListener(listener1);
				listener1.invalidated(null);

				InvalidationListener listener2 = e -> {
					var t = view.getOptionTree2();
					if (t >= 1 && t <= trees.size()) {
						tree2.set(trees.get(t - 1));
						RunAfterAWhile.applyInFXThread(runOptimization, runOptimization);
					}
				};
				view.optionTree2Property().addListener(listener2);
				listener2.invalidated(null);


				view.getTrees().addListener((InvalidationListener) e -> {
					listener1.invalidated(e);
					listener2.invalidated(e);
				});
			}

			view.optionOptimizeReticulateCrossings1Property().addListener(e -> RunAfterAWhile.applyInFXThread(runOptimization, runOptimization));
			view.optionOptimizeTanglegramCrossings1Property().addListener(e -> RunAfterAWhile.applyInFXThread(runOptimization, runOptimization));
			view.optionOptimizeReticulateCrossings2Property().addListener(e -> RunAfterAWhile.applyInFXThread(runOptimization, runOptimization));
			view.optionOptimizeTanglegramCrossings2Property().addListener(e -> RunAfterAWhile.applyInFXThread(runOptimization, runOptimization));
			view.optionUsePQTreeProperty().addListener(e -> RunAfterAWhile.applyInFXThread(runOptimization, runOptimization));
			controller.getUseNNPresortingCBox().disableProperty().bind((view.optionOptimizeReticulateCrossings1Property().not().and(view.optionOptimizeTanglegramCrossings1Property().not())).or(view.optionOptimizeReticulateCrossings2Property().not().and(view.optionOptimizeTanglegramCrossings2Property().not())));

			view.optionOptimizeReticulateCrossings1Property().addListener((v, o, n) -> view.getUndoManager().add("reticulate crossings", view.optionOptimizeReticulateCrossings1Property(), o, n));
			view.optionOptimizeTanglegramCrossings1Property().addListener((v, o, n) -> view.getUndoManager().add("tanglegram crossings", view.optionOptimizeTanglegramCrossings1Property(), o, n));
			view.optionOptimizeReticulateCrossings2Property().addListener((v, o, n) -> view.getUndoManager().add("reticulate crossings", view.optionOptimizeReticulateCrossings2Property(), o, n));
			view.optionOptimizeTanglegramCrossings2Property().addListener((v, o, n) -> view.getUndoManager().add("tanglegram crossings", view.optionOptimizeTanglegramCrossings2Property(), o, n));

			view.optionShowTreeNamesProperty().addListener(e -> {
				setLabel(tree1.get(), view.isOptionShowTreeNames(), view.isOptionShowTreeInfo(), controller.getTree1NameLabel());
				setLabel(tree2.get(), view.isOptionShowTreeNames(), view.isOptionShowTreeInfo(), controller.getTree2NameLabel());
			});
			view.optionShowTreeInfoProperty().addListener(e -> {
				setLabel(tree1.get(), view.isOptionShowTreeNames(), view.isOptionShowTreeInfo(), controller.getTree1NameLabel());
				setLabel(tree2.get(), view.isOptionShowTreeNames(), view.isOptionShowTreeInfo(), controller.getTree2NameLabel());
			});
		}

		{
			var connectors = new Connectors(mainWindow, controller.getMiddlePane(), controller.getLeftPane(), nodeShapeMap1, controller.getRightPane(), nodeShapeMap2,
					new SimpleObjectProperty<>(Color.DARKGRAY), new SimpleDoubleProperty(1.0));
			tree1Pane.setRunAfterUpdate(connectors::update);
			tree2Pane.setRunAfterUpdate(connectors::update);
			view.optionFontScaleFactorProperty().addListener(e -> connectors.update());

			view.optionOrientationProperty().addListener((v, o, n) -> {
				LayoutUtils.applyOrientation(controller.getMiddlePane(), LayoutOrientation.valueOf(n), LayoutOrientation.valueOf(o), node -> false, new SimpleBooleanProperty(false), connectors::update, true);
			});
		}

		{
			final ObservableSet<TreeDiagramType> disabledDiagrams1 = FXCollections.observableSet();
			tree1.addListener((v, o, n) -> {
				disabledDiagrams1.clear();
				if (tree1.get() != null && tree1.get().isReticulated()) {
					disabledDiagrams1.add(TriangularCladogram);
					if (view.getOptionDiagram1() == TriangularCladogram)
						view.optionDiagram1Property().set(RectangularCladogram);
				}
			});

			controller.getDiagram1CBox().setButtonCell(ComboBoxUtils.createButtonCell(disabledDiagrams1, TreeDiagramType::icon, false));
			controller.getDiagram1CBox().setCellFactory(ComboBoxUtils.createCellFactory(disabledDiagrams1, TreeDiagramType::icon, false));
			controller.getDiagram1CBox().getItems().addAll(RectangularPhylogram, RectangularCladogram, TriangularCladogram);
			controller.getDiagram1CBox().setValue(view.getOptionDiagram1());
			view.optionDiagram1Property().addListener((v, o, n) -> controller.getDiagram1CBox().setValue(n));
			controller.getDiagram1CBox().valueProperty().addListener((v, o, n) -> {
				view.setOptionOrientation("Rotate0Deg");
				view.optionDiagram1Property().set(n);
			});
		}
		{
			final ObservableSet<TreeDiagramType> disabledDiagrams2 = FXCollections.observableSet();
			tree2.addListener((v, o, n) -> {
				disabledDiagrams2.clear();
				if (tree2.get() != null && tree2.get().isReticulated()) {
					disabledDiagrams2.add(TreeDiagramType.TriangularCladogram);
					if (view.getOptionDiagram2() == TriangularCladogram)
						view.optionDiagram2Property().set(RectangularCladogram);
				}
			});

			controller.getDiagram2CBox().setButtonCell(ComboBoxUtils.createButtonCell(disabledDiagrams2, TreeDiagramType::icon, true));
			controller.getDiagram2CBox().setCellFactory(ComboBoxUtils.createCellFactory(disabledDiagrams2, TreeDiagramType::icon, true));
			controller.getDiagram2CBox().getItems().addAll(RectangularPhylogram, RectangularCladogram, TriangularCladogram);
			controller.getDiagram2CBox().setValue(view.getOptionDiagram2());
			view.optionDiagram2Property().addListener((v, o, n) -> controller.getDiagram2CBox().setValue(n));
			controller.getDiagram2CBox().valueProperty().addListener((v, o, n) -> {
				view.setOptionOrientation("Rotate0Deg");
				view.optionDiagram2Property().set(n);
			});
		}

		controller.getFlipButton().setOnAction(e -> {
			if (view.getOptionOrientation().equals("Rotate0Deg"))
				view.setOptionOrientation("FlipRotate180Deg");
			else
				view.setOptionOrientation("Rotate0Deg");
		});
		controller.getFlipButton().disableProperty().bind(view.emptyProperty());

		{
			var labelProperty = new SimpleStringProperty();
			var defaultState = (view.isOptionShowTreeInfo() ? "d" : view.isOptionShowTreeNames() ? "n" : null);
			BasicFX.makeMultiStateToggle(controller.getShowTreeNamesToggleButton(), defaultState, labelProperty, "-", "n", "d");
			labelProperty.addListener((v, o, n) -> {
				view.setOptionShowTreeNames("n".equals(n) || "d".equals(n));
				view.setOptionShowTreeInfo("d".equals(n));
			});
		}

		controller.getPreviousButton().setOnAction(e -> {
			view.getUndoManager().setRecordChanges(false);
			try {
				view.setOptionTree1(view.getOptionTree1() - 1);
				view.setOptionTree2(view.getOptionTree2() - 1);
			} finally {
				view.getUndoManager().setRecordChanges(true);
				view.getUndoManager().add("previous", () -> {
					view.setOptionTree1(view.getOptionTree1() + 1);
					view.setOptionTree2(view.getOptionTree2() + 1);
				}, () -> {
					view.setOptionTree1(view.getOptionTree1() - 1);
					view.setOptionTree2(view.getOptionTree2() - 1);
				});
			}
		});
		controller.getPreviousButton().disableProperty().bind(view.optionTree1Property().lessThanOrEqualTo(1).or(view.optionTree2Property().lessThanOrEqualTo(1)));

		controller.getNextButton().setOnAction(e -> {
			try {
				view.getUndoManager().setRecordChanges(false);
				view.setOptionTree1(view.getOptionTree1() + 1);
				view.setOptionTree2(view.getOptionTree2() + 1);
			} finally {
				view.getUndoManager().setRecordChanges(true);
				view.getUndoManager().add("next", () -> {
					view.setOptionTree1(view.getOptionTree1() - 1);
					view.setOptionTree2(view.getOptionTree2() - 1);
				}, () -> {
					view.setOptionTree1(view.getOptionTree1() + 1);
					view.setOptionTree2(view.getOptionTree2() + 1);
				});
			}

		});
		controller.getNextButton().disableProperty().bind(view.optionTree1Property().greaterThanOrEqualTo(Bindings.size(trees)).or(view.optionTree2Property().greaterThanOrEqualTo(Bindings.size(trees))));

		InvalidationListener updateDimensions = e -> {
			var bounds = targetBounds.get();
			var middleWidth = Math.min(300, Math.max(40, 0.2 * bounds.getWidth()));
			var treePaneWidth = (0.5 * bounds.getWidth() - middleWidth);
			var height = bounds.getHeight() - 200;
			controller.getMiddlePane().setPrefWidth(middleWidth);
			controller.getMiddlePane().setPrefHeight(height);
			treePaneDimensions.set(new Dimension2D(view.getOptionHorizontalZoomFactor() * treePaneWidth, view.getOptionVerticalZoomFactor() * height));
		};

		targetBounds.addListener(updateDimensions);
		view.optionVerticalZoomFactorProperty().addListener(updateDimensions);
		view.optionHorizontalZoomFactorProperty().addListener(updateDimensions);

		findToolBar = FindReplaceTaxa.create(mainWindow, view.getUndoManager());
		findToolBar.setShowFindToolBar(false);
		controller.getvBox().getChildren().add(findToolBar);

		// FindReplaceUtils.setup(findToolBar, controller.getFindToggleButton(), true);

		var undoManager = view.getUndoManager();
		view.optionTree1Property().addListener((v, o, n) -> undoManager.add("tree 1", view.optionTree1Property(), o, n));
		view.optionTree2Property().addListener((v, o, n) -> undoManager.add("tree 2", view.optionTree2Property(), o, n));
		view.optionDiagram1Property().addListener((v, o, n) -> undoManager.add("diagram type 1", view.optionDiagram1Property(), o, n));
		view.optionDiagram2Property().addListener((v, o, n) -> undoManager.add("diagram type 2", view.optionDiagram2Property(), o, n));
		view.optionOrientationProperty().addListener((v, o, n) -> undoManager.add("layout orientation", view.optionOrientationProperty(), o, n));
		view.optionFontScaleFactorProperty().addListener((v, o, n) -> undoManager.add("font size", view.optionFontScaleFactorProperty(), o, n));
		view.optionHorizontalZoomFactorProperty().addListener((v, o, n) -> undoManager.add("horizontal zoom", view.optionHorizontalZoomFactorProperty(), o, n));
		view.optionVerticalZoomFactorProperty().addListener((v, o, n) -> undoManager.add("vertical zoom", view.optionVerticalZoomFactorProperty(), o, n));
		view.optionShowTreeNamesProperty().addListener((v, o, n) -> undoManager.add("show tree names", view.optionShowTreeNamesProperty(), o, n));
		view.optionShowTreeInfoProperty().addListener((v, o, n) -> undoManager.add("show tree info", view.optionShowTreeInfoProperty(), o, n));

		controller.getContractHorizontallyButton().setOnAction(e -> view.setOptionHorizontalZoomFactor((1.0 / 1.1) * view.getOptionHorizontalZoomFactor()));
		controller.getContractHorizontallyButton().disableProperty().bind(view.emptyProperty().or(view.optionHorizontalZoomFactorProperty().greaterThan(128)));

		controller.getExpandHorizontallyButton().setOnAction(e -> view.setOptionHorizontalZoomFactor(1.1 * view.getOptionHorizontalZoomFactor()));
		controller.getExpandHorizontallyButton().disableProperty().bind(view.emptyProperty());

		controller.getExpandVerticallyButton().setOnAction(e -> view.setOptionVerticalZoomFactor(1.1 * view.getOptionVerticalZoomFactor()));
		controller.getExpandVerticallyButton().disableProperty().bind(view.emptyProperty().or(view.optionVerticalZoomFactorProperty().greaterThan(128)));

		controller.getContractVerticallyButton().setOnAction(e -> view.setOptionVerticalZoomFactor((1.0 / 1.1) * view.getOptionVerticalZoomFactor()));
		controller.getContractVerticallyButton().disableProperty().bind(view.emptyProperty());

		controller.getExpandCollapseVerticallyButton().setOnAction(e -> {
			if (tree1Pane.getTreePane() != null && tree2Pane.getTreePane() != null) {
				var minLabelHeight1 = tree1Pane.getTreePane().getMinLabelHeight();
				var minLabelHeight2 = tree2Pane.getTreePane().getMinLabelHeight();

				if (minLabelHeight1.isPresent() && minLabelHeight2.isPresent()) {
					var minHeight = Math.min(minLabelHeight1.getAsDouble(), minLabelHeight2.getAsDouble());
					if (minHeight < 10) {
						var factor = 14.0 / minHeight;
						if (factor > 0) {
							view.setOptionFontScaleFactor(factor * view.getOptionFontScaleFactor());
							view.setOptionVerticalZoomFactor(factor * view.getOptionVerticalZoomFactor());
						}
					} else {
						var scrollBar = BasicFX.getScrollBar(controller.getScrollPane(), Orientation.VERTICAL);
						if (scrollBar != null) {
							var total = (scrollBar.getMax() - scrollBar.getMin());
							var extent = scrollBar.getVisibleAmount();
							var factor = extent / total;
							if (factor > 0) {
								view.setOptionFontScaleFactor(factor * view.getOptionFontScaleFactor());
								view.setOptionVerticalZoomFactor(factor * view.getOptionVerticalZoomFactor());
							}
						}
					}
				}
			}
		});
		controller.getExpandCollapseVerticallyButton().disableProperty().bind(view.emptyProperty());

		view.viewTabProperty().addListener((v, o, n) -> {
			if (n != null) {
				controller.getvBox().getChildren().add(0, n.getAlgorithmBreadCrumbsToolBar());
			}
		});
		view.emptyProperty().addListener(e -> view.getRoot().setDisable(view.emptyProperty().get()));

		SwipeUtils.setOnSwipeLeft(controller.getAnchorPane(), () -> controller.getNextButton().fire());
		SwipeUtils.setOnSwipeRight(controller.getAnchorPane(), () -> controller.getNextButton().fire());
		SwipeUtils.setOnSwipeUp(controller.getAnchorPane(), () -> controller.getFlipButton().fire());
		SwipeUtils.setOnSwipeDown(controller.getAnchorPane(), () -> controller.getFlipButton().fire());

		Platform.runLater(this::setupMenuItems);
	}

	// todo: make this the same as in single tree
	private static void setLabel(PhyloTree tree, boolean showName, boolean showInfo, TextField label) {
		if (tree != null && (showName || showInfo)) {
			label.setText((showName ? tree.getName() : "") + (showName && showInfo ? " : " : "") + (showInfo ? RootedNetworkProperties.computeInfoString(tree) : ""));
			label.setVisible(true);
		} else
			label.setVisible(false);
	}


	@Override
	public void setupMenuItems() {
		var mainController = mainWindow.getController();

		mainController.getCutMenuItem().disableProperty().bind(new SimpleBooleanProperty(true));
		mainController.getPasteMenuItem().disableProperty().bind(new SimpleBooleanProperty(true));

		mainController.getCopyMenuItem().setOnAction(e -> {
			var list = new ArrayList<String>();
			for (var taxon : mainWindow.getTaxonSelectionModel().getSelectedItems()) {
				list.add(RichTextLabel.getRawText(taxon.getDisplayLabelOrName()).trim());
			}
			if (!list.isEmpty()) {
				ClipboardUtils.putString(StringUtils.toString(list, "\n"));
			} else {
				mainWindow.getController().getCopyNewickMenuItem().fire();
			}
		});
		mainController.getCopyMenuItem().disableProperty().bind(view.emptyProperty());

		mainWindow.getController().getCopyNewickMenuItem().setOnAction(e -> {
			var buf = new StringBuilder();
			if (tree1.get() != null)
				buf.append(tree1.get().toBracketString(true)).append(";\n");
			if (tree2.get() != null)
				buf.append(tree2.get().toBracketString(true)).append(";\n");
			ClipboardUtils.putString(buf.toString());
		});
		mainWindow.getController().getCopyNewickMenuItem().disableProperty().bind(view.emptyProperty());

		mainWindow.getController().getIncreaseFontSizeMenuItem().setOnAction(e -> view.setOptionFontScaleFactor(1.2 * view.getOptionFontScaleFactor()));
		mainWindow.getController().getIncreaseFontSizeMenuItem().disableProperty().bind(view.emptyProperty());
		mainWindow.getController().getDecreaseFontSizeMenuItem().setOnAction(e -> view.setOptionFontScaleFactor((1.0 / 1.2) * view.getOptionFontScaleFactor()));
		mainWindow.getController().getDecreaseFontSizeMenuItem().disableProperty().bind(view.emptyProperty());

		mainController.getZoomInMenuItem().setOnAction(controller.getExpandVerticallyButton().getOnAction());
		mainController.getZoomInMenuItem().disableProperty().bind(controller.getExpandVerticallyButton().disableProperty());

		mainController.getZoomOutMenuItem().setOnAction(controller.getContractVerticallyButton().getOnAction());
		mainController.getZoomOutMenuItem().disableProperty().bind(controller.getContractVerticallyButton().disableProperty());

		mainController.getZoomInHorizontalMenuItem().setOnAction(controller.getExpandHorizontallyButton().getOnAction());
		mainController.getZoomInHorizontalMenuItem().disableProperty().bind(controller.getExpandHorizontallyButton().disableProperty());

		mainController.getZoomOutHorizontalMenuItem().setOnAction(controller.getContractHorizontallyButton().getOnAction());
		mainController.getZoomOutHorizontalMenuItem().disableProperty().bind(controller.getContractHorizontallyButton().disableProperty());

		mainController.getFlipMenuItem().setOnAction(controller.getFlipButton().getOnAction());
		mainController.getFlipMenuItem().disableProperty().bind(controller.getFlipButton().disableProperty());

		ExportUtils.setup(mainWindow, view.getViewTab().getDataNode(), view.emptyProperty());
	}

	public FindToolBar getFindToolBar() {
		return findToolBar;
	}

	@Override
	public boolean allowFindReplace() {
		return true;
	}
}
