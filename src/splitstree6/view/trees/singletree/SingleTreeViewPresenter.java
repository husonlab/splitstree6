/*
 *  SingleTreeViewPresenter.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.view.trees.singletree;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import javafx.geometry.Bounds;
import javafx.geometry.Dimension2D;
import javafx.geometry.Insets;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.DataFormat;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Shape;
import javafx.stage.Stage;
import jloda.fx.control.RichTextLabel;
import jloda.fx.find.FindToolBar;
import jloda.fx.find.Searcher;
import jloda.fx.label.EditLabelDialog;
import jloda.fx.undo.UndoManager;
import jloda.fx.util.BasicFX;
import jloda.graph.Graph;
import jloda.graph.Node;
import jloda.phylo.PhyloTree;
import jloda.util.Single;
import jloda.util.StringUtils;
import splitstree6.data.parts.Taxon;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.view.splits.viewer.ComboBoxUtils;
import splitstree6.view.trees.layout.TreeDiagramType;
import splitstree6.view.trees.layout.TreeLabel;
import splitstree6.view.trees.treepages.LayoutOrientation;
import splitstree6.view.trees.treepages.RunAfterAWhile;
import splitstree6.view.trees.treepages.TreePane;
import splitstree6.window.MainWindow;

import java.util.ArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * single tree presenter
 * Daniel Huson 3.2022
 */
public class SingleTreeViewPresenter implements IDisplayTabPresenter {
	private final MainWindow mainWindow;
	private final SingleTreeView singleTreeView;
	private final SingleTreeViewController controller;

	private final FindToolBar findToolBar;

	private final ObjectProperty<TreePane> treePane = new SimpleObjectProperty<>(this, "treePane");
	private final InvalidationListener updateListener;

	private final BooleanProperty showScaleBar = new SimpleBooleanProperty(true);

