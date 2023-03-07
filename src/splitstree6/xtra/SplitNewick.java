/*
 *  SplitNewick.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.xtra;

import jloda.graph.Node;
import jloda.graph.NodeArray;
import jloda.graph.algorithms.PQTree;
import jloda.phylo.PhyloSplitsGraph;
import jloda.phylo.PhyloTree;
import jloda.util.BitSetUtils;
import jloda.util.CollectionUtils;
import jloda.util.IteratorUtils;
import jloda.util.StringUtils;
import splitstree6.algorithms.utils.GreedyCompatible;
import splitstree6.algorithms.utils.TreesUtilities;
import splitstree6.data.parts.ASplit;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Newick IO for splits
 * Daniel Huson, 3.2023
 */
public class SplitNewick {

	/**
	 * write a collection of splits in SplitsNewick format
	 *
	 * @param splits the splits
	 * @param w      writer
	 */
	public void write(Function<Integer, String> taxonLabel, List<ASplit> splits, boolean includeWeights, Writer w) throws IOException {
		write(taxonLabel, splits, includeWeights, null, w);
	}

	/**
	 * write a collection of splits in SplitsNewick format, using the given taxon ordering
	 *
	 * @param splits   splits
	 * @param ordering the taxon ordering - using a good ordering will result in a shorter Newick string
	 * @param w        writer
	 */
	public void write(Function<Integer, String> taxonLabel, List<ASplit> splits, boolean includeWeights, ArrayList<Integer> ordering, Writer w) throws IOException {
		if (splits.size() > 0) {
			var compatible = new HashSet<>(GreedyCompatible.apply(splits, ASplit::getWeight));
			var additional = splits.stream().filter(s -> !compatible.contains(s)).collect(Collectors.toCollection(ArrayList::new));
			if (ordering == null) {
				var pqTree = new PQTree(splits.iterator().next().getAllTaxa());
				compatible.stream().map(s -> s.getPartNotContaining(1)).forEach(pqTree::accept);
				additional.stream().map(s -> s.getPartNotContaining(1)).forEach(pqTree::accept);
				ordering = pqTree.extractAnOrdering();
			}
			var taxonRank = new HashMap<Integer, Integer>();
			for (int rank = 0; rank < ordering.size(); rank++) {
				taxonRank.put(ordering.get(rank), rank);
			}
			var tree = TreesUtilities.computeTreeFromCompatibleSplits(taxonLabel, splits);
			try (NodeArray<Integer> node2rank = tree.newNodeArray()) {
				for (var entry : TreesUtilities.extractClusters(tree).entrySet()) {
					var rank = BitSetUtils.asStream(entry.getValue()).mapToInt(taxonRank::get).max().orElse(0);
					node2rank.put(entry.getKey(), rank);
				}

				for (var v : tree.nodes()) {
					var outEdges = IteratorUtils.asList(v.outEdges());
					outEdges.sort(Comparator.comparingInt(a -> node2rank.get(a.getTarget())));
					var all = CollectionUtils.union(IteratorUtils.asList(v.inEdges()), outEdges);
					v.rearrangeAdjacentEdges(all);
				}
			}
			var treeNewick = tree.toBracketString(includeWeights);
			if (additional.size() == 0) {
				w.write(treeNewick);
			} else { // insert other splits


			}
		}
	}

	/**
	 * writes a split graph in SplitsNewick format
	 *
	 * @param graph split graph
	 * @param w     writer
	 */
	public void write(PhyloSplitsGraph graph, boolean includeWeights, Writer w) throws IOException {
		write(t -> graph.getLabel(graph.getTaxon2Node(t)), extractSplits(graph), includeWeights, null, w);
	}

	/**
	 * writes a split graph in SplitsNewick format, using the given taxon ordering
	 *
	 * @param graph    split graph
	 * @param ordering the taxon ordering
	 * @param w        writer
	 */
	public void write(PhyloSplitsGraph graph, boolean includeWeights, ArrayList<Integer> ordering, Writer w) throws IOException {
		write(t -> graph.getLabel(graph.getTaxon2Node(t)), extractSplits(graph), includeWeights, ordering, w);
	}

	public static ArrayList<ASplit> read(Reader r) throws IOException {
		BufferedReader br;
		if (r instanceof BufferedReader) {
			br = (BufferedReader) r;
		} else {
			br = new BufferedReader(r);
		}
		return read(br.readLine());
	}

