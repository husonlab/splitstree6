/*
 *  TreeViewPresenter.java Copyright (C) 2023 Daniel H. Huson
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

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import javafx.collections.SetChangeListener;
import javafx.collections.WeakSetChangeListener;
import javafx.geometry.Bounds;
import javafx.geometry.Dimension2D;
import javafx.geometry.Insets;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.layout.Pane;
import jloda.fx.control.RichTextLabel;
import jloda.fx.control.ZoomableScrollPane;
import jloda.fx.find.FindToolBar;
import jloda.fx.undo.UndoManager;
import jloda.fx.util.BasicFX;
import jloda.fx.util.RunAfterAWhile;
import jloda.graph.Graph;
import jloda.phylo.PhyloTree;
import jloda.util.Basic;
import jloda.util.CanceledException;
import jloda.util.Single;
import jloda.util.StringUtils;
import jloda.util.progress.ProgressSilent;
import splitstree6.data.parts.Taxon;
import splitstree6.layout.LayoutUtils;
import splitstree6.layout.tree.HeightAndAngles;
import splitstree6.layout.tree.TreeDiagramType;
import splitstree6.layout.tree.TreeLabel;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.view.findreplace.FindReplaceTaxa;
import splitstree6.view.format.edges.LabelEdgesBy;
import splitstree6.view.trees.tanglegram.optimize.EmbeddingOptimizer;
import splitstree6.view.trees.treepages.TreePane;
import splitstree6.view.utils.ComboBoxUtils;
import splitstree6.view.utils.ExportUtils;
import splitstree6.window.MainWindow;

import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * single tree presenter
 * Daniel Huson 3.2022
 */
public class TreeViewPresenter implements IDisplayTabPresenter {
	private final LongProperty updateCounter = new SimpleLongProperty(0L);

	private final MainWindow mainWindow;
	private final TreeView view;
	private final TreeViewController controller;

	private final FindToolBar findToolBar;

	private final ObjectProperty<TreePane> treePane = new SimpleObjectProperty<>(this, "treePane");
	private final ObjectProperty<PhyloTree> tree;

	private final BooleanProperty changingOrientation = new SimpleBooleanProperty(this, "changingOrientation", false);

	private final InvalidationListener updateListener;

	private final BooleanProperty showScaleBar = new SimpleBooleanProperty(true);

	private final SetChangeListener<Taxon> selectionChangeListener;

