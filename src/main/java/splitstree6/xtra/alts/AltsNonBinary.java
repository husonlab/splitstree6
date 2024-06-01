/*
 *  AltsNonBinary.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.xtra.alts;

import jloda.fx.util.ArgsOptions;
import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.phylo.NewickIO;
import jloda.phylo.PhyloTree;
import jloda.util.Pair;
import jloda.util.ProgramExecutorService;
import jloda.util.UsageException;
import jloda.util.progress.ProgressListener;
import jloda.util.progress.ProgressPercentage;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.io.readers.ImportManager;
import splitstree6.io.readers.trees.TreesReader;
import splitstree6.xtra.hyperstrings.HyperSequence;
import splitstree6.xtra.hyperstrings.ProgressiveSCS;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static splitstree6.utils.PathMultiplicityDistance.compute;
import static splitstree6.utils.TreesUtils.addAdhocTaxonIds;


/**
 * implementation of the ALTSNetwork method for non-binary input trees
 * Banu Cetinkaya, 2023-24
 */
public class AltsNonBinary {
	public static void main(String[] args) throws UsageException, IOException {
		var options = new ArgsOptions(args, AltsNonBinary.class, "Non-binary version of ALTSNetwork");
		var infile = options.getOptionMandatory("-i", "input", "Input Newick file", "");
		var outfile = options.getOption("-o", "output", "Output Newick file (.gz or stdout ok)", "stdout");
		ProgramExecutorService.setNumberOfCoresToUse(options.getOption("-t", "threads", "Number of threads to use", 4));
		options.done();

		TaxaBlock taxaBlock = new TaxaBlock();
		TreesBlock inputTreesBlock = new TreesBlock();
		loadTrees(infile, taxaBlock, inputTreesBlock);

		var progress = new ProgressPercentage("Computing hybridization network", -1);

		var start = Instant.now();
		var networks = apply(inputTreesBlock.getTrees(), progress);
		var end = Instant.now();
		System.err.println("Time taken: " + Duration.between(start, end).toSeconds() + " seconds");

		LinkedList<Integer> order = new LinkedList<>();
		order.add(3);
		order.add(2);
		order.add(1);
		order.add(4);
		order.add(5);
		order.add(6);

		HashMap<String, Integer> labelTaxonIdMap = new HashMap<>();
		labelTaxonIdMap.put("a", 1);
		labelTaxonIdMap.put("b", 2);
		labelTaxonIdMap.put("c", 3);
		labelTaxonIdMap.put("d", 4);
		labelTaxonIdMap.put("e", 5);
		labelTaxonIdMap.put("f", 6);


		/*NewickTreeProcessor processor = new NewickTreeProcessor(labelTaxonIdMap, order);
		for (var tree : inputTreesBlock.getTrees()){
			for (var node : tree.leaves()){
				System.err.println(node.getLabel() + " " + processor.findPathFromLeafToRoot(node));
			}
			System.err.println("-------");
		}*/

		for (var network : networks)
			System.err.println(network.toBracketString(false));
	}
	/**
	 *
	 * @param trees
	 * @param progress
	 * @return
	 * @throws IOException
	 */
	public static List<PhyloTree> apply(Collection<PhyloTree> trees, ProgressListener progress) throws IOException {
		HashMap<String, Integer> labelTaxonIdMap = new HashMap<>();
		var initialOrder = getInitialOrder(trees, labelTaxonIdMap);
		//System.err.println("initial order: " + initialOrder);
		HybridizationContext context = new HybridizationContext();
		backTrack(trees, initialOrder, labelTaxonIdMap, initialOrder.size()-1, 1000, context);
		return resultingNetworks(context.hybridizationResultSet, labelTaxonIdMap, progress);
	}

	/**
	 *
	 * @param fileName
	 * @param taxaBlock
	 * @param treesBlock
	 * @throws IOException
	 */
	public static void loadTrees(String fileName, TaxaBlock taxaBlock, TreesBlock treesBlock) throws IOException {
		var importManager = ImportManager.getInstance();
		var dataType = importManager.getDataType(fileName);
		if (dataType.equals(TreesBlock.class)) {
			var fileFormat = importManager.getFileFormat(fileName);
			var importer = (TreesReader) importManager.getImporterByDataTypeAndFileFormat(dataType, fileFormat);
			try (var progress = new ProgressPercentage("Reading: " + fileName)) {
				importer.read(progress, fileName, taxaBlock, treesBlock);
			}
			System.err.println("Trees: " + treesBlock.getNTrees());
		} else throw new IOException("File does not contain trees");

	}

