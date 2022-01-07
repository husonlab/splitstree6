/*
 *  Copyright (C) 2018. Daniel H. Huson
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

/*
 *  TanglegramEmbeddingOptimizer.java Copyright (C) 2022 Daniel H. Huson
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

import javafx.beans.property.*;
import jloda.fx.util.AService;
import jloda.phylo.PhyloTree;
import jloda.util.Pair;
import splitstree6.view.trees.tanglegram.optimize.EmbeddingOptimizer;
import splitstree6.window.MainWindow;

import java.util.function.BiConsumer;

/**
 * optimizes the embedding of a collection of rooted trees or networks
 * Daniel Huson, 1.2022
 */
public class TanglegramEmbeddingOptimizer {
	private final AService<Pair<int[], int[]>> service;
	private final ObjectProperty<PhyloTree> tree1 = new SimpleObjectProperty<>();
	private final ObjectProperty<PhyloTree> tree2 = new SimpleObjectProperty<>();
	private final EmbeddingOptimizer embeddingOptimizer = new EmbeddingOptimizer();

	private final BooleanProperty useShortestPaths = new SimpleBooleanProperty(this, "useShortestPaths", false);
	private final BooleanProperty useFastAlignmentHeuristic = new SimpleBooleanProperty(this, "useFastAlignmentHeuristic", false);

	/**
	 * setup the optimizer
	 *
	 * @param mainWindow main window
	 */
	public TanglegramEmbeddingOptimizer(MainWindow mainWindow) {
		service = new AService<>(mainWindow.getController().getBottomFlowPane());

		service.setCallable(() -> {
			embeddingOptimizer.apply(new PhyloTree[]{tree1.get(), tree2.get()}, service.getProgressListener(), isUseShortestPaths(), isUseFastAlignmentHeuristic());

			var cycle1_0 = embeddingOptimizer.getFirstOrder().stream().mapToInt(label -> mainWindow.getWorkflow().getWorkingTaxaBlock().indexOf(label)).filter(t -> t != -1).toArray();
			var cycle1 = new int[cycle1_0.length + 1];
			System.arraycopy(cycle1_0, 0, cycle1, 1, cycle1_0.length);
			var cycle2_0 = embeddingOptimizer.getSecondOrder().stream().mapToInt(label -> mainWindow.getWorkflow().getWorkingTaxaBlock().indexOf(label)).filter(t -> t != -1).toArray();
			var cycle2 = new int[cycle2_0.length + 1];
			System.arraycopy(cycle2_0, 0, cycle2, 1, cycle2_0.length);
			return new Pair<>(cycle1, cycle2);
		});
	}

	public void apply(PhyloTree tree1, PhyloTree tree2, BiConsumer<int[], int[]> output) {
		this.tree1.set(new PhyloTree(tree1));
		this.tree2.set(new PhyloTree(tree2));
		service.setOnSucceeded(e -> output.accept(service.getValue().getFirst(), service.getValue().getSecond()));
		service.restart();
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