	/**
	 * the tree view presenter
	 *
	 * @param mainWindow
	 * @param view
	 * @param targetBounds
	 */
	public TreeViewPresenter(MainWindow mainWindow, TreeView view, ObjectProperty<Bounds> targetBounds) {
		this.mainWindow = mainWindow;
		this.view = view;
		this.controller = view.getController();
		this.tree = view.treeProperty();

		view.optionTreeProperty().addListener((v, o, n) -> {
			var nTree = n.intValue();
			if (nTree > 0 && nTree <= view.getTrees().size())
				tree.set(view.getTrees().get(nTree - 1));
			else
				tree.set(null);
		});

		view.getTrees().addListener((InvalidationListener) e -> {
			controller.getTreeCBox().getItems().setAll(view.getTrees().stream().map(Graph::getName).collect(Collectors.toList()));
			if (view.getOptionTree() == 0 && view.getTrees().size() > 0)
				view.setOptionTree(1);
			if (view.getOptionTree() >= 1 && view.getOptionTree() <= view.getTrees().size()) {
				controller.getTreeCBox().setValue(controller.getTreeCBox().getItems().get(view.getOptionTree() - 1));
				tree.set(view.getTrees().get(view.getOptionTree() - 1));
			}

			Platform.runLater(() -> {
				try {
					if (view.getOptionLabelEdgesBy() == LabelEdgesBy.None) {
						var dirty = mainWindow.isDirty();
						if (tree.get().hasEdgeConfidences())
							view.setOptionLabelEdgesBy(LabelEdgesBy.Confidence);
						else if (tree.get().hasEdgeProbabilities())
							view.setOptionLabelEdgesBy(LabelEdgesBy.Probability);
						mainWindow.setDirty(dirty);
					}
				} catch (Exception ex) {
					Basic.caught(ex);
				}
			});
		});

		view.optionTreeProperty().addListener((v, o, n) -> {
			var index = n.intValue() - 1;
			if (index >= 0 && index < controller.getTreeCBox().getItems().size())
				controller.getTreeCBox().setValue(controller.getTreeCBox().getItems().get(index));
		});

		controller.getTreeCBox().valueProperty().addListener((v, o, n) -> {
			if (n != null) {
				var i = controller.getTreeCBox().getItems().indexOf(n);
				if (i >= 0 && i < view.size()) {
					view.setOptionTree(i + 1);
					return;
				}
			}
			view.setOptionTree(0);
		});

		controller.getNextButton().setOnAction(e -> view.setOptionTree(view.getOptionTree() + 1));
		controller.getNextButton().disableProperty().bind(Bindings.size(view.getTrees()).isEqualTo(0).or(view.optionTreeProperty().greaterThanOrEqualTo(Bindings.size(view.getTrees()))));

		controller.getPreviousButton().setOnAction(e -> view.setOptionTree(view.getOptionTree() - 1));
		controller.getPreviousButton().disableProperty().bind(Bindings.size(view.getTrees()).isEqualTo(0).or(view.optionTreeProperty().lessThanOrEqualTo(1)));

		{
			var labelProperty = new SimpleStringProperty();
			BasicFX.makeMultiStateToggle(controller.getShowTreeNamesToggleButton(), view.getOptionTreeLabels().label(), labelProperty, TreeLabel.labels());
			labelProperty.addListener((v, o, n) -> view.setOptionTreeLabels(TreeLabel.valueOfLabel(n)));
		}
		view.optionTreeLabelsProperty().addListener((v, o, n) -> TreeLabel.setLabel(tree.get(), n, controller.getTreeNameLabel()));
		tree.addListener((v, o, n) -> TreeLabel.setLabel(n, view.getOptionTreeLabels(), controller.getTreeNameLabel()));
		TreeLabel.setLabel(tree.get(), view.getOptionTreeLabels(), controller.getTreeNameLabel());

		var toScale = new SimpleBooleanProperty(this, "toScale", view.getOptionDiagram().isPhylogram());
		var lockAspectRatio = new SimpleBooleanProperty(this, "lockAspectRatio");

		view.optionDiagramProperty().addListener((v, o, n) -> {
			toScale.set(n.isPhylogram());
			lockAspectRatio.set(n.isRadialOrCircular());
		});
		lockAspectRatio.set(view.getOptionDiagram() != null && view.getOptionDiagram().isRadialOrCircular());
		toScale.set(view.getOptionDiagram() != null && view.getOptionDiagram().isPhylogram());

		var scrollPane = controller.getScrollPane();

		scrollPane.lockAspectRatioProperty().bind(lockAspectRatio);
		scrollPane.setRequireShiftOrControlToZoom(false);
		controller.getScrollPane().setPannable(true);

		scrollPane.setPadding(new Insets(10, 0, 0, 10));

		final ObservableSet<TreeDiagramType> disabledDiagrams = FXCollections.observableSet();
		view.reticulatedProperty().addListener((v, o, n) -> {
			disabledDiagrams.clear();
			if (n) {
				disabledDiagrams.add(TreeDiagramType.TriangularCladogram);
				disabledDiagrams.add(TreeDiagramType.RadialCladogram);
				disabledDiagrams.add(TreeDiagramType.RadialPhylogram);
			}
		});
		controller.getDiagramCBox().setButtonCell(ComboBoxUtils.createButtonCell(disabledDiagrams, TreeDiagramType::icon));
		controller.getDiagramCBox().setCellFactory(ComboBoxUtils.createCellFactory(disabledDiagrams, TreeDiagramType::icon));
		controller.getDiagramCBox().getItems().addAll(TreeDiagramType.values());
		controller.getDiagramCBox().valueProperty().bindBidirectional(view.optionDiagramProperty());

		controller.getRotateLeftButton().setOnAction(e -> view.setOptionOrientation(view.getOptionOrientation().getRotateLeft()));
		controller.getRotateLeftButton().disableProperty().bind(view.emptyProperty().or(view.emptyProperty()));
		controller.getRotateRightButton().setOnAction(e -> view.setOptionOrientation(view.getOptionOrientation().getRotateRight()));
		controller.getRotateRightButton().disableProperty().bind(controller.getRotateLeftButton().disableProperty());
		controller.getFlipButton().setOnAction(e -> view.setOptionOrientation(view.getOptionOrientation().getFlip()));
		controller.getFlipButton().disableProperty().bind(controller.getRotateLeftButton().disableProperty());

		controller.getScaleBar().visibleProperty().bind(toScale.and(showScaleBar));

		{
			final InvalidationListener scaleListener = e -> {
				if (treePane.get() == null) {
					controller.getScaleBar().factorXProperty().unbind();
					changingOrientation.unbind();
				} else {
					controller.getScaleBar().factorXProperty().bind(view.getOptionOrientation().isWidthHeightSwitched() ? treePane.get().scaleYProperty() : treePane.get().scaleXProperty());
					changingOrientation.bind(treePane.get().changingOrientationProperty());
				}
			};
			treePane.addListener(scaleListener);
			view.optionOrientationProperty().addListener(scaleListener);
			scaleListener.invalidated(null);
		}


		tree.addListener((v, o, n) -> {
			if (o != null) // not the first tree
				TreeViewEdits.clearEdits(view.optionEditsProperty());
		});


		updateListener = e -> {
			if (tree.get() != null) {
				RunAfterAWhile.apply(tree.get().getName(), () -> Platform.runLater(() -> {
					var tree = this.tree.get();
					if (tree.isReticulated()) {
						tree = new PhyloTree(tree);
						try {
							if (true)
								EmbeddingOptimizer.apply(tree, new ProgressSilent());
							else
								TreeEmbeddingOptimizer.apply(tree, new ProgressSilent());
						} catch (CanceledException ignored) {
						}
					}
					var bounds = targetBounds.get();
					if (bounds != null) {
						var width = bounds.getWidth();
						var height = bounds.getHeight();
						if (lockAspectRatio.get())
							width = height = Math.min(width, height);
						var box = new Dimension2D(view.getOptionHorizontalZoomFactor() * width - 100, view.getOptionVerticalZoomFactor() * height - 150);

						if (!view.emptyProperty().get()) {
							var pane = new TreePane(mainWindow.getStage(), mainWindow.getWorkflow().getWorkingTaxaBlock(), tree, mainWindow.getTaxonSelectionModel(), box.getWidth(), box.getHeight(),
									view.getOptionDiagram(), view.getOptionLabelEdgesBy(), view.getOptionAveraging(), view.optionOrientationProperty(), view.optionFontScaleFactorProperty(), null,
									controller.getScaleBar().unitLengthXProperty(), view.getNodeShapeMap(), view.getEdgeShapeMap());
							view.setEdgeSelectionModel(pane.getEdgeSelectionModel());
							treePane.set(pane);
							pane.setRunAfterUpdate(() -> {
								if (view.getOptionEdits().length > 0) {
									TreeEdits.applyEdits(view.getOptionEdits(), view.getEdgeShapeMap());
									if (false)
										Platform.runLater(() -> TreeEdits.clearEdits(view.optionEditsProperty()));
								}
								updateCounter.set(updateCounter.get() + 1);
							});
							pane.drawTree();

							scrollPane.setContent(pane);
						} else {
							treePane.set(null);
							updateCounter.set(updateCounter.get() + 1);
							scrollPane.setContent(new Pane());
						}
					}
				}));
			}
		};

		scrollPane.setUpdateScaleMethod(() -> {
			Platform.runLater(() -> {
				view.setOptionHorizontalZoomFactor(scrollPane.getZoomFactorX() * view.getOptionHorizontalZoomFactor());
				view.setOptionVerticalZoomFactor(scrollPane.getZoomFactorY() * view.getOptionVerticalZoomFactor());
			});
		});

		view.optionHorizontalZoomFactorProperty().addListener((v, o, n) -> {
			if (treePane.get() != null) {
				treePane.get().setScaleX(treePane.get().getScaleX() / o.doubleValue() * n.doubleValue());
				if (!lockAspectRatio.get())
					updateListener.invalidated(null);
			}
		});

		view.optionVerticalZoomFactorProperty().addListener((v, o, n) -> {
			if (treePane.get() != null) {
				treePane.get().setScaleY(treePane.get().getScaleY() / o.doubleValue() * n.doubleValue());
				updateListener.invalidated(null);
			}
		});

		if (false)
			view.optionFontScaleFactorProperty().addListener(e -> {
				//if (treePane.get() != null)
				//	ProgramExecutorService.submit(100, () -> Platform.runLater(() -> treePane.get().layoutLabels(treeView.getOptionOrientation())));
			});

		view.getTrees().addListener(updateListener);
		view.optionTreeProperty().addListener(updateListener);
		view.optionDiagramProperty().addListener(updateListener);
		view.optionLabelEdgesByProperty().addListener(updateListener);

		final ObservableSet<HeightAndAngles.Averaging> disabledAveraging = FXCollections.observableSet();
		view.optionDiagramProperty().addListener((v, o, n) -> {
			disabledAveraging.clear();
			if (n == TreeDiagramType.RadialPhylogram) {
				disabledAveraging.add(HeightAndAngles.Averaging.ChildAverage);
			}
		});

		controller.getAveragingCBox().setButtonCell(ComboBoxUtils.createButtonCell(disabledAveraging, HeightAndAngles.Averaging::createLabel));
		controller.getAveragingCBox().setCellFactory(ComboBoxUtils.createCellFactory(disabledAveraging, HeightAndAngles.Averaging::createLabel));
		controller.getAveragingCBox().getItems().addAll(HeightAndAngles.Averaging.values());
		controller.getAveragingCBox().valueProperty().bindBidirectional(view.optionAveragingProperty());
		view.optionAveragingProperty().addListener(updateListener);

		controller.getContractHorizontallyButton().setOnAction(e -> view.setOptionHorizontalZoomFactor((1.0 / 1.1) * view.getOptionHorizontalZoomFactor()));
		controller.getContractHorizontallyButton().disableProperty().bind(view.emptyProperty().or(lockAspectRatio));

		controller.getExpandHorizontallyButton().setOnAction(e -> view.setOptionHorizontalZoomFactor(1.1 * view.getOptionHorizontalZoomFactor()));
		controller.getExpandHorizontallyButton().disableProperty().bind(view.emptyProperty().or(lockAspectRatio).or(view.optionHorizontalZoomFactorProperty().greaterThan(8.0 / 1.1)));

		controller.getExpandVerticallyButton().setOnAction(e -> {
			view.setOptionVerticalZoomFactor(1.1 * view.getOptionVerticalZoomFactor());
			if (lockAspectRatio.get())
				view.setOptionHorizontalZoomFactor(1.1 * view.getOptionHorizontalZoomFactor());
		});
		controller.getExpandVerticallyButton().disableProperty().bind(view.emptyProperty().or(view.optionVerticalZoomFactorProperty().greaterThan(8.0 / 1.1)));

		controller.getContractVerticallyButton().setOnAction(e -> {
			view.setOptionVerticalZoomFactor((1.0 / 1.1) * view.getOptionVerticalZoomFactor());
			if (lockAspectRatio.get())
				view.setOptionHorizontalZoomFactor((1.0 / 1.1) * view.getOptionHorizontalZoomFactor());
		});
		controller.getContractVerticallyButton().disableProperty().bind(view.emptyProperty());

		controller.getIncreaseFontButton().setOnAction(e -> view.setOptionFontScaleFactor(1.2 * view.getOptionFontScaleFactor()));
		controller.getIncreaseFontButton().disableProperty().bind(view.emptyProperty());
		controller.getDecreaseFontButton().setOnAction(e -> view.setOptionFontScaleFactor((1.0 / 1.2) * view.getOptionFontScaleFactor()));
		controller.getDecreaseFontButton().disableProperty().bind(view.emptyProperty());

		findToolBar = FindReplaceTaxa.create(mainWindow, view.getUndoManager());
		findToolBar.setShowFindToolBar(false);
		controller.getvBox().getChildren().add(findToolBar);

		// FindReplaceUtils.setup(findToolBar, controller.getFindToggleButton(), true);

		view.viewTabProperty().addListener((v, o, n) -> {
			if (n != null) {
				controller.getvBox().getChildren().add(0, n.getAlgorithmBreadCrumbsToolBar());
			}
		});
		view.emptyProperty().addListener(e -> view.getRoot().setDisable(view.emptyProperty().get()));

		var undoManager = view.getUndoManager();

		view.optionDiagramProperty().addListener((v, o, n) -> undoManager.add("diagram", view.optionDiagramProperty(), o, n));
		view.optionTreeLabelsProperty().addListener((v, o, n) -> undoManager.add("show tree names", view.optionTreeLabelsProperty(), o, n));
		view.optionAveragingProperty().addListener((v, o, n) -> undoManager.add("node averaging", view.optionAveragingProperty(), o, n));
		view.optionOrientationProperty().addListener((v, o, n) -> undoManager.add("orientation", view.optionOrientationProperty(), o, n));
		view.optionFontScaleFactorProperty().addListener((v, o, n) -> undoManager.add("font size", view.optionFontScaleFactorProperty(), o, n));

		//view.optionHorizontalZoomFactorProperty().addListener((v, o, n) -> undoManager.add("horizontal zoom", view.optionHorizontalZoomFactorProperty(), o, n));
		//view.optionVerticalZoomFactorProperty().addListener((v, o, n) -> undoManager.add("vertical zoom", view.optionVerticalZoomFactorProperty(), o, n));

		// treeView.optionShowTreeNamesProperty().addListener((v, o, n) -> undoManager.add("show tree names", treeView.optionShowTreeNamesProperty(), o, n));
		// treeView.optionShowTreeInfoProperty().addListener((v, o, n) -> undoManager.add("show tree info", treeView.optionShowTreeInfoProperty(), o, n));

		var object = new Object();
		selectionChangeListener = e -> {
			if (e.wasAdded()) {
				RunAfterAWhile.applyInFXThreadOrClearIfAlreadyWaiting(object, () -> {
					var taxon = e.getElementAdded();
					var v = tree.get().getTaxon2Node(mainWindow.getWorkingTaxa().indexOf(taxon));
					var node = view.getNodeShapeMap().get(v);
					controller.getScrollPane().ensureVisible(node);
				});
			}

		};
		mainWindow.getTaxonSelectionModel().getSelectedItems().addListener(new WeakSetChangeListener<>(selectionChangeListener));

		Platform.runLater(this::setupMenuItems);
		updateListener.invalidated(null);
	}

