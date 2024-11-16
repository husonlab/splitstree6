/*
 *  SplitsViewPresenter.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.view.splits.viewer;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.*;
import javafx.geometry.Bounds;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.ImageView;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.shape.Shape;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Pair;
import jloda.fx.control.RichTextLabel;
import jloda.fx.find.FindToolBar;
import jloda.fx.qr.QRViewUtils;
import jloda.fx.undo.UndoManager;
import jloda.fx.util.BasicFX;
import jloda.fx.util.ClipboardUtils;
import jloda.fx.util.RunAfterAWhile;
import jloda.fx.util.SwipeUtils;
import jloda.util.*;
import splitstree6.algorithms.utils.CharactersUtilities;
import splitstree6.data.CharactersBlock;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.Taxon;
import splitstree6.layout.ScaleUtils;
import splitstree6.layout.splits.LabelSplitsBy;
import splitstree6.layout.splits.LoopView;
import splitstree6.layout.splits.SplitsDiagramType;
import splitstree6.layout.splits.SplitsRooting;
import splitstree6.layout.tree.LabeledNodeShape;
import splitstree6.layout.tree.LayoutOrientation;
import splitstree6.layout.tree.PaneLabel;
import splitstree6.main.SplitsTree6;
import splitstree6.qr.SplitNewickQR;
import splitstree6.splits.Compatibility;
import splitstree6.splits.SplitNewick;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.view.findreplace.FindReplaceTaxa;
import splitstree6.view.utils.ComboBoxUtils;
import splitstree6.view.utils.ExportUtils;
import splitstree6.view.utils.NodeLabelDialog;
import splitstree6.window.MainWindow;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * splits network presenter
 * Daniel Huson 1.2022
 */
public class SplitsViewPresenter implements IDisplayTabPresenter {
	private final LongProperty updateCounter = new SimpleLongProperty(0L);

	private final MainWindow mainWindow;
	private final SplitsView view;
	private final SplitsViewController controller;

	private final FindToolBar findToolBar;

	private final InvalidationListener selectionListener;

	private final InvalidationListener updateListener;

	private final BooleanProperty showScaleBar = new SimpleBooleanProperty(true);

	private final SplitNetworkPane splitNetworkPane;

	private final SetChangeListener<Taxon> selectionChangeListener;

	private final Single<Double> oldZoom = new Single<>(null);

	private final Object sync1 = new Object();

	private final Object sync2 = new Object();

