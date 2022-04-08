/*
 * SplitsViewPresenter.java Copyright (C) 2022 Daniel H. Huson
 *
 * (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package splitstree6.view.splits.viewer;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.collections.ObservableSet;
import javafx.geometry.Bounds;
import javafx.geometry.Dimension2D;
import javafx.scene.Group;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.DataFormat;
import javafx.scene.shape.Shape;
import javafx.stage.Stage;
import jloda.fx.control.RichTextLabel;
import jloda.fx.find.FindToolBar;
import jloda.fx.label.EditLabelDialog;
import jloda.fx.undo.UndoManager;
import jloda.fx.util.BasicFX;
import jloda.fx.util.ProgramExecutorService;
import jloda.fx.util.ResourceManagerFX;
import jloda.util.BitSetUtils;
import jloda.util.IteratorUtils;
import jloda.util.Single;
import jloda.util.StringUtils;
import splitstree6.data.SplitsBlock;
import splitstree6.data.parts.Compatibility;
import splitstree6.layout.splits.LoopView;
import splitstree6.layout.splits.SplitsDiagramType;
import splitstree6.layout.splits.SplitsRooting;
import splitstree6.layout.tree.LayoutOrientation;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.view.findreplace.FindReplaceTaxa;
import splitstree6.view.utils.ComboBoxUtils;
import splitstree6.window.MainWindow;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * splits network presenter
 * Daniel Huson 1.2022
 */
public class SplitsViewPresenter implements IDisplayTabPresenter {
	private final LongProperty updateCounter = new SimpleLongProperty(0L);

	private final MainWindow mainWindow;
	private final SplitsView splitsView;
	private final SplitsViewController controller;

	private final FindToolBar findToolBar;

	private final InvalidationListener selectionListener;

	private final ObjectProperty<SplitNetworkPane> splitNetworkPane = new SimpleObjectProperty<>();
	private final InvalidationListener updateListener;

	private final BooleanProperty showScaleBar = new SimpleBooleanProperty(true);


