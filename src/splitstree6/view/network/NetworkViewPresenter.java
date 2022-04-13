/*
 * NetworkViewPresenter.java Copyright (C) 2022 Daniel H. Huson
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
import javafx.geometry.Bounds;
import javafx.scene.Group;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import jloda.fx.control.RichTextLabel;
import jloda.fx.find.FindToolBar;
import jloda.fx.util.ResourceManagerFX;
import jloda.graph.Node;
import jloda.util.StringUtils;
import splitstree6.data.NetworkBlock;
import splitstree6.layout.network.DiagramType;
import splitstree6.layout.tree.LayoutOrientation;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.view.findreplace.FindReplaceTaxa;
import splitstree6.view.utils.ComboBoxUtils;
import splitstree6.window.MainWindow;

import java.util.ArrayList;

public class NetworkViewPresenter implements IDisplayTabPresenter {
	private final LongProperty updateCounter = new SimpleLongProperty(0L);

	private final MainWindow mainWindow;
	private final NetworkView networkView;
	private final NetworkViewController controller;

	private final FindToolBar findToolBar;

	private final InvalidationListener updateListener;

	private final NetworkPane networkPane;

	private final MouseInteraction mouseInteraction;

	public NetworkViewPresenter(MainWindow mainWindow, NetworkView networkView, ObjectProperty<Bounds> targetBounds, ObjectProperty<NetworkBlock> networkBlock, ObservableMap<Integer, RichTextLabel> taxonLabelMap,
								ObservableMap<Node, Group> nodeShapeMap, ObservableMap<jloda.graph.Edge, Group> edgeShapeMap) {
		this.mainWindow = mainWindow;
		this.networkView = networkView;
		this.controller = networkView.getController();

		mouseInteraction = new MouseInteraction(mainWindow.getStage(), networkView.getUndoManager(), mainWindow.getTaxonSelectionModel());

		controller.getScrollPane().setLockAspectRatio(true);
		controller.getScrollPane().setRequireShiftOrControlToZoom(true);
		controller.getScrollPane().setUpdateScaleMethod(() -> networkView.setOptionZoomFactor(controller.getScrollPane().getZoomFactorY() * networkView.getOptionZoomFactor()));

		controller.getDiagramCBox().setButtonCell(ComboBoxUtils.createButtonCell(null, null));
		controller.getDiagramCBox().setCellFactory(ComboBoxUtils.createCellFactory(null, null));
		controller.getDiagramCBox().getItems().addAll(DiagramType.values());
		controller.getDiagramCBox().valueProperty().bindBidirectional(networkView.optionDiagramProperty());

		controller.getOrientationCBox().setButtonCell(ComboBoxUtils.createButtonCell(null, LayoutOrientation::createLabel));
		controller.getOrientationCBox().setCellFactory(ComboBoxUtils.createCellFactory(null, LayoutOrientation::createLabel));
		controller.getOrientationCBox().getItems().addAll(LayoutOrientation.values());
		controller.getOrientationCBox().valueProperty().bindBidirectional(networkView.optionOrientationProperty());

		var paneWidth = new SimpleDoubleProperty();
		var paneHeight = new SimpleDoubleProperty();
		targetBounds.addListener((v, o, n) -> {
			paneWidth.set(n.getWidth() - 40);
			paneHeight.set(n.getHeight() - 80);
		});

		networkPane = new NetworkPane(mainWindow, mainWindow.workingTaxaProperty(), networkBlock, mainWindow.getTaxonSelectionModel(),
				paneWidth, paneHeight, networkView.optionDiagramProperty(), networkView.optionOrientationProperty(),
				networkView.optionZoomFactorProperty(), networkView.optionFontScaleFactorProperty(),
				taxonLabelMap, nodeShapeMap, edgeShapeMap);

		networkPane.setRunAfterUpdate(() -> {
			var taxa = mainWindow.getWorkflow().getWorkingTaxaBlock();
			mouseInteraction.setup(taxonLabelMap, nodeShapeMap, taxa::get, taxa::indexOf);

			/*
			if (networkView.getOptionEdits().length > 0) {
				SplitNetworkEdits.applyEdits(networkView.getOptionEdits(), nodeShapeMap, null);
				Platform.runLater(() -> SplitNetworkEdits.clearEdits(networkView.optionEditsProperty()));
			}
			 */
			updateCounter.set(updateCounter.get() + 1);
		});

		controller.getScrollPane().setContent(networkPane);

		updateListener = e -> networkPane.drawNetwork();

		networkBlock.addListener(updateListener);
		networkView.optionDiagramProperty().addListener(updateListener);

		controller.getZoomInButton().setOnAction(e -> networkView.setOptionZoomFactor(1.1 * networkView.getOptionZoomFactor()));
		controller.getZoomInButton().disableProperty().bind(networkView.emptyProperty().or(networkView.optionZoomFactorProperty().greaterThan(8.0 / 1.1)));
		controller.getZoomOutButton().setOnAction(e -> networkView.setOptionZoomFactor((1.0 / 1.1) * networkView.getOptionZoomFactor()));
		controller.getZoomOutButton().disableProperty().bind(networkView.emptyProperty());

		controller.getIncreaseFontButton().setOnAction(e -> networkView.setOptionFontScaleFactor(1.2 * networkView.getOptionFontScaleFactor()));
		controller.getIncreaseFontButton().disableProperty().bind(networkView.emptyProperty());
		controller.getDecreaseFontButton().setOnAction(e -> networkView.setOptionFontScaleFactor((1.0 / 1.2) * networkView.getOptionFontScaleFactor()));
		controller.getDecreaseFontButton().disableProperty().bind(networkView.emptyProperty());

		findToolBar = FindReplaceTaxa.create(mainWindow, networkView.getUndoManager());
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

		networkView.viewTabProperty().addListener((v, o, n) -> {
			if (n != null) {
				controller.getvBox().getChildren().add(0, n.getAlgorithmBreadCrumbsToolBar());
			}
		});
		networkView.emptyProperty().addListener(e -> networkView.getRoot().setDisable(networkView.emptyProperty().get()));

		var undoManager = networkView.getUndoManager();

		networkView.optionDiagramProperty().addListener((v, o, n) -> undoManager.add("diagram", networkView.optionDiagramProperty(), o, n));
		networkView.optionOrientationProperty().addListener((v, o, n) -> undoManager.add("orientation", networkView.optionOrientationProperty(), o, n));
		networkView.optionFontScaleFactorProperty().addListener((v, o, n) -> undoManager.add("font size", networkView.optionFontScaleFactorProperty(), o, n));
		networkView.optionZoomFactorProperty().addListener((v, o, n) -> undoManager.add("zoom factor", networkView.optionZoomFactorProperty(), o, n));

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

		mainController.getLayoutLabelsMenuItem().setOnAction(e -> updateLabelLayout());
		mainController.getLayoutLabelsMenuItem().disableProperty().bind(networkView.emptyProperty());

		mainController.getRotateLeftMenuItem().setOnAction(e -> networkView.setOptionOrientation(networkView.getOptionOrientation().getRotateLeft()));
		mainController.getRotateLeftMenuItem().disableProperty().bind(networkView.emptyProperty());
		mainController.getRotateRightMenuItem().setOnAction(e -> networkView.setOptionOrientation(networkView.getOptionOrientation().getRotateRight()));
		mainController.getRotateRightMenuItem().disableProperty().bind(networkView.emptyProperty());
		mainController.getFlipMenuItem().setOnAction(e -> networkView.setOptionOrientation(networkView.getOptionOrientation().getFlip()));
		mainController.getFlipMenuItem().disableProperty().bind(networkView.emptyProperty());

	}

	public LongProperty updateCounterProperty() {
		return updateCounter;
	}

	public void updateLabelLayout() {
		Platform.runLater(() -> networkPane.layoutLabels(networkView.getOptionOrientation()));
	}
}
