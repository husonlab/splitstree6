/*
 *  TanglegramTreePane.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.view.trees.tanglegram;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import javafx.geometry.Dimension2D;
import javafx.scene.Group;
import jloda.fx.phylo.embed.Averaging;
import jloda.fx.selection.SelectionModel;
import jloda.fx.undo.UndoManager;
import jloda.fx.util.RunAfterAWhile;
import jloda.graph.Node;
import jloda.phylo.PhyloTree;
import jloda.util.IteratorUtils;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.Taxon;
import splitstree6.layout.tree.LabeledNodeShape;
import splitstree6.layout.tree.PaneLabel;
import splitstree6.layout.tree.TreeDiagramType;
import splitstree6.view.format.edgelabel.LabelEdgesBy;
import splitstree6.view.trees.treepages.TreePane;
import splitstree6.window.MainWindow;

/**
 * a tanglegram tree pane
 * Daniel Huson, 12.2021
 */
public class TanglegramTreePane extends Group {
	private final InvalidationListener updater;
	private Runnable runAfterUpdate;

	private TreePane treePane;

	private final BooleanProperty changingOrientation = new SimpleBooleanProperty(this, "changingOrientation", false);

	public TanglegramTreePane(MainWindow mainWindow, UndoManager undoManager, TaxaBlock taxaBlock, SelectionModel<Taxon> taxonSelectionModel,
							  ObjectProperty<PhyloTree> tree, ObjectProperty<Dimension2D> dimensions,
							  ObjectProperty<TreeDiagramType> optionDiagram, ObjectProperty<LabelEdgesBy> labelByEdges, ObjectProperty<Averaging> optionAveraging, StringProperty optionOrientation,
							  ReadOnlyDoubleProperty fontScaleFactor, ObservableMap<Node, LabeledNodeShape> nodeShapeMap, LongProperty updateRequested) {

		updater = e -> RunAfterAWhile.apply(this, () ->
				Platform.runLater(() -> {
					getChildren().clear();
					double factor;
					if (tree.get() != null && taxaBlock != null) {
						var taxaCount = IteratorUtils.count(tree.get().getTaxa());
						factor = (taxaBlock.getNtax() > taxaCount ? (double) taxaCount / taxaBlock.getNtax() : 1.0);
					} else {
						factor = 1.0;
					}

					if (dimensions.get().getWidth() > 0 && dimensions.get().getHeight() > 0 && tree.get() != null) {
						treePane = new TreePane(mainWindow, undoManager, taxaBlock, tree.get(), taxonSelectionModel, dimensions.get().getWidth(), factor * dimensions.get().getHeight(),
								optionDiagram.get(), labelByEdges.get(), optionAveraging.get(), optionOrientation, fontScaleFactor, new SimpleObjectProperty<>(PaneLabel.None),
								null, nodeShapeMap, FXCollections.observableHashMap(), false);

						changingOrientation.bind(treePane.changingOrientationProperty());
						treePane.setRunAfterUpdate(getRunAfterUpdate());
						treePane.drawTree();
						getChildren().add(treePane);
					}
				})
		);

		tree.addListener(new WeakInvalidationListener(updater));
		optionDiagram.addListener(new WeakInvalidationListener(updater));
		labelByEdges.addListener(new WeakInvalidationListener(updater));
		// optionOrientation.addListener(new WeakInvalidationListener(updater)); // treepane listens for changes of orientation
		dimensions.addListener(new WeakInvalidationListener(updater));
		optionAveraging.addListener(new WeakInvalidationListener(updater));
		updateRequested.addListener(new WeakInvalidationListener(updater));
	}

	public Runnable getRunAfterUpdate() {
		return runAfterUpdate;
	}

	public void setRunAfterUpdate(Runnable runAfterUpdate) {
		this.runAfterUpdate = runAfterUpdate;
	}

	public boolean isChangingOrientation() {
		return changingOrientation.get();
	}

	public BooleanProperty changingOrientationProperty() {
		return changingOrientation;
	}

	public TreePane getTreePane() {
		return treePane;
	}
}
