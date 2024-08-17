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
import jloda.graph.EdgeArray;
import jloda.phylo.NewickIO;
import jloda.phylo.PhyloTree;
import jloda.phylo.algorithms.ClusterPoppingAlgorithm;
import jloda.phylo.algorithms.RootedNetworkProperties;
import jloda.util.*;
import jloda.util.progress.ProgressSilent;
import splitstree6.algorithms.trees.trees2trees.*;
import splitstree6.compute.autumn.Cluster;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.io.readers.trees.NewickReader;
import splitstree6.utils.ListIterator;
import splitstree6.utils.ProgressTimeOut;
import splitstree6.utils.TreesUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.*;

/**
 * samples subtrees and also adds rSPRs to a given input tree. Additionally, can run
 * a rooted network algorithm on the generated trees and report time and h
 * Daniel Huson, 6.2024
 */
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
		final ArgsOptions options = new ArgsOptions(args, this, "Samples related trees and optionally runs a network algorithm");
		options.setVersion(ProgramProperties.getProgramVersion());
		options.setLicense("This is free software, licensed under the terms of the GNU General Public License, Version 3.");
		options.setAuthors("Daniel H. Huson");

		options.comment("Input and output");
		var inputFile = options.getOptionMandatory("-i", "input", "Input tree file (stdin, *.gz ok)", "");
		var outputFile = options.getOption("-o", "output", "Output file (stdout, *.gz ok)", "stdout");

		options.comment("Options");
		var numberOfTaxaRangeString = options.getOption("-t", "taxa", "Number of taxa to restrict the input tree(s) to (range ok, \"\"=keep original size)", "");
		var requestedSPRs = options.getOption("-r", "rSPR", "Number of rSPRs to add to each tree (value < 1: will use value*number-of-taxa)", 1.0);
		var firstTreeNoSPR = options.getOption("-fn", "firstTreeNoSPR", "Do not apply any rSPRs to first tree", false);
		var missingTaxa = options.getOption("-m", "missing", "Number of missing taxa (value < 1: will use value*number-of-taxa)", 0.0);
		var contractedInternalEdges = options.getOption("-c", "contracted", "Number of contracted internal edges  (value < 1: will use value*number-of-taxa)", 0.0);
		var numTrees = options.getOption("-n", "numTrees", "Number of trees to sample", 1);
		var randomSeed = options.getOption("-s", "seed", "Random generator seed (0:use random seed)", 42);
		var echoInputTree = options.getOption("-e", "echoInput", "Echo the input tree to output", false);

		var replicates = options.getOption("-R", "replicates", "Number replicates per input tree", 1);
		var runAlgorithm = options.getOption("-a", "algorithm", "Run algorithm and report stats", List.of("PhyloFusion", "PhyloFusionMedium", "PhyloFusionFast", "Autumn", "ALTSNetwork", "ALTSExternal", "external"), "");
		var timeOut = 1000L * options.getOption("-to", "timeOut", "Abort algorithm after this many seconds", 300);
		var externalName = options.getOption("-xp", "externalPath", "Path to external program", "");

		ProgramExecutorService.setNumberOfCoresToUse(options.getOption("-th", "threads", "Set number of threads to use", 8));
		options.done();

		if (runAlgorithm.equals("external"))
			runAlgorithm = externalName;

		FileUtils.checkFileReadableNonEmpty(inputFile);
		FileUtils.checkAllFilesDifferent(inputFile, outputFile);

		if (requestedSPRs <= 0)
			throw new UsageException("--rSPR must be greater 0, got: " + requestedSPRs);
		if (requestedSPRs > 1)
			requestedSPRs = (int) Math.round(requestedSPRs);
		if (runAlgorithm.equalsIgnoreCase("autumn") && numTrees != 2)
			throw new UsageException("--algorithm autumn requires --numTrees 2, not " + numTrees);
		if (echoInputTree && !runAlgorithm.isBlank())
			throw new UsageException("--echoInput and --algorithm: do not specify both");

		var randomForMissingTaxa = new Random();
		var randomForContractEdges = new Random();
		var randomForSPRs = new Random();
		var randomForApplicationOfSPR = new Random();

		if (randomSeed != 0) {
			randomForMissingTaxa.setSeed(randomSeed);
			randomForContractEdges.setSeed(randomSeed);
			randomForSPRs.setSeed(randomSeed);
			randomForApplicationOfSPR.setSeed(randomSeed);
		}

		try (var r = new BufferedReader(new InputStreamReader(FileUtils.getInputStreamPossiblyZIPorGZIP(inputFile)));
			 var w = new OutputStreamWriter(FileUtils.getOutputStreamPossiblyZIPorGZIP(outputFile))) {
			var countInputTrees = 0;
			var countOutputTrees = 0;

			var taxIdMap = new HashMap<String, Integer>();

			while (r.ready()) {
				var line = r.readLine();
				if (line.startsWith("(")) {
					var inputTree0 = NewickIO.valueOf(line);
					TreesUtils.addAdhocTaxonIds(inputTree0, taxIdMap);
					var inputTaxa0 = BitSetUtils.asBitSet(inputTree0.getTaxa());

					int[] numberOfTaxaRange;
					if (numberOfTaxaRangeString.isBlank())
						numberOfTaxaRange = new int[]{inputTaxa0.cardinality()};
					else
						numberOfTaxaRange = BitSetUtils.asList(BitSetUtils.valueOf(numberOfTaxaRangeString)).stream().mapToInt(t -> t).toArray();

					for (var numberOfTaxa : numberOfTaxaRange) {
						if (numberOfTaxaRange.length > 1 && options.isVerbose())
							System.err.println("NumberOfTaxa: " + numberOfTaxa);
						BitSet inputTaxa;
						PhyloTree inputTree;
						if (numberOfTaxa > 0 && numberOfTaxa < inputTaxa0.cardinality()) {
							var array = new int[BitSetUtils.max(inputTaxa0) + 1];
							var count = 0;
							inputTaxa = new BitSet();
							for (var t : BitSetUtils.members(inputTaxa0)) {
								count++;
								array[t] = count;
								inputTaxa.set(count);
								if (count == numberOfTaxa)
									break;
							}
							inputTree = TreesUtils.computeInducedTree(array, inputTree0);
							assert inputTree != null;
							assert BitSetUtils.asBitSet(inputTree.getTaxa()).equals(BitSetUtils.asBitSet(BitSetUtils.range(1, numberOfTaxa + 1)));
						} else {
							inputTaxa = inputTaxa0;
							inputTree = inputTree0;
						}

						for (var v : inputTree.nodes()) {
							if (v.getInDegree() == 1 && v.getOutDegree() == 1)
								inputTree.delDivertex(v);
						}
						countInputTrees++;
						if (echoInputTree)
							w.write(inputTree.toBracketString(true) + "[&&NHX:GN=in%s];\n".formatted(countInputTrees));

						var data = new ArrayList<DataPoint>();
						for (var replicate = 1; replicate <= replicates; replicate++) {
							var appliedSPRs = 0;
							if (options.isVerbose())
								System.err.println("Running replicate " + replicate);
							var producedTrees = new ArrayList<PhyloTree>();
							for (var t = 0; t < numTrees; t++) {
								var tree = new PhyloTree(inputTree);
								check(tree);
								if (missingTaxa > 0) {
									var missing = (int) Math.round(missingTaxa >= 1 ? missingTaxa : missingTaxa * inputTaxa.cardinality());
									if (missing > 0) {
										var keep = CollectionUtils.randomize(BitSetUtils.asList(inputTaxa), randomForMissingTaxa)
												.subList(0, inputTaxa.cardinality() - missing);
										var newId = 0;
										var oldNewId = new int[BitSetUtils.max(inputTaxa) + 1];
										for (var tax : keep) {
											oldNewId[tax] = ++newId;
										}
										tree = TreesUtils.computeInducedTree(oldNewId, tree);
										if (tree == null)
											throw new RuntimeException("Tree is null");
										check(tree);
									}
								}
								if (contractedInternalEdges > 0) {
									List<Edge> edges = CollectionUtils.randomize(IteratorUtils.asStream(tree.edges()).filter(e -> !e.getTarget().isLeaf()).toList(), randomForContractEdges);
									var numContractEdges = (int) Math.round(contractedInternalEdges >= 1 ? contractedInternalEdges : contractedInternalEdges * edges.size());
									if (numContractEdges > 0 && numContractEdges < edges.size()) {
										edges = edges.subList(edges.size() - numContractEdges, edges.size());
									}
									RootedNetworkProperties.contractEdges(tree, new HashSet<>(edges), null);
									check(tree);
								}
								if (requestedSPRs > 0) {
									int numberToApply;
									if (firstTreeNoSPR && t == 0)
										numberToApply = 0;
									else if (requestedSPRs < 1) {
										numberToApply = (int) Math.ceil(requestedSPRs * numberOfTaxa);
									} else {
										numberToApply = (int) requestedSPRs;
									}
									appliedSPRs += numberToApply;

									for (var i = 0; i < numberToApply; i++) {
										var edges = IteratorUtils.asList(tree.edges());
										edges.removeAll(tree.getRoot().outEdgesStream(false).toList());
										if (edges.size() < 2)
											throw new RuntimeException("Too few edges");

										var movingEdge = edges.get(randomForSPRs.nextInt(edges.size()));
										edges.removeAll(getEdgesBelow(movingEdge));

										var targetEdge = movingEdge;
										Collection<BitSet> all;

										var count = 0;
										while (true) {
											targetEdge = edges.get(randomForSPRs.nextInt(edges.size()));
											if (!(targetEdge.getSource() == movingEdge.getTarget() || targetEdge.getTarget() == movingEdge.getSource())) {
												try (EdgeArray<Edge> srcTarMap = tree.newEdgeArray()) {
													var newTree = new PhyloTree();
													newTree.copy(tree, null, srcTarMap);
													var before = TreesUtils.collectAllHardwiredClusters(newTree);
													applyRootedSPR(srcTarMap.get(targetEdge), srcTarMap.get(movingEdge), newTree);
													var after = TreesUtils.collectAllHardwiredClusters(newTree);
													if (!before.equals(after) && tree.isConnected()) {
														all = CollectionUtils.union(before, after);
														tree = newTree;
														break;
													}
												}
											}
											if (++count > 100)
												throw new RuntimeException("Too few edges");
										}

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
										check(tree);
									}
									countOutputTrees++;
									if (runAlgorithm.isBlank())
										w.write(tree.toBracketString(true) + "[&&NHX:GN=out%s];\n".formatted(countOutputTrees));

									producedTrees.add(tree);
								}
							}
							if (!runAlgorithm.isBlank()) {
								data.add(runAlgorithm(runAlgorithm, numberOfTaxa, timeOut, replicate, requestedSPRs, appliedSPRs, contractedInternalEdges, missingTaxa, producedTrees, options.isVerbose()));
							}
						}
						if (!data.isEmpty()) {
							w.write(DataPoint.header());
							for (var d : data) {
								w.write(d.toString());
							}
							w.write("\n" + DataPoint.average(runAlgorithm, data) + "\n");
							w.flush();
						}
					}
				}
			}
			if (options.isVerbose()) {
				System.err.printf("Input trees:  %,10d%n", countInputTrees);
				System.err.printf("Output trees:%,11d%n", countOutputTrees);
			}
		}
	}

	private Collection<Edge> getEdgesBelow(Edge e) {
		var list = new ArrayList<Edge>();
		var stack = new Stack<Edge>();
		stack.add(e);
		while (!stack.isEmpty()) {
			e = stack.pop();
			list.add(e);
			for (var f : e.getTarget().outEdges()) {
				stack.push(f);
			}
		}
		return list;
	}

	private DataPoint runAlgorithm(String algorithmName, int numberOfTaxa, long timeOut, int replicate, double requestedSPRs, int totalSPRs, double maxProportionContractedInternalEdges, double maxProportionMissingTaxa, List<PhyloTree> inputTrees, boolean verbose) throws IOException, UsageException {
		var taxaBlock = new TaxaBlock();
		var inputBlock = new TreesBlock();
		{
			var reader = new NewickReader();
			reader.read(new ProgressSilent(), new ListIterator<>(inputTrees.stream().map(t -> t.toBracketString(false) + ";").toList()), taxaBlock, inputBlock);
		}

		if (verbose) {
			System.err.println("Input trees:");
			for (var tree : inputBlock.getTrees())
				System.err.println(tree.toBracketString(false) + ";");
		}

		if (inputBlock.isReticulated())
			throw new IOException("Illegal rooted network in input");

		Trees2Trees algorithm;

		if (algorithmName.endsWith(".sh")) {
			var external = new ExternalTrees2Trees();
			external.setOptionExecutable(algorithmName);
			algorithm = external;
		} else if (algorithmName.toLowerCase().startsWith("phylofusion")) {
			var phyloFusion = new PhyloFusion();
			phyloFusion.setOptionMutualRefinement(true);
			phyloFusion.setOptionNormalizeEdgeWeights(true);
			phyloFusion.setOptionCalculateWeights(false);
			phyloFusion.setOptionOnlyOneNetwork(true);
			if (algorithmName.toLowerCase().endsWith("fast"))
				phyloFusion.setOptionSearchHeuristic(PhyloFusion.Search.Fast);
			else if (algorithmName.toLowerCase().endsWith("medium"))
				phyloFusion.setOptionSearchHeuristic(PhyloFusion.Search.Medium);
			else
				phyloFusion.setOptionSearchHeuristic(PhyloFusion.Search.Thorough);
			algorithm = phyloFusion;
		} else if (algorithmName.equalsIgnoreCase("altsNetwork")) {
			var alts = new ALTSNetwork();
			alts.setOptionMutualRefinement(true);
			algorithm = alts;
		} else if (algorithmName.equalsIgnoreCase("altsExternal")) {
			if (inputBlock.isPartial())
				throw new IOException("--algorithm ALTSExternal not applicable to trees with missing taxa");
			for (var tree : inputTrees) {
				if (tree.nodeStream().anyMatch(v -> v.getOutDegree() > 2))
					throw new IOException("--algorithm ALTSExternal not applicable to trees with multifurcations");
			}
			var alts = new ALTSExternal();
			alts.setOptionALTSExecutableFile("/Users/huson/cpp/louxin/alts");
			algorithm = alts;
		} else if (algorithmName.equalsIgnoreCase("autumn")) {
			var autumn = new AutumnAlgorithm();
			autumn.setOptionOnlyOneNetwork(true);
			algorithm = autumn;

		} else throw new UsageException("Unknown algorithm " + algorithmName);

		var outputBlock = new TreesBlock();
		var time = System.currentTimeMillis();
		try {
			algorithm.compute(new ProgressTimeOut(timeOut), taxaBlock, inputBlock, outputBlock);
		} catch (IOException ex) { // timed out
			if (ProgressTimeOut.MESSAGE.equals(ex.getMessage())) {
				System.err.println("Timed out: " + replicate);
				return new DataPoint(algorithmName, replicate, taxaBlock.getNtax(), inputTrees.size(),
						maxProportionContractedInternalEdges, maxProportionMissingTaxa, requestedSPRs, totalSPRs, -1, timeOut / 1000.0);
			} else throw ex;
		}
		time = System.currentTimeMillis() - time;

		var network = outputBlock.getTree(1);

		var h = network.nodeStream().filter(v -> v.getInDegree() > 1).mapToInt(v -> v.getInDegree() - 1).sum();

		if (verbose) {
			System.err.println("network: " + network.toBracketString(true) + ";");
		}

		var dataPoint = new DataPoint(algorithmName, replicate, numberOfTaxa, inputTrees.size(),
				maxProportionContractedInternalEdges, maxProportionMissingTaxa, requestedSPRs, totalSPRs, h, time / 1000.0);

		if (verbose)
			System.err.print(dataPoint);

		return dataPoint;
	}

	private void applyRootedSPR(Edge targetEdge, Edge movingEdge, PhyloTree tree) throws IOException {
		var p1 = targetEdge.getSource();
		var q1 = targetEdge.getTarget();
		var p2 = movingEdge.getSource();
		var q2 = movingEdge.getTarget();

		var n1 = tree.newNode();

		var e1 = tree.newEdge(p1, n1);
		tree.setWeight(e1, 0.5 * tree.getWeight(movingEdge));
		var e2 = tree.newEdge(n1, q1);
		tree.setWeight(e2, 0.5 * tree.getWeight(movingEdge));

		tree.deleteEdge(movingEdge);

		var f1 = tree.newEdge(n1, q2);
		tree.setWeight(f1, tree.getWeight(targetEdge));
		tree.deleteEdge(targetEdge);

		if (p2.getInDegree() == 1 && p2.getOutDegree() == 1)
			tree.delDivertex(p2);
	}

	public static void check(PhyloTree tree) throws IOException {
		var recLeaves = new Counter(0);
		tree.postorderTraversal(v -> {
			if (v.isLeaf())
				recLeaves.increment();
		});
		var leaves = tree.nodeStream().filter(v -> v.getOutDegree() == 0).count();
		if (recLeaves.get() != leaves)
			throw new IOException("leaves != recLeaves");
	}

	public static PhyloTree subtree(int nTaxa, PhyloTree tree) {
		var taxa = BitSetUtils.asBitSet(BitSetUtils.range(1, nTaxa + 1));
		var clusters = new HashSet<BitSet>();
		clusters.add(taxa);
		for (var cluster : TreesUtils.collectAllHardwiredClusters(tree)) {
			cluster.and(taxa);
			if (cluster.cardinality() > 0)
				clusters.add(cluster);
		}
		var result = new PhyloTree();
		ClusterPoppingAlgorithm.apply(clusters, result);
		result.nodeStream().filter(result::hasTaxa).forEach(v -> result.setLabel(v, "t" + result.getTaxon(v)));
		return result;
	}

	/**
	 * data point to report
	 *
	 * @param replicate
	 * @param nTaxa
	 * @param nTrees
	 * @param contractedEdges
	 * @param missingTaxa
	 * @param totalSPRs
	 * @param h
	 * @param timeInSeconds
	 */
	public record DataPoint(String algorithmName, int replicate, int nTaxa, int nTrees,
							double contractedEdges,
							double missingTaxa, double requestedSPRs, int totalSPRs, int h,
							double timeInSeconds) {
		public String toString() {
			return "%s\t%d\t%d\t%d\t%s\t%s\t%s\t%d\t%d\t%d\t%.1f%n".formatted(algorithmName, replicate, nTaxa, nTrees,
					StringUtils.removeTrailingZerosAfterDot(contractedEdges), StringUtils.removeTrailingZerosAfterDot(missingTaxa),
					StringUtils.removeTrailingZerosAfterDot(requestedSPRs), totalSPRs, h, Math.max(totalSPRs, h), timeInSeconds);
		}

		public static String header() {
			return "#algorithm\treplicate\ttaxa\ttrees\tcontract\tmissing\tspr\tTotalSPRs\tH\tMAX\tSeconds\n";

		}

		public static String average(String algorithmName, Collection<DataPoint> data) {
			data = data.stream().filter(d -> d.h() >= 0).toList();
			var nTaxa = data.stream().mapToInt(DataPoint::nTaxa).average().orElse(0.0);
			var nTrees = data.stream().mapToInt(DataPoint::nTrees).average().orElse(0.0);
			var contractedEdges = data.stream().mapToDouble(DataPoint::contractedEdges).average().orElse(0.0);
			var missingTaxa = data.stream().mapToDouble(DataPoint::missingTaxa).average().orElse(0.0);
			var nSPR = data.stream().mapToDouble(DataPoint::requestedSPRs).average().orElse(0.0);
			var totalSPRs = data.stream().mapToDouble(DataPoint::totalSPRs).average().orElse(0.0);
			var h = data.stream().mapToInt(DataPoint::h).average().orElse(0.0);
			var max = data.stream().mapToInt(d -> Math.max(d.totalSPRs, d.h)).average().orElse(0.0);
			var time = data.stream().mapToDouble(DataPoint::timeInSeconds).average().orElse(0.0);

			return "av\t%s\t%.1f\t%.0f\t%s\t%s\t%s\t%s\t%.1f\t%.1f\t%.1f%n".formatted(algorithmName, nTaxa, nTrees,
					StringUtils.removeTrailingZerosAfterDot(contractedEdges), StringUtils.removeTrailingZerosAfterDot(missingTaxa),
					StringUtils.removeTrailingZerosAfterDot(nSPR), StringUtils.removeTrailingZerosAfterDot(totalSPRs), h, max, time);
		}
	}
}