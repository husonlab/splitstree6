/*
 *  TreeSelector.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.algorithms.trees.trees2trees;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.phylo.PhyloTree;
import jloda.phylo.algorithms.ClusterPoppingAlgorithm;
import jloda.util.SetUtils;
import jloda.util.StringUtils;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.IFilter;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.utils.TreesUtils;
import splitstree6.xtra.kernelize.BiconnectedComponents;
import splitstree6.xtra.kernelize.ClusterIncompatibilityGraph;

import java.util.*;

/**
 * blob trees
 * Daniel Huson, 4/2024
 */
public class BlobTree extends Trees2Trees implements IFilter {
	private final BooleanProperty optionSeparateBlobs = new SimpleBooleanProperty(this, "optionSeparateBlobs", false);


	@Override
	public String getShortDescription() {
		return "Extract the blob tree from a rooted network";
	}

	@Override
	public String getCitation() {
		return "Huson et al 2012;DH Huson, R. Rupp and C. Scornavacca, Phylogenetic Networks, Cambridge, 2012.";
	}

	@Override
	public List<String> listOptions() {
		return List.of(optionSeparateBlobs.getName());
	}

	@Override
	public String getToolTip(String optionName) {
		if (!optionName.startsWith("option")) {
			optionName = "option" + optionName;
		}
		return switch (optionName) {
			case "optionSeparateBlobs" ->
					"For any blob that shares its top node with some other blob, insert an edge above it to keep blobs separate";
			default -> super.getToolTip(optionName);
		};
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock inputData, TreesBlock outputData) {
		outputData.clear();
		outputData.setPartial(inputData.isPartial());
		outputData.setRooted(true);
		outputData.setReticulated(false);

		for (var network : inputData.getTrees()) {
			if (network.isReticulated()) {
				var tree = new PhyloTree();
				computeBlobTree(isOptionSeparateBlobs(), network, tree);
				for (var v : tree.nodes()) {
					if (tree.hasTaxa(v)) {
						tree.setLabel(v, taxaBlock.getLabel(tree.getTaxon(v)));
					}
				}
				outputData.getTrees().add(tree);
			} else
				outputData.getTrees().add(network);
		}
	}

	@Override
	public boolean isApplicable(TaxaBlock taxa, TreesBlock datablock) {
		return datablock.isReticulated() && datablock.isRooted();
	}

	@Override
	public boolean isActive() {
		return true;
	}

	public static void computeBlobTree(boolean separateBlobs, PhyloTree network, PhyloTree tree) {
		if (true) {
			tree.clear();
			tree.copy(network).close();

			if (separateBlobs) {
				var components = BiconnectedComponents.apply(tree);

				var map = new HashMap<Node, Set<Set<Node>>>();
				for (var component : components) {
					if (component.size() > 2) {
						var nodesWithParentsNotInComponent = new ArrayList<Node>();
						for (var v : component) {
							for (var p : v.parents()) {
								if (!component.contains(p)) {
									nodesWithParentsNotInComponent.add(v);
								}
							}
						}
						if (nodesWithParentsNotInComponent.size() > 1) {
							throw new RuntimeException("Biconnected component has too many top nodes " + nodesWithParentsNotInComponent.size());
						}
						Node topNode;
						if (!nodesWithParentsNotInComponent.isEmpty())
							topNode = nodesWithParentsNotInComponent.get(0);
						else topNode = tree.getRoot();
						map.computeIfAbsent(topNode, k -> new HashSet<>()).add(component);
					}
				}
				for (var entry : map.entrySet()) {
					if (entry.getValue().size() > 1) {
						for (var component : entry.getValue()) {
							var top = entry.getKey();
							var blobTop = tree.newNode();
							{
								var e = tree.newEdge(top, blobTop);
								if (tree.hasEdgeWeights())
									tree.setWeight(e, 0);
							}
							for (var v : component) {
								var toDelete = new ArrayList<Edge>();
								for (var e : v.inEdges()) {
									if (e.getSource() == top) {
										var f = tree.newEdge(blobTop, e.getTarget());
										if (tree.hasEdgeWeights()) {
											tree.setWeight(f, tree.getWeight(e));
										}
										if (tree.hasEdgeConfidences()) {
											tree.setConfidence(f, tree.getConfidence(e));
										}
										toDelete.add(e);
									}
								}
								for (var e : toDelete) {
									tree.deleteEdge(e);
								}
							}
						}
					}
				}
			}

			var components = BiconnectedComponents.apply(tree);
			if (false) {
				for (var component : components) {
					System.err.println(StringUtils.toString(component.stream().map(v -> v.getId()).toList(), " "));
				}
			}

			for (var component : components) {
				if (component.size() > 2) {
					var nodesWithParentsInComponent = new ArrayList<Node>();
					var nodesWithParentsNotInComponent = new ArrayList<Node>();
					var nodesWithChildrenNotInComponent = new ArrayList<Node>();

					for (var v : component) {
						for (var p : v.parents()) {
							if (component.contains(p)) {
								nodesWithParentsInComponent.add(v);
							} else {
								nodesWithParentsNotInComponent.add(v);
							}
						}

						for (var c : v.children()) {
							if (!component.contains(c)) {
								nodesWithChildrenNotInComponent.add(v);
							}
						}
					}

					if (nodesWithParentsNotInComponent.size() > 1) {
						throw new RuntimeException("Biconnected component has too many top nodes " + nodesWithParentsNotInComponent.size());
					}

					if (SetUtils.intersect(nodesWithParentsInComponent, nodesWithParentsNotInComponent)) {
						throw new RuntimeException("Node has parents both inside and outside of biconnected component");
					}

					Node topNode;
					if (nodesWithParentsNotInComponent.size() == 1) {
						topNode = nodesWithParentsNotInComponent.get(0);
					} else {
						if (component.contains(tree.getRoot()))
							topNode = tree.getRoot();
						else
							throw new RuntimeException("Biconnected component has no top node");
					}
					for (var p : nodesWithChildrenNotInComponent) {
						for (var e : p.outEdges()) {
							var q = e.getTarget();
							if (!component.contains(q) && !topNode.isChild(q)) {
								if (false)
									System.err.println("new edge: " + topNode.getId() + " " + q.getId());
								var f = tree.newEdge(topNode, q);
								if (tree.hasEdgeWeights()) {
									tree.setWeight(f, tree.getWeight(e));
								}
								if (tree.hasEdgeConfidences()) {
									tree.setConfidence(f, tree.getConfidence(e));
								}
							}
						}
					}
					for (var v : component) {
						if (v != topNode) {
							if (false)
								System.err.println("deleting: " + v.getId());
							tree.deleteNode(v);
						}
					}
				}
			}
			tree.clearReticulateEdges();
			if (false)
				System.err.println(tree.toBracketString(false) + ";");
		} else {
			// setup cluster weight map
			var clusterWeightMap = new HashMap<BitSet, Double>();
			try (var nodeClusterMap = TreesUtils.extractClusters(network)) {
				for (var entry : nodeClusterMap.entrySet()) {
					var v = entry.getKey();
					var cluster = entry.getValue();
					if (v.getInDegree() == 1) {
						clusterWeightMap.put(cluster, network.getWeight(v.getFirstInEdge()));
					} else {
						clusterWeightMap.put(cluster, 0.0);
					}
				}
			}
			// determine clusters to keep:
			var incompatibiltyGraph = ClusterIncompatibilityGraph.apply(List.of(network));
			var clusters = incompatibiltyGraph.nodeStream().filter(v -> v.getDegree() == 0).map(v -> (BitSet) v.getInfo()).toList();

			ClusterPoppingAlgorithm.apply(clusters, tree);
			try (var nodeClusterMap = TreesUtils.extractClusters(tree)) {
				for (var v : nodeClusterMap.keySet()) {
					if (v.getInDegree() == 0) {
						var cluster = nodeClusterMap.get(v);
						tree.setWeight(v.getFirstInEdge(), clusterWeightMap.get(cluster));
					}
				}
			}
		}
	}

	public boolean isOptionSeparateBlobs() {
		return optionSeparateBlobs.get();
	}

	public BooleanProperty optionSeparateBlobsProperty() {
		return optionSeparateBlobs;
	}
}
