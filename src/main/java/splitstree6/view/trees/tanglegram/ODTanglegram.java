/*
 * ODTanglegramAlgorithm.java Copyright (C) 2025 Daniel H. Huson
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
 *
 */

package splitstree6.view.trees.tanglegram;

import javafx.scene.layout.Pane;
import jloda.fx.util.AService;
import jloda.graph.Node;
import jloda.phylo.LSAUtils;
import jloda.phylo.PhyloTree;
import jloda.util.Single;
import jloda.util.progress.ProgressListener;
import jloda.util.progress.ProgressPercentage;
import splitstree6.tools.RunWorkflow;

import java.util.*;
import java.util.function.Consumer;

import static splitstree6.xtra.layout.ORDNetworkLayoutAlgorithm.computeNodeHeightMap;

/**
 * optimize the LSAchildren of phylogenies for a tanglegram drawing
 * Daniel Huson, 8.2025
 */
public class ODTanglegram {
	private static boolean verbose = false;

	/**
	 * optimize the tanglegram layout by updating the LSA children map
	 *
	 * @param statusPane                      used for progress bar
	 * @param network1                        first phylogeny
	 * @param network2                        second phylogeny
	 * @param optimizeTaxonDisplacement1      optimize taxon displacement in first phylogeny
	 * @param optimizeReticulateDisplacement1 optimize reticulate displacement in first phylogeny
	 * @param optimizeTaxonDisplacement2      optimize taxon displacement in second phylogeny
	 * @param optimizeReticulateDisplacement2 optimize reticulate displacement in second phylogeny
	 * @param usePQTree                       use PQ tree presorting for two-sided tanglegram
	 * @param runningConsumer                 this is called in the FX thread when the running state is set to true and again, when set to false
	 * @param successRunnable                 this is run in the FX thread once the calculation has successfully completed
	 * @param failedConsumer                  this is called in the FX thread if the computation failed
	 */
	public static void apply(Pane statusPane, PhyloTree network1, PhyloTree network2, boolean optimizeTaxonDisplacement1, boolean optimizeReticulateDisplacement1, boolean optimizeTaxonDisplacement2,
							 boolean optimizeReticulateDisplacement2, boolean usePQTree, Consumer<Boolean> runningConsumer, Runnable successRunnable, Consumer<Throwable> failedConsumer) {
		var childrenMap1 = new HashMap<Node, List<Node>>();
		LSAUtils.computeLSAChildrenMap(network1, childrenMap1);
		var childrenMap2 = new HashMap<Node, List<Node>>();
		LSAUtils.computeLSAChildrenMap(network2, childrenMap2);

		var bestChildrenMap1 = new Single<>(new HashMap<Node, List<Node>>());
		bestChildrenMap1.get().putAll(childrenMap1);
		var bestChildrenMap2 = new Single<>(new HashMap<Node, List<Node>>());
		bestChildrenMap2.get().putAll(childrenMap2);

		var bestScore = new Single<>(Double.MAX_VALUE);

		var finalOptimizeReticulateDisplacement1 = optimizeReticulateDisplacement1 && network1.hasReticulateEdges();
		var finalOptimizeReticulateDisplacement2 = optimizeReticulateDisplacement2 && network2.hasReticulateEdges();

		if (false) {
			runningConsumer.accept(true);
			var random = new Random(666);
			apply(network2, childrenMap2, network1, childrenMap1, optimizeTaxonDisplacement1, optimizeReticulateDisplacement1, random, new ProgressPercentage());
			network1.getLSAChildrenMap().clear();
			network1.getLSAChildrenMap().putAll(childrenMap1);
			network2.getLSAChildrenMap().clear();
			network2.getLSAChildrenMap().putAll(childrenMap2);
			runningConsumer.accept(false);
			successRunnable.run();
			return;
		}

		if (optimizeTaxonDisplacement1 || finalOptimizeReticulateDisplacement1 || optimizeTaxonDisplacement2 || finalOptimizeReticulateDisplacement2) {
			int rounds;

			var optimizeSide1 = (optimizeTaxonDisplacement1 || finalOptimizeReticulateDisplacement1);
			var optimizeSide2 = (optimizeTaxonDisplacement2 || finalOptimizeReticulateDisplacement2);

			if (!optimizeSide1 || !optimizeSide2)
				rounds = 1;
			else if (network1.getNumberOfNodes() > 500 || network2.getNumberOfNodes() > 500)
				rounds = 2;
			else rounds = 5;

			var random = new Random(666);
			var service = new AService<Boolean>();
			service.setProgressParentPane(statusPane);
			service.setCallable(() -> {
				var progress = service.getProgressListener();

				bestScore.set(computeScore(network1, childrenMap1, network2, childrenMap2, optimizeTaxonDisplacement1, finalOptimizeReticulateDisplacement1, optimizeTaxonDisplacement2, finalOptimizeReticulateDisplacement2));

				if (usePQTree && optimizeSide1 && optimizeSide2) {
					progress.setTasks("Tanglegram", "PQ-tree sorting");
					progress.setMaximum(-1);
					if (PQTreeHeuristic.reorderUsingPQTree(network1, childrenMap1, network2, childrenMap2) && !finalOptimizeReticulateDisplacement1 && !finalOptimizeReticulateDisplacement2)
						return true; // both reordered to accommodate all
					var score = computeScore(network1, childrenMap1, network2, childrenMap2, optimizeTaxonDisplacement1, finalOptimizeReticulateDisplacement1, optimizeTaxonDisplacement2, finalOptimizeReticulateDisplacement2);
					if (score < bestScore.get()) {
						bestChildrenMap1.get().clear();
						bestChildrenMap1.get().putAll(childrenMap1);
						bestChildrenMap2.get().clear();
						bestChildrenMap2.get().putAll(childrenMap2);
					}
				}

				progress.setTasks("Tanglegram", "optimizing");
				progress.setMaximum(2 * rounds);

				for (var i = 0; i < rounds; i++) {
					if (optimizeSide1) {
						progress.setProgress(2 * i);
						var score = apply(network2, childrenMap2, network1, childrenMap1, optimizeTaxonDisplacement1, finalOptimizeReticulateDisplacement1, random, progress);
						if (score < bestScore.get()) {
							bestChildrenMap1.get().clear();
							bestChildrenMap1.get().putAll(childrenMap1);
						}
					}
					if (optimizeSide2) {
						progress.setProgress(2 * i + 1);
						var score = apply(network1, childrenMap1, network2, childrenMap2, optimizeTaxonDisplacement2, finalOptimizeReticulateDisplacement2, random, progress);
						if (score < bestScore.get()) {
							bestChildrenMap2.get().clear();
							bestChildrenMap2.get().putAll(childrenMap2);
						}
					}
				}
				return true;
			});
			service.runningProperty().addListener((v, o, n) -> {
				if (n) {
					if (RunWorkflow.runningJobsInView != null)
						RunWorkflow.runningJobsInView.add(service);
					runningConsumer.accept(true);
				} else {
					if (RunWorkflow.runningJobsInView != null)
						RunWorkflow.runningJobsInView.remove(service);
				}
			});
			service.setOnFailed(e -> {
				runningConsumer.accept(false);
				failedConsumer.accept(service.getException());
			});
			service.setOnSucceeded(e -> {
				if (optimizeSide1) {
					network1.getLSAChildrenMap().clear();
					network1.getLSAChildrenMap().putAll(bestChildrenMap1.get());
				}
				if (optimizeSide2) {
					network2.getLSAChildrenMap().clear();
					network2.getLSAChildrenMap().putAll(bestChildrenMap2.get());
				}
				if (true) {
					System.err.println("DO-Tanglegram\t" + computeInfoString(network1, childrenMap1, network2, childrenMap2));
				}
				runningConsumer.accept(false);
				successRunnable.run();
			});
			service.setOnCancelled(e -> runningConsumer.accept(false));
			service.start();
		} else {
			successRunnable.run();
		}
	}