	/**
	 * Takes trees and return initial order
	 * @param trees
	 * @param labelTaxonIdMap
	 * @return list of initial order of ids
	 */
	private static LinkedList<Integer> getInitialOrder(Collection<PhyloTree> trees, HashMap<String, Integer> labelTaxonIdMap) {
		LinkedList<Integer> order = new LinkedList<>();
		Set<Integer> seen = new HashSet<>();
		for (PhyloTree tree : trees) {
			addAdhocTaxonIds(tree, labelTaxonIdMap);
			tree.nodeStream()
					.filter(v -> tree.getLabel(v) != null && labelTaxonIdMap.containsKey(tree.getLabel(v)))
					.map(tree::getLabel)
					.map(label -> labelTaxonIdMap.get(label)) // Convert label to taxon ID
					.filter(taxonId -> !seen.contains(taxonId))
					.forEach(taxonId -> {
						order.add(taxonId);
						seen.add(taxonId);
					});
		}
		return order;
	}

	private static HybridizationResult calculateHybridization(Collection<PhyloTree> trees, LinkedList<Integer> order,
															  HashMap<String, Integer> labelTaxonIdMap) throws IOException {

		Map<Integer, ArrayList<HyperSequence>> leafNodeHyperStringMap = new HashMap<>();
		NewickTreeProcessor processor = new NewickTreeProcessor(labelTaxonIdMap, order);
		for (var tree : trees) {
			for (Node node : tree.leaves()) {
				HyperSequence path = processor.findPathFromLeafToRoot(node);
				leafNodeHyperStringMap.computeIfAbsent(labelTaxonIdMap.get(node.getLabel()), k -> new ArrayList<>()).add(path);
			}
		}

		Map<Integer, HyperSequence> alignments = new HashMap<>();
		for (var entries : leafNodeHyperStringMap.entrySet()){
			var sequences = new ArrayList<HyperSequence>();
			for (var hyperStrings : entries.getValue()){
				sequences.add(hyperStrings);
			}
			alignments.put(entries.getKey(), ProgressiveSCS.apply(sequences));

		}
		//System.err.println("order: " + order + " alignments: " + alignments);

		ConcurrentHashMap<String, AtomicInteger> elementCounts = new ConcurrentHashMap<>();
		// Using parallel stream to count occurrences of each element
		alignments.values().parallelStream()
				.map(hyperSeq -> hyperSeq.toString())  // Assuming getSequenceString returns a String representation
				.flatMap(value -> Arrays.stream(value.split("[, :]+")))  // Split using regex that matches commas and colons
				.flatMap(element -> expandRange(element))  // Expand ranges like "4-6" to "4, 5, 6"
				.filter(element -> !element.isEmpty())
				.forEach(element -> elementCounts.computeIfAbsent(element, k -> new AtomicInteger(0)).incrementAndGet());


		// Sum up (count - 1) for each element that appears more than once
		int numOfHyb = elementCounts.values().parallelStream()
				.mapToInt(AtomicInteger::get)
				.filter(count -> count > 1)
				.map(count -> count - 1)
				.sum();

		return new HybridizationResult(numOfHyb, alignments, order);
	}

	// Method to handle the expansion of ranges like "4-6"
	private static Stream<String> expandRange(String element) {
		if (element.contains("-")) {
			String[] parts = element.split("-");
			int start = Integer.parseInt(parts[0]);
			int end = Integer.parseInt(parts[1]);
			return Stream.iterate(start, n -> n <= end, n -> n + 1).map(String::valueOf);
		} else {
			return Stream.of(element);
		}
	}