	public void setupMenuItems() {
		var mainController = mainWindow.getController();

		mainController.getCopyMenuItem().setOnAction(e -> {
			var list = new ArrayList<String>();
			for (var taxon : mainWindow.getTaxonSelectionModel().getSelectedItems()) {
				list.add(RichTextLabel.getRawText(taxon.getDisplayLabelOrName()).trim());
			}
			if (!list.isEmpty()) {
				var content = new ClipboardContent();
				content.put(DataFormat.PLAIN_TEXT, StringUtils.toString(list, "\n"));
				Clipboard.getSystemClipboard().setContent(content);
			}
		});
		mainController.getCopyMenuItem().disableProperty().bind(mainWindow.getTaxonSelectionModel().sizeProperty().isEqualTo(0));

		mainController.getCutMenuItem().disableProperty().bind(new SimpleBooleanProperty(true));


		mainWindow.getController().getCopyNewickMenuItem().setOnAction(e -> {
			var tree = this.tree.get();
			if (tree != null)
				BasicFX.putTextOnClipBoard(tree.toBracketString(true) + ";\n");
		});
		mainWindow.getController().getCopyNewickMenuItem().disableProperty().bind(view.emptyProperty());

		mainController.getPasteMenuItem().disableProperty().bind(new SimpleBooleanProperty(true));

		mainWindow.getController().getIncreaseFontSizeMenuItem().setOnAction(controller.getIncreaseFontButton().getOnAction());
		mainWindow.getController().getIncreaseFontSizeMenuItem().disableProperty().bind(controller.getIncreaseFontButton().disableProperty());
		mainWindow.getController().getDecreaseFontSizeMenuItem().setOnAction(controller.getDecreaseFontButton().getOnAction());
		mainWindow.getController().getDecreaseFontSizeMenuItem().disableProperty().bind(controller.getDecreaseFontButton().disableProperty());

		mainController.getZoomInMenuItem().setOnAction(controller.getExpandVerticallyButton().getOnAction());
		mainController.getZoomInMenuItem().disableProperty().bind(controller.getExpandVerticallyButton().disableProperty());

		mainController.getZoomOutMenuItem().setOnAction(controller.getContractVerticallyButton().getOnAction());
		mainController.getZoomOutMenuItem().disableProperty().bind(controller.getContractVerticallyButton().disableProperty());

		mainController.getZoomInHorizontalMenuItem().setOnAction(controller.getExpandHorizontallyButton().getOnAction());
		mainController.getZoomInHorizontalMenuItem().disableProperty().bind(controller.getExpandHorizontallyButton().disableProperty());

		mainController.getZoomOutHorizontalMenuItem().setOnAction(controller.getContractHorizontallyButton().getOnAction());
		mainController.getZoomOutHorizontalMenuItem().disableProperty().bind(controller.getContractHorizontallyButton().disableProperty());

		mainController.getFindMenuItem().setOnAction(e -> findToolBar.setShowFindToolBar(true));
		mainController.getFindAgainMenuItem().setOnAction(e -> findToolBar.findAgain());
		mainController.getFindAgainMenuItem().disableProperty().bind(findToolBar.canFindAgainProperty().not());
		mainController.getReplaceMenuItem().setOnAction(e -> findToolBar.setShowReplaceToolBar(true));
		mainController.getFindMenuItem().setDisable(false);
		mainController.getReplaceMenuItem().setDisable(false);

		mainController.getSelectAllMenuItem().setOnAction(e ->
		{
			mainWindow.getTaxonSelectionModel().selectAll(mainWindow.getWorkflow().getWorkingTaxaBlock().getTaxa());
			//treeView.getSplitSelectionModel().selectAll(IteratorUtils.asList(BitSetUtils.range(1, treeView.getTreesBlock().getNsplits() + 1)));
		});
		mainController.getSelectAllMenuItem().disableProperty().bind(view.emptyProperty());

		mainController.getSelectNoneMenuItem().setOnAction(e -> {
			mainWindow.getTaxonSelectionModel().clearSelection();
			treePane.get().getEdgeSelectionModel().clearSelection();
		});
		mainController.getSelectNoneMenuItem().disableProperty().bind(mainWindow.getTaxonSelectionModel().sizeProperty().isEqualTo(0));

		mainController.getSelectInverseMenuItem().setOnAction(e -> mainWindow.getWorkflow().getWorkingTaxaBlock().getTaxa().forEach(t -> mainWindow.getTaxonSelectionModel().toggleSelection(t)));
		mainController.getSelectInverseMenuItem().disableProperty().bind(view.emptyProperty());

		mainController.getShowScaleBarMenuItem().selectedProperty().bindBidirectional(showScaleBar);
		mainController.getShowScaleBarMenuItem().disableProperty().bind(
				view.optionDiagramProperty().isEqualTo(TreeDiagramType.CircularCladogram)
						.or(view.optionDiagramProperty().isEqualTo(TreeDiagramType.TriangularCladogram))
						.or(view.optionDiagramProperty().isEqualTo(TreeDiagramType.RectangularCladogram)));

		mainController.getRotateLeftMenuItem().setOnAction(controller.getRotateLeftButton().getOnAction());
		mainController.getRotateLeftMenuItem().disableProperty().bind(controller.getRotateLeftButton().disableProperty());
		mainController.getRotateRightMenuItem().setOnAction(controller.getRotateRightButton().getOnAction());
		mainController.getRotateRightMenuItem().disableProperty().bind(controller.getRotateRightButton().disableProperty());
		mainController.getFlipMenuItem().setOnAction(controller.getFlipButton().getOnAction());
		mainController.getFlipMenuItem().disableProperty().bind(controller.getFlipButton().disableProperty());

		mainController.getLayoutLabelsMenuItem().setOnAction(e -> updateLabelLayout());
		mainController.getLayoutLabelsMenuItem().disableProperty().bind(treePane.isNull().or(view.optionDiagramProperty().isNotEqualTo(TreeDiagramType.RadialPhylogram)));

		ExportUtils.setup(mainWindow, view.getViewTab().getDataNode(), view.emptyProperty());
	}

