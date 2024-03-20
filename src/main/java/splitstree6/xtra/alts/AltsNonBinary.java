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

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static splitstree6.splits.TreesUtils.addAdhocTaxonIds;
import static splitstree6.utils.PathMultiplicityDistance.compute;


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
		System.err.println(networks.size() + " different networks found.");

		for (var network : networks)
			System.err.println(network.toBracketString(false));

	}

	public static List<PhyloTree> apply(Collection<PhyloTree> trees, ProgressListener progress) throws IOException {
		HashMap<String, Integer> labelTaxonIdMap = new HashMap<>();
		var initialOrder = getInitialOrder(trees, labelTaxonIdMap);
		//System.err.println("initial order: " + initialOrder);
		HybridizationContext context = new HybridizationContext();
		backTrack(trees, initialOrder, labelTaxonIdMap, initialOrder.size()-1, 1000, context);
		return resultingNetworks(context.hybridizationResultSet, labelTaxonIdMap, progress);
	}

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

	/**
	 *
	 * @param tree
	 * @param order
	 * @param labelTaxonIdMap
	 * @return labelled tree
	 */
	public static String internalNodeLabeller(PhyloTree tree, LinkedList<Integer> order, HashMap<String, Integer> labelTaxonIdMap) {
		Deque<Integer> stack = new ArrayDeque<>();
		StringBuilder labelledNewick = new StringBuilder();

		String newick = removeEncapsulatingSingleQuotes(tree.toBracketString(false));
		for (int i = 0; i < newick.length(); i++) {
			char ch = newick.charAt(i);
			labelledNewick.append(ch);
			if (ch == '(') {
				stack.push(i);
			} else if (ch == ')') {
				if (!stack.isEmpty()) {
					int startIndex = stack.pop();
					String contents = newick.substring(startIndex + 1, i);
					LinkedList<Integer> processedContentSmallestIds = processBrackets(contents, order, labelTaxonIdMap);

					LinkedList<Integer> toBeAddedIds = new LinkedList<>();
					toBeAddedIds = removeSmallestBasedOnOrder(processedContentSmallestIds, order);
					String toBeAdded = "";
					for (var id : toBeAddedIds){
						toBeAdded = toBeAdded + getLabelByTaxonId(labelTaxonIdMap, id) + "/";
					}
					toBeAdded = toBeAdded.substring(0, toBeAdded.length()-1);

					labelledNewick.append(toBeAdded);
				}
			}
		}
		return labelledNewick.toString();
	}
	public static LinkedList<Integer> processBrackets(String content, LinkedList<Integer> order, HashMap<String, Integer> labelTaxonIdMap) {
		LinkedList<Integer> ids = new LinkedList<>();
		StringBuilder buffer = new StringBuilder();
		Stack<Integer> bracketPositions = new Stack<>();

		for (int i = 0; i < content.length(); i++) {
			char ch = content.charAt(i);
			if (ch == '(') {
				bracketPositions.push(i);
				if (buffer.length() > 0) {
					// Process buffer content before bracket as outside element
					ids.add(findSmallestElementId(buffer.toString(), order, labelTaxonIdMap));
					buffer = new StringBuilder(); // Reset buffer
				}
			} else if (ch == ')' && !bracketPositions.isEmpty()) {
				int startIndex = bracketPositions.pop();
				// Process content within brackets
				if (bracketPositions.isEmpty()) {
					String innerContent = content.substring(startIndex + 1, i);
					ids.add(findSmallestElementId(innerContent, order, labelTaxonIdMap));
				}
			} else if (bracketPositions.isEmpty()) {
				// Accumulate characters outside of brackets
				if (ch != ',') buffer.append(ch);
				else if (buffer.length() > 0) {
					// Process buffer content as outside element
					ids.add(findSmallestElementId(buffer.toString(), order, labelTaxonIdMap));
					buffer = new StringBuilder(); // Reset buffer
				}
			}
		}

		// Process any remaining content outside of brackets
		if (buffer.length() > 0) {
			ids.add(findSmallestElementId(buffer.toString(), order, labelTaxonIdMap));
		}

		return ids.stream()
				.distinct() // Remove duplicates
				.collect(Collectors.toCollection(LinkedList::new));
	}
	private static Integer findSmallestElementId(String str, LinkedList<Integer> order, HashMap<String, Integer> labelTaxonIdMap) {
		String[] splitStr = str.split(",");
		Integer smallestTaxonId = null;
		int smallestOrderIndex = Integer.MAX_VALUE;

		for (String label : splitStr) {
			label = label.replaceAll("[()]", "").trim(); // Clean the label
			Integer taxonId = labelTaxonIdMap.get(label);
			if (taxonId != null) {
				int orderIndex = order.indexOf(taxonId);
				if (orderIndex != -1 && orderIndex < smallestOrderIndex) {
					smallestTaxonId = taxonId;
					smallestOrderIndex = orderIndex;
				}
			}
		}
		return smallestTaxonId;
	}
	private static String getLabelByTaxonId(HashMap<String, Integer> labelTaxonIdMap, Integer taxonId) {
		for (Map.Entry<String, Integer> entry : labelTaxonIdMap.entrySet()) {
			if (entry.getValue().equals(taxonId)) {
				return entry.getKey(); // Return the label (key) when the taxon ID (value) matches
			}
		}
		return null; // Return null if no matching taxon ID is found
	}
	private static LinkedList<Integer> removeSmallestBasedOnOrder(LinkedList<Integer> processedIds, LinkedList<Integer> order) {
		if (processedIds == null || processedIds.isEmpty() || processedIds.size() == 1 || order == null || order.isEmpty()) {
			return processedIds;
		}
		Integer smallestElement = null;
		int smallestIndexInOrder = Integer.MAX_VALUE;
		// Find the smallest element based on the order
		for (Integer element : processedIds) {
			int indexInOrder = order.indexOf(element);
			if (indexInOrder != -1 && indexInOrder < smallestIndexInOrder) {
				smallestElement = element;
				smallestIndexInOrder = indexInOrder;
			}
		}
		// Remove the smallest element from processedIds
		if (smallestElement != null) {
			processedIds.remove(smallestElement);
		}
		return processedIds;
	}
	private static String removeEncapsulatingSingleQuotes(String input) {
		String regex = "(?<=\\(|,)\\s*'([^']+?)'\\s*(?=\\)|,)";
		Pattern pattern = Pattern.compile(regex);
		Matcher matcher = pattern.matcher(input);

		StringBuffer sb = new StringBuffer();
		while (matcher.find()) {
			matcher.appendReplacement(sb, "$1");
		}
		matcher.appendTail(sb);
		return sb.toString();
	}

	/**
	 *
	 * @param leafNode
	 * @return hyperString for each leaf node
	 */
	public static LinkedList<String> path(Node leafNode) {
		LinkedList<String> path = new LinkedList<>();
		Node currentNode = leafNode;
		while (currentNode.getParent() != null) {
			List<String> checkParent = List.of(currentNode.getParent().getLabel().split("/"));
			if (checkParent.contains(leafNode.getLabel())) {
				break;
			}
			path.add(currentNode.getParent().getLabel());
			currentNode = currentNode.getParent();
		}
		//System.err.println(leafNode.getLabel() + " " + reverseLinkedListRecursive(path, 0));
		return reverseLinkedListRecursive(path, 0);
	}
	private static LinkedList<String> reverseLinkedListRecursive(LinkedList<String> list, int index) {
		if (index == list.size()) {
			return new LinkedList<>();
		}
		LinkedList<String> reversed = reverseLinkedListRecursive(list, index + 1);
		reversed.add(list.get(index));
		return reversed;
	}

	/**
	 *
	 * @param trees
	 * @param order
	 * @param labelTaxonIdMap
	 * @return number of hybridization, alignment and corresponding order
	 * @throws IOException
	 */
	private static HybridizationResult calculateHybridization(Collection<PhyloTree> trees, LinkedList<Integer> order,
															  HashMap<String, Integer> labelTaxonIdMap) throws IOException {
		Map<Integer, LinkedList<LinkedList<String>>> leafNodeHyperStringMap = new HashMap<>();
		for (var tree : trees) {
			PhyloTree labelledTree = NewickIO.valueOf(internalNodeLabeller(tree, order, labelTaxonIdMap));
			for (Node node : labelledTree.leaves()) {
				LinkedList<String> path = path(node);
				leafNodeHyperStringMap.computeIfAbsent(labelTaxonIdMap.get(node.getLabel()), k -> new LinkedList<>()).add(path);
			}
		}
		Map<Integer, String> alignments = alignMultipleSequences(leafNodeHyperStringMap);

		ConcurrentHashMap<String, AtomicInteger> elementCounts = new ConcurrentHashMap<>();
		// Using parallel stream to count occurrences of each element
		alignments.values().parallelStream()
				.flatMap(value -> Arrays.stream(value.split("[,/]+")))
				.filter(element -> !element.isEmpty())
				.forEach(element -> elementCounts.computeIfAbsent(element, k -> new AtomicInteger(0)).incrementAndGet());

		// Sum up (count - 1) for each element
		int numOfHyb = elementCounts.values().parallelStream()
				.mapToInt(AtomicInteger::get)
				.filter(count -> count > 1)
				.map(count -> count - 1)
				.sum();

		return new HybridizationResult(numOfHyb, alignments, order);
	}
	/**
	 *
	 * @param inputMap
	 * input map contains node ids and their respective hyper strings
	 * @return ids and respective shortest super sequences
	 */
	public static Map<Integer, String> alignMultipleSequences(Map<Integer, LinkedList<LinkedList<String>>> inputMap) {
		Map<Integer, String> consensusMap = new HashMap<>();

		for (Integer key : inputMap.keySet()) {
			LinkedList<LinkedList<String>> sequences = inputMap.get(key);
			// Check if all lists are null or empty
			boolean allNullorEmpty = sequences.stream().allMatch(seq -> seq == null || seq.isEmpty());

			if (allNullorEmpty) {
				// Put an empty string as the consensus for this key
				consensusMap.put(key, "");
				continue;
			}

			String currentConsensus = sequences.stream()
					.filter(seq -> seq != null && !seq.isEmpty())
					.findFirst()
					.map(LinkedList::getFirst)
					.orElse("");

			// Start aligning from the second non-null and non-empty sequence
			for (LinkedList<String> sequence : sequences) {
				if (sequence != null && !sequence.isEmpty() && !sequence.equals(new LinkedList<>(List.of(currentConsensus)))) {
					currentConsensus = scsOfTwoCommaSeparatedLists(new LinkedList<>(List.of(currentConsensus)), sequence);
				}
			}

			consensusMap.put(key, postProcessAlignment(currentConsensus));
		}
		return consensusMap;
	}

	private static String postProcessAlignment(String input) {
		// Split the input into parts
		String[] parts = input.split(",");
		// Use a list for easier manipulation
		List<String> partsList = new ArrayList<>(Arrays.asList(parts));
		outerLoop:
		for (int i = 0; i < partsList.size(); i++) {
			if (partsList.get(i).contains("/")) {
				String[] subParts = partsList.get(i).split("/");
				List<String> subPartsList = Arrays.asList(subParts);

				// Process previous parts, remove all except first
				boolean firstFound = false;
				for (int j = 0; j < i; j++) {
					if (subPartsList.contains(partsList.get(j))) {
						if (!firstFound) {
							firstFound = true; // Skip the first occurrence
						} else {
							partsList.remove(j); // Remove the rest
							i--; // Adjust the current index after removal
							j--; // Adjust the loop index after removal
						}
					}
				}

				// Process next parts, remove all except last
				int lastIndexOfSubPartAfterI = -1;
				for (int j = partsList.size() - 1; j > i; j--) {
					if (subPartsList.contains(partsList.get(j))) {
						lastIndexOfSubPartAfterI = j;
						break;
					}
				}

				for (int j = i + 1; j < lastIndexOfSubPartAfterI; j++) {
					if (subPartsList.contains(partsList.get(j))) {
						partsList.remove(j); // Remove until reaching the last occurrence
						j--; // Adjust the loop index after removal
						lastIndexOfSubPartAfterI--; // Adjust the target index for removals
					}
				}
			}
		}
		// Join the remaining parts back into a string
		return String.join(",", partsList);
	}
	private static String scsOfTwoCommaSeparatedLists(LinkedList<String> seq1List, LinkedList<String> seq2List) {
		// Join all strings in each LinkedList into a single, comma-separated string
		String seq1Joined = String.join(",", seq1List);
		String seq2Joined = String.join(",", seq2List);

		// Splitting the joined strings into arrays, trimming each element
		String[] seq1 = Arrays.stream(seq1Joined.split(","))
				.map(String::trim)
				.toArray(String[]::new);
		String[] seq2 = Arrays.stream(seq2Joined.split(","))
				.map(String::trim)
				.toArray(String[]::new);

		int m = seq1.length;
		int n = seq2.length;

		// DP table to store the count of operations required
		int[][] dp = new int[m + 1][n + 1];

		// Filling the DP table
		for (int i = 0; i <= m; i++) {
			for (int j = 0; j <= n; j++) {
				if (i == 0) {
					dp[i][j] = j;
				} else if (j == 0) {
					dp[i][j] = i;
				} else if (seq1[i - 1].equals(seq2[j - 1])) {
					dp[i][j] = 1 + dp[i - 1][j - 1];
				} else {
					dp[i][j] = 1 + Math.min(dp[i - 1][j], dp[i][j - 1]);
				}
			}
		}
		// Building the SCS from the DP table
		int index = dp[m][n];
		String[] scs = new String[index];
		int i = m, j = n;
		while (i > 0 && j > 0) {
			if (seq1[i - 1].equals(seq2[j - 1])) {
				scs[index - 1] = seq1[i - 1];
				i--;
				j--;
				index--;
			} else if (dp[i - 1][j] > dp[i][j - 1]) {
				scs[index - 1] = seq2[j - 1];
				j--;
				index--;
			} else {
				scs[index - 1] = seq1[i - 1];
				i--;
				index--;
			}
		}
		while (i > 0) {
			scs[index - 1] = seq1[i - 1];
			i--;
			index--;
		}
		while (j > 0) {
			scs[index - 1] = seq2[j - 1];
			j--;
			index--;
		}
		return String.join(",", scs);
	}

	/**
	 *
	 * @param trees
	 * @param order
	 * @param labelTaxonIdMap
	 * @param position
	 * @param desiredNumOfPermutations
	 * @return set of hybridization results containing number of hybridization, order and alignments
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

	/**
	 *
	 * @param hybridizationResults
	 * @param labelTaxonIdMap
	 * @param progress
	 * @return
	 * @throws IOException
	 */
	public static List<PhyloTree> resultingNetworks(Set<HybridizationResult> hybridizationResults,
													HashMap<String, Integer> labelTaxonIdMap, ProgressListener progress) throws IOException {
		List<PhyloTree> trees = new ArrayList<>();
		for (var result : hybridizationResults){
			var tree = network(result.getAlignments(), result.getOrder(), labelTaxonIdMap);
			if (!trees.contains(tree) && isTreeAddedToFinalList(trees,tree)) {
				//System.err.println(result.getHybridizationScore() + " " + result.getAlignments() + " " + result.getOrder());
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
	public static PhyloTree network(Map<Integer, String> taxaSCSmap, LinkedList<Integer> finalOrder, HashMap<String, Integer> labelTaxonIdMap) throws IOException {
		PhyloTree tree = new PhyloTree();

		Map<Integer, String> sortedMap = sortKeysByOrder(taxaSCSmap, finalOrder);

		Map<Integer, Node> keyNodesMap = new HashMap<>();
		List<Pair<Node, Integer>> connectionsToMake = new ArrayList<>();

		for (Map.Entry<Integer, String> nodes : sortedMap.entrySet()) {
			//create first node in chain
			Node firstNode = tree.newNode();
			//add key node to the map to draw edges later
			keyNodesMap.put(nodes.getKey(), firstNode);

			Node previousNode = firstNode;

			//create inner nodes
			String[] values = nodes.getValue() != null ? nodes.getValue().split(",") : new String[0];
			for (var value : values) {
				if (value.isBlank()) {
					continue;
				}
				Node currentNode = tree.newNode();
				//currentNode.setLabel(value+"$");

				tree.newEdge(previousNode, currentNode);

				String[] innerNodeValues = value.split("/");
				for (var nonBinaryNode : innerNodeValues) {
					// Store the connection to be made later
					// For a non-binary node, split the label and make more than one connection
					connectionsToMake.add(new Pair<>(currentNode, labelTaxonIdMap.get(nonBinaryNode)));
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

	private static Map<Integer, String> sortKeysByOrder(Map<Integer, String> map, LinkedList<Integer> order) {
		Map<Integer, String> sortedMap = new LinkedHashMap<>();
		for (Integer key : order) {
			if (map.containsKey(key)) {
				sortedMap.put(key, map.get(key));
			}
		}
		return sortedMap;
	}
}
