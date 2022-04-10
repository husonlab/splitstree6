/*
 *  TreeViewPresenter.java Copyright (C) 2022 Daniel H. Huson
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
import javafx.collections.ObservableMap;
import javafx.collections.ObservableSet;
import javafx.geometry.Bounds;
import javafx.geometry.Dimension2D;
import javafx.geometry.Insets;
import javafx.scene.Group;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Shape;
import jloda.fx.control.RichTextLabel;
import jloda.fx.find.FindToolBar;
import jloda.fx.util.BasicFX;
import jloda.fx.util.ResourceManagerFX;
import jloda.fx.util.RunAfterAWhile;
import jloda.fx.window.MainWindowManager;
import jloda.graph.Graph;
import jloda.phylo.PhyloTree;
import jloda.util.Single;
import jloda.util.StringUtils;
import splitstree6.layout.tree.*;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.view.findreplace.FindReplaceTaxa;
import splitstree6.view.trees.treepages.TreePane;
import splitstree6.view.utils.ComboBoxUtils;
import splitstree6.window.MainWindow;

import java.util.ArrayList;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * single tree presenter
 * Daniel Huson 3.2022
 */
public class TreeViewPresenter implements IDisplayTabPresenter {
	private final LongProperty updateCounter = new SimpleLongProperty(0L);

	private final MainWindow mainWindow;
	private final TreeView treeView;
	private final TreeViewController controller;

	private final FindToolBar findToolBar;

	private final ObjectProperty<TreePane> treePane = new SimpleObjectProperty<>(this, "treePane");
	private final InvalidationListener updateListener;

	private final BooleanProperty showScaleBar = new SimpleBooleanProperty(true);