	public SplitsViewPresenter(MainWindow mainWindow, SplitsView splitsView, ObjectProperty<Bounds> targetBounds, ObjectProperty<SplitsBlock> splitsBlock,
							   ObservableMap<Integer, RichTextLabel> taxonLabelMap, ObservableMap<jloda.graph.Node, Group> nodeShapeMap,
							   ObservableMap<Integer, ArrayList<Shape>> splitShapeMap,
							   ObservableList<LoopView> loopViews) {
		this.mainWindow = mainWindow;
		this.splitsView = splitsView;
		this.controller = splitsView.getController();

		splitNetworkPane.addListener((v, o, n) -> {
			controller.getScrollPane().setContent(n);
		});

		controller.getScrollPane().setLockAspectRatio(true);
		controller.getScrollPane().setRequireShiftOrControlToZoom(true);
		controller.getScrollPane().setUpdateScaleMethod(() -> splitsView.setOptionZoomFactor(controller.getScrollPane().getZoomFactorY() * splitsView.getOptionZoomFactor()));

		final ObservableSet<SplitsDiagramType> disabledDiagramTypes = FXCollections.observableSet();

		disabledDiagramTypes.add(SplitsDiagramType.Outline);

		splitsBlock.addListener((v, o, n) -> {
			disabledDiagramTypes.clear();
			if (n == null) {
				disabledDiagramTypes.addAll(List.of(SplitsDiagramType.values()));
			} else {
				if (n.getCompatibility() == Compatibility.compatible || n.getCompatibility() == Compatibility.cyclic) {
					if (splitsView.getOptionDiagram() == SplitsDiagramType.Splits)
						splitsView.setOptionDiagram(SplitsDiagramType.Outline);
				} else {
					disabledDiagramTypes.add(SplitsDiagramType.Outline);
					disabledDiagramTypes.add(SplitsDiagramType.OutlineTopology);
					if (splitsView.getOptionDiagram() == SplitsDiagramType.Outline)
						Platform.runLater(() -> splitsView.setOptionDiagram(SplitsDiagramType.Splits));
					if (splitsView.getOptionDiagram() == SplitsDiagramType.OutlineTopology)
						Platform.runLater(() -> splitsView.setOptionDiagram(SplitsDiagramType.SplitsTopology));
				}
				if (n.getFit() > 0) {
					controller.getFitLabel().setText(StringUtils.removeTrailingZerosAfterDot(String.format("Fit: %.2f", n.getFit())));
				} else
					controller.getFitLabel().setText("");
				if (controller.showInternalLabelsToggleButton().isSelected() && !n.hasConfidenceValues())
					controller.showInternalLabelsToggleButton().setSelected(false);
			}
			controller.showInternalLabelsToggleButton().setDisable(splitsBlock.get() == null || !splitsBlock.get().hasConfidenceValues());
		});

		controller.getDiagramCBox().setButtonCell(ComboBoxUtils.createButtonCell(disabledDiagramTypes, null));
		controller.getDiagramCBox().setCellFactory(ComboBoxUtils.createCellFactory(disabledDiagramTypes, null));
		controller.getDiagramCBox().getItems().addAll(SplitsDiagramType.values());
		controller.getDiagramCBox().valueProperty().bindBidirectional(splitsView.optionDiagramProperty());

		final ObservableSet<SplitsRooting> disabledRootings = FXCollections.observableSet();

		selectionListener = e -> {
			// splitsView.getSplitSelectionModel().clearSelection();

			if (mainWindow.getTaxonSelectionModel().getSelectedItems().size() == 0) {
				disabledRootings.add(SplitsRooting.OutGroup);
				disabledRootings.add(SplitsRooting.OutGroupAlt);
			} else
				disabledRootings.clear();

			splitsView.getSplitSelectionModel().clearSelection();
			if (mainWindow.getTaxonSelectionModel().size() > 0) {
				var workingTaxaBlock = mainWindow.getWorkflow().getWorkingTaxaBlock();
				var set = BitSetUtils.asBitSet(mainWindow.getTaxonSelectionModel().getSelectedItems().stream().map(workingTaxaBlock::indexOf).collect(Collectors.toList()));
				var first = set.nextSetBit(0);
				if (first != -1) {
					var split = splitsBlock.get().getSplits().parallelStream().filter(s -> BitSetUtils.contains(s.getPartContaining(first), set)).min(Comparator.comparingInt(a -> a.getPartContaining(first).cardinality()));
					if (split.isPresent()) {
						var splitId = splitsBlock.get().indexOf(split.get());
						splitsView.getSplitSelectionModel().select(splitId);
					}
				}
			}
		};
		if (mainWindow.getTaxonSelectionModel().getSelectedItems().size() == 0) {
			disabledRootings.add(SplitsRooting.OutGroup);
			disabledRootings.add(SplitsRooting.OutGroupAlt);
		}
		mainWindow.getTaxonSelectionModel().getSelectedItems().addListener(new WeakInvalidationListener(selectionListener));

		controller.getRootingCBox().setButtonCell(ComboBoxUtils.createButtonCell(disabledRootings, null));
		controller.getRootingCBox().setCellFactory(ComboBoxUtils.createCellFactory(disabledRootings, null));
		controller.getRootingCBox().getItems().addAll(SplitsRooting.values());
		controller.getRootingCBox().valueProperty().bindBidirectional(splitsView.optionRootingProperty());

		controller.getOrientationCBox().setButtonCell(ComboBoxUtils.createButtonCell(null, LayoutOrientation::createLabel));
		controller.getOrientationCBox().setCellFactory(ComboBoxUtils.createCellFactory(null, LayoutOrientation::createLabel));
		controller.getOrientationCBox().getItems().addAll(LayoutOrientation.values());
		controller.getOrientationCBox().valueProperty().bindBidirectional(splitsView.optionOrientationProperty());

		controller.showInternalLabelsToggleButton().selectedProperty().bindBidirectional(splitsView.optionShowConfidenceProperty());

		controller.getScaleBar().visibleProperty().bind((splitsView.optionDiagramProperty().isEqualTo(SplitsDiagramType.Outline).or(splitsView.optionDiagramProperty().isEqualTo(SplitsDiagramType.Splits)))
				.and(splitsView.emptyProperty().not()).and(showScaleBar));
		controller.getScaleBar().factorXProperty().bind(splitsView.optionZoomFactorProperty());

		controller.getFitLabel().visibleProperty().bind(controller.getScaleBar().visibleProperty());

		var boxDimension = new SimpleObjectProperty<Dimension2D>();
		targetBounds.addListener((v, o, n) -> boxDimension.set(new Dimension2D(n.getWidth() - 40, n.getHeight() - 80)));

		var first = new Single<>(true);

		var mouseInteraction = new MouseInteraction(mainWindow.getStage(), splitsView.getUndoManager(), mainWindow.getTaxonSelectionModel(), splitsView.getSplitSelectionModel());

		updateListener = e -> {
			if (first.get())
				first.set(false);
			else
				SplitNetworkEdits.clearEdits(splitsView.optionEditsProperty());

			var pane = new SplitNetworkPane(mainWindow, mainWindow.getWorkflow().getWorkingTaxaBlock(), splitsBlock.get(), mainWindow.getTaxonSelectionModel(),
					splitsView.getSplitSelectionModel(), boxDimension.get().getWidth(), boxDimension.get().getHeight(), splitsView.getOptionDiagram(), splitsView.optionOrientationProperty(),
					splitsView.getOptionRooting(), splitsView.getOptionRootAngle(), splitsView.optionZoomFactorProperty(), splitsView.optionFontScaleFactorProperty(),
					splitsView.optionShowConfidenceProperty(), controller.getScaleBar().unitLengthXProperty(),
					taxonLabelMap, nodeShapeMap, splitShapeMap, loopViews);

			splitNetworkPane.set(pane);

			pane.setRunAfterUpdate(() -> {
				var taxa = mainWindow.getWorkflow().getWorkingTaxaBlock();
				var splits = splitsBlock.get();
				mouseInteraction.setup(taxonLabelMap, nodeShapeMap, splitShapeMap, taxa::get, taxa::indexOf, splits::get);

				for (var label : BasicFX.getAllRecursively(pane, RichTextLabel.class)) {
					label.setOnContextMenuRequested(m -> showContextMenu(m, mainWindow.getStage(), splitsView.getUndoManager(), label));
					if (label.getUserData() instanceof Shape shape)
						shape.setOnContextMenuRequested(m -> showContextMenu(m, mainWindow.getStage(), splitsView.getUndoManager(), label));
				}
				if (splitsView.getOptionDiagram().isOutline()) {
					for (var loop : loopViews) {
						loop.setFill(splitsView.getOptionOutlineFill());
					}
				}
				if (splitsView.getOptionEdits().length > 0) {
					SplitNetworkEdits.applyEdits(splitsView.getOptionEdits(), nodeShapeMap, splitShapeMap);
				}
				updateCounter.set(updateCounter.get() + 1);
			});
			pane.drawNetwork();
		};

		splitsView.optionOutlineFillProperty().addListener((v, o, n) -> {
			if (splitsView.getOptionDiagram().isOutline()) {
				for (var loop : loopViews) {
					loop.setFill(splitsView.getOptionOutlineFill());
				}
			}
		});

		splitsView.optionFontScaleFactorProperty().addListener(e -> {
			if (splitNetworkPane.get() != null)
				ProgramExecutorService.submit(100, () -> Platform.runLater(() -> splitNetworkPane.get().layoutLabels(splitsView.getOptionOrientation())));
		});

		splitsBlock.addListener(updateListener);
		splitsView.optionDiagramProperty().addListener(updateListener);
		splitsView.optionRootingProperty().addListener(updateListener);

		controller.getZoomInButton().setOnAction(e -> splitsView.setOptionZoomFactor(1.1 * splitsView.getOptionZoomFactor()));
		controller.getZoomInButton().disableProperty().bind(splitsView.emptyProperty().or(splitsView.optionZoomFactorProperty().greaterThan(8.0 / 1.1)));
		controller.getZoomOutButton().setOnAction(e -> splitsView.setOptionZoomFactor((1.0 / 1.1) * splitsView.getOptionZoomFactor()));
		controller.getZoomOutButton().disableProperty().bind(splitsView.emptyProperty());

		controller.getIncreaseFontButton().setOnAction(e -> splitsView.setOptionFontScaleFactor(1.2 * splitsView.getOptionFontScaleFactor()));
		controller.getIncreaseFontButton().disableProperty().bind(splitsView.emptyProperty());
		controller.getDecreaseFontButton().setOnAction(e -> splitsView.setOptionFontScaleFactor((1.0 / 1.2) * splitsView.getOptionFontScaleFactor()));
		controller.getDecreaseFontButton().disableProperty().bind(splitsView.emptyProperty());

		findToolBar = FindReplaceTaxa.create(mainWindow, splitsView.getUndoManager());
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

		splitsView.viewTabProperty().addListener((v, o, n) -> {
			if (n != null) {
				controller.getvBox().getChildren().add(0, n.getAlgorithmBreadCrumbsToolBar());
			}
		});
		splitsView.emptyProperty().addListener(e -> splitsView.getRoot().setDisable(splitsView.emptyProperty().get()));

		var undoManager = splitsView.getUndoManager();

		splitsView.optionRootAngleProperty().addListener((c, o, n) -> undoManager.add("root angle", splitsView.optionRootAngleProperty(), o, n));
		splitsView.optionRootingProperty().addListener((c, o, n) -> undoManager.add("rooting", splitsView.optionRootingProperty(), o, n));
		splitsView.optionDiagramProperty().addListener((v, o, n) -> undoManager.add("diagram", splitsView.optionDiagramProperty(), o, n));
		splitsView.optionOrientationProperty().addListener((v, o, n) -> undoManager.add("orientation", splitsView.optionOrientationProperty(), o, n));
		splitsView.optionFontScaleFactorProperty().addListener((v, o, n) -> undoManager.add("font size", splitsView.optionFontScaleFactorProperty(), o, n));
		splitsView.optionZoomFactorProperty().addListener((v, o, n) -> undoManager.add("zoom factor", splitsView.optionZoomFactorProperty(), o, n));

		Platform.runLater(this::setupMenuItems);
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
		mainController.getCopyMenuItem().disableProperty().bind(mainWindow.getTaxonSelectionModel().sizeProperty().isEqualTo(0).or(mainWindow.getStage().focusedProperty().not()));

		mainController.getCutMenuItem().disableProperty().bind(new SimpleBooleanProperty(true));
		mainController.getCopyNewickMenuItem().disableProperty().bind(new SimpleBooleanProperty(true));

		mainController.getPasteMenuItem().disableProperty().bind(new SimpleBooleanProperty(true));

		mainWindow.getController().getIncreaseFontSizeMenuItem().setOnAction(controller.getIncreaseFontButton().getOnAction());
		mainWindow.getController().getIncreaseFontSizeMenuItem().disableProperty().bind(controller.getIncreaseFontButton().disableProperty());
		mainWindow.getController().getDecreaseFontSizeMenuItem().setOnAction(controller.getDecreaseFontButton().getOnAction());
		mainWindow.getController().getDecreaseFontSizeMenuItem().disableProperty().bind(controller.getDecreaseFontButton().disableProperty());


		mainController.getZoomInMenuItem().setOnAction(controller.getZoomInButton().getOnAction());
		mainController.getZoomInMenuItem().disableProperty().bind(controller.getZoomOutButton().disableProperty());

		mainController.getZoomOutMenuItem().setOnAction(controller.getZoomOutButton().getOnAction());
		mainController.getZoomOutMenuItem().disableProperty().bind(controller.getZoomOutButton().disableProperty());

		mainController.getFindMenuItem().setOnAction(e -> findToolBar.setShowFindToolBar(true));
		mainController.getFindAgainMenuItem().setOnAction(e -> findToolBar.findAgain());
		mainController.getFindAgainMenuItem().disableProperty().bind(findToolBar.canFindAgainProperty().not());
		mainController.getReplaceMenuItem().setOnAction(e -> findToolBar.setShowReplaceToolBar(true));

		mainController.getSelectAllMenuItem().setOnAction(e -> {
			mainWindow.getTaxonSelectionModel().selectAll(mainWindow.getWorkflow().getWorkingTaxaBlock().getTaxa());
			splitsView.getSplitSelectionModel().selectAll(IteratorUtils.asList(BitSetUtils.range(1, splitsView.getSplitsBlock().getNsplits() + 1)));
		});
		mainController.getSelectNoneMenuItem().setOnAction(e -> {
			mainWindow.getTaxonSelectionModel().clearSelection();
			splitsView.getSplitSelectionModel().clearSelection();
		});
		mainController.getSelectNoneMenuItem().disableProperty().bind(mainWindow.getTaxonSelectionModel().sizeProperty().isEqualTo(0));

		mainController.getLayoutLabelsMenuItem().setOnAction(e -> updateLabelLayout());
		mainController.getLayoutLabelsMenuItem().disableProperty().bind(splitNetworkPane.isNull());

		mainController.getShowScaleBarMenuItem().selectedProperty().bindBidirectional(showScaleBar);
		mainController.getShowScaleBarMenuItem().disableProperty().bind(splitsView.optionDiagramProperty().isEqualTo(SplitsDiagramType.SplitsTopology).or(splitsView.optionDiagramProperty().isEqualTo(SplitsDiagramType.OutlineTopology)));

		mainController.getRotateLeftMenuItem().setOnAction(e -> {
			if (splitsView.getSplitSelectionModel().size() == 0)
				splitsView.setOptionOrientation(splitsView.getOptionOrientation().getRotateLeft());
			else
				splitsView.getSplitsFormat().getPresenter().rotateSplitsLeft();
		});
		mainController.getRotateLeftMenuItem().disableProperty().bind(splitsView.emptyProperty());
		mainController.getRotateRightMenuItem().setOnAction(e -> {
			if (splitsView.getSplitSelectionModel().size() == 0)
				splitsView.setOptionOrientation(splitsView.getOptionOrientation().getRotateRight());
			else
				splitsView.getSplitsFormat().getPresenter().rotateSplitsRight();
		});
		mainController.getRotateRightMenuItem().disableProperty().bind(splitsView.emptyProperty());
		mainController.getFlipMenuItem().setOnAction(e -> splitsView.setOptionOrientation(splitsView.getOptionOrientation().getFlip()));
		mainController.getFlipMenuItem().disableProperty().bind(splitsView.emptyProperty());
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

	public void updateLabelLayout() {
		if (splitNetworkPane.get() != null)
			Platform.runLater(() -> splitNetworkPane.get().layoutLabels(splitsView.getOptionOrientation()));
	}


	public ReadOnlyLongProperty updateCounterProperty() {
		return updateCounter;
	}
}
