/*
 *  SampleTrees.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.tools;

import jloda.fx.util.ArgsOptions;
import jloda.graph.Edge;
import jloda.phylo.NewickIO;
import jloda.phylo.PhyloTree;
import jloda.phylo.algorithms.RootedNetworkProperties;
import jloda.util.*;
import splitstree6.compute.autumn.Cluster;
import splitstree6.utils.TreesUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.*;

public class SampleTrees {
	public static void main(String[] args) {
		try {
			ProgramProperties.setProgramName("SampleTrees");
			ProgramProperties.setProgramVersion(splitstree6.main.Version.SHORT_DESCRIPTION);

			PeakMemoryUsageMonitor.start();
			(new SampleTrees()).run(args);
			PeakMemoryUsageMonitor.report();
			System.exit(0);
		} catch (Exception ex) {
			Basic.caught(ex);
			System.exit(1);
		}
	}

	/**
	 * run
	 */
	private void run(String[] args) throws Exception {
		final ArgsOptions options = new ArgsOptions(args, this, "Samples related trees");
		options.setVersion(ProgramProperties.getProgramVersion());
		options.setLicense("This is free software, licensed under the terms of the GNU General Public License, Version 3.");
		options.setAuthors("Daniel H. Huson");

		options.comment("Input and output");
		var inputFile = options.getOptionMandatory("-i", "input", "Input tree file (stdin, *.gz ok)", "");
		var outputFile = options.getOption("-o", "output", "Output file (stdout, *.gz ok)", "stdout");

		options.comment("Options");
		var numberOfTaxa = options.getOption("-t", "taxa", "Number of taxa to restrict the input tree(s) to (0=keep original size)", 0);
		var rSPRs = options.getOption("-r", "rSPR", "Number of rSPRs", 1);
		var maxProportionMissingTaxa = options.getOption("-m", "missing", "Proportion of missing taxa", 0.0);
		var maxProportionContractedInternalEdges = options.getOption("-c", "maxContracted", "Maximum proportion of contracted internal edges", 0.0);
		var numTrees = options.getOption("-n", "numTrees", "Number of trees to sample", 1);
		var randomSeed = options.getOption("-s", "seed", "Random generator seed (0:use random seed)", 0);
		var echoInputTree = options.getOption("-e", "echoInput", "Echo the input tree to output", false);

		options.done();

		FileUtils.checkFileReadableNonEmpty(inputFile);
		FileUtils.checkAllFilesDifferent(inputFile, outputFile);

		var randomForMissingTaxa = new Random();
		var randomForContractEdges = new Random();
		var randomForSPRs = new Random();
		if (randomSeed != 0) {
			randomForMissingTaxa.setSeed(randomSeed);
			randomForContractEdges.setSeed(randomSeed);
			randomForSPRs.setSeed(randomSeed);
		}

		try (var r = new BufferedReader(new InputStreamReader(FileUtils.getInputStreamPossiblyZIPorGZIP(inputFile)));
			 var w = new OutputStreamWriter(FileUtils.getOutputStreamPossiblyZIPorGZIP(outputFile))) {
			var countInputTrees = 0;
			var countOutputTrees = 0;
			var taxIdMap = new HashMap<String, Integer>();
			while (r.ready()) {
				var line = r.readLine();
				if (!line.startsWith("#")) {
					var inputTree = NewickIO.valueOf(line);
					TreesUtils.addAdhocTaxonIds(inputTree, taxIdMap);
					var inputTaxa = BitSetUtils.asBitSet(inputTree.getTaxa());

					if (numberOfTaxa > 0 && numberOfTaxa < inputTaxa.cardinality()) {
						var array = new int[BitSetUtils.max(inputTaxa) + 1];
						var count = 0;
						var newTaxa = new BitSet();
						for (var t : BitSetUtils.members(inputTaxa)) {
							count++;
							array[t] = count;
							newTaxa.set(count);
							if (count == numberOfTaxa)
								break;
						}
						inputTaxa = newTaxa;
						inputTree = TreesUtils.computeInducedTree(array, inputTree);
						assert inputTree != null;
					}

					for (var v : inputTree.nodes()) {
						if (v.getInDegree() == 1 && v.getOutDegree() == 1)
							inputTree.delDivertex(v);
					}
					countInputTrees++;
					if (echoInputTree)
						w.write(inputTree.toBracketString(true) + "[&&NHX:GN=in%s];\n".formatted(countInputTrees));
					for (var t = 0; t < numTrees; t++) {
						var tree = new PhyloTree(inputTree);
						if (maxProportionMissingTaxa > 0) {
							var missing = (int) (maxProportionMissingTaxa * inputTaxa.cardinality());
							if (missing > 0) {
								List<Integer> keep = new ArrayList<>(BitSetUtils.asList(inputTaxa));
								CollectionUtils.randomize(keep, randomForMissingTaxa);
								keep = keep.subList(0, keep.size() - missing);
								var newId = 0;
								var oldNewId = new int[BitSetUtils.max(inputTaxa) + 1];
								for (var tax : keep) {
									oldNewId[tax] = ++newId;
								}
								tree = TreesUtils.computeInducedTree(oldNewId, tree);
								if (tree == null)
									throw new RuntimeException("Tree is null");
							}
						}
						if (maxProportionContractedInternalEdges > 0) {
							List<Edge> edges = CollectionUtils.randomize(IteratorUtils.asStream(tree.edges()).filter(e -> !e.getTarget().isLeaf()).toList(), randomForContractEdges);
							var numContractEdges = (int) Math.ceil(maxProportionContractedInternalEdges * edges.size());
							if (numContractEdges > 0 && numContractEdges < edges.size()) {
								edges = edges.subList(edges.size() - numContractEdges, edges.size());
							}
							RootedNetworkProperties.contractEdges(tree, new HashSet<>(edges), null);
						}
						if (rSPRs > 0) {
							for (var i = 0; i < rSPRs; i++) {
								var edges = IteratorUtils.asList(tree.edges());
								if (edges.size() < 2)
									throw new RuntimeException("Too few edges");
								var first = edges.get(randomForSPRs.nextInt(edges.size()));
								var count = 0;
								while (first.getSource() == tree.getRoot()) {
									first = edges.get(randomForSPRs.nextInt(edges.size()));
									if (++count > 100)
										throw new RuntimeException("Too few edges");
								}
								var second = first;
								while (second == first || second.getSource() == first.getTarget() || second.getTarget() == first.getSource() || second.getSource() == tree.getRoot()) {
									second = edges.get(randomForSPRs.nextInt(edges.size()));
									if (++count > 100)
										throw new RuntimeException("Too few edges");
								}
								var before = TreesUtils.collectAllHardwiredClusters(new PhyloTree(tree));
								applyRootedSPR(first, second, tree);
								var after = TreesUtils.collectAllHardwiredClusters(tree);
								if (before.equals(after))
									System.err.println("no change");
								else {
									var all = CollectionUtils.union(before, after);
									var compatible = true;
									allPairsLoop:
									for (var a : all) {
										for (var b : all) {
											if (a != b && Cluster.incompatible(a, b)) {
												compatible = false;
												break allPairsLoop;
											}
										}
									}
									if (compatible)
										System.err.println("no rSPR");
								}
							}
							countOutputTrees++;
							w.write(tree.toBracketString(true) + "[&&NHX:GN=out%s];\n".formatted(countOutputTrees));
						}
					}
				}
			}
			System.err.printf("Input trees:  %,10d%n", countInputTrees);
			System.err.printf("Output trees:%,11d%n", countOutputTrees);
		}
	}

	private void applyRootedSPR(Edge first, Edge second, PhyloTree tree) {
		var p1 = first.getSource();
		var q1 = first.getTarget();
		var p2 = second.getSource();
		var q2 = second.getTarget();

		var n1 = tree.newNode();

		var e1 = tree.newEdge(p1, n1);
		tree.setWeight(e1, 0.5 * tree.getWeight(first));
		var e2 = tree.newEdge(n1, q1);
		tree.setWeight(e2, 0.5 * tree.getWeight(first));

		tree.deleteEdge(first);

		var f1 = tree.newEdge(n1, q2);
		tree.setWeight(f1, tree.getWeight(second));
		tree.deleteEdge(second);

		if (p2.getInDegree() == 1 && p2.getOutDegree() == 1)
			tree.delDivertex(p2);
	}
}