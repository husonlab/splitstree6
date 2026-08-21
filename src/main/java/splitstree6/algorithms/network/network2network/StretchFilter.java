/*
 *  StretchFilter.java Copyright (C) 2026 Daniel H. Huson
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

package splitstree6.algorithms.network.network2network;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.phylo.PhyloGraph;
import jloda.util.progress.ProgressListener;
import splitstree6.data.NetworkBlock;
import splitstree6.data.TaxaBlock;

import java.io.IOException;
import java.util.*;

/**
 * Stretch filter: lossy sparsification of a metric-realizing network. Greedily removes edges (heaviest first)
 * as long as no pairwise taxon distance grows by more than {@code optionMaxStretchPercent}% relative to its
 * value in the <em>input</em> network -- i.e.\ it keeps the network a {@code (1 + p/100)}-spanner of the input.
 * 0% leaves the network unchanged.
 * <p>
 * Because removing an edge can only lengthen shortest paths, the spanner test is one-sided. The reference
 * distances are the input network's own taxon-to-taxon shortest paths, so the filter works on any network that
 * realizes a metric (e.g. a RazorNet network) and needs no separate distance matrix. This is the general,
 * composable form of RazorNet's built-in max-stretch post-processing.
 * <p>
 * Daniel Huson, 2026
 */
public class StretchFilter extends Network2Network {
	private final DoubleProperty optionMaxStretchPercent = new SimpleDoubleProperty(this, "optionMaxStretchPercent", 0.0);

	@Override
	public List<String> listOptions() {
		return List.of(optionMaxStretchPercent.getName());
	}

	@Override
	public String getToolTip(String optionName) {
		if (optionMaxStretchPercent.getName().equals(optionName))
			return "remove edges while keeping every pairwise taxon distance within this percent of its value in the input network (0 = keep the network unchanged)";
		return super.getToolTip(optionName);
	}

	/**
	 * Not applicable to a point cloud: its edges carry no length to trade away (see {@link NetworkBlock.Type}).
	 */
	@Override
	public boolean isApplicable(TaxaBlock taxa, NetworkBlock networkBlock) {
		return super.isApplicable(taxa, networkBlock) && networkBlock.getNetworkType() != NetworkBlock.Type.Points;
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, NetworkBlock inputData, NetworkBlock outputData) throws IOException {
		outputData.copy(inputData);

		var stretch = getOptionMaxStretchPercent() / 100.0;
		if (stretch <= 0) {
			setShortDescription("stretch 0% (network unchanged)");
			return;
		}

		try {
			var graph = outputData.getGraph();
			var taxa = graph.nodeStream().filter(graph::hasTaxa).toList();
			var edges0 = graph.getNumberOfEdges();

			var alive = new HashSet<Edge>();
			graph.edges().forEach(alive::add);

			// reference: the taxon-to-taxon distances in the input network (shortest paths over all edges)
			var ref = new HashMap<Node, Map<Node, Double>>();
			for (var u : taxa)
				ref.put(u, NetworkSimplification.singleSource(graph, u, alive));

			var factor = 1.0 + stretch;

			// try to drop edges heaviest-first; keep the drop only while every taxon pair stays within the bound
			var order = new ArrayList<>(alive);
			order.sort(Comparator.comparingDouble(e -> -graph.getWeight(e)));

			progress.setSubtask("Stretch filter");
			progress.setMaximum(order.size());
			progress.setProgress(0);
			for (var e : order) {
				if (alive.contains(e)) {
					var without = new HashSet<>(alive);
					without.remove(e);
					if (withinStretch(graph, taxa, without, ref, factor))
						alive.remove(e);
				}
				progress.incrementProgress();
			}

			for (var e : graph.edgeStream().filter(e -> !alive.contains(e)).toList())
				graph.deleteEdge(e);

			var changed = graph.getNumberOfEdges() < edges0;
			if (changed)
				NetworkSimplification.cleanAndSmooth(graph, outputData, progress);

			var achieved = maxAchievedStretch(graph, taxa, ref);
			setShortDescription(String.format("stretch <= %.1f%%: removed %,d of %,d edges (max inflation used %.1f%%)",
					100 * stretch, edges0 - graph.getNumberOfEdges(), edges0, 100 * (achieved - 1.0)));
		} catch (IOException ex) {
			throw ex;
		} catch (Exception ex) {
			throw new IOException(ex);
		}
	}

	/**
	 * True iff every taxon pair's shortest path over the {@code alive} edge set stays within {@code factor} times
	 * its reference distance. An unreachable pair fails (so removals that would disconnect taxa are rejected).
	 */
	private static boolean withinStretch(PhyloGraph graph, List<Node> taxa, Set<Edge> alive, Map<Node, Map<Node, Double>> ref, double factor) {
		for (var i = 0; i < taxa.size(); i++) {
			var u = taxa.get(i);
			var dist = NetworkSimplification.singleSource(graph, u, alive);
			var refU = ref.get(u);
			for (var j = i + 1; j < taxa.size(); j++) {
				var v = taxa.get(j);
				var got = dist.getOrDefault(v, Double.MAX_VALUE);
				if (got > refU.get(v) * factor + 1e-9)
					return false;
			}
		}
		return true;
	}

	/**
	 * Largest realized ratio {@code d_graph(u,v)/d_ref(u,v)} over taxon pairs on the final graph (1.0 = exact).
	 */
	private static double maxAchievedStretch(PhyloGraph graph, List<Node> taxa, Map<Node, Map<Node, Double>> ref) {
		var all = new HashSet<Edge>();
		graph.edges().forEach(all::add);
		var max = 1.0;
		for (var i = 0; i < taxa.size(); i++) {
			var u = taxa.get(i);
			var dist = NetworkSimplification.singleSource(graph, u, all);
			var refU = ref.get(u);
			for (var j = i + 1; j < taxa.size(); j++) {
				var v = taxa.get(j);
				var r = refU.get(v);
				var got = dist.getOrDefault(v, Double.MAX_VALUE);
				if (r != null && r > 0 && got < Double.MAX_VALUE)
					max = Math.max(max, got / r);
			}
		}
		return max;
	}

	public double getOptionMaxStretchPercent() {
		return optionMaxStretchPercent.get();
	}

	public DoubleProperty optionMaxStretchPercentProperty() {
		return optionMaxStretchPercent;
	}

	public void setOptionMaxStretchPercent(double value) {
		optionMaxStretchPercent.set(value);
	}
}