	/**
	 *
	 * @param trees
	 * @param order
	 * @param labelTaxonIdMap
	 * @param position
	 * @param desiredNumOfPermutations
	 * @param context
	 * @return
	 * @throws IOException
	 */
	public static Set<HybridizationResult> backTrack(Collection<PhyloTree> trees, LinkedList<Integer> order,
													 HashMap<String, Integer> labelTaxonIdMap, int position,
													 int desiredNumOfPermutations, HybridizationContext context
	) throws IOException {
		if (context.numOfPermutations == factorialRecursive(order.size())-1 || context.numOfPermutations == desiredNumOfPermutations){
			return context.hybridizationResultSet;
		} else {
			if (position < 0) {
				context.numOfPermutations++;
				HybridizationResult hybridizationResult = calculateHybridization(trees, new LinkedList<>(order), labelTaxonIdMap);
				//System.err.println("score:" + hybridizationResult.getHybridizationScore() + " order: " + hybridizationResult.getOrder() + " alignment: "+ hybridizationResult.getAlignments());
				if (hybridizationResult.getHybridizationScore() != 0 && hybridizationResult.getHybridizationScore() <= context.minHybridizationScore){
					context.minHybridizationScore = hybridizationResult.getHybridizationScore();
					//add the first result to the list
					if (context.hybridizationResultSet.isEmpty()){
						context.hybridizationResultSet.add(hybridizationResult);
					} else {
						//if any hyb scores in the set bigger than current min
						boolean isSmaller = context.hybridizationResultSet.parallelStream()
								.anyMatch(result -> result.getHybridizationScore() > context.minHybridizationScore);
						//if any hyb scores in the set equal to current min
						boolean isEqual = context.hybridizationResultSet.parallelStream()
								.anyMatch(result -> result.getHybridizationScore() == context.minHybridizationScore);
						if (isSmaller){
							//if smaller found clear the list add new min
							context.hybridizationResultSet.clear();
							context.hybridizationResultSet.add(hybridizationResult);
							//System.err.println("smaller-> score:" + hybridizationResult.getHybridizationScore() + " order: " + hybridizationResult.getOrder() + " alignment: "+ hybridizationResult.getAlignments());
						} else if (isEqual) {
							context.hybridizationResultSet.add(hybridizationResult);
							//System.err.println("equal-> score:" + hybridizationResult.getHybridizationScore() + " order: " + hybridizationResult.getOrder() + " alignment: "+ hybridizationResult.getAlignments());
						}
					}
				}
			} else {
				for (int i = position; i < order.size(); i++) {
					Collections.swap(order, position, i); // Swap the current element with the element at the position
					backTrack(trees, order, labelTaxonIdMap, position - 1, desiredNumOfPermutations, context); // Recurse for the next position
					Collections.swap(order, i, position); // Swap back to backtrack
				}
			}
		}
		return context.hybridizationResultSet;
	}

	public static long factorialRecursive(int n) {
		if (n == 0) {
			return 1;
		}
		return n * factorialRecursive(n - 1);
	}