	public static ArrayList<ASplit> read(String line) throws IOException {
		var bras = extractBras(line);

		String treeLine;
		if (bras.size() == 0)
			treeLine = line;
		else
			treeLine = line.replaceAll("<[0-9:\\-]*|", "").replaceAll("|[0-9:]*>", "");

		var tree = new PhyloTree();
		tree.parseBracketNotation(treeLine, true, true);
		var splits = new ArrayList<ASplit>();
		TreesUtilities.computeSplits(BitSetUtils.asBitSet(tree.getTaxa()), tree, splits);

		if (bras.size() > 0) {
			var labelIdMap = new HashMap<String, Integer>();
			for (var v : tree.leaves()) {
				labelIdMap.put(tree.getLabel(v), tree.getTaxon(v));
			}
			// additional splits:
			for (var bra : bras) {
				var split = extractSplit(line, bra, labelIdMap);
				splits.add(split);
			}
		}
		return splits;
	}

	public static ArrayList<String> extractBras(String line) {
		var matcher = Bra.newPattern().matcher(line);
		var list = new ArrayList<String>();
		while (matcher.find()) {
			list.add(matcher.group());
		}
		return list;
	}

	private static ASplit extractSplit(String line, String bra, HashMap<String, Integer> labelIdMap) {
		var matcher = Ket.newPattern();
		return null;
	}

	public static ArrayList<ASplit> extractSplits(PhyloSplitsGraph graph) {
		var id2cluster = new HashMap<Integer, BitSet>();
		extractSplitsRec(graph.getTaxon2Node(1), 0, new BitSet(), id2cluster);
		var taxa = BitSetUtils.asBitSet(graph.getTaxa());
		var split2weight = new HashMap<Integer, Double>();
		for (var e : graph.edges()) {
			var splitId = graph.getSplit(e);
			if (!split2weight.containsKey(splitId))
				split2weight.put(splitId, graph.getWeight(e));
		}
		return id2cluster.entrySet().stream().map(e -> new ASplit(BitSetUtils.minus(taxa, e.getValue()), e.getValue(), split2weight.get(e.getKey()))).collect(Collectors.toCollection(ArrayList::new));
	}

	private static void extractSplitsRec(Node v, int splitId, BitSet used, HashMap<Integer, BitSet> id2cluster) {
		var graph = (PhyloSplitsGraph) v.getOwner();
		if (splitId > 0) {
			used.set(splitId);
			if (graph.hasTaxa(v)) {
				id2cluster.computeIfAbsent(splitId, k -> new BitSet()).set(graph.getTaxon(v));
			}
		}
		for (var e : v.adjacentEdges()) {
			var eSplitId = graph.getSplit(e);
			if (eSplitId != splitId) {
				extractSplitsRec(e.getOpposite(v), eSplitId, used, id2cluster);
			}
		}
		if (splitId > 0) {
			used.clear(splitId);
		}
	}

	public static record Bra(int id) {
		public String toString() {
			return "<" + id + "}";
		}

		public static Pattern newPattern() {
			return Pattern.compile("<([0-9]*)\\|");
		}
	}

	public record Ket(int id, double weight, double confidence, double probability) {
		public Ket(int id, double weight, double confidence) {
			this(id, weight, confidence, Double.MIN_VALUE);
		}

		public Ket(int id, double weight) {
			this(id, weight, Double.MIN_VALUE, Double.MIN_VALUE);
		}

		public Ket(int id) {
			this(id, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE);
		}

		public boolean hasWeight() {
			return weight != Double.MIN_VALUE;
		}

		public boolean hasConfidence() {
			return confidence != Double.MIN_VALUE;
		}

		public boolean hasProbability() {
			return probability != Double.MIN_VALUE;
		}

		public String toString() {
			var buf = new StringBuilder("|" + id);
			if (hasWeight()) {
				buf.append(StringUtils.removeTrailingZerosAfterDot(":%.8f", weight));
				if (hasConfidence()) {
					buf.append(StringUtils.removeTrailingZerosAfterDot(":%.8f", confidence));
					if (hasProbability())
						buf.append(StringUtils.removeTrailingZerosAfterDot(":%.8f", probability));
				}
			}
			buf.append(">");
			return buf.toString();
		}

		public static Pattern newPattern() {
			return Pattern.compile("\\|([0-9]+)([-0-9.eE])?([-0-9.eE])?([-0-9.eE])?>");
		}
	}

}
