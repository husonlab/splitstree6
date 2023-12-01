/*
 * NetworkViewPresenter.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.view.network;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.property.*;
import javafx.collections.ObservableMap;
import javafx.collections.SetChangeListener;
import javafx.collections.WeakSetChangeListener;
import javafx.geometry.Bounds;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import jloda.fx.control.RichTextLabel;
import jloda.fx.find.FindToolBar;
import jloda.fx.util.AService;
import jloda.fx.util.RunAfterAWhile;
import jloda.graph.Node;
import jloda.util.StringUtils;
import splitstree6.data.NetworkBlock;
import splitstree6.data.parts.Taxon;
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

	private final SetChangeListener<Taxon> selectionChangeListener;


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

		controller.getScrollPane().setLockAspectRatio(true);
		controller.getScrollPane().setRequireShiftOrControlToZoom(false);
		controller.getScrollPane().setPannable(true);

		controller.getScrollPane().setUpdateScaleMethod(() -> view.setOptionZoomFactor(controller.getScrollPane().getZoomFactorY() * view.getOptionZoomFactor()));

		controller.getDiagramCBox().setButtonCell(ComboBoxUtils.createButtonCell(null, null));
		controller.getDiagramCBox().setCellFactory(ComboBoxUtils.createCellFactory(null, null));
		controller.getDiagramCBox().getItems().addAll(DiagramType.values());
		controller.getDiagramCBox().valueProperty().bindBidirectional(view.optionDiagramProperty());

		controller.getRotateLeftButton().setOnAction(e -> view.setOptionOrientation(view.getOptionOrientation().getRotateLeft()));
		controller.getRotateLeftButton().disableProperty().bind(view.emptyProperty().or(view.emptyProperty()));
		controller.getRotateRightButton().setOnAction(e -> view.setOptionOrientation(view.getOptionOrientation().getRotateRight()));
		controller.getRotateRightButton().disableProperty().bind(controller.getRotateLeftButton().disableProperty());
		controller.getFlipButton().setOnAction(e -> view.setOptionOrientation(view.getOptionOrientation().getFlip()));
		controller.getFlipButton().disableProperty().bind(controller.getRotateLeftButton().disableProperty());

		var paneWidth = new SimpleDoubleProperty();
		var paneHeight = new SimpleDoubleProperty();
		targetBounds.addListener((v, o, n) -> {
			paneWidth.set(n.getWidth() - 40);
			paneHeight.set(n.getHeight() - 80);
		});

		networkPane = new NetworkPane(mainWindow, mainWindow.workingTaxaProperty(), networkBlock, mainWindow.getTaxonSelectionModel(),
				paneWidth, paneHeight, view.optionDiagramProperty(), view.optionOrientationProperty(),
				view.optionZoomFactorProperty(), view.optionFontScaleFactorProperty(),
				taxonLabelMap, nodeShapeMap, edgeShapeMap);

		interactionSetup = new InteractionSetup(mainWindow.getStage(), networkPane, view.getUndoManager(), view.optionEditsProperty(), mainWindow.getTaxonSelectionModel());

		networkPane.setRunAfterUpdate(() -> {
			var taxa = mainWindow.getWorkflow().getWorkingTaxaBlock();
			interactionSetup.apply(taxonLabelMap, nodeShapeMap,
					t -> (t >= 1 && t <= taxa.getNtax() ? taxa.get(t) : null), taxa::indexOf);

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

			updateCounter.set(updateCounter.get() + 1);
			controller.getInfoLabel().setText(networkBlock.get().getInfoString());
		});

		controller.getScrollPane().setContent(networkPane);

		updateListener = e -> networkPane.drawNetwork();

		networkBlock.addListener(updateListener);
		view.optionDiagramProperty().addListener(updateListener);

		controller.getZoomInButton().setOnAction(e -> view.setOptionZoomFactor(1.1 * view.getOptionZoomFactor()));
		controller.getZoomInButton().disableProperty().bind(view.emptyProperty().or(view.optionZoomFactorProperty().greaterThan(8.0 / 1.1)));
		controller.getZoomOutButton().setOnAction(e -> view.setOptionZoomFactor((1.0 / 1.1) * view.getOptionZoomFactor()));
		controller.getZoomOutButton().disableProperty().bind(view.emptyProperty());

		controller.getIncreaseFontButton().setOnAction(e -> view.setOptionFontScaleFactor(1.2 * view.getOptionFontScaleFactor()));
		controller.getIncreaseFontButton().disableProperty().bind(view.emptyProperty());
		controller.getDecreaseFontButton().setOnAction(e -> view.setOptionFontScaleFactor((1.0 / 1.2) * view.getOptionFontScaleFactor()));
		controller.getDecreaseFontButton().disableProperty().bind(view.emptyProperty());

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

		var object = new Object();
		selectionChangeListener = e -> {
			if (e.wasAdded()) {
				RunAfterAWhile.applyInFXThreadOrClearIfAlreadyWaiting(object, () -> {
					var taxon = e.getElementAdded();
					var v = networkBlock.get().getGraph().getTaxon2Node(mainWindow.getWorkingTaxa().indexOf(taxon));
					var node = nodeShapeMap.get(v);
					controller.getScrollPane().ensureVisible(node);
				});
			}
		};
		mainWindow.getTaxonSelectionModel().getSelectedItems().addListener(new WeakSetChangeListener<>(selectionChangeListener));


		Platform.runLater(this::setupMenuItems);
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