	public static PhyloTree network(Map<Integer, HyperSequence> taxaSCSmap, LinkedList<Integer> finalOrder, HashMap<String, Integer> labelTaxonIdMap) throws IOException {
		PhyloTree tree = new PhyloTree();

		Map<Integer, HyperSequence> sortedMap = sortKeysByOrder(taxaSCSmap, finalOrder);

		Map<Integer, Node> keyNodesMap = new HashMap<>();
		List<Pair<Node, Integer>> connectionsToMake = new ArrayList<>();

		for (Map.Entry<Integer, HyperSequence> nodes : sortedMap.entrySet()) {
			//create first node in chain
			Node firstNode = tree.newNode();
			//add key node to the map to draw edges later
			keyNodesMap.put(nodes.getKey(), firstNode);

			Node previousNode = firstNode;

			//create inner nodes
			String[] values = nodes.getValue() != null ? nodes.getValue().toString().split(":") : new String[0];
			for (var value : values) {
				if (value.isBlank()) {
					continue;
				}
				if (value.contains("-")){
					value = expandRangeString(value.trim());
				}
				Node currentNode = tree.newNode();
				//currentNode.setLabel(value+"$");
				tree.newEdge(previousNode, currentNode);
				Edge edge = tree.newEdge(previousNode, currentNode);

				String[] innerNodeValues = value.trim().split(",");
				for (var nonBinaryNode : innerNodeValues) {
					// Store the connection to be made later
					// For a non-binary node, split the label and make more than one connection
					connectionsToMake.add(new Pair<>(currentNode, Integer.valueOf(nonBinaryNode.trim())));
				}
				previousNode = currentNode;
			}
			//add leaf nodes at the end of each chain
			Node leafNode = tree.newNode();
			leafNode.setLabel(getLabelByTaxonId(labelTaxonIdMap, nodes.getKey()));
			tree.newEdge(previousNode, leafNode);
			tree.addTaxon(leafNode, nodes.getKey());

		}


		// Process stored connections
		for (Pair<Node, Integer> connection : connectionsToMake) {
			Node fromNode = connection.getFirst();
			Node toNode = keyNodesMap.get(connection.getSecond());
			if (fromNode != null && toNode != null) {
				tree.newEdge(fromNode, toNode);
			}
		}

		for (var n : tree.nodes()) {
			if (n.getInDegree() == 0) {
				tree.setRoot(n);
			}
		}

		for (var n : tree.nodes()) {
			if (n.getInDegree() == 1 && n.getOutDegree() == 1) {
				tree.delDivertex(n);
			}
		}

		for (var e : tree.edges()) {
			if (e.getTarget().getInDegree() > 1)
				tree.setReticulate(e, true);
		}

		String resultingTree = tree.toBracketString(false).replaceAll("##", "#");
		return NewickIO.valueOf(resultingTree);
	}

	private static Map<Integer, HyperSequence> sortKeysByOrder(Map<Integer, HyperSequence> map, LinkedList<Integer> order) {
		Map<Integer, HyperSequence> sortedMap = new LinkedHashMap<>();
		for (Integer key : order) {
			if (map.containsKey(key)) {
				sortedMap.put(key, map.get(key));
			}
		}
		return sortedMap;
	}

	private static String getLabelByTaxonId(HashMap<String, Integer> labelTaxonIdMap, Integer taxonId) {
		for (Map.Entry<String, Integer> entry : labelTaxonIdMap.entrySet()) {
			if (entry.getValue().equals(taxonId)) {
				return entry.getKey(); // Return the label (key) when the taxon ID (value) matches
			}
		}
		return null; // Return null if no matching taxon ID is found
	}

	private static String expandRangeString(String input) {
		// Split the input string by commas to process each segment individually
		String[] segments = input.split(",");
		StringBuilder result = new StringBuilder();

		for (String segment : segments) {
			if (segment.contains("-")) {
				// If the segment is a range, split it further by '-'
				String[] rangeParts = segment.split("-");
				int start = Integer.parseInt(rangeParts[0]);
				int end = Integer.parseInt(rangeParts[1]);
				// Append each number within the range to the result
				for (int i = start; i <= end; i++) {
					if (result.length() > 0) {
						result.append(",");  // Append a comma before adding new numbers if not the first entry
					}
					result.append(i);
				}
			} else {
				// If the segment is not a range, simply append it
				if (result.length() > 0) {
					result.append(",");  // Append a comma before adding new numbers if not the first entry
				}
				result.append(segment);
			}
		}

		return result.toString();
	}


	public static List<PhyloTree> resultingNetworks(Set<HybridizationResult> hybridizationResults,
													HashMap<String, Integer> labelTaxonIdMap, ProgressListener progress) throws IOException {
		List<PhyloTree> trees = new ArrayList<>();
		for (var result : hybridizationResults){
			var tree = network(result.getAlignments(), result.getOrder(), labelTaxonIdMap);
			if (!trees.contains(tree) && isTreeAddedToFinalList(trees,tree)) {
				System.err.println(result.getHybridizationScore() + " ---> " + result.getOrder());
				trees.add(tree);
			}
		}
		return trees;
	}

	private static boolean isTreeAddedToFinalList(List<PhyloTree> trees, PhyloTree newTree){
		for (var existingTree : trees){
			double distance = compute(existingTree, newTree);
			if (distance==0.0){
				return false;
			}
		}
		return true;
	}


}
