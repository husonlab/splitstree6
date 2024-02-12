/*
 *  AltsNonBinary.java Copyright (C) 2023 Daniel H. Huson
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
import jloda.fx.util.ProgramExecutorService;
import jloda.graph.Node;
import jloda.phylo.LSAUtils;
import jloda.phylo.NewickIO;
import jloda.phylo.PhyloTree;
import jloda.util.Basic;
import jloda.util.FileUtils;
import jloda.util.Pair;
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
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


/**
 * implementation of the ALTSNetwork method for non-binary input trees
 * Banu Cetinkaya, 2023-24
 */
public class AltsNonBinary {
	private static Set<HybridizationResult> hybridizationResultSet = new HashSet<>();
	private static int numOfPermutations = 0;
	private static int minHybridizationScore = Integer.MAX_VALUE;
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
		// todo: add this to your algorithm and call the method progress.checkForCancel(); frequently
		// it will throw a UserCanceled exception if the user has indicated to cancel the calculation
		// when this exception is thrown, stop your code, clean up and then rethrow the exception

		var start = Instant.now();
		var networks = apply(inputTreesBlock.getTrees(), progress);
		var end = Instant.now();
		System.out.println("Time taken: " + Duration.between(start, end).toSeconds() + " seconds");
		System.out.println(networks.size() + " networks found over " + numOfPermutations + " permutations.");

		for (var network : networks)
			System.out.println(network.toBracketString(false));

