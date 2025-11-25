/*
 * DoTanglegram.java Copyright (C) 2025 Daniel H. Huson
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
import jloda.graph.NodeArray;
import jloda.phylo.LSAUtils;
import jloda.phylo.PhyloTree;
import jloda.util.*;
import jloda.util.progress.ProgressListener;
import splitstree6.tools.RunWorkflow;

import java.util.*;
import java.util.function.Consumer;

import static jloda.phylogeny.dolayout.Common.computeNodeHeightMap;


/**
 * optimize the LSAchildren of phylogenies for a tanglegram drawing
 * Daniel Huson, 8.2025
 */
public class DoTanglegram {
	public static int PARALLEL_JOBS = 32;

	private enum Randomize {None, All, LSANodes}

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
	 * @param useNNPresort                       use PQ tree presorting for two-sided tanglegram
	 * @param runningConsumer                 this is called in the FX thread when the running state is set to true and again, when set to false
	 * @param successRunnable                 this is run in the FX thread once the calculation has successfully completed
	 * @param failedConsumer                  this is called in the FX thread if the computation failed
	 */
	public static void apply(Pane statusPane, PhyloTree network1, PhyloTree network2, boolean optimizeTaxonDisplacement1, boolean optimizeReticulateDisplacement1, boolean optimizeTaxonDisplacement2,
							 boolean optimizeReticulateDisplacement2, boolean useNNPresort, Consumer<Boolean> runningConsumer, Runnable successRunnable, Consumer<Throwable> failedConsumer) {
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

		{
			System.err.println("Initial score: " + computeScore(network1, childrenMap1, network2, childrenMap2, optimizeTaxonDisplacement1, finalOptimizeReticulateDisplacement1, optimizeTaxonDisplacement2, finalOptimizeReticulateDisplacement2));
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

			var service = new AService<Boolean>();
			service.setProgressParentPane(statusPane);
			service.setCallable(() -> {
				var progress = service.getProgressListener();

				bestScore.set(computeScore(network1, childrenMap1, network2, childrenMap2, optimizeTaxonDisplacement1, finalOptimizeReticulateDisplacement1, optimizeTaxonDisplacement2, finalOptimizeReticulateDisplacement2));
				bestChildrenMap1.get().clear();
				bestChildrenMap1.get().putAll(childrenMap1);
				bestChildrenMap2.get().clear();
				bestChildrenMap2.get().putAll(childrenMap2);

				if (useNNPresort && optimizeSide1 && optimizeSide2) {
					progress.setTasks("Tanglegram", "NNet presort");
					progress.setMaximum(-1);

					NNCircularOrderingHeuristic.apply(network1, childrenMap1, network2, childrenMap2);

					var score = computeScore(network1, childrenMap1, network2, childrenMap2, optimizeTaxonDisplacement1, finalOptimizeReticulateDisplacement1, optimizeTaxonDisplacement2, finalOptimizeReticulateDisplacement2);
					System.err.println("NN score: " + score);

					if (score < bestScore.get()) {
						bestChildrenMap1.get().clear();
						bestChildrenMap1.get().putAll(childrenMap1);
						bestChildrenMap2.get().clear();
						bestChildrenMap2.get().putAll(childrenMap2);
					}
				}

				progress.setTasks("Tanglegram", "optimizing");

				var nJobs = ((!network1.hasReticulateEdges() && !network2.hasReticulateEdges()) ? 1 : PARALLEL_JOBS);
				nJobs = 32;
				var jobs = new ArrayList<>(IteratorUtils.asList(BitSetUtils.range(0, nJobs)));

				progress.setMaximum(jobs.size());
				ExecuteInParallel.apply(jobs, job -> {
					if (false) System.err.println("Job: " + job);
					var jobRandom = new Random(13L * job);
					var jobNetwork1 = new PhyloTree();
					var jobChildrenMap1 = new HashMap<Node, List<Node>>();
					var jobBackMap1 = new HashMap<Node, Node>();
					try (NodeArray<Node> srcTarMap1 = network1.newNodeArray()) {
						jobNetwork1.copy(network1, srcTarMap1, null);
						jobBackMap1 = invert(srcTarMap1);
						var randomize = (job == 0 ? Randomize.None : (network1.hasReticulateEdges() ? Randomize.LSANodes : Randomize.All));
						jobChildrenMap1 = copyAndPermuteLSAChildren(childrenMap1, srcTarMap1, randomize, jobRandom);
					}
					var jobNetwork2 = new PhyloTree();
					var jobChildrenMap2 = new HashMap<Node, List<Node>>();
					var jobBackMap2 = new HashMap<Node, Node>();
					try (NodeArray<Node> srcTarMap2 = network2.newNodeArray()) {
						jobNetwork2.copy(network2, srcTarMap2, null);
						jobBackMap2 = invert(srcTarMap2);
						var randomize = (job == 0 ? Randomize.None : (network2.hasReticulateEdges() ? Randomize.LSANodes : Randomize.All));
						jobChildrenMap2 = copyAndPermuteLSAChildren(childrenMap2, srcTarMap2, randomize, jobRandom);
					}

					final var jobBestChildrenMap1 = new HashMap<>(jobChildrenMap1);
					final var jobBestChildrenMap2 = new HashMap<>(jobChildrenMap2);
					var jobBestScore = Double.POSITIVE_INFINITY;

					for (var i = 0; i < rounds; i++) {
						progress.checkForCancel();
						if (optimizeSide1) {
							apply(jobNetwork2, jobChildrenMap2, jobNetwork1, jobChildrenMap1, optimizeTaxonDisplacement1, finalOptimizeReticulateDisplacement1, jobRandom, progress);
							var score = computeScore(jobNetwork1, jobChildrenMap1, jobNetwork2, jobChildrenMap2, optimizeTaxonDisplacement1, finalOptimizeReticulateDisplacement1, optimizeTaxonDisplacement2, finalOptimizeReticulateDisplacement2);
							if (score < jobBestScore) {
								jobBestScore = score;
								jobBestChildrenMap1.clear();
								jobBestChildrenMap1.putAll(jobChildrenMap1);
								jobBestChildrenMap2.clear();
								jobBestChildrenMap2.putAll(jobChildrenMap2);
							}
						}
						if (optimizeSide2) {
							apply(jobNetwork1, jobChildrenMap1, jobNetwork2, jobChildrenMap2, optimizeTaxonDisplacement2, finalOptimizeReticulateDisplacement2, jobRandom, progress);
							var score = computeScore(jobNetwork1, jobChildrenMap1, jobNetwork2, jobChildrenMap2, optimizeTaxonDisplacement1, finalOptimizeReticulateDisplacement1, optimizeTaxonDisplacement2, finalOptimizeReticulateDisplacement2);
							if (score < jobBestScore) {
								jobBestScore = score;
								jobBestChildrenMap1.clear();
								jobBestChildrenMap1.putAll(jobChildrenMap1);
								jobBestChildrenMap2.clear();
								jobBestChildrenMap2.putAll(jobChildrenMap2);
							}
						}
					}

					//need to map back to original networks:
					if (jobBestScore < bestScore.get()) {
						var entries1 = new ArrayList<>(jobBestChildrenMap1.entrySet());
						jobBestChildrenMap1.clear();
						for (var entry : entries1) {
							var key = jobBackMap1.get(entry.getKey());
							var value = new ArrayList<>(entry.getValue().stream().map(jobBackMap1::get).toList());
							jobBestChildrenMap1.put(key, value);
						}

						var entries2 = new ArrayList<>(jobBestChildrenMap2.entrySet());
						jobBestChildrenMap2.clear();
						for (var entry : entries2) {
							var key = jobBackMap2.get(entry.getKey());
							var value = new ArrayList<>(entry.getValue().stream().map(jobBackMap2::get).toList());
							jobBestChildrenMap2.put(key, value);
						}

						synchronized (bestScore) {
							if (jobBestScore < bestScore.get()) {
								if (false) System.err.println(bestScore.get() + " -> " + jobBestScore);
								bestScore.set(jobBestScore);
								bestChildrenMap1.get().clear();
								bestChildrenMap1.get().putAll(jobBestChildrenMap1);
								bestChildrenMap2.get().clear();
								bestChildrenMap2.get().putAll(jobBestChildrenMap2);
							}
						}
					}
				}, Math.min(jobs.size(), ProgramExecutorService.getNumberOfCoresToUse()), progress);
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
				network1.getLSAChildrenMap().clear();
				if (optimizeSide1) {
					network1.getLSAChildrenMap().putAll(bestChildrenMap1.get());
				} else {
					network1.getLSAChildrenMap().putAll(childrenMap1);
				}
				network2.getLSAChildrenMap().clear();
				if (optimizeSide2) {
					network2.getLSAChildrenMap().putAll(bestChildrenMap2.get());
				} else {
					network2.getLSAChildrenMap().putAll(childrenMap2);
				}
				if (true) {
					System.err.println("DO-Tanglegram\t" + computeInfoString(network1, network1.getLSAChildrenMap(), network2, network2.getLSAChildrenMap()));
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

	private static HashMap<Node, Node> invert(Map<Node, Node> one2oneMap) {
		var invertedMap = new HashMap<Node, Node>();
		for (var entry : one2oneMap.entrySet()) {
			invertedMap.put(entry.getValue(), entry.getKey());
		}
		return invertedMap;
	}

	private static HashMap<Node, List<Node>> copyAndPermuteLSAChildren(HashMap<Node, List<Node>> childrenMap, Map<Node, Node> srcTarMap, Randomize randomize, Random random) {
		var copyChildrenMap = new HashMap<Node, List<Node>>();
		for (var entry : childrenMap.entrySet()) {
			var key = srcTarMap.get(entry.getKey());
			var values = new ArrayList<>(entry.getValue().stream().map(srcTarMap::get).toList());
			if (values.size() > 1) {
				var networkChildren = IteratorUtils.asSet(key.children());
				if (randomize == Randomize.All || (randomize == Randomize.LSANodes && !networkChildren.containsAll(values))) {
					Collections.shuffle(values, random);
				}
			}
			copyChildrenMap.put(key, values);
		}
		return copyChildrenMap;
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

		var resultAndScore = jloda.phylogeny.dolayout.DoTanglegram.apply(network1.getRoot(), v -> (network1.hasTaxa(v) ? network1.getTaxon(v) : null), childrenMap1::get,
				network2.getRoot(), v -> (network2.hasTaxa(v) ? network2.getTaxon(v) : null), childrenMap2::get, reticulateEdges2,
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
		return ReportTanglegramStats.apply(network1, childrenMap1, yMap1::get, network1::getLabel, network2, childrenMap2, yMap2::get, network2::getLabel)
				.score(useTaxonDisplacement1, useTaxonDisplacement2, useReticulateDisplacement1, useReticulateDisplacement2);
	}

	public static String computeInfoString(PhyloTree network1, Map<Node, List<Node>> children1, PhyloTree network2, Map<Node, List<Node>> children2) {
		var yMap1 = computeNodeHeightMap(network1.getRoot(), children1);
		var yMap2 = computeNodeHeightMap(network2.getRoot(), children2);
		return ReportTanglegramStats.apply(
				network1, children1, yMap1::get, network1::getLabel,
				network2, children2, yMap2::get, network2::getLabel).toString();
	}
}

