/*
 *  TreePageFactory.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.view.trees.treepages;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Dimension2D;
import javafx.scene.Node;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.util.Callback;
import jloda.fx.util.BasicFX;
import jloda.fx.util.RunAfterAWhile;
import jloda.phylo.PhyloTree;
import jloda.util.CanceledException;
import jloda.util.progress.ProgressSilent;
import splitstree6.layout.tree.LayoutOrientation;
import splitstree6.view.trees.tanglegram.optimize.EmbeddingOptimizer;
import splitstree6.window.MainWindow;

/**
 * tree-page factory
 * Daniel Huson, 11.2021
 */
public class TreePageFactory implements Callback<Integer, Node> {
	private final MainWindow mainWindow;
	private final TreePagesView treePagesView;
	private final ObservableList<PhyloTree> trees;

	private final ReadOnlyIntegerProperty rows;
	private final ReadOnlyIntegerProperty cols;
	private final ReadOnlyObjectProperty<Dimension2D> dimensions;

	private final ObjectProperty<GridPane> gridPane = new SimpleObjectProperty<>();

	private final InvalidationListener updater;

	private final IntegerProperty numberChangingOrientation = new SimpleIntegerProperty(this, "numberChangingOrientation", 0);

	private int page;

	public TreePageFactory(MainWindow mainWindow, TreePagesView treePagesView, ObservableList<PhyloTree> trees, ReadOnlyIntegerProperty rows, ReadOnlyIntegerProperty cols, ReadOnlyObjectProperty<Dimension2D> dimensions) {
		this.mainWindow = mainWindow;
		this.treePagesView = treePagesView;
		this.trees = trees;
		this.rows = rows;
		this.cols = cols;
		this.dimensions = dimensions;

		gridPane.addListener((v, o, n) -> {
			if (n != null) {
				n.setHgap(5);
				n.setVgap(5);
			}
		});
		gridPane.set(new GridPane());

		updater = e -> RunAfterAWhile.apply(this, this::update);

		trees.addListener(new WeakInvalidationListener(updater));
		treePagesView.optionDiagramProperty().addListener(new WeakInvalidationListener(updater));
		treePagesView.optionAveragingProperty().addListener(new WeakInvalidationListener(updater));
		treePagesView.optionLabelEdgesByProperty().addListener(new WeakInvalidationListener(updater));
		rows.addListener(new WeakInvalidationListener(updater));
		cols.addListener(new WeakInvalidationListener(updater));
		dimensions.addListener(new WeakInvalidationListener(updater));

		treePagesView.optionZoomFactorProperty().addListener((v, o, n) -> {
			for (var treeViewPane : BasicFX.findRecursively(gridPane.get(), p -> p.getId() != null && p.getId().equals("treeView"))) {
				treeViewPane.setScaleX(treeViewPane.getScaleX() / o.doubleValue() * n.doubleValue());
				treeViewPane.setScaleY(treeViewPane.getScaleY() / o.doubleValue() * n.doubleValue());
			}
		});
	}

	private void update() {
		var taxaBlock = mainWindow.getWorkflow().getWorkingTaxaBlock();
		var taxonSelectionModel = mainWindow.getTaxonSelectionModel();

		Platform.runLater(() -> gridPane.get().getChildren().clear());

		var start = page * rows.get() * cols.get();
		var top = Math.min(trees.size(), start + rows.get() * cols.get());
		var r = 0;
		var c = 0;
		for (int which = start; which < top; which++) {
			var tree = trees.get(which);
			if (tree.isReticulated()) {
				tree = new PhyloTree(tree);
				try {
					EmbeddingOptimizer.apply(tree, new ProgressSilent());
				} catch (CanceledException ignored) {
				}
			}
			var name = (tree.getName() != null ? tree.getName() : "tree-" + (which + 1));

			Pane pane;
			if (dimensions.get().getWidth() > 0 && dimensions.get().getHeight() > 0) {
				var treePane = new TreePane(mainWindow.getStage(), treePagesView.getUndoManager(), taxaBlock, tree, taxonSelectionModel, dimensions.get().getWidth(), dimensions.get().getHeight(),
						treePagesView.getOptionDiagram(), treePagesView.getOptionLabelEdgesBy(), treePagesView.getOptionAveraging(), treePagesView.optionOrientationProperty(),
						treePagesView.optionFontScaleFactorProperty(), treePagesView.optionTreeLabelsProperty(), null,
						FXCollections.observableHashMap(), FXCollections.observableHashMap());
				treePane.changingOrientationProperty().addListener((v, o, n) -> numberChangingOrientation.set(numberChangingOrientation.get() + (n ? 1 : -1)));
				treePane.setRunAfterUpdate(() -> {
					for (var treeViewPane : BasicFX.findRecursively(treePane, p -> p.getId() != null && p.getId().equals("treeView"))) {
						treeViewPane.setScaleX(treeViewPane.getScaleX() * treePagesView.getOptionZoomFactor());
						treeViewPane.setScaleY(treeViewPane.getScaleY() * treePagesView.getOptionZoomFactor());
					}
				});
				treePane.drawTree();
				pane = treePane;
			} else
				pane = new Pane();

			pane.setPrefSize(dimensions.get().getWidth(), dimensions.get().getHeight());
			pane.setMinSize(Pane.USE_PREF_SIZE, Pane.USE_PREF_SIZE);
			pane.setMaxSize(Pane.USE_PREF_SIZE, Pane.USE_PREF_SIZE);

			GridPane.setRowIndex(pane, r);
			GridPane.setColumnIndex(pane, c);
			Platform.runLater(() -> gridPane.get().getChildren().add(pane));
			if (++c == cols.get()) {
				r++;
				c = 0;
			}
		}
	}

	@Override
	public Node call(Integer page) {
		gridPane.set(new GridPane());

		if (page >= 0) {
			this.page = page;
			update();
		}
		return gridPane.get();
	}

	public void updateLabelLayout(LayoutOrientation orientation) {
		for (var treePane : BasicFX.getAllRecursively(gridPane.get(), TreePane.class)) {
			treePane.updateLabelLayout(orientation);
		}
	}

	public BooleanBinding changingOrientationBinding() {
		return numberChangingOrientation.greaterThan(0);
	}
}