	/**
	 * the splits view presenter
	 *
	 * @param mainWindow
	 * @param view
	 * @param targetBounds
	 * @param splitsBlock
	 * @param taxonLabelMap
	 * @param nodeLabeledShapeMap
	 * @param splitShapeMap
	 * @param loopViews
	 */
	public SplitsViewPresenter(MainWindow mainWindow, SplitsView view, ObjectProperty<Bounds> targetBounds, ObjectProperty<SplitsBlock> splitsBlock,
							   ObservableMap<Integer, RichTextLabel> taxonLabelMap, ObservableMap<jloda.graph.Node, LabeledNodeShape> nodeLabeledShapeMap,
							   ObservableMap<Integer, ArrayList<Shape>> splitShapeMap,
							   ObservableList<LoopView> loopViews) {
		this.mainWindow = mainWindow;
		this.view = view;
		this.controller = view.getController();

		controller.getScrollPane().setLockAspectRatio(true);
		controller.getScrollPane().setRequireShiftOrControlToZoom(false);
		controller.getScrollPane().setPannable(true);
		controller.getScrollPane().setUpdateScaleMethod(() -> view.setOptionZoomFactor(controller.getScrollPane().getZoomFactorY() * view.getOptionZoomFactor()));

		final ObservableSet<SplitsDiagramType> disabledDiagramTypes = FXCollections.observableSet();

		disabledDiagramTypes.add(SplitsDiagramType.Outline);

		splitsBlock.addListener((v, o, n) -> {
			disabledDiagramTypes.clear();
			if (n == null) {
				disabledDiagramTypes.addAll(List.of(SplitsDiagramType.values()));
			} else {
				if (n.getCompatibility() != Compatibility.compatible && n.getCompatibility() != Compatibility.cyclic) {
					disabledDiagramTypes.add(SplitsDiagramType.Outline);
					disabledDiagramTypes.add(SplitsDiagramType.OutlineTopology);
					if (view.getOptionDiagram() == SplitsDiagramType.Outline)
						Platform.runLater(() -> view.setOptionDiagram(SplitsDiagramType.Splits));
					if (view.getOptionDiagram() == SplitsDiagramType.OutlineTopology)
						Platform.runLater(() -> view.setOptionDiagram(SplitsDiagramType.SplitsTopology));
				}
				if (n.getFit() > 0) {
					controller.getFitLabel().setText(String.format("Fit: %.1f", n.getFit()));
				} else
					controller.getFitLabel().setText("");
				if (!n.hasConfidenceValues() && view.getOptionLabelSplitsBy() == LabelSplitsBy.Confidence)
					view.setOptionLabelSplitsBy(LabelSplitsBy.None);
			}
		});

		controller.getDiagramCBox().setButtonCell(ComboBoxUtils.createButtonCell(disabledDiagramTypes, null));
		controller.getDiagramCBox().setCellFactory(ComboBoxUtils.createCellFactory(disabledDiagramTypes, null));
		controller.getDiagramCBox().getItems().addAll(SplitsDiagramType.values());
		controller.getDiagramCBox().valueProperty().bindBidirectional(view.optionDiagramProperty());

		final ObservableSet<SplitsRooting> disabledRootings = FXCollections.observableSet();

		selectionListener = e -> {
			// splitsView.getSplitSelectionModel().clearSelection();

			if (mainWindow.getTaxonSelectionModel().getSelectedItems().isEmpty()) {
				disabledRootings.add(SplitsRooting.OutGroup);
				disabledRootings.add(SplitsRooting.OutGroupAlt);
			} else
				disabledRootings.clear();

			view.getSplitSelectionModel().clearSelection();
			if (mainWindow.getTaxonSelectionModel().size() > 0) {
				var workingTaxaBlock = mainWindow.getWorkflow().getWorkingTaxaBlock();
				var set = BitSetUtils.asBitSet(mainWindow.getTaxonSelectionModel().getSelectedItems().stream().map(workingTaxaBlock::indexOf).filter(t -> t >= 0).collect(Collectors.toList()));
				var first = set.nextSetBit(0);
				if (first != -1) {
					var split = splitsBlock.get().getSplits().parallelStream().filter(s -> BitSetUtils.contains(s.getPartContaining(first), set)).min(Comparator.comparingInt(a -> a.getPartContaining(first).cardinality()));
					if (split.isPresent()) {
						var splitId = splitsBlock.get().indexOf(split.get());
						view.getSplitSelectionModel().select(splitId);
					}
				}
			}
		};
		if (mainWindow.getTaxonSelectionModel().getSelectedItems().isEmpty()) {
			disabledRootings.add(SplitsRooting.OutGroup);
			disabledRootings.add(SplitsRooting.OutGroupAlt);
		}
		mainWindow.getTaxonSelectionModel().getSelectedItems().addListener(new WeakInvalidationListener(selectionListener));

		controller.getRootingCBox().setButtonCell(ComboBoxUtils.createButtonCell(disabledRootings, null));
		controller.getRootingCBox().setCellFactory(ComboBoxUtils.createCellFactory(disabledRootings, null));
		controller.getRootingCBox().getItems().addAll(SplitsRooting.values());
		controller.getRootingCBox().valueProperty().bindBidirectional(view.optionRootingProperty());

		{
			var labelProperty = new SimpleStringProperty();
			BasicFX.makeMultiStateToggle(controller.getShowScalarBarFitToggle(), view.getOptionPaneLabel().label(), labelProperty, "-", "s");
			labelProperty.addListener((v, o, n) -> {
				var value = PaneLabel.valueOfLabel(n);
				showScaleBar.set(value.showScaleBar());
				view.setOptionPaneLabel(value);
			});
			showScaleBar.set(view.getOptionPaneLabel().showScaleBar());
		}
		view.optionPaneLabelProperty().addListener((v, o, n) -> {
			PaneLabel.setLabel(view.getSplitsBlock(), n, controller.getFitLabel());
		});
		view.splitsBlockProperty().addListener((v, o, n) -> PaneLabel.setLabel(n, view.getOptionPaneLabel(), controller.getFitLabel()));
		PaneLabel.setLabel(view.getSplitsBlock(), view.getOptionPaneLabel(), controller.getFitLabel());

		controller.getScaleBar().visibleProperty().bind((view.optionDiagramProperty().isEqualTo(SplitsDiagramType.Outline).or(view.optionDiagramProperty().isEqualTo(SplitsDiagramType.Splits)))
				.and(view.emptyProperty().not()).and(showScaleBar));
		controller.getScaleBar().factorXProperty().bind(view.optionZoomFactorProperty());

		controller.getSetScaleRatioButton().setOnAction(e -> {
			var currentValue = 100.0 / (controller.getScaleBar().getUnitLengthX() * view.getOptionZoomFactor());
			var dialog = new TextInputDialog(StringUtils.removeTrailingZerosAfterDot(currentValue));
			dialog.setTitle("Set Scale Ratio - SplitsTree App");
			dialog.setHeaderText("Define the length representation per 100 pixels");
			dialog.setContentText("Length per 100 pixels:");
			dialog.initOwner(mainWindow.getStage());
			dialog.initModality(Modality.APPLICATION_MODAL);
			dialog.showAndWait().ifPresent(resultString -> {
				if (NumberUtils.isDouble(resultString)) {
					var result = NumberUtils.parseDouble(resultString);
					if (result > 0) {
						Platform.runLater(() -> {
							var newValue = 100.0 / (result * controller.getScaleBar().getUnitLengthX());
							view.setOptionZoomFactor(newValue);
						});
					}
				}
			});
		});

		if (false) {
			BasicFX.reportChanges("unitLengthXProperty", controller.getScaleBar().unitLengthXProperty());
			BasicFX.reportChanges("optionZoomFactorProperty", view.optionZoomFactorProperty());
		}

		controller.getFitLabel().visibleProperty().bind(controller.getScaleBar().visibleProperty());

		// setup QR-code:
		var data = new SimpleObjectProperty<Pair<TaxaBlock, SplitsBlock>>();
		data.bind(Bindings.createObjectBinding(() -> new Pair<>(mainWindow.getWorkflow().getWorkingTaxaBlock(), view.getSplitsBlock()), mainWindow.workingTaxaProperty(), view.splitsBlockProperty(), updateCounter));
		var qrImageView = new SimpleObjectProperty<ImageView>();
		QRViewUtils.setup(controller.getInnerAnchorPane(), data, SplitNewickQR.createFunction(), qrImageView, view.optionShowQRCodeProperty());

		var paneWidth = new SimpleDoubleProperty();
		var paneHeight = new SimpleDoubleProperty();
		targetBounds.addListener((v, o, n) -> {
			paneWidth.set(n.getWidth() - 40);
			paneHeight.set(n.getHeight() - 80);
		});

		splitNetworkPane = new SplitNetworkPane(mainWindow, mainWindow.workingTaxaProperty(), splitsBlock, mainWindow.getTaxonSelectionModel(),
				view.getSplitSelectionModel(), paneWidth, paneHeight, view.optionDiagramProperty(), view.optionOrientationProperty(),
				view.optionRootingProperty(), view.optionRootAngleProperty(), view.optionFontScaleFactorProperty(),
				view.optionLabelSplitsByProperty(), controller.getScaleBar().unitLengthXProperty(),
				taxonLabelMap, nodeLabeledShapeMap, splitShapeMap, loopViews);

		var mouseInteraction = new InteractionSetup(mainWindow.getStage(), splitNetworkPane, view.getUndoManager(), mainWindow.getTaxonSelectionModel(), view.getSplitSelectionModel());

		// todo: before update: reset zoom???

		splitNetworkPane.setRunAfterUpdate(() -> {
			var taxa = mainWindow.getWorkingTaxa();
			var splits = splitsBlock.get();
			mouseInteraction.setup(taxonLabelMap, nodeLabeledShapeMap, splitShapeMap, taxa::get, taxa::indexOf,
					id -> id >= 1 && id <= splits.getNsplits() ? splits.get(id) : null, view);

			for (var label : BasicFX.getAllRecursively(splitNetworkPane, RichTextLabel.class)) {
				label.setOnContextMenuRequested(m -> showContextMenu(m, mainWindow.getStage(), view.getUndoManager(), label));
				if (label.getUserData() instanceof Shape shape)
					shape.setOnContextMenuRequested(m -> showContextMenu(m, mainWindow.getStage(), view.getUndoManager(), label));
			}
			if (view.getOptionDiagram().isOutline()) {
				for (var loop : loopViews) {
					loop.setFill(view.getOptionOutlineFill());
				}
			}
			if (view.getOptionEdits().length > 0) {
				SplitNetworkEdits.applyEdits(view.getOptionEdits(), nodeLabeledShapeMap, splitShapeMap);
				Platform.runLater(() -> SplitNetworkEdits.clearEdits(view.optionEditsProperty()));
			}
			updateCounter.set(updateCounter.get() + 1);
		});

		view.optionZoomFactorProperty().addListener((v, o, n) -> {
			var zoomFactor = n.doubleValue() / o.doubleValue();
			if (zoomFactor > 0 && zoomFactor != 1.0) {
				if (oldZoom.get() == null) {
					oldZoom.set(o.doubleValue());
				}
				RunAfterAWhile.applyInFXThread(sync1, () -> {
					if (oldZoom.isNotNull()) {
						var factor = n.doubleValue() / oldZoom.get();
						if (factor > 0 && factor != 1.0) {
							view.getUndoManager().add("Zoom",
									() -> ScaleUtils.scaleTranslate(controller.getScrollPane().getContent(), a -> a.getId() != null && a.getId().equals("graph-node"), 1.0 / factor, 1.0 / factor),
									() -> ScaleUtils.scaleTranslate(controller.getScrollPane().getContent(), a -> a.getId() != null && a.getId().equals("graph-node"), factor, factor));
							oldZoom.set(null);
						}
					}
				});
				ScaleUtils.scaleTranslate(controller.getScrollPane().getContent(), a -> a.getId() != null && a.getId().equals("graph-node"), zoomFactor, zoomFactor);
			}
		});

		controller.getScrollPane().setContent(splitNetworkPane);
		controller.getScrollPane().setUpdateScaleMethod(() -> view.setOptionZoomFactor(view.getOptionZoomFactor() * controller.getScrollPane().getZoomFactorY()));

		updateListener = e -> RunAfterAWhile.apply(splitNetworkPane, () -> Platform.runLater(splitNetworkPane::drawNetwork));

		view.optionOutlineFillProperty().addListener((v, o, n) -> {
			if (view.getOptionDiagram().isOutline()) {
				for (var loop : loopViews) {
					loop.setFill(view.getOptionOutlineFill());
				}
			}
		});

		view.optionFontScaleFactorProperty().addListener(e -> {
			ProgramExecutorService.submit(100, () -> Platform.runLater(() -> splitNetworkPane.layoutLabels(view.getOptionOrientation())));
		});

		splitsBlock.addListener(updateListener);
		view.optionDiagramProperty().addListener(updateListener);
		view.optionRootingProperty().addListener(updateListener);

		controller.getZoomInButton().setOnAction(e -> view.setOptionZoomFactor(1.1 * view.getOptionZoomFactor()));
		controller.getZoomInButton().disableProperty().bind(view.emptyProperty().or(view.optionZoomFactorProperty().greaterThan(8.0 / 1.1)));
		controller.getZoomOutButton().setOnAction(e -> view.setOptionZoomFactor((1.0 / 1.1) * view.getOptionZoomFactor()));
		controller.getZoomOutButton().disableProperty().bind(view.emptyProperty());

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

		view.optionRootAngleProperty().addListener((c, o, n) -> undoManager.add("root angle", view.optionRootAngleProperty(), o, n));
		view.optionRootingProperty().addListener((c, o, n) -> undoManager.add("rooting", view.optionRootingProperty(), o, n));
		view.optionDiagramProperty().addListener((v, o, n) -> undoManager.add("diagram", view.optionDiagramProperty(), o, n));
		view.optionOrientationProperty().addListener((v, o, n) -> undoManager.add("orientation", view.optionOrientationProperty(), o, n));
		view.optionFontScaleFactorProperty().addListener((v, o, n) -> undoManager.add("font size", view.optionFontScaleFactorProperty(), o, n));

		view.optionZoomFactorProperty().addListener(e -> {
			if (controller.getZoomButtonPane() != null)
				controller.getZoomButtonPane().show();
		});

		selectionChangeListener = e -> {
			if (e.wasAdded()) {
				RunAfterAWhile.applyInFXThreadOrClearIfAlreadyWaiting(sync2, () -> {
					var taxon = e.getElementAdded();
					var node = taxonLabelMap.get(mainWindow.getWorkingTaxa().indexOf(taxon));
					controller.getScrollPane().ensureVisible(node);
				});
			}
		};
		mainWindow.getTaxonSelectionModel().getSelectedItems().addListener(new WeakSetChangeListener<>(selectionChangeListener));

		SwipeUtils.setOnSwipeLeft(controller.getAnchorPane(), () -> controller.getFlipHorizontalButton().fire());
		SwipeUtils.setOnSwipeRight(controller.getAnchorPane(), () -> controller.getFlipHorizontalButton().fire());
		SwipeUtils.setOnSwipeUp(controller.getAnchorPane(), () -> controller.getFlipVerticalButton().fire());
		SwipeUtils.setOnSwipeDown(controller.getAnchorPane(), () -> controller.getFlipVerticalButton().fire());

		Platform.runLater(this::setupMenuItems);
	}

