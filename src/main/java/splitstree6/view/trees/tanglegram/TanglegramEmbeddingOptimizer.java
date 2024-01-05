/*
 * TanglegramEmbeddingOptimizer.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.view.trees.tanglegram;

import javafx.application.Platform;
import javafx.beans.property.*;
import jloda.fx.util.AService;
import jloda.fx.util.RunAfterAWhile;
import jloda.phylo.PhyloTree;
import jloda.util.Pair;
import splitstree6.view.trees.tanglegram.optimize.EmbeddingOptimizer;
import splitstree6.window.MainWindow;

import java.util.function.Consumer;

/**
 * optimizes the embedding of a collection of rooted trees or networks
 * Daniel Huson, 1.2022
 */
public class TanglegramEmbeddingOptimizer {
	private final AService<Pair<PhyloTree, PhyloTree>> service;
	private final ObjectProperty<PhyloTree> tree1 = new SimpleObjectProperty<>();
	private final ObjectProperty<PhyloTree> tree2 = new SimpleObjectProperty<>();

	private final BooleanProperty useShortestPaths = new SimpleBooleanProperty(this, "useShortestPaths", false);
	private final BooleanProperty useFastAlignmentHeuristic = new SimpleBooleanProperty(this, "useFastAlignmentHeuristic", false);

	/**
	 * create the optimizer
	 *
	 * @param mainWindow main window
	 */
	public TanglegramEmbeddingOptimizer(MainWindow mainWindow) {
		service = new AService<>(mainWindow.getController().getBottomFlowPane());

		service.setCallable(() -> {
			EmbeddingOptimizer.apply(new PhyloTree[]{tree1.get(), tree2.get()}, service.getProgressListener(), isUseShortestPaths(), isUseFastAlignmentHeuristic());
			return new Pair<>(tree1.get(), tree2.get());
		});
	}

	public void apply(PhyloTree tree1, PhyloTree tree2, Consumer<Pair<PhyloTree, PhyloTree>> resultConsumer) {
		this.tree1.set(new PhyloTree(tree1));
		// LSATree.computeNodeLSAChildrenMap(this.tree1.get());
		this.tree2.set(new PhyloTree(tree2));
		// LSATree.computeNodeLSAChildrenMap(this.tree2.get());
		service.setOnSucceeded(e -> resultConsumer.accept(service.getValue()));
		RunAfterAWhile.apply(this, () -> Platform.runLater(service::restart));
	}

	public boolean isUseShortestPaths() {
		return useShortestPaths.get();
	}

	public BooleanProperty useShortestPathsProperty() {
		return useShortestPaths;
	}

	public void setUseShortestPaths(boolean useShortestPaths) {
		this.useShortestPaths.set(useShortestPaths);
	}

	public boolean isUseFastAlignmentHeuristic() {
		return useFastAlignmentHeuristic.get();
	}

	public BooleanProperty useFastAlignmentHeuristicProperty() {
		return useFastAlignmentHeuristic;
	}

	public void setUseFastAlignmentHeuristic(boolean useFastAlignmentHeuristic) {
		this.useFastAlignmentHeuristic.set(useFastAlignmentHeuristic);
	}

	public ReadOnlyBooleanProperty runningProperty() {
		return service.runningProperty();
	}
}