	public void updateLabelLayout() {
		if (treePane.get() != null)
			Platform.runLater(() -> treePane.get().updateLabelLayout(view.getOptionOrientation()));
	}

	public LongProperty updateCounterProperty() {
		return updateCounter;
	}

	private static void setupZoomTracking(UndoManager undoManager, ZoomableScrollPane scrollPane, ReadOnlyDoubleProperty zoomX, ReadOnlyDoubleProperty zoomY) {
		var oldZoomX = new Single<Double>(null);

		zoomX.addListener((v, o, n) -> {
			var zoomFactor = n.doubleValue() / o.doubleValue();
			if (zoomFactor > 0 && zoomFactor != 1.0) {
				if (oldZoomX.get() == null) {
					oldZoomX.set(o.doubleValue());
				}
				RunAfterAWhile.applyInFXThread(oldZoomX, () -> {
					var factor = n.doubleValue() / oldZoomX.get();
					if (factor > 0 && factor != 1.0) {
						undoManager.add("Zoom horizontally",
								() -> LayoutUtils.scaleTranslate(scrollPane.getContent(), a -> a.getId() != null && a.getId().equals("graph-node"), 1.0 / factor, 1.0),
								() -> LayoutUtils.scaleTranslate(scrollPane.getContent(), a -> a.getId() != null && a.getId().equals("graph-node"), factor, 1.0));
						oldZoomX.set(null);
					}
				});
			}
		});

		var oldZoomY = new Single<Double>(null);

		zoomY.addListener((v, o, n) -> {
			var zoomFactor = n.doubleValue() / o.doubleValue();
			if (zoomFactor > 0 && zoomFactor != 1.0) {
				if (oldZoomY.get() == null) {
					oldZoomY.set(o.doubleValue());
				}
				RunAfterAWhile.applyInFXThread(oldZoomY, () -> {
					var factor = n.doubleValue() / oldZoomY.get();
					if (factor > 0 && factor != 1.0) {
						undoManager.add("Zoom vertically",
								() -> LayoutUtils.scaleTranslate(scrollPane.getContent(), a -> a.getId() != null && a.getId().equals("graph-node"), 1.0, 1.0 / factor),
								() -> LayoutUtils.scaleTranslate(scrollPane.getContent(), a -> a.getId() != null && a.getId().equals("graph-node"), 1.0, factor));
						oldZoomY.set(null);
					}
				});
			}
		});
	}
}
