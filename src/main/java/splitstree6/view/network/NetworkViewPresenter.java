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
import javafx.geometry.Point2D;
import javafx.scene.control.ScrollPane;
import jloda.fx.control.RichTextLabel;
import jloda.fx.find.FindToolBar;
import jloda.fx.selection.SelectionModel;
import jloda.fx.selection.SetSelectionModel;
import jloda.fx.util.*;
import jloda.fx.window.NotificationManager;
import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.util.StringUtils;
import splitstree6.data.NetworkBlock;
import javafx.scene.control.Tooltip;
import jloda.fx.icons.MaterialIcons;
import splitstree6.layout.network.DiagramType;
import splitstree6.layout.network.LayoutAlgorithm;
import splitstree6.layout.network.NetworkLayout;
import splitstree6.layout.network.RectilinearLayout;
import splitstree6.layout.tree.LabeledEdgeShape;
import splitstree6.layout.tree.LabeledNodeShape;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.view.findreplace.FindReplaceTaxa;
import splitstree6.view.utils.ExportUtils;
import splitstree6.view.utils.RubberBandSelector;
import splitstree6.window.MainWindow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.ToDoubleFunction;

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

	private final NetworkGrid grid = new NetworkGrid();
	private final BooleanProperty straightening = new SimpleBooleanProperty(this, "straightening", false);

	/**
	 * time allowed for straightening; networks of up to about a hundred nodes need less and then always come out
	 * the same, see {@link RectilinearLayout}
	 */
	private static final long STRAIGHTEN_BUDGET_MILLIS = 2000;

	/**
	 * the network view presenter
	 *
	 * @param view
	 * @param targetBounds
	 * @param networkBlock
	 * @param taxonLabelMap
	 * @param nodeShapeMap
	 * @param edgeShapeMap
	 */
	public NetworkViewPresenter(NetworkView view, ObjectProperty<Bounds> targetBounds, ObjectProperty<NetworkBlock> networkBlock, ObservableMap<Integer, RichTextLabel> taxonLabelMap,
								ObservableMap<Node, LabeledNodeShape> nodeShapeMap, ObservableMap<jloda.graph.Edge, LabeledEdgeShape> edgeShapeMap) {
		this.mainWindow = view.getMainWindow();
		this.view = view;
		this.controller = view.getController();

		controller.getScrollPane().setLockAspectRatio(true);
		controller.getScrollPane().setRequireShiftOrControlToZoom(false);
		controller.getScrollPane().setPannable(true);

		controller.getScrollPane().setUpdateScaleMethod(() -> view.setOptionZoomFactor(controller.getScrollPane().getZoomFactorY() * view.getOptionZoomFactor()));

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
				paneWidth, paneHeight, view.optionDiagramProperty(), view.optionLayoutAlgorithmProperty(), view.optionOrientationProperty(),
				view.optionZoomFactorProperty(), view.optionFontScaleFactorProperty(),
				taxonLabelMap, nodeShapeMap, edgeShapeMap, view.optionLayoutSeedProperty());

		interactionSetup = new InteractionSetup(mainWindow.getStage(), networkPane, view.getUndoManager(), view.optionEditsProperty(),
				t -> mainWindow.getWorkingTaxa().get(t), nodeSelectionModel, edgeSelectionModel, mainWindow.getTaxonSelectionModel(), grid);

		networkPane.setRunBeforeUpdate(() -> {
			nodeSelectionModel.clearSelection();
			edgeSelectionModel.clearSelection();
			if (!first) NetworkEdits.clearEdits(view.optionEditsProperty());
		});

		networkPane.setRunAfterUpdate(() -> {
			var taxa = mainWindow.getWorkflow().getWorkingTaxaBlock();
			interactionSetup.apply(taxonLabelMap, nodeShapeMap, edgeShapeMap,
					t -> (t >= 1 && t <= taxa.getNtax() ? taxa.get(t) : null), taxa::indexOf);
			if (grid.isSnap())
				snapAllToGrid(false); // a new drawing, while the grid is on
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
		view.optionLayoutAlgorithmProperty().addListener(updateListener);

		// The layout algorithm used to be picked by the parity of the layout seed, and reseeding was a button
		// of its own, so that pressing it switched algorithm as well as reseeding. It is an explicit option
		// now, and this toggle is it. The icon swaps with the state, so
		// which algorithm is in use can be read without hovering: a scatter of points for MDS, which places
		// points to match distances, and hub-and-spokes for the force-directed layout.
		var algorithmToggle = controller.getLayoutAlgorithmToggleButton();
		InvalidationListener updateAlgorithmToggle = e -> {
			var forceDirected = (view.getOptionLayoutAlgorithm() == LayoutAlgorithm.ForceDirected);
			algorithmToggle.setSelected(forceDirected);
			MaterialIcons.setIcon(algorithmToggle, forceDirected ? MaterialIcons.hub : MaterialIcons.scatter_plot);
			algorithmToggle.setTooltip(new Tooltip(forceDirected
					? "Layout: force-directed. Fewer crossings on large networks, but takes seconds. Click for MDS"
					: "Layout: MDS. Fast, and fewer crossings on small networks. Click for force-directed"));
		};
		view.optionLayoutAlgorithmProperty().addListener(updateAlgorithmToggle);
		// deferred, as the controller also sets its icons that way: the icon font is not ready any earlier
		Platform.runLater(() -> updateAlgorithmToggle.invalidated(null));
		algorithmToggle.setOnAction(e -> view.setOptionLayoutAlgorithm(algorithmToggle.isSelected() ? LayoutAlgorithm.ForceDirected : LayoutAlgorithm.MDS));
		algorithmToggle.disableProperty().bind(view.emptyProperty());

		// The grid, to make it easy to pull a network into a rectilinear drawing by hand: switching it on snaps all
		// nodes to the grid, as one edit that can be undone, and while it is on, dragged nodes move in grid steps
		var gridToggle = controller.getGridToggleButton();
		gridToggle.selectedProperty().bindBidirectional(grid.snapProperty());
		gridToggle.setOnAction(e -> {
			if (gridToggle.isSelected())
				snapAllToGrid(true);
		});
		gridToggle.setTooltip(new Tooltip("Snap nodes to a grid: aligns all nodes, and dragged nodes then move in grid steps"));
		gridToggle.disableProperty().bind(view.emptyProperty());
		// zooming scales the node positions about the origin, and the grid has to scale with them
		view.optionZoomFactorProperty().addListener((v, o, n) -> grid.scale(n.doubleValue() / o.doubleValue()));

		var straightenButton = controller.getStraightenButton();
		straightenButton.setOnAction(e -> straighten());
		straightenButton.setTooltip(new Tooltip("Straighten: move the nodes on the grid so that edges run horizontally, vertically or diagonally, with few crossings"));
		straightenButton.disableProperty().bind(view.emptyProperty().or(straightening));

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
		view.optionLayoutAlgorithmProperty().addListener((v, o, n) -> undoManager.add("layout algorithm", view.optionLayoutAlgorithmProperty(), o, n));
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

		AdditionalConsoleOutput.setup(view);
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

	/**
	 * snaps all nodes to the grid, after choosing the grid for the current drawing
	 *
	 * @param undoable whether this is an edit that can be undone; undoing it also switches the grid off
	 */
	private void snapAllToGrid(boolean undoable) {
		var networkBlock = view.getNetworkBlock();
		var nodeShapeMap = view.getNodeShapeMap();
		if (networkBlock == null || nodeShapeMap.isEmpty())
			return;
		var graph = networkBlock.getGraph();
		var oldPositions = new LinkedHashMap<LabeledNodeShape, Point2D>(); // in node order, which breaks ties
		for (var v : graph.nodes()) {
			var shape = nodeShapeMap.get(v);
			if (shape != null)
				oldPositions.put(shape, new Point2D(shape.getTranslateX(), shape.getTranslateY()));
		}
		grid.setSpacing(NetworkGrid.computeSpacing(graph, view.getOptionDiagram(), v -> nodeShapeMap.containsKey(v) ? oldPositions.get(nodeShapeMap.get(v)) : null));
		var newPositions = NetworkGrid.snapAll(oldPositions, grid.getSpacing());
		setPositions(newPositions);
		if (undoable) {
			view.getUndoManager().add("snap to grid", () -> {
				grid.setSnap(false);
				setPositions(oldPositions);
			}, () -> {
				grid.setSnap(true);
				setPositions(newPositions);
			});
		}
	}

	/**
	 * straightens the drawing on the grid, see {@link RectilinearLayout}, and switches the grid on, so that the
	 * drawing can then be adjusted by hand in grid steps. The search runs in the background; its result is applied
	 * as one edit that can be undone, unless the drawing changed in the meantime
	 */
	private void straighten() {
		var networkBlock = view.getNetworkBlock();
		var nodeShapeMap = view.getNodeShapeMap();
		if (networkBlock == null || nodeShapeMap.isEmpty())
			return;
		var graph = networkBlock.getGraph();
		var shapes = new LinkedHashMap<Node, LabeledNodeShape>();
		var oldPositions = new LinkedHashMap<LabeledNodeShape, Point2D>(); // in node order, which breaks ties
		for (var v : graph.nodes()) {
			var shape = nodeShapeMap.get(v);
			if (shape == null)
				return; // not drawn yet
			shapes.put(v, shape);
			oldPositions.put(shape, new Point2D(shape.getTranslateX(), shape.getTranslateY()));
		}
		var oldSnap = grid.isSnap();
		var oldSpacing = grid.getSpacing();
		var spacing = (oldSnap && oldSpacing > 0 ? oldSpacing : NetworkGrid.computeSpacing(graph, view.getOptionDiagram(), v -> oldPositions.get(shapes.get(v))));
		var snapped = NetworkGrid.snapAll(oldPositions, spacing);
		var start = new HashMap<Node, RectilinearLayout.GridPoint>();
		for (var v : graph.nodes()) {
			var p = snapped.get(shapes.get(v));
			start.put(v, new RectilinearLayout.GridPoint((int) Math.round(p.getX() / spacing), (int) Math.round(p.getY() / spacing)));
		}
		// target lengths in grid steps: a grid step is half the unit of the lengths the layout gives the edges
		ToDoubleFunction<Edge> targetLength;
		if (view.getOptionDiagram() == DiagramType.Network) {
			var scaling = NetworkLayout.setupScaling(graph);
			targetLength = e -> 2 * scaling.applyAsDouble(e);
		} else
			targetLength = e -> 2.0;
		var search = RectilinearLayout.prepare(graph, start, targetLength); // reads the graph here, on the FX thread

		straightening.set(true);
		AService.run(() -> search.run(STRAIGHTEN_BUDGET_MILLIS), result -> {
			straightening.set(false);
			for (var v : shapes.keySet()) {
				var shape = shapes.get(v);
				if (nodeShapeMap.get(v) != shape || shape.getTranslateX() != oldPositions.get(shape).getX() || shape.getTranslateY() != oldPositions.get(shape).getY())
					return; // redrawn or edited while the search ran
			}
			var newPositions = new LinkedHashMap<LabeledNodeShape, Point2D>();
			for (var v : shapes.keySet())
				newPositions.put(shapes.get(v), new Point2D(spacing * result.get(v).x(), spacing * result.get(v).y()));
			grid.setSpacing(spacing);
			grid.setSnap(true);
			setPositions(newPositions);
			view.getUndoManager().add("straighten", () -> {
				grid.setSpacing(oldSpacing);
				grid.setSnap(oldSnap);
				setPositions(oldPositions);
			}, () -> {
				grid.setSpacing(spacing);
				grid.setSnap(true);
				setPositions(newPositions);
			});
		}, ex -> {
			straightening.set(false);
			NotificationManager.showError("Straighten failed: " + ex.getMessage());
		});
	}

	private void setPositions(Map<LabeledNodeShape, Point2D> positions) {
		for (var entry : positions.entrySet()) {
			entry.getKey().setTranslateX(entry.getValue().getX());
			entry.getKey().setTranslateY(entry.getValue().getY());
		}
		updateLabelLayout();
	}

	public FindToolBar getFindToolBar() {
		return findToolBar;
	}

	@Override
	public boolean allowFindReplace() {
		return true;
	}

	@Override
	public ScrollPane getScrollPane() {
		return controller.getScrollPane();
	}
}