		System.err.println("Writing: " + outfile);
		try (var w = FileUtils.getOutputWriterPossiblyZIPorGZIP(outfile)) {
			for (var network : networks) {
				w.write(NewickIO.toString(network, false) + ";\n");
			}
		}

	}

	public static List<PhyloTree> apply(Collection<PhyloTree> trees, ProgressListener progress) {
		try {
			var initialOrder = getInitialOrder(trees); // todo: should use taxon ids, not labels
			backTrack(trees, initialOrder, initialOrder.size()-1, 1000);
			return resultingNetworks(hybridizationResultSet, progress);
		} catch (IOException ex) {
			Basic.caught(ex);
			return new ArrayList<>();
		}
	}

	private static ArrayList<String> getInitialOrder(Collection<PhyloTree> trees) {
		var order = new ArrayList<String>();
		{
			var seen = new HashSet<String>();
			for (var tree : trees) {
				tree.nodeStream().filter(v -> tree.getLabel(v) != null).map(tree::getLabel)
						.filter(label -> !label.isBlank() && !seen.contains(label))
						.forEach(label -> {
							order.add(label);
							seen.add(label);
						});
			}
		}
		return order;
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
	 * Label the internal nodes to find hyper strings
	 * internalNodeLabeller, processBrackets and findSmallestElement methods belong to this process
	 */
	public static String internalNodeLabeller(String newick, List<String> order) {
		Deque<Integer> stack = new ArrayDeque<>();
		StringBuilder labelledNewick = new StringBuilder();

		List<String> newOrder = new ArrayList<>();
		int charAt = 0;
		while(newick.charAt(charAt) == '('){
			charAt++;
		}
		if ( newick.charAt(charAt) == '\''){
			for (var element : order){
				newOrder.add("'"+element+"'");
			}
		} else if (newick.charAt(charAt) == '\"') {
			for (var element : order){
				newOrder.add("'"+element+"'");
			}
		}
		if (!newOrder.isEmpty()){
			order = newOrder;
		}

		for (int i = 0; i < newick.length(); i++) {
			char ch = newick.charAt(i);
			labelledNewick.append(ch);
			if (ch == '(') {
				stack.push(i);
			} else if (ch == ')') {
				if (!stack.isEmpty()) {
					int startIndex = stack.pop();
					String contents = newick.substring(startIndex + 1, i);
					//System.out.println("content: " + contents);
					String processedContent = processBrackets(contents, order);
					//System.out.println("processed content: "+processedContent);
					String smallestRemoved = processedContent.trim().replaceAll("\\b" + Pattern.quote(findSmallestElement(processedContent, order)) + "\\b", "").
							replace(",","/").trim().replaceAll("^/+|/+$", "").replaceAll("/{2,}", "/");
					//System.out.println("smallest removed: " + smallestRemoved);
					labelledNewick.append(smallestRemoved);
					//System.out.println(labelledNewick);
				}
			}
		}
		//System.out.println(labelledNewick);
		return labelledNewick.toString();
	}

	private static String processBrackets(String content, List<String> order) {
		StringBuilder result = new StringBuilder();
		Stack<Integer> stack = new Stack<>();
		int lastProcessedIndex = 0;

		for (int i = 0; i < content.length(); i++) {
			char ch = content.charAt(i);
			if (ch == '(') {
				stack.push(i);
			} else if (ch == ')' && !stack.isEmpty()) {
				int startIndex = stack.pop();
				if (stack.isEmpty()) {
					String innerContent = content.substring(startIndex + 1, i);
					//System.out.println("inner content: "+innerContent);
					String smallest = findSmallestElement(innerContent, order);
					result.append(content, lastProcessedIndex, startIndex).append(smallest);
					lastProcessedIndex = i + 1;
				}
			}
		}
		if (lastProcessedIndex < content.length()) {
			result.append(content.substring(lastProcessedIndex));
		}
		return result.toString();
	}

	private static String findSmallestElement(String str, List<String> order) {
		String[] splitStr = str.split(",");
		String smallest = null;
		int smallestOrderIndex = Integer.MAX_VALUE;
		for (String s : splitStr) {
			int orderIndex = order.indexOf(s.replaceAll("[()]", "").trim());
			if (orderIndex != -1 && orderIndex < smallestOrderIndex) {
				smallest = s.replaceAll("[()]", "").trim();
				smallestOrderIndex = orderIndex;
			}
		}
		return smallest != null ? smallest : "";
	}

	/**
	 * Finds the hyperstring for each taxa
	 * Starts from leaf go up until no parent found. If leaf encounters its own label, path creation stops there.
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
		//System.out.println(leafNode.getLabel() + " " + reverseLinkedListRecursive(path, 0));
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
	 * Progressively computes the shortest common sequence for each taxon
	 */
	public static Map<String, String> alignMultipleSequences(Map<String, LinkedList<LinkedList<String>>> inputMap) {
		Map<String, String> consensusMap = new HashMap<>();

		for (String key : inputMap.keySet()) {
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
			//System.out.println("map: " + consensusMap);
		}

		return consensusMap;
	}

	public static String postProcessAlignment(String input) {
		// Split the input into parts
		String[] parts = input.split(",");
		// Use a list for easier manipulation
		List<String> partsList = new ArrayList<>(Arrays.asList(parts));

		outerLoop:
		for (int i = 0; i < partsList.size(); i++) {
			if (partsList.get(i).contains("/")) {
				String[] subParts = partsList.get(i).split("/");
				List<String> subPartsList = Arrays.asList(subParts);

				// Check for each subPart
				for (String subPart : subPartsList) {
					// Check if the current subPart is present in the list (excluding the current part)
					boolean subPartPresentElsewhere = false;
					for (int j = 0; j < partsList.size(); j++) {
						if (j != i && partsList.get(j).equals(subPart)) {
							subPartPresentElsewhere = true;
							break;
						}
					}

					// If any subPart is not present elsewhere, continue to the next part
					if (!subPartPresentElsewhere) {
						continue outerLoop;
					}
				}

				// If all subParts are confirmed to be present elsewhere, proceed with removal
				// Process previous parts
				for (int j = i - 1; j >= 0; j--) {
					if (subPartsList.contains(partsList.get(j))) {
						partsList.remove(j); // Remove the last occurrence found
						i--; // Adjust the current index after removal
						break; // Stop after removing the first matching previous part
					}
				}

				// Process next parts
				for (int j = i + 1; j < partsList.size(); j++) {
					if (subPartsList.contains(partsList.get(j))) {
						partsList.remove(j); // Remove the first occurrence found
						break; // Stop after removing the first matching next part
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
	 * Calculates alignments for given trees and corresponding hybridization number
	 */
	private static HybridizationResult calculateHybridization(Collection<PhyloTree> trees, List<String> order) throws IOException {
		Map<String, LinkedList<LinkedList<String>>> leafNodeHyperStringMap = new HashMap<>();

		for (var tree : trees) {
			PhyloTree labelledTree = NewickIO.valueOf(internalNodeLabeller(tree.toBracketString(false), order));
			for (Node node : labelledTree.nodes()) {
				if (node.isLeaf()) {
					LinkedList<String> path = path(node);
					leafNodeHyperStringMap.computeIfAbsent(node.getLabel(), k -> new LinkedList<>()).add(path);
				}
			}
		}

		Map<String, String> alignments = alignMultipleSequences(leafNodeHyperStringMap);

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

	public static List<PhyloTree> resultingNetworks(Set<HybridizationResult> hybridizationResults, ProgressListener progress) throws IOException {
		List<PhyloTree> trees = new ArrayList<>();
		for (var result : hybridizationResults){
			PhyloTree tree = network(result.getAlignments(), result.getOrder());
			if (!trees.contains(tree)) {
				trees.add(tree);
			}
		}
		return trees;
	}

	/**
	 * back tracking test
	 */
	public static Set<HybridizationResult> backTrack(Collection<PhyloTree> trees, List<String> order, int position, int desiredNumOfPermutations) throws IOException {
		if (numOfPermutations == factorialRecursive(order.size())-1 || numOfPermutations == desiredNumOfPermutations){
			return hybridizationResultSet;
		} else {
			if (position < 0) {
				numOfPermutations ++;
				HybridizationResult hybridizationResult = calculateHybridization(trees, new ArrayList<>(order));
				if (hybridizationResult.getHybridizationScore() <= minHybridizationScore){
					minHybridizationScore = hybridizationResult.getHybridizationScore();
					//add the first result to the list
					if (hybridizationResultSet.isEmpty()){
						hybridizationResultSet.add(hybridizationResult);
					} else {
						//if any hyb scores in the set bigger than current min
						boolean isSmaller = hybridizationResultSet.stream()
								.anyMatch(result -> result.getHybridizationScore() > minHybridizationScore);
						//if any hyb scores in the set equal to current min
						boolean isEqual = hybridizationResultSet.stream()
								.anyMatch(result -> result.getHybridizationScore() == minHybridizationScore);
						if (isSmaller){
							//if smaller found clear the list add new min
							hybridizationResultSet.clear();
							hybridizationResultSet.add(hybridizationResult);
						} else if (isEqual) {
							hybridizationResultSet.add(hybridizationResult);
							//System.out.println("equal added: " + order + " " +hybridizationResult.getAlignments());
						}
					}
				}
			} else {
				for (int i = position; i < order.size(); i++) {
					Collections.swap(order, position, i); // Swap the current element with the element at the position
					backTrack(trees, order, position - 1, desiredNumOfPermutations); // Recurse for the next position
					Collections.swap(order, i, position); // Swap back to backtrack
				}
			}
		}
		return hybridizationResultSet;
	}
	public static long factorialRecursive(int n) {
		if (n == 0) {
			return 1;
		}
		return n * factorialRecursive(n - 1);
	}

	/**
	 * Calls the recursive function and returns the tree
	 */
	/*public static List<PhyloTree> resultingNetworks(Collection<PhyloTree> trees, List<String> initialOrder, ProgressListener progress) throws IOException {
		var finalOrder = new ArrayList<String>();
		return List.of(calculateBestScoreOrder(trees, initialOrder, finalOrder, Integer.MAX_VALUE));
	}*/

	/**
	 * Recursively calculate the order with min num of hybridization, and compute the corresponding network at the end.
	 * Called in resultingTree
	 */
	private static PhyloTree calculateBestScoreOrder(Collection<PhyloTree> trees, List<String> remainingOrder, List<String> finalOrder, int currentMinHybridization) throws IOException {
		if (remainingOrder.isEmpty()) {
			HybridizationResult result = calculateHybridization(trees, finalOrder);
			System.err.println("Final Order: " + finalOrder);
			//System.out.println("Alignments for best order: " + result.getAlignments());
			System.err.println("Number of hybridization: " + currentMinHybridization);

			return network(result.getAlignments(), finalOrder);
		}

		ConcurrentHashMap<String, Integer> hybridizationMap = new ConcurrentHashMap<>();
		AtomicInteger minHybridization = new AtomicInteger(currentMinHybridization);

		remainingOrder.parallelStream().forEach(element -> {
			List<String> combinedOrder = new ArrayList<>(finalOrder);
			combinedOrder.add(element); // Add the current element to the fixed positions
			combinedOrder.addAll(remainingOrder.stream().filter(e -> !e.equals(element)).collect(Collectors.toList())); // Add the rest of the modified order

			int currentHybridization;
			try {
				HybridizationResult hybridizationResult = calculateHybridization(trees, combinedOrder);
				currentHybridization = hybridizationResult.getHybridizationScore();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			hybridizationMap.put(element, currentHybridization);

			// Update the minimum hybridization value
			if (currentHybridization < minHybridization.get()) {
				minHybridization.set(currentHybridization);
			}
		});
		// Find the entry with the minimum hybridization value
		Map.Entry<String, Integer> bestEntry = Collections.min(hybridizationMap.entrySet(), Comparator.comparing(Map.Entry::getValue));

		if (bestEntry != null) {
			finalOrder.add(bestEntry.getKey());
			remainingOrder.remove(bestEntry.getKey());
			//findBestOrder(treesBlock, remainingOrder, finalOrder, minHybridization.get());
		}
		return calculateBestScoreOrder(trees, remainingOrder, finalOrder, minHybridization.get());
	}

	/**
	 * Takes the map and returns the corresponding tree.
	 * Called in bestOrderNetwork
	 */
	public static PhyloTree network(Map<String, String> taxaSCSmap, List<String> finalOrder) throws IOException {
		PhyloTree tree = new PhyloTree();

		Map<String, String> sortedMap = sortKeysByOrder(taxaSCSmap, finalOrder);

		Map<String, Node> keyNodesMap = new HashMap<>();
		List<Pair<Node, String>> connectionsToMake = new ArrayList<>();

		int taxId = 0;
		for (Map.Entry<String, String> nodes : sortedMap.entrySet()) {
			//create first node in chain
			Node firstNode = tree.newNode();
			//firstNode.setLabel(nodes.getKey()+"*");
			tree.addTaxon(firstNode, taxId);
			//add key node to the map to draw edges later
			keyNodesMap.put(nodes.getKey() + "*", firstNode);

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
				tree.addTaxon(currentNode, taxId);

				String[] innerNodeValues = value.split("/");
				for (var nonBinaryNode : innerNodeValues) {
					// Store the connection to be made later
					// For a non-binary node, split the label and make more than one connection
					connectionsToMake.add(new Pair<>(currentNode, nonBinaryNode));
				}
				previousNode = currentNode;
			}
			//add leaf nodes at the end of each chain
			Node leafNode = tree.newNode();
			leafNode.setLabel(nodes.getKey());
			tree.newEdge(previousNode, leafNode);
			tree.addTaxon(leafNode, taxId);

			taxId++;
		}

		// Process stored connections
		for (Pair<Node, String> connection : connectionsToMake) {
			Node fromNode = connection.getFirst();
			Node toNode = keyNodesMap.get(connection.getSecond() + "*");
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

		String tree1 = tree.toBracketString(false).replaceAll("##", "#");
		return NewickIO.valueOf(tree1);
	}




	private static Map<String, String> sortKeysByOrder(Map<String, String> map, List<String> order) {
		Map<String, String> sortedMap = new LinkedHashMap<>();
		for (String key : order) {
			if (map.containsKey(key)) {
				sortedMap.put(key, map.get(key));
			}
		}
		return sortedMap;
	}


	/**
	 * Alternative implementation for network
	 */
	public static PhyloTree computeNetwork(TreesBlock treesBlock, Map<String, String> taxaSCSmap) {
		PhyloTree tree = new PhyloTree();

		//create root
		Node root = tree.newNode();
		//root.setLabel("root");
		tree.setRoot(root);
		//tree.addTaxon(root, tree.getNumberOfTaxa());

		// Map to store key nodes
		Map<String, Node> keyNodesMap = new HashMap<>();
		for (var keyNodeLabel : taxaSCSmap.keySet()) {
			//create a node to start cascade
			Node firstNodeInCascade = tree.newNode();
			firstNodeInCascade.setLabel(keyNodeLabel + "*");
			tree.addTaxon(firstNodeInCascade, tree.getNumberOfTaxa());

			//connect the keyNode to root
			tree.newEdge(root, firstNodeInCascade);

			keyNodesMap.put(keyNodeLabel, firstNodeInCascade);

		}
		int id = 0;
		//traverse through each key's values to create inner nodes
		for (Map.Entry<String, String> entry : taxaSCSmap.entrySet()) {
			//start from first node in cascade and process inner nodes
			Node previousNode = keyNodesMap.get(entry.getKey());

			//create inner nodes
			String[] values = entry.getValue() != null ? entry.getValue().split(",") : new String[0];
			for (var value : values) {
				if (value.isBlank()) {
					continue;
				}
				//create node for each value
				Node currentNode = tree.newNode();
				currentNode.setLabel(value + id);
				tree.addTaxon(currentNode, tree.getNumberOfTaxa());

				//add and edge from inner node to its corresponding key node
				String[] nonBinaryNodes = value.split("/");
				for (var nonBinaryNode : nonBinaryNodes) {
					tree.newEdge(currentNode, keyNodesMap.get(nonBinaryNode));
				}
				//add an edge from previous node in map to current node
				tree.newEdge(previousNode, currentNode);

				id++;
				//update previous node
				previousNode = currentNode;
			}
			//create leaf node and connect it to the previous node
			Node leafNode = tree.newNode();
			leafNode.setLabel(entry.getKey());
			tree.newEdge(previousNode, leafNode);
		}

		for (var n : tree.nodes()) {
			System.out.println(n + " " + n.getLabel());
			if (n.getInDegree() == 1 && n.getOutDegree() == 1) {
				tree.delDivertex(n);
			}
		}
		for (var e : tree.edges()) {
			if (e.getTarget().getInDegree() > 1)
				tree.setReticulate(e, true);
		}

		if (tree.isReticulated())
			treesBlock.setReticulated(true);

		LSAUtils.computeLSAChildrenMap(tree, tree.newNodeArray());

		treesBlock.getTrees().add(tree);
		treesBlock.setPartial(false);

		return tree;
	}
}
