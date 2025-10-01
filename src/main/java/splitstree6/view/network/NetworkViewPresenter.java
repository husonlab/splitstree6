/*
 *  NetworkViewPresenter.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.view.network;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.property.*;
import javafx.collections.ObservableMap;
import javafx.geometry.Bounds;
import jloda.fx.control.RichTextLabel;
import jloda.fx.find.FindToolBar;
import jloda.fx.selection.SelectionModel;
import jloda.fx.selection.SetSelectionModel;
import jloda.fx.util.*;
import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.util.StringUtils;
import splitstree6.data.NetworkBlock;
import splitstree6.layout.network.DiagramType;
import splitstree6.layout.tree.LabeledEdgeShape;
import splitstree6.layout.tree.LabeledNodeShape;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.view.findreplace.FindReplaceTaxa;
import splitstree6.view.utils.ComboBoxUtils;
import splitstree6.view.utils.ExportUtils;
import splitstree6.window.MainWindow;

import java.util.ArrayList;

public class NetworkViewPresenter implements IDisplayTabPresenter {
	private final LongProperty updateCounter = new SimpleLongProperty(0L);

	private final MainWindow mainWindow;
	private final NetworkView view;
	private final NetworkViewController controller;

	private final FindToolBar findToolBar;

	private final InvalidationListener updateListener;

	private final NetworkPane networkPane;

	private final InteractionSetup interactionSetup;

	private final SelectionModel<LabeledNodeShape> networkNodeSelectionModel = new SetSelectionModel<>();
	private final SelectionModel<Node> nodeSelectionModel = new SetSelectionModel<>();
	private final SelectionModel<Edge> edgeSelectionModel = new SetSelectionModel<>();

	private final RubberBandSelector rubberBandSelection;
	private boolean first = true;

	/**
	 * the network view presenter
	 *
	 * @param mainWindow
	 * @param view
	 * @param targetBounds
	 * @param networkBlock
	 * @param taxonLabelMap
	 * @param nodeShapeMap
	 * @param edgeShapeMap
	 */
	public NetworkViewPresenter(MainWindow mainWindow, NetworkView view, ObjectProperty<Bounds> targetBounds, ObjectProperty<NetworkBlock> networkBlock, ObservableMap<Integer, RichTextLabel> taxonLabelMap,
								ObservableMap<Node, LabeledNodeShape> nodeShapeMap, ObservableMap<jloda.graph.Edge, LabeledEdgeShape> edgeShapeMap) {
		this.mainWindow = mainWindow;
		this.view = view;
		this.controller = view.getController();

		targetBounds.addListener((InvalidationListener) e -> {
			if (false) System.err.println("target bounds: " + targetBounds.get());
		});

		controller.getScrollPane().setLockAspectRatio(true);
		controller.getScrollPane().setRequireShiftOrControlToZoom(false);
		controller.getScrollPane().setPannable(true);

		controller.getScrollPane().setUpdateScaleMethod(() -> view.setOptionZoomFactor(controller.getScrollPane().getZoomFactorY() * view.getOptionZoomFactor()));

		controller.getDiagramCBox().setButtonCell(ComboBoxUtils.createButtonCell(null, null));
		controller.getDiagramCBox().setCellFactory(ComboBoxUtils.createCellFactory(null, null));
		controller.getDiagramCBox().getItems().addAll(DiagramType.values());
		controller.getDiagramCBox().valueProperty().bindBidirectional(view.optionDiagramProperty());

		var reopenAfterRotateOrFlipBroken = new SimpleBooleanProperty(false);

		controller.getRotateLeftButton().setOnAction(e -> view.setOptionOrientation(view.getOptionOrientation().getRotateLeft(5)));
		controller.getRotateLeftButton().disableProperty().bind(view.emptyProperty().or(view.emptyProperty()).or(reopenAfterRotateOrFlipBroken));
		controller.getRotateRightButton().setOnAction(e -> view.setOptionOrientation(view.getOptionOrientation().getRotateRight(5)));
		controller.getRotateRightButton().disableProperty().bind(controller.getRotateLeftButton().disableProperty().or(reopenAfterRotateOrFlipBroken));
		controller.getFlipButton().setOnAction(e -> view.setOptionOrientation(view.getOptionOrientation().getFlipHorizontal()));
		controller.getFlipButton().disableProperty().bind(controller.getRotateLeftButton().disableProperty().or(reopenAfterRotateOrFlipBroken));

		var paneWidth = new SimpleDoubleProperty();
		var paneHeight = new SimpleDoubleProperty();
		targetBounds.addListener((v, o, n) -> {
			paneWidth.set(n.getWidth() - 40);
			paneHeight.set(n.getHeight() - 80);
		});


		networkPane = new NetworkPane(mainWindow, mainWindow.workingTaxaProperty(), networkBlock,
				paneWidth, paneHeight, view.optionDiagramProperty(), view.optionOrientationProperty(),
				view.optionZoomFactorProperty(), view.optionFontScaleFactorProperty(),
				taxonLabelMap, nodeShapeMap, edgeShapeMap, view.optionLayoutSeedProperty());

		interactionSetup = new InteractionSetup(mainWindow.getStage(), networkPane, view.getUndoManager(), view.optionEditsProperty(),
				t -> mainWindow.getWorkingTaxa().get(t), nodeSelectionModel, edgeSelectionModel, mainWindow.getTaxonSelectionModel());

		networkPane.setRunBeforeUpdate(() -> {
			nodeSelectionModel.clearSelection();
			edgeSelectionModel.clearSelection();
		});

		networkPane.setRunAfterUpdate(() -> {
			var taxa = mainWindow.getWorkflow().getWorkingTaxaBlock();
			interactionSetup.apply(taxonLabelMap, nodeShapeMap, edgeShapeMap,
					t -> (t >= 1 && t <= taxa.getNtax() ? taxa.get(t) : null), taxa::indexOf);
			if (first) {
				first = false;
				if (view.getOptionEdits().length > 0) {
					AService.run(() -> {
						Thread.sleep(700); // wait long enough for all label layouting to finish
						Platform.runLater(() -> {
							NetworkEdits.applyEdits(view.getOptionEdits(), nodeShapeMap, edgeShapeMap);
							NetworkEdits.clearEdits(view.optionEditsProperty());
						});
						return null;
					}, k -> {
					}, k -> {
					});
				}
			}

			updateCounter.set(updateCounter.get() + 1);
			controller.getInfoLabel().setText(networkBlock.get().getInfoString());
		});


		rubberBandSelection = new RubberBandSelector(networkPane, nodeShapeMap.values(), edgeShapeMap.values(), nodeSelectionModel::clearSelection, edgeSelectionModel::clearSelection,
				shape -> {
					var v = nodeShapeMap.keySet().stream().filter(k -> nodeShapeMap.get(k) == shape).findFirst();
					v.ifPresent(nodeSelectionModel::toggleSelection);
				}, shape -> {
			var e = edgeShapeMap.keySet().stream().filter(k -> edgeShapeMap.get(k) == shape).findFirst();
			e.ifPresent(edgeSelectionModel::toggleSelection);
		});

		controller.getScrollPane().setContent(networkPane);

		updateListener = e -> networkPane.drawNetwork();

		networkBlock.addListener(updateListener);
		view.optionDiagramProperty().addListener(updateListener);

		controller.getNewLayoutButton().setOnAction(e -> {
			var oldValue = view.getOptionLayoutSeed();
			var newValue = oldValue + 7;
			view.getUndoManager().doAndAdd("layout", () -> {
				view.optionLayoutSeedProperty().set(oldValue);
				updateListener.invalidated(null);
			}, () -> {
				view.optionLayoutSeedProperty().set(newValue);
				updateListener.invalidated(null);
			});

		});
		controller.getNewLayoutButton().disableProperty().bind(view.emptyProperty());

		controller.getZoomInButton().setOnAction(e -> view.setOptionZoomFactor(1.1 * view.getOptionZoomFactor()));
		controller.getZoomInButton().disableProperty().bind(view.emptyProperty().or(view.optionZoomFactorProperty().greaterThan(8.0 / 1.1)));
		controller.getZoomOutButton().setOnAction(e -> view.setOptionZoomFactor((1.0 / 1.1) * view.getOptionZoomFactor()));
		controller.getZoomOutButton().disableProperty().bind(view.emptyProperty());

		findToolBar = FindReplaceTaxa.create(mainWindow, view.getUndoManager());
		findToolBar.setShowFindToolBar(false);
		controller.getvBox().getChildren().add(findToolBar);

		view.viewTabProperty().addListener((v, o, n) -> {
			if (n != null) {
				controller.getvBox().getChildren().add(0, n.getAlgorithmBreadCrumbsToolBar());
			}
		});
		view.emptyProperty().addListener(e -> view.getRoot().setDisable(view.emptyProperty().get()));

		var undoManager = view.getUndoManager();

		view.optionDiagramProperty().addListener((v, o, n) -> undoManager.add("diagram", view.optionDiagramProperty(), o, n));
		view.optionOrientationProperty().addListener((v, o, n) -> undoManager.add("orientation", view.optionOrientationProperty(), o, n));
		view.optionFontScaleFactorProperty().addListener((v, o, n) -> undoManager.add("font size", view.optionFontScaleFactorProperty(), o, n));
		view.optionZoomFactorProperty().addListener((v, o, n) -> undoManager.add("zoom factor", view.optionZoomFactorProperty(), o, n));

		SwipeUtils.setOnSwipeLeft(controller.getAnchorPane(), () -> controller.getFlipButton().fire());
		SwipeUtils.setOnSwipeRight(controller.getAnchorPane(), () -> controller.getFlipButton().fire());
		SwipeUtils.setConsumeSwipeUp(controller.getAnchorPane());
		SwipeUtils.setConsumeSwipeDown(controller.getAnchorPane());

		Platform.runLater(this::setupMenuItems);

		RunAfterAWhile.applyInFXThread(this, () -> {
			if (mainWindow.getWorkflow().getWorkingTaxaBlock() != null && mainWindow.getWorkflow().getWorkingTaxaBlock().getTraitsBlock() != null
				&& mainWindow.getWorkflow().getWorkingTaxaBlock().getTraitsBlock().size() > 0) {
				view.optionActiveTraitsProperty().set(mainWindow.getWorkflow().getWorkingTaxaBlock().getTraitsBlock().getTraitLabels().toArray(new String[0]));
			}
		});
	}

	@Override
	public void setupMenuItems() {
		var mainController = mainWindow.getController();

		mainController.getCopyMenuItem().setOnAction(e -> {
			var list = new ArrayList<String>();
			for (var taxon : mainWindow.getTaxonSelectionModel().getSelectedItems()) {
				list.add(RichTextLabel.getRawText(taxon.getDisplayLabelOrName()).trim());
			}
			if (!list.isEmpty()) {
				ClipboardUtils.putString(StringUtils.toString(list, "\n"));
			}
		});
		mainController.getCopyMenuItem().disableProperty().bind(mainWindow.getTaxonSelectionModel().sizeProperty().isEqualTo(0));

		mainController.getCutMenuItem().disableProperty().bind(new SimpleBooleanProperty(true));
		mainController.getCopyNewickMenuItem().disableProperty().bind(new SimpleBooleanProperty(true));

		mainController.getPasteMenuItem().disableProperty().bind(new SimpleBooleanProperty(true));

		mainController.getIncreaseFontSizeMenuItem().setOnAction(e -> view.setOptionFontScaleFactor(1.2 * view.getOptionFontScaleFactor()));
		mainController.getIncreaseFontSizeMenuItem().disableProperty().bind(view.emptyProperty());
		mainController.getDecreaseFontSizeMenuItem().setOnAction(e -> view.setOptionFontScaleFactor((1.0 / 1.2) * view.getOptionFontScaleFactor()));
		mainController.getDecreaseFontSizeMenuItem().disableProperty().bind(view.emptyProperty());

		mainController.getZoomInMenuItem().setOnAction(controller.getZoomInButton().getOnAction());
		mainController.getZoomInMenuItem().disableProperty().bind(controller.getZoomOutButton().disableProperty());

		if (false) {
			mainController.getSelectAllMenuItem().setOnAction(e -> networkNodeSelectionModel.getSelectedItems().addAll(BasicFX.getAllRecursively(view.getMainNode(), LabeledNodeShape.class)));
			mainController.getSelectAllMenuItem().disableProperty().bind(view.emptyProperty());

			mainController.getSelectInverseMenuItem().setOnAction(e -> {
				for (var shape : BasicFX.getAllRecursively(view.getMainNode(), LabeledNodeShape.class)) {
					networkNodeSelectionModel.toggleSelection(shape);
				}
			});
			mainController.getSelectInverseMenuItem().disableProperty().bind(view.emptyProperty());

			mainController.getSelectNoneMenuItem().setOnAction(e -> networkNodeSelectionModel.clearSelection());
			mainController.getSelectNoneMenuItem().disableProperty().bind(view.emptyProperty());
		}

		mainController.getSelectButton().setOnAction(e -> {
			var all = BasicFX.getAllRecursively(view.getMainNode(), LabeledNodeShape.class);
			if (networkNodeSelectionModel.size() < all.size())
				mainController.getSelectAllMenuItem().fire();
			else
				mainController.getSelectNoneMenuItem().fire();
		});
		mainController.getSelectButton().disableProperty().bind(view.emptyProperty());

		mainController.getZoomOutMenuItem().setOnAction(controller.getZoomOutButton().getOnAction());
		mainController.getZoomOutMenuItem().disableProperty().bind(controller.getZoomOutButton().disableProperty());

		mainController.getLayoutLabelsMenuItem().setOnAction(e -> updateLabelLayout());
		mainController.getLayoutLabelsMenuItem().disableProperty().bind(view.emptyProperty());

		mainController.getRotateLeftMenuItem().setOnAction(controller.getRotateLeftButton().getOnAction());
		mainController.getRotateLeftMenuItem().disableProperty().bind(controller.getRotateLeftButton().disableProperty());
		mainController.getRotateRightMenuItem().setOnAction(controller.getRotateRightButton().getOnAction());
		mainController.getRotateRightMenuItem().disableProperty().bind(controller.getRotateRightButton().disableProperty());
		mainController.getFlipMenuItem().setOnAction(controller.getFlipButton().getOnAction());
		mainController.getFlipMenuItem().disableProperty().bind(controller.getFlipButton().disableProperty());

		ExportUtils.setup(mainWindow, view.getViewTab().getDataNode(), view.emptyProperty());
	}

	public LongProperty updateCounterProperty() {
		return updateCounter;
	}

	public void updateLabelLayout() {
		Platform.runLater(() -> networkPane.layoutLabels(view.getOptionOrientation()));
	}

	public FindToolBar getFindToolBar() {
		return findToolBar;
	}

	@Override
	public boolean allowFindReplace() {
		return true;
	}
}