	public TreeViewPresenter(MainWindow mainWindow, TreeView treeView, ObjectProperty<Bounds> targetBounds,
							 ObservableMap<jloda.graph.Node, Group> nodeShapeMap, ObservableMap<Integer, Shape> splitShapeMap) {
		this.mainWindow = mainWindow;
		this.treeView = treeView;
		this.controller = treeView.getController();

		var treeProperty = new SimpleObjectProperty<PhyloTree>(this, "tree");
		treeView.optionTreeProperty().addListener((v, o, n) -> {
			var nTree = n.intValue();
			if (nTree > 0 && nTree <= treeView.getTrees().size())
				treeProperty.set(treeView.getTrees().get(nTree - 1));
			else
				treeProperty.set(null);
		});

		treeView.getTrees().addListener((InvalidationListener) e -> {
			controller.getTreeCBox().getItems().setAll(treeView.getTrees().stream().map(Graph::getName).collect(Collectors.toList()));
			if (treeView.getOptionTree() == 0 && treeView.getTrees().size() > 0)
				treeView.setOptionTree(1);
			if (treeView.getOptionTree() >= 1 && treeView.getOptionTree() <= treeView.getTrees().size()) {
				controller.getTreeCBox().setValue(controller.getTreeCBox().getItems().get(treeView.getOptionTree() - 1));
				treeProperty.set(treeView.getTrees().get(treeView.getOptionTree() - 1));
			}
		});

		controller.getTreeCBox().valueProperty().addListener((v, o, n) -> {
			if (n != null) {
				var i = controller.getTreeCBox().getItems().indexOf(n);
				if (i >= 0 && i < treeView.size()) {
					treeView.setOptionTree(i + 1);
					return;
				}
			}
			treeView.setOptionTree(0);
		});

		controller.getNextButton().setOnAction(e -> treeView.setOptionTree(treeView.getOptionTree() + 1));
		controller.getNextButton().disableProperty().bind(Bindings.size(treeView.getTrees()).isEqualTo(0).or(treeView.optionTreeProperty().greaterThanOrEqualTo(Bindings.size(treeView.getTrees()))));

		controller.getPreviousButton().setOnAction(e -> treeView.setOptionTree(treeView.getOptionTree() - 1));
		controller.getPreviousButton().disableProperty().bind(Bindings.size(treeView.getTrees()).isEqualTo(0).or(treeView.optionTreeProperty().lessThanOrEqualTo(1)));

		{
			var labelProperty = new SimpleStringProperty();
			BasicFX.makeMultiStateToggle(controller.getShowTreeNamesToggleButton(), treeView.getOptionTreeLabels().label(), labelProperty, TreeLabel.labels());
			labelProperty.addListener((v, o, n) -> treeView.setOptionTreeLabels(TreeLabel.valueOfLabel(n)));
		}
		treeView.optionTreeLabelsProperty().addListener((v, o, n) -> TreeLabel.setLabel(treeProperty.get(), n, controller.getTreeNameLabel()));
		treeProperty.addListener((v, o, n) -> TreeLabel.setLabel(n, treeView.getOptionTreeLabels(), controller.getTreeNameLabel()));
		TreeLabel.setLabel(treeProperty.get(), treeView.getOptionTreeLabels(), controller.getTreeNameLabel());

		var toScale = new SimpleBooleanProperty(this, "toScale", treeView.getOptionDiagram().isPhylogram());
		var lockAspectRatio = new SimpleBooleanProperty(this, "lockAspectRatio");

		treeView.optionDiagramProperty().addListener((v, o, n) -> {
			toScale.set(n.isPhylogram());
			lockAspectRatio.set(n.isRadialOrCircular());
		});
		lockAspectRatio.set(treeView.getOptionDiagram() != null && treeView.getOptionDiagram().isRadialOrCircular());
		toScale.set(treeView.getOptionDiagram() != null && treeView.getOptionDiagram().isPhylogram());

		var scrollPane = controller.getScrollPane();
		scrollPane.lockAspectRatioProperty().bind(lockAspectRatio);
		scrollPane.setRequireShiftOrControlToZoom(true);
		scrollPane.setPadding(new Insets(10, 0, 0, 10));

		controller.getDiagramCBox().setButtonCell(ComboBoxUtils.createButtonCell(null, TreeDiagramType::createNode));
		controller.getDiagramCBox().setCellFactory(ComboBoxUtils.createCellFactory(null, TreeDiagramType::createNode));
		controller.getDiagramCBox().getItems().addAll(TreeDiagramType.values());
		controller.getDiagramCBox().valueProperty().bindBidirectional(treeView.optionDiagramProperty());

		controller.getOrientationCBox().setButtonCell(ComboBoxUtils.createButtonCell(null, LayoutOrientation::createLabel));
		controller.getOrientationCBox().setCellFactory(ComboBoxUtils.createCellFactory(null, LayoutOrientation::createLabel));
		controller.getOrientationCBox().getItems().addAll(LayoutOrientation.values());
		controller.getOrientationCBox().valueProperty().bindBidirectional(treeView.optionOrientationProperty());

		controller.getShowInternalLabelsToggleButton().selectedProperty().bindBidirectional(treeView.optionShowConfidenceProperty());

		controller.getScaleBar().visibleProperty().bind(toScale.and(showScaleBar));

		{
			final InvalidationListener scaleListener = e -> {
				if (treePane.get() == null) {
					controller.getScaleBar().factorXProperty().unbind();
				} else {
					controller.getScaleBar().factorXProperty().bind(treeView.getOptionOrientation().isWidthHeightSwitched() ? treePane.get().scaleYProperty() : treePane.get().scaleXProperty());
				}
			};
			treePane.addListener(scaleListener);
			treeView.optionOrientationProperty().addListener(scaleListener);
			scaleListener.invalidated(null);
		}

		var first = new Single<>(true);

		updateListener = e -> {
			if (treeProperty.get() != null) {
				RunAfterAWhile.apply(treeProperty.get().getName(), () -> Platform.runLater(() -> {
					var bounds = targetBounds.get();
					if (bounds != null) {
						var width = bounds.getWidth();
						var height = bounds.getHeight();
						if (lockAspectRatio.get())
							width = height = Math.min(width, height);
						var box = new Dimension2D(treeView.getOptionHorizontalZoomFactor() * width, treeView.getOptionVerticalZoomFactor() * height);

						if (first.get())
							first.set(false);
						else
							TreeViewEdits.clearEdits(treeView.optionEditsProperty());

						if (!treeView.emptyProperty().get()) {
							var pane = new TreePane(mainWindow.getStage(), mainWindow.getWorkflow().getWorkingTaxaBlock(), treeProperty.get(), mainWindow.getTaxonSelectionModel(), box.getWidth(), box.getHeight(),
									treeView.getOptionDiagram(), treeView.getOptionAveraging(), treeView.optionOrientationProperty(), treeView.optionFontScaleFactorProperty(), null,
									treeView.optionShowInternalLabelsProperty(), controller.getScaleBar().unitLengthXProperty(), nodeShapeMap);
							treePane.set(pane);
							pane.setRunAfterUpdate(() -> {
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
				treeView.setOptionHorizontalZoomFactor(scrollPane.getZoomFactorX() * treeView.getOptionHorizontalZoomFactor());
				treeView.setOptionVerticalZoomFactor(scrollPane.getZoomFactorY() * treeView.getOptionVerticalZoomFactor());
			});
		});

		treeView.optionHorizontalZoomFactorProperty().addListener((v, o, n) -> {
			treePane.get().setScaleX(treePane.get().getScaleX() / o.doubleValue() * n.doubleValue());
			if (!lockAspectRatio.get())
				updateListener.invalidated(null);
		});

		treeView.optionVerticalZoomFactorProperty().addListener((v, o, n) -> {
			treePane.get().setScaleY(treePane.get().getScaleY() / o.doubleValue() * n.doubleValue());
			updateListener.invalidated(null);
		});

		treeView.optionFontScaleFactorProperty().addListener(e -> {
			//if (treePane.get() != null)
			//	ProgramExecutorService.submit(100, () -> Platform.runLater(() -> treePane.get().layoutLabels(treeView.getOptionOrientation())));
		});

		treeView.getTrees().addListener(updateListener);
		treeView.optionTreeProperty().addListener(updateListener);
		treeView.optionDiagramProperty().addListener(updateListener);

		final ObservableSet<HeightAndAngles.Averaging> disabledAveraging = FXCollections.observableSet();
		treeView.optionDiagramProperty().addListener((v, o, n) -> {
			disabledAveraging.clear();
			if (n == TreeDiagramType.RadialPhylogram) {
				disabledAveraging.add(HeightAndAngles.Averaging.ChildAverage);
			}
		});

		controller.getAveragingCBox().setButtonCell(ComboBoxUtils.createButtonCell(disabledAveraging, HeightAndAngles.Averaging::createLabel));
		controller.getAveragingCBox().setCellFactory(ComboBoxUtils.createCellFactory(disabledAveraging, HeightAndAngles.Averaging::createLabel));
		controller.getAveragingCBox().getItems().addAll(HeightAndAngles.Averaging.values());
		controller.getAveragingCBox().valueProperty().bindBidirectional(treeView.optionAveragingProperty());
		treeView.optionAveragingProperty().addListener(updateListener);

		controller.getContractHorizontallyButton().setOnAction(e -> treeView.setOptionHorizontalZoomFactor((1.0 / 1.1) * treeView.getOptionHorizontalZoomFactor()));
		controller.getContractHorizontallyButton().disableProperty().bind(treeView.emptyProperty().or(lockAspectRatio));

		controller.getExpandHorizontallyButton().setOnAction(e -> treeView.setOptionHorizontalZoomFactor(1.1 * treeView.getOptionHorizontalZoomFactor()));
		controller.getExpandHorizontallyButton().disableProperty().bind(treeView.emptyProperty().or(lockAspectRatio).or(treeView.optionHorizontalZoomFactorProperty().greaterThan(8.0 / 1.1)));

		controller.getExpandVerticallyButton().setOnAction(e -> {
			treeView.setOptionVerticalZoomFactor(1.1 * treeView.getOptionVerticalZoomFactor());
			if (lockAspectRatio.get())
				treeView.setOptionHorizontalZoomFactor(1.1 * treeView.getOptionHorizontalZoomFactor());
		});
		controller.getExpandVerticallyButton().disableProperty().bind(treeView.emptyProperty().or(treeView.optionVerticalZoomFactorProperty().greaterThan(8.0 / 1.1)));

		controller.getContractVerticallyButton().setOnAction(e -> {
			treeView.setOptionVerticalZoomFactor((1.0 / 1.1) * treeView.getOptionVerticalZoomFactor());
			if (lockAspectRatio.get())
				treeView.setOptionHorizontalZoomFactor((1.0 / 1.1) * treeView.getOptionHorizontalZoomFactor());
		});
		controller.getContractVerticallyButton().disableProperty().bind(treeView.emptyProperty());

		controller.getIncreaseFontButton().setOnAction(e -> treeView.setOptionFontScaleFactor(1.2 * treeView.getOptionFontScaleFactor()));
		controller.getIncreaseFontButton().disableProperty().bind(treeView.emptyProperty());
		controller.getDecreaseFontButton().setOnAction(e -> treeView.setOptionFontScaleFactor((1.0 / 1.2) * treeView.getOptionFontScaleFactor()));
		controller.getDecreaseFontButton().disableProperty().bind(treeView.emptyProperty());

		findToolBar = FindReplaceTaxa.create(mainWindow, treeView.getUndoManager());
		findToolBar.setShowFindToolBar(false);
		controller.getvBox().getChildren().add(findToolBar);
		controller.getFindToggleButton().setOnAction(e -> {
			if (!findToolBar.isShowFindToolBar()) {
				findToolBar.setShowFindToolBar(true);
				controller.getFindToggleButton().setSelected(true);
				controller.getFindToggleButton().setGraphic(ResourceManagerFX.getIconAsImageView("sun/Replace24.gif", 16));
			} else if (!findToolBar.isShowReplaceToolBar()) {
				findToolBar.setShowReplaceToolBar(true);
				controller.getFindToggleButton().setSelected(true);
			} else {
				findToolBar.setShowFindToolBar(false);
				findToolBar.setShowReplaceToolBar(false);
				controller.getFindToggleButton().setSelected(false);
				controller.getFindToggleButton().setGraphic(ResourceManagerFX.getIconAsImageView("sun/Find24.gif", 16));
			}
		});

		treeView.viewTabProperty().addListener((v, o, n) -> {
			if (n != null) {
				controller.getvBox().getChildren().add(0, n.getAlgorithmBreadCrumbsToolBar());
			}
		});
		treeView.emptyProperty().addListener(e -> treeView.getRoot().setDisable(treeView.emptyProperty().get()));

		var undoManager = treeView.getUndoManager();

		treeView.optionDiagramProperty().addListener((v, o, n) -> undoManager.add("diagram", treeView.optionDiagramProperty(), o, n));
		treeView.optionTreeLabelsProperty().addListener((v, o, n) -> undoManager.add("show tree names", treeView.optionTreeLabelsProperty(), o, n));
		treeView.optionAveragingProperty().addListener((v, o, n) -> undoManager.add("node averaging", treeView.optionAveragingProperty(), o, n));
		treeView.optionOrientationProperty().addListener((v, o, n) -> undoManager.add("orientation", treeView.optionOrientationProperty(), o, n));
		treeView.optionFontScaleFactorProperty().addListener((v, o, n) -> undoManager.add("font size", treeView.optionFontScaleFactorProperty(), o, n));
		treeView.optionHorizontalZoomFactorProperty().addListener((v, o, n) -> undoManager.add("horizontal zoom", treeView.optionHorizontalZoomFactorProperty(), o, n));
		treeView.optionVerticalZoomFactorProperty().addListener((v, o, n) -> undoManager.add("vertical zoom", treeView.optionVerticalZoomFactorProperty(), o, n));
		// treeView.optionShowTreeNamesProperty().addListener((v, o, n) -> undoManager.add("show tree names", treeView.optionShowTreeNamesProperty(), o, n));
		// treeView.optionShowTreeInfoProperty().addListener((v, o, n) -> undoManager.add("show tree info", treeView.optionShowTreeInfoProperty(), o, n));

		Platform.runLater(this::setupMenuItems);
		updateListener.invalidated(null);


		if (false) { // code for testing ideas about drawing on a canvas
			var drawOnCanvas = new DrawOnCanvas();

			treeView.optionTreeProperty().addListener((v, o, n) -> {
				if (n.intValue() >= 1 && n.intValue() <= treeView.getTrees().size()) {
					drawOnCanvas.draw(mainWindow.getController().getBottomFlowPane(), treeView.getTrees().get(n.intValue() - 1), mainWindow.getWorkflow().getWorkingTaxaBlock().getNtax(),
							t -> mainWindow.getWorkflow().getWorkingTaxaBlock().get(t).displayLabelProperty(), treeView.getOptionDiagram(), treeView.getOptionAveraging(), 850, 850, false);
				}
			});
		}
	}

	public void setupMenuItems() {
		var mainController = mainWindow.getController();

		mainController.getCopyMenuItem().setOnAction(e -> {
			var list = new ArrayList<String>();
			for (var taxon : mainWindow.getTaxonSelectionModel().getSelectedItems()) {
				list.add(RichTextLabel.getRawText(taxon.getDisplayLabelOrName()).trim());
			}
			if (list.size() > 0) {
				var content = new ClipboardContent();
				content.put(DataFormat.PLAIN_TEXT, StringUtils.toString(list, "\n"));
				Clipboard.getSystemClipboard().setContent(content);
			}
		});
		mainController.getCopyMenuItem().disableProperty().bind(mainWindow.getTaxonSelectionModel().sizeProperty().isEqualTo(0));

		mainController.getCutMenuItem().disableProperty().bind(new SimpleBooleanProperty(true));
		mainController.getCopyNewickMenuItem().disableProperty().bind(new SimpleBooleanProperty(true));

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

		mainController.getSelectAllMenuItem().setOnAction(e ->
		{
			mainWindow.getTaxonSelectionModel().selectAll(mainWindow.getWorkflow().getWorkingTaxaBlock().getTaxa());
			//treeView.getSplitSelectionModel().selectAll(IteratorUtils.asList(BitSetUtils.range(1, treeView.getTreesBlock().getNsplits() + 1)));
		});
		mainController.getSelectAllMenuItem().disableProperty().bind(treeView.emptyProperty());

		mainController.getSelectNoneMenuItem().setOnAction(e -> {
			mainWindow.getTaxonSelectionModel().clearSelection();
			//treeView.getSplitSelectionModel().clearSelection();
		});
		mainController.getSelectNoneMenuItem().disableProperty().bind(mainWindow.getTaxonSelectionModel().sizeProperty().isEqualTo(0));

		mainController.getSelectInverseMenuItem().setOnAction(e -> mainWindow.getWorkflow().getWorkingTaxaBlock().getTaxa().forEach(t -> mainWindow.getTaxonSelectionModel().toggleSelection(t)));
		mainController.getSelectInverseMenuItem().disableProperty().bind(treeView.emptyProperty());

		mainController.getSelectFromPreviousMenuItem().setOnAction(e -> {
			var taxonBlock = mainWindow.getWorkflow().getWorkingTaxaBlock();
			if (taxonBlock != null) {
				MainWindowManager.getPreviousSelection().stream().map(taxonBlock::get).filter(Objects::nonNull).forEach(t -> mainWindow.getTaxonSelectionModel().select(t));
			}
		});
		mainController.getSelectFromPreviousMenuItem().disableProperty().bind(Bindings.isEmpty(MainWindowManager.getPreviousSelection()));

		mainController.getShowScaleBarMenuItem().selectedProperty().bindBidirectional(showScaleBar);
		mainController.getShowScaleBarMenuItem().disableProperty().bind(
				treeView.optionDiagramProperty().isEqualTo(TreeDiagramType.CircularCladogram)
						.or(treeView.optionDiagramProperty().isEqualTo(TreeDiagramType.TriangularCladogram))
						.or(treeView.optionDiagramProperty().isEqualTo(TreeDiagramType.RectangularCladogram)));

		mainController.getRotateLeftMenuItem().setOnAction(e -> treeView.setOptionOrientation(treeView.getOptionOrientation().getRotateLeft()));
		mainController.getRotateLeftMenuItem().disableProperty().bind(treeView.emptyProperty());
		mainController.getRotateRightMenuItem().setOnAction(e -> treeView.setOptionOrientation(treeView.getOptionOrientation().getRotateRight()));
		mainController.getRotateRightMenuItem().disableProperty().bind(treeView.emptyProperty());
		mainController.getFlipMenuItem().setOnAction(e -> treeView.setOptionOrientation(treeView.getOptionOrientation().getFlip()));
		mainController.getFlipMenuItem().disableProperty().bind(treeView.emptyProperty());

		mainController.getLayoutLabelsMenuItem().setOnAction(e -> updateLabelLayout());
		mainController.getLayoutLabelsMenuItem().disableProperty().bind(treePane.isNull().or(treeView.optionDiagramProperty().isNotEqualTo(TreeDiagramType.RadialPhylogram)));
	}

	public void updateLabelLayout() {
		if (treePane.get() != null)
			Platform.runLater(() -> treePane.get().updateLabelLayout(treeView.getOptionOrientation()));
	}

	public LongProperty updateCounterProperty() {
		return updateCounter;
	}
}