	public void setupMenuItems() {
		var mainController = mainWindow.getController();

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
			try {
				ClipboardUtils.putString(SplitNewick.toString(t -> mainWindow.getWorkingTaxa().get(t).getName(),
						view.getSplitsBlock().getSplits(), true, false) + ";\n");
			} catch (IOException ignored) {
			}
		});
		mainWindow.getController().getCopyNewickMenuItem().disableProperty().bind(view.emptyProperty());

		mainController.getPasteMenuItem().disableProperty().bind(new SimpleBooleanProperty(true));

		mainWindow.getController().getIncreaseFontSizeMenuItem().setOnAction(e -> view.setOptionFontScaleFactor(1.2 * view.getOptionFontScaleFactor()));
		mainWindow.getController().getIncreaseFontSizeMenuItem().disableProperty().bind(view.emptyProperty());
		mainWindow.getController().getDecreaseFontSizeMenuItem().setOnAction(e -> view.setOptionFontScaleFactor((1.0 / 1.2) * view.getOptionFontScaleFactor()));
		mainWindow.getController().getDecreaseFontSizeMenuItem().disableProperty().bind(view.emptyProperty());

		mainController.getZoomInMenuItem().setOnAction(controller.getZoomInButton().getOnAction());
		mainController.getZoomInMenuItem().disableProperty().bind(controller.getZoomOutButton().disableProperty());

		mainController.getZoomOutMenuItem().setOnAction(controller.getZoomOutButton().getOnAction());
		mainController.getZoomOutMenuItem().disableProperty().bind(controller.getZoomOutButton().disableProperty());

		mainController.getSelectAllMenuItem().setOnAction(e -> {
			mainWindow.getTaxonSelectionModel().selectAll(mainWindow.getWorkflow().getWorkingTaxaBlock().getTaxa());
			view.getSplitSelectionModel().selectAll(IteratorUtils.asList(BitSetUtils.range(1, view.getSplitsBlock().getNsplits() + 1)));
		});
		mainController.getSelectAllMenuItem().disableProperty().bind(view.emptyProperty());

		mainController.getSelectNoneMenuItem().setOnAction(e -> {
			mainWindow.getTaxonSelectionModel().clearSelection();
			view.getSplitSelectionModel().clearSelection();
		});
		mainController.getSelectNoneMenuItem().disableProperty().bind(mainWindow.getTaxonSelectionModel().sizeProperty().isEqualTo(0));

		mainController.getSelectInverseMenuItem().setOnAction(e -> {
			var selectedSplits = new HashSet<>(view.getSplitSelectionModel().getSelectedItems());
			mainWindow.getWorkflow().getWorkingTaxaBlock().getTaxa().forEach(t -> mainWindow.getTaxonSelectionModel().toggleSelection(t));
			IteratorUtils.asList(BitSetUtils.range(1, view.getSplitsBlock().getNsplits() + 1)).forEach(s -> view.getSplitSelectionModel().setSelected(s, !selectedSplits.contains(s)));
		});
		mainController.getSelectInverseMenuItem().disableProperty().bind(view.emptyProperty());

		mainController.getSelectCompatibleSitesMenuItem().setOnAction(e -> {
			if (mainWindow.getWorkflow().getWorkingDataBlock() instanceof CharactersBlock charactersBlock) {
				var compatible = CharactersUtilities.computeAllCompatible(charactersBlock, view.getSplitsBlock(), view.getSplitSelectionModel().getSelectedItems());
				System.err.printf("Compatible sites (%,d): %s%n", compatible.cardinality(), StringUtils.toString(compatible));
				if (compatible.cardinality() > 0) {
					var alignmentViewer = mainWindow.getAlignmentViewer();
					if (alignmentViewer != null) {
						alignmentViewer.getSelectedSites().clear();
						alignmentViewer.setSelectedSites(compatible);
					}
				}
			}
		});
		mainController.getSelectCompatibleSitesMenuItem().disableProperty().bind(Bindings.createBooleanBinding(
				() -> !(mainWindow.getWorkflow().getWorkingDataBlock() instanceof CharactersBlock && view.getSplitSelectionModel().size() > 0),
				mainWindow.getWorkflow().validProperty(), view.getSplitSelectionModel().getSelectedItems()));

		mainController.getLayoutLabelsMenuItem().setOnAction(e -> updateLabelLayout());
		mainController.getLayoutLabelsMenuItem().disableProperty().bind(view.emptyProperty());

		mainController.setupSingleBidirectionalBinding(mainController.getShowScaleBarMenuItem().selectedProperty(), showScaleBar);
		mainController.getShowScaleBarMenuItem().setOnAction(e -> showScaleBar.set(!mainController.getShowScaleBarMenuItem().isSelected()));
		mainController.getShowScaleBarMenuItem().disableProperty().bind(view.optionDiagramProperty().isEqualTo(SplitsDiagramType.SplitsTopology).or(view.optionDiagramProperty().isEqualTo(SplitsDiagramType.OutlineTopology)));

		mainController.setupSingleBidirectionalBinding(mainController.getShowQRCodeMenuItem().selectedProperty(), view.optionShowQRCodeProperty());
		mainController.getShowQRCodeMenuItem().disableProperty().bind(view.emptyProperty());

		mainController.getRotateRightMenuItem().setOnAction(e -> view.setOptionOrientation(LayoutOrientation.valueOf(view.getOptionOrientation()).getRotateRight(5).toString()));
		mainController.getRotateRightMenuItem().disableProperty().bind(splitNetworkPane.changingOrientationProperty());

		mainController.getRotateLeftMenuItem().setOnAction(e -> view.setOptionOrientation(LayoutOrientation.valueOf(view.getOptionOrientation()).getRotateLeft(5).toString()));
		mainController.getRotateLeftMenuItem().disableProperty().bind(splitNetworkPane.changingOrientationProperty());

		controller.getRotateLeftButton().setOnAction(e -> {
			if (!splitNetworkPane.changingOrientationProperty().get())
				mainController.getRotateLeftMenuItem().getOnAction().handle(null);
		});
		controller.getRotateRightButton().setOnAction(e -> {
			if (!splitNetworkPane.changingOrientationProperty().get())
				mainController.getRotateRightMenuItem().getOnAction().handle(null);
		});


		controller.getFlipHorizontalButton().setOnAction(e -> {
			if (!splitNetworkPane.changingOrientationProperty().get()) {
				view.setOptionOrientation(LayoutOrientation.valueOf(view.getOptionOrientation()).getFlipHorizontal().toString());
			}
		});

		mainController.getFlipMenuItem().setOnAction(controller.getFlipHorizontalButton().getOnAction());
		mainController.getFlipMenuItem().disableProperty().bind(splitNetworkPane.changingOrientationProperty());

		controller.getFlipVerticalButton().setOnAction(e -> {
			if (!splitNetworkPane.changingOrientationProperty().get()) {
				view.setOptionOrientation(LayoutOrientation.valueOf(view.getOptionOrientation()).getFlipVertical().toString());
			}
		});

		ExportUtils.setup(mainWindow, view.getViewTab().getDataNode(), view.emptyProperty());
	}

	private static void showContextMenu(ContextMenuEvent event, Stage stage, UndoManager undoManager, RichTextLabel label) {
		var editLabelMenuItem = new MenuItem("Edit Label...");
		editLabelMenuItem.setOnAction(e -> {
			if (false && SplitsTree6.isDesktop()) {
				NodeLabelDialog.apply(undoManager, stage, label);
			} else {
				NodeLabelDialog.apply(undoManager, label, null);
			}
		});
		var menu = new ContextMenu();
		menu.getItems().add(editLabelMenuItem);
		menu.show(label, event.getScreenX(), event.getScreenY());
	}

	public void updateLabelLayout() {
		Platform.runLater(() -> splitNetworkPane.layoutLabels(view.getOptionOrientation()));
	}

	public ReadOnlyLongProperty updateCounterProperty() {
		return updateCounter;
	}

	public SplitNetworkPane getSplitNetworkPane() {
		return splitNetworkPane;
	}

	@Override
	public FindToolBar getFindToolBar() {
		return findToolBar;
	}

	@Override
	public boolean allowFindReplace() {
		return true;
	}

}
