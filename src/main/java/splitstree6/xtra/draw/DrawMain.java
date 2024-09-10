/*
 *  DrawMain.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.xtra.draw;

import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import jloda.fx.control.CopyableLabel;
import jloda.fx.control.ZoomableScrollPane;
import jloda.fx.util.Icebergs;
import jloda.graph.algorithms.IsDAG;
import jloda.util.Basic;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Collections;

public class DrawMain extends Application {

	private final DrawPane drawPane;

	public DrawMain() {
		Icebergs.setEnabled(true);
		drawPane = new DrawPane();
	}


	@Override
	public void start(Stage stage) throws Exception {
		var toleranceSlider = new Slider(0.1, 20, 4);
		toleranceSlider.valueProperty().bindBidirectional(drawPane.toleranceProperty());

		var showNodesButton = new ToggleButton("Nodes");
		showNodesButton.selectedProperty().bindBidirectional(drawPane.getNodesGroup().visibleProperty());
		var showEdgesButton = new ToggleButton("Edges");
		showEdgesButton.selectedProperty().bindBidirectional(drawPane.getEdgesGroup().visibleProperty());
		var showLabelsButton = new ToggleButton("Labels");
		showLabelsButton.selectedProperty().bindBidirectional(drawPane.getNodeLabelsGroup().visibleProperty());
		var showOthersButton = new ToggleButton("Others");
		showOthersButton.selectedProperty().bindBidirectional(drawPane.getOtherGroup().visibleProperty());

		var deleteButton = new Button("Delete");
		deleteButton.setOnAction(unused -> {
			drawPane.getUndoManager().doAndAdd(new DeleteNodesEdgesCommand(drawPane,
					drawPane.getNodeSelectionModel().getSelectedItems(),
					drawPane.getEdgeSelectionModel().getSelectedItems()));
		});
		deleteButton.disableProperty().bind(drawPane.getNodeSelectionModel().sizeProperty().isEqualTo(0)
				.and(drawPane.getEdgeSelectionModel().sizeProperty().isEqualTo(0)));

		var clearButton = new Button("Clear");
		clearButton.setOnAction(e -> {
			drawPane.getUndoManager().doAndAdd(new DeleteNodesEdgesCommand(drawPane, drawPane.getNetwork().getNodesAsList(),
					Collections.emptyList()));
		});
		clearButton.disableProperty().bind(drawPane.getNetworkFX().emptyProperty());

		var undoButton = new Button("Undo");
		undoButton.disableProperty().bind(drawPane.getUndoManager().undoableProperty().not());
		undoButton.setOnAction(e -> drawPane.getUndoManager().undo());
		var redoButton = new Button("Redo");
		redoButton.disableProperty().bind(drawPane.getUndoManager().redoableProperty().not());
		redoButton.setOnAction(e -> drawPane.getUndoManager().redo());

		var zoomInButton = new Button("Zoom In");
		zoomInButton.setOnAction(e -> {
			drawPane.setScaleX(1.1 * drawPane.getScaleX());
			drawPane.setScaleY(1.1 * drawPane.getScaleY());
		});
		var ZoomOutButton = new Button("Zoom Out");
		ZoomOutButton.setOnAction(e -> {
			drawPane.setScaleX(1 / 1.1 * drawPane.getScaleX());
			drawPane.setScaleY(1 / 1.1 * drawPane.getScaleY());
		});

		var labelLeavesButton = new Button("abc...");
		labelLeavesButton.setOnAction(e -> {
			drawPane.getUndoManager().doAndAdd(new LabelLeavesCommand(drawPane));
		});

		var newickLabel = new CopyableLabel("");
		var showNewickButton = new Button("Newick");
		showNewickButton.setOnAction(e -> {
			newickLabel.setText(drawPane.toBracketString(false));
			try {
				InputOutput.save(new OutputStreamWriter(System.err), drawPane);
			} catch (IOException ex) {
				Basic.caught(ex);
			}
		});
		showNewickButton.disableProperty().bind(drawPane.validProperty().not());

		var toolBar = new ToolBar(toleranceSlider, showNodesButton, showEdgesButton,
				labelLeavesButton, showNewickButton, deleteButton, clearButton, new Separator(Orientation.VERTICAL),
				undoButton, redoButton, new Separator(Orientation.VERTICAL),
				zoomInButton, ZoomOutButton);

		var infoLabel = new Label("");
		infoLabel.setStyle("-fx-font-size: 14;");
		var statusBar = new StackPane(infoLabel);
		statusBar.setStyle("-fx-background-color: white;");
		drawPane.getNetworkFX().lastUpdateProperty().addListener(e -> {
			var network = drawPane.getNetwork();
			infoLabel.setText("Nodes: %,d Edges: %,d, Leaves: %,d, Roots: %,d, h: %,d, isDag: %s"
					.formatted(network.getNumberOfNodes(), network.getNumberOfEdges(), network.nodeStream().filter(v -> v.getOutDegree() == 0).count(),
							network.nodeStream().filter(v -> v.getInDegree() == 0).count(),
							network.nodeStream().filter(v -> v.getInDegree() > 0).mapToInt(v -> v.getInDegree() - 1).sum(),
							IsDAG.apply(network)
					));
		});

		var root = new BorderPane();

		var scrollPane = new ZoomableScrollPane(drawPane);
		scrollPane.setFitToWidth(true);
		scrollPane.setFitToHeight(true);
		scrollPane.setPannable(true);
		scrollPane.setLockAspectRatio(true);
		scrollPane.setRequireShiftOrControlToZoom(true);

		drawPane.minWidthProperty().bind(Bindings.createDoubleBinding(() ->
				scrollPane.getViewportBounds().getWidth(), scrollPane.viewportBoundsProperty()));

		drawPane.minHeightProperty().bind(Bindings.createDoubleBinding(() ->
				scrollPane.getViewportBounds().getHeight(), scrollPane.viewportBoundsProperty()));

		root.setCenter(scrollPane);

		var vbox = new VBox(toolBar, newickLabel);
		vbox.setStyle("-fx-background-color: white;");
		root.setTop(vbox);
		root.setBottom(statusBar);

		stage.setScene(new Scene(root, 800, 800));
		stage.show();
	}
}