	public SingleTreeViewPresenter(MainWindow mainWindow, SingleTreeView singleTreeView, ObjectProperty<Bounds> targetBounds,
								   ObservableMap<Node, Shape> nodeShapeMap, ObservableMap<Integer, Shape> splitShapeMap) {
		this.mainWindow = mainWindow;
		this.singleTreeView = singleTreeView;
		this.controller = singleTreeView.getController();

		var treeProperty = new SimpleObjectProperty<PhyloTree>(this, "tree");
		singleTreeView.optionTreeProperty().addListener((v, o, n) -> {
			if (n.intValue() > 0 && n.intValue() <= singleTreeView.getTrees().size())
				treeProperty.set(singleTreeView.getTrees().get(n.intValue() - 1));
			else
				treeProperty.set(null);
		});

		singleTreeView.getTrees().addListener((InvalidationListener) e -> {
			controller.getTreeCBox().getItems().setAll(singleTreeView.getTrees().stream().map(Graph::getName).collect(Collectors.toList()));
			if (singleTreeView.getOptionTree() == 0 && singleTreeView.getTrees().size() > 0)
				singleTreeView.setOptionTree(1);
			if (singleTreeView.getOptionTree() >= 1 && singleTreeView.getOptionTree() <= singleTreeView.getTrees().size())
				controller.getTreeCBox().setValue(controller.getTreeCBox().getItems().get(singleTreeView.getOptionTree() - 1));
		});

		controller.getTreeCBox().valueProperty().addListener((v, o, n) -> {
			if (n != null) {
				var i = controller.getTreeCBox().getItems().indexOf(n);
				if (i >= 0 && i < singleTreeView.size()) {
					singleTreeView.setOptionTree(i + 1);
					return;
				}
			}
			singleTreeView.setOptionTree(0);
		});

		controller.getNextButton().setOnAction(e -> singleTreeView.setOptionTree(singleTreeView.getOptionTree() + 1));
		controller.getNextButton().disableProperty().bind(Bindings.size(singleTreeView.getTrees()).isEqualTo(0).or(singleTreeView.optionTreeProperty().greaterThan(Bindings.size(singleTreeView.getTrees()))));

		controller.getPreviousButton().setOnAction(e -> singleTreeView.setOptionTree(singleTreeView.getOptionTree() - 1));
		controller.getPreviousButton().disableProperty().bind(Bindings.size(singleTreeView.getTrees()).isEqualTo(0).or(singleTreeView.optionTreeProperty().lessThanOrEqualTo(1)));

		{
			var labelProperty = new SimpleStringProperty();
			BasicFX.makeMultiStateToggle(controller.getShowTreeNamesToggleButton(), singleTreeView.getOptionTreeLabels().label(), labelProperty, TreeLabel.labels());
			labelProperty.addListener((v, o, n) -> singleTreeView.setOptionTreeLabels(TreeLabel.valueOfLabel(n)));
		}
		singleTreeView.optionTreeLabelsProperty().addListener((v, o, n) -> TreeLabel.setLabel(treeProperty.get(), n, controller.getTreeNameLabel()));
		treeProperty.addListener((v, o, n) -> TreeLabel.setLabel(n, singleTreeView.getOptionTreeLabels(), controller.getTreeNameLabel()));
		TreeLabel.setLabel(treeProperty.get(), singleTreeView.getOptionTreeLabels(), controller.getTreeNameLabel());

		var toScale = new SimpleBooleanProperty(this, "toScale", singleTreeView.getOptionDiagram().isPhylogram());
		var lockAspectRatio = new SimpleBooleanProperty(this, "lockAspectRatio");

		singleTreeView.optionDiagramProperty().addListener((v, o, n) -> {
			toScale.set(n.isPhylogram());
			lockAspectRatio.set(n.isRadialOrCircular());
		});
		lockAspectRatio.set(singleTreeView.getOptionDiagram() != null && singleTreeView.getOptionDiagram().isRadialOrCircular());
		toScale.set(singleTreeView.getOptionDiagram() != null && singleTreeView.getOptionDiagram().isPhylogram());

		var scrollPane = controller.getScrollPane();
		scrollPane.lockAspectRatioProperty().bind(lockAspectRatio);
		scrollPane.setRequireShiftOrControlToZoom(true);
		scrollPane.setPannable(false);
		scrollPane.setPadding(new Insets(10, 0, 0, 10));

		controller.getDiagramCBox().setButtonCell(ComboBoxUtils.createButtonCell(FXCollections.observableSet(), null));
		controller.getDiagramCBox().setCellFactory(ComboBoxUtils.createCellFactory(FXCollections.observableSet(), null));
		controller.getDiagramCBox().getItems().addAll(TreeDiagramType.values());
		controller.getDiagramCBox().valueProperty().bindBidirectional(singleTreeView.optionDiagramProperty());

		controller.getOrientationCBox().setButtonCell(ComboBoxUtils.createButtonCell(null, LayoutOrientation::createLabel));
		controller.getOrientationCBox().setCellFactory(ComboBoxUtils.createCellFactory(null, LayoutOrientation::createLabel));
		controller.getOrientationCBox().getItems().addAll(LayoutOrientation.values());
		controller.getOrientationCBox().valueProperty().bindBidirectional(singleTreeView.optionOrientationProperty());

		controller.getShowInternalLabelsToggleButton().selectedProperty().bindBidirectional(singleTreeView.optionShowConfidenceProperty());

		controller.getScaleBar().visibleProperty().bind(toScale.and(showScaleBar));
		controller.getScaleBar().factorXProperty().bind(singleTreeView.optionHorizontalZoomFactorProperty());

		var first = new Single<>(true);

		updateListener = e -> {
			if (treeProperty.get() != null) {
				RunAfterAWhile.apply(treeProperty.get().getName(), () -> Platform.runLater(() -> {
					var box = new Dimension2D(100, 100);
					var bounds = targetBounds.get();
					if (bounds != null) {
						var width = bounds.getWidth();
						var height = bounds.getHeight();
						if (lockAspectRatio.get())
							width = height = Math.min(width, height);
						box = new Dimension2D(singleTreeView.getOptionHorizontalZoomFactor() * width, singleTreeView.getOptionVerticalZoomFactor() * height);
					}

					if (first.get())
						first.set(false);
					else
						SingleTreeEdits.clearEdits(singleTreeView.optionEditsProperty());

					if (!singleTreeView.emptyProperty().get()) {
						treePane.set(new TreePane(mainWindow.getStage(), mainWindow.getWorkflow().getWorkingTaxaBlock(), treeProperty.get(), mainWindow.getTaxonSelectionModel(), box.getWidth(), box.getHeight(),
								singleTreeView.getOptionDiagram(), singleTreeView.getOptionAveraging(), singleTreeView.optionOrientationProperty(), singleTreeView.optionFontScaleFactorProperty(), null,
								singleTreeView.optionShowInternalLabelsProperty(), controller.getScaleBar().unitLengthXProperty()));

						treePane.get().drawTree();
						treePane.get().setRunAfterUpdate(() -> {
						});
						scrollPane.setContent(treePane.get());
					} else {
						treePane.set(null);
						scrollPane.setContent(new Pane());
					}
				}));
			}
		};

		scrollPane.setUpdateScaleMethod(() -> {
			Platform.runLater(() -> {
				singleTreeView.setOptionHorizontalZoomFactor(scrollPane.getZoomFactorX() * singleTreeView.getOptionHorizontalZoomFactor());
				singleTreeView.setOptionVerticalZoomFactor(scrollPane.getZoomFactorY() * singleTreeView.getOptionVerticalZoomFactor());
			});
		});

		singleTreeView.optionHorizontalZoomFactorProperty().addListener((v, o, n) -> {
			scrollPane.getContentNode().setScaleX(scrollPane.getContentNode().getScaleX() / o.doubleValue() * n.doubleValue());
			if (!lockAspectRatio.get())
				updateListener.invalidated(null);
		});

		singleTreeView.optionVerticalZoomFactorProperty().addListener((v, o, n) -> {
			scrollPane.getContentNode().setScaleY(scrollPane.getContentNode().getScaleY() / o.doubleValue() * n.doubleValue());
			updateListener.invalidated(null);
		});

		singleTreeView.optionFontScaleFactorProperty().addListener(e -> {
			//if (treePane.get() != null)
			//	ProgramExecutorService.submit(100, () -> Platform.runLater(() -> treePane.get().layoutLabels(singleTreeView.getOptionOrientation())));
		});

		singleTreeView.getTrees().addListener(updateListener);
		singleTreeView.optionTreeProperty().addListener(updateListener);
		singleTreeView.optionDiagramProperty().addListener(updateListener);

		controller.getContractHorizontallyButton().setOnAction(e -> singleTreeView.setOptionHorizontalZoomFactor((1.0 / 1.1) * singleTreeView.getOptionHorizontalZoomFactor()));
		controller.getContractHorizontallyButton().disableProperty().bind(singleTreeView.emptyProperty().or(lockAspectRatio));

		controller.getExpandHorizontallyButton().setOnAction(e -> singleTreeView.setOptionHorizontalZoomFactor(1.1 * singleTreeView.getOptionHorizontalZoomFactor()));
		controller.getExpandHorizontallyButton().disableProperty().bind(singleTreeView.emptyProperty().or(lockAspectRatio).or(singleTreeView.optionHorizontalZoomFactorProperty().greaterThan(8.0 / 1.1)));

		controller.getExpandVerticallyButton().setOnAction(e ->
		{
			singleTreeView.setOptionVerticalZoomFactor(1.1 * singleTreeView.getOptionVerticalZoomFactor());
			if (lockAspectRatio.get())
				singleTreeView.setOptionHorizontalZoomFactor(1.1 * singleTreeView.getOptionHorizontalZoomFactor());
		});
		controller.getExpandVerticallyButton().disableProperty().bind(singleTreeView.emptyProperty().or(singleTreeView.optionVerticalZoomFactorProperty().greaterThan(8.0 / 1.1)));

		controller.getContractVerticallyButton().setOnAction(e -> {
			singleTreeView.setOptionVerticalZoomFactor((1.0 / 1.1) * singleTreeView.getOptionVerticalZoomFactor());
			if (lockAspectRatio.get())
				singleTreeView.setOptionHorizontalZoomFactor((1.0 / 1.1) * singleTreeView.getOptionHorizontalZoomFactor());
		});
		controller.getContractVerticallyButton().disableProperty().bind(singleTreeView.emptyProperty());

		controller.getIncreaseFontButton().setOnAction(e -> singleTreeView.setOptionFontScaleFactor(1.2 * singleTreeView.getOptionFontScaleFactor()));
		controller.getIncreaseFontButton().disableProperty().bind(singleTreeView.emptyProperty());
		controller.getDecreaseFontButton().setOnAction(e -> singleTreeView.setOptionFontScaleFactor((1.0 / 1.2) * singleTreeView.getOptionFontScaleFactor()));
		controller.getDecreaseFontButton().disableProperty().bind(singleTreeView.emptyProperty());

		final Function<Integer, Taxon> t2taxon = t -> mainWindow.getActiveTaxa().get(t);

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

		singleTreeView.viewTabProperty().addListener((v, o, n) -> {
			if (n != null) {
				controller.getvBox().getChildren().add(0, n.getAlgorithmBreadCrumbsToolBar());
			}
		});
		singleTreeView.emptyProperty().addListener(e -> singleTreeView.getRoot().setDisable(singleTreeView.emptyProperty().get()));

		var undoManager = singleTreeView.getUndoManager();

		singleTreeView.optionDiagramProperty().addListener((v, o, n) -> undoManager.add(" set diagram", singleTreeView.optionDiagramProperty(), o, n));
		singleTreeView.optionTreeLabelsProperty().addListener((v, o, n) -> undoManager.add("set show tree names", singleTreeView.optionTreeLabelsProperty(), o, n));
		singleTreeView.optionAveragingProperty().addListener((v, o, n) -> undoManager.add(" set node averaging", singleTreeView.optionAveragingProperty(), o, n));
		singleTreeView.optionOrientationProperty().addListener((v, o, n) -> undoManager.add(" set orientation", singleTreeView.optionOrientationProperty(), o, n));
		singleTreeView.optionFontScaleFactorProperty().addListener((v, o, n) -> undoManager.add("set font size", singleTreeView.optionFontScaleFactorProperty(), o, n));
		singleTreeView.optionHorizontalZoomFactorProperty().addListener((v, o, n) -> undoManager.add(" set horizontal zoom", singleTreeView.optionHorizontalZoomFactorProperty(), o, n));
		singleTreeView.optionVerticalZoomFactorProperty().addListener((v, o, n) -> undoManager.add(" set vertical zoom", singleTreeView.optionVerticalZoomFactorProperty(), o, n));
		// singleTreeView.optionShowTreeNamesProperty().addListener((v, o, n) -> undoManager.add("set show tree names", singleTreeView.optionShowTreeNamesProperty(), o, n));
		// singleTreeView.optionShowTreeInfoProperty().addListener((v, o, n) -> undoManager.add("set show tree info", singleTreeView.optionShowTreeInfoProperty(), o, n));

		Platform.runLater(this::setupMenuItems);

		// if(tree.get()==null && singleTreeView.getOptionTree()>=0 && singleTreeView.getOptionTree()<singleTreeView.getTrees().size())
		//	tree.set(singleTreeView.getTrees().get(singleTreeView.getOptionTree()-1));
		updateListener.invalidated(null);
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

		mainController.getFindMenuItem().setOnAction(controller.getFindButton().getOnAction());
		mainController.getFindAgainMenuItem().setOnAction(e -> findToolBar.findAgain());
		mainController.getFindAgainMenuItem().disableProperty().bind(findToolBar.canFindAgainProperty().not());

		mainController.getSelectAllMenuItem().setOnAction(e ->
		{
			mainWindow.getTaxonSelectionModel().selectAll(mainWindow.getWorkflow().getWorkingTaxaBlock().getTaxa());
			//singleTreeView.getSplitSelectionModel().selectAll(IteratorUtils.asList(BitSetUtils.range(1, singleTreeView.getTreesBlock().getNsplits() + 1)));
		});
		mainController.getSelectNoneMenuItem().setOnAction(e -> {
			mainWindow.getTaxonSelectionModel().clearSelection();
			//singleTreeView.getSplitSelectionModel().clearSelection();
		});
		mainController.getSelectNoneMenuItem().disableProperty().bind(mainWindow.getTaxonSelectionModel().sizeProperty().isEqualTo(0));

		mainController.getShowScaleBarMenuItem().selectedProperty().bindBidirectional(showScaleBar);
		mainController.getShowScaleBarMenuItem().disableProperty().bind(
				singleTreeView.optionDiagramProperty().isEqualTo(TreeDiagramType.CircularCladogram)
						.or(singleTreeView.optionDiagramProperty().isEqualTo(TreeDiagramType.TriangularCladogram))
						.or(singleTreeView.optionDiagramProperty().isEqualTo(TreeDiagramType.RectangularCladogram)));

		mainController.getRotateLeftMenuItem().setOnAction(e -> singleTreeView.setOptionOrientation(singleTreeView.getOptionOrientation().getRotateLeft()));
		mainController.getRotateLeftMenuItem().disableProperty().bind(singleTreeView.emptyProperty());
		mainController.getRotateRightMenuItem().setOnAction(e -> singleTreeView.setOptionOrientation(singleTreeView.getOptionOrientation().getRotateRight()));
		mainController.getRotateRightMenuItem().disableProperty().bind(singleTreeView.emptyProperty());
		mainController.getFlipMenuItem().setOnAction(e -> singleTreeView.setOptionOrientation(singleTreeView.getOptionOrientation().getFlip()));
		mainController.getFlipMenuItem().disableProperty().bind(singleTreeView.emptyProperty());

		mainController.getLayoutLabelsMenuItem().setOnAction(e -> {
			if (treePane.get() != null)
				treePane.get().updateLabelLayout(singleTreeView.getOptionOrientation());
		});
		mainController.getLayoutLabelsMenuItem().disableProperty().bind(treePane.isNull().or(singleTreeView.optionDiagramProperty().isNotEqualTo(TreeDiagramType.RadialPhylogram)));
	}

	private static void showContextMenu(ContextMenuEvent event, Stage stage, UndoManager undoManager, RichTextLabel label) {
		var editLabelMenuItem = new MenuItem("Edit Label...");
		editLabelMenuItem.setOnAction(e -> {
			var oldText = label.getText();
			var editLabelDialog = new EditLabelDialog(stage, label);
			var result = editLabelDialog.showAndWait();
			if (result.isPresent() && !result.get().equals(oldText)) {
				undoManager.doAndAdd("Edit Label", () -> label.setText(oldText), () -> label.setText(result.get()));
			}
		});
		var menu = new ContextMenu();
		menu.getItems().add(editLabelMenuItem);
		menu.show(label, event.getScreenX(), event.getScreenY());
	}
}