	/**
	 * optimize network2 while keeping network 1 fixed
	 *
	 * @param network1 fixed
	 * @param network2 to be optimized, only its LSA children map will be modified
	 * @param random   random number generator
	 */
	private static double apply(PhyloTree network1, Map<Node, List<Node>> childrenMap1, PhyloTree network2, Map<Node, List<Node>> childrenMap2, boolean optimizeTaxonDisplacement, boolean optimizeReticulateDisplacement, Random random, ProgressListener progress) {
		var reticulateEdges2 = new HashMap<Node, List<Node>>();
		for (var e : network2.edges()) {
			if (network2.isReticulateEdge(e) && !network2.isTransferAcceptorEdge(e)) {
				reticulateEdges2.computeIfAbsent(e.getSource(), k -> new ArrayList<>()).add(e.getTarget());
				reticulateEdges2.computeIfAbsent(e.getTarget(), k -> new ArrayList<>()).add(e.getSource());
			}
		}

		var resultAndScore = splitstree6.xtra.layout.ODTanglegramAlgorithm.apply(network1.getName(), network1.getRoot(), v -> (network1.hasTaxa(v) ? network1.getTaxon(v) : null), childrenMap1::get,
				network2.getName(), network2.getRoot(), v -> (network2.hasTaxa(v) ? network2.getTaxon(v) : null), childrenMap2::get, reticulateEdges2,
				optimizeTaxonDisplacement, optimizeReticulateDisplacement, random, progress::isUserCancelled);
		childrenMap2.clear();
		childrenMap2.putAll(resultAndScore.result());
		return resultAndScore.score();
	}

	public static double computeScore(PhyloTree network1, Map<Node, List<Node>> childrenMap1, PhyloTree network2, Map<Node, List<Node>> childrenMap2,
									  boolean useTaxonDisplacement1, boolean useTaxonDisplacement2,
									  boolean useReticulateDisplacement1, boolean useReticulateDisplacement2) {
		var yMap1 = computeNodeHeightMap(network1.getRoot(), childrenMap1);
		var yMap2 = computeNodeHeightMap(network2.getRoot(), childrenMap2);
		return ReportTanglegramStats.apply(network1, yMap1::get, network1::getLabel, network2, yMap2::get, network2::getLabel)
				.score(useTaxonDisplacement1, useTaxonDisplacement2, useReticulateDisplacement1, useReticulateDisplacement2);
	}

	public static String computeInfoString(PhyloTree network1, Map<Node, List<Node>> children1, PhyloTree network2, Map<Node, List<Node>> children2) {
		var yMap1 = computeNodeHeightMap(network1.getRoot(), children1);
		var yMap2 = computeNodeHeightMap(network2.getRoot(), children2);
		return ReportTanglegramStats.apply(
				network1, yMap1::get, network1::getLabel,
				network2, yMap2::get, network2::getLabel).toString();
	}
}

