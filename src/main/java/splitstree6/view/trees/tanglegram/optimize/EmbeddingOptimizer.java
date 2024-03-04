/*
 * EmbeddingOptimizer.java Copyright (C) 2024 Daniel H. Huson
 *
 * (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package splitstree6.view.trees.tanglegram.optimize;

import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.graph.NodeArray;
import jloda.phylo.PhyloTree;
import jloda.util.*;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.distances.distances2splits.neighbornet.NeighborNetCycleSplitsTree4;

import java.io.IOException;
import java.util.*;

/**
 * compute an optimal embedding using the Neighbor-net heuristic
 * Daniel Huson, Celine Scornavacca 7.2010
 */
public class EmbeddingOptimizer {
	public static final boolean DEBUG = false;
	public static final boolean printILP = false;

	/**
	 * update the embedding algorithm for a single tree
	 */
	public static void apply(PhyloTree tree, ProgressListener progressListener) throws CanceledException {
		if (printILP) {
			int tempIndex = 1;
			for (var v : tree.nodes()) {
				if (v.getOutDegree() != 0) {
					tree.setLabel(v, Integer.toString(tempIndex));
					tempIndex++;
				}
			}
		}

		if (tree.getRoot() == null || tree.getNumberReticulateEdges() == 0) {
			tree.getLSAChildrenMap().clear();
			return;
		}
		//System.err.println("Computing optimal embedding using circular-ordering algorithm");
		apply(new PhyloTree[]{tree}, progressListener, false, true);
	}

	/**
	 * compute the embedding algorithm to a whole set of trees
	 */
	public static void apply(PhyloTree[] trees, ProgressListener progressListener, boolean shortestPath, boolean useFastAlignmentHeuristic) throws CanceledException {
		progressListener.setTasks("Computing embedding", "optimizing");
		//progressListener.setCancelable(false);
		progressListener.setMaximum(-1);
		progressListener.setProgress(-1);

		//System.err.println("Computing optimal embedding using circular-ordering algorithm");
		final var taxon2Id = new TreeMap<String, Integer>();
		final var id2Taxon = new HashMap<Integer, String>();

		var dummyLeaves = new Vector<Node>();
		var count = 1;
		var idRho = 0;

		// add formal root nodes
		for (PhyloTree tree : trees) {
			var dummyLeaf = new Node(tree);
			var newRoot = new Node(tree);
			dummyLeaves.add(dummyLeaf);
			tree.newEdge(newRoot, tree.getRoot());
			tree.newEdge(newRoot, dummyLeaf);
			tree.setRoot(newRoot);
			tree.setLabel(dummyLeaf, "rho****");

			var tax = OptimizeUtils.getTaxaForTanglegram(tree);
			for (Iterator<String> taxIt = tax.iterator(); taxIt.hasNext(); ) {
				var taxName = taxIt.next();
				if (!taxon2Id.containsKey(taxName)) {
					taxon2Id.put(taxName, count);
					id2Taxon.put(count, taxName);
					if (taxName.equals("rho****"))
						idRho = count;
					count++;
				}
			}
		}
		if (DEBUG) {
			System.err.println("idRho: " + idRho);
			System.err.println("taxon2Id:");
			for (var taxon : taxon2Id.keySet()) {
				System.err.println(taxon + " " + taxon2Id.get(taxon));
			}
		}

		final int[] circularOrdering;
		//if(!useFastAlignmentHeuristic)
		//    shortestPath =true;
		if (shortestPath)
			circularOrdering = computerCircularOrderingShortestPathMatrix(trees, taxon2Id, id2Taxon);
		else
			circularOrdering = computerCircularOrderingHardwiredMatrix(trees, taxon2Id, id2Taxon);

		if (DEBUG)
			System.err.println("circularOrdering: " + StringUtils.toString(circularOrdering, " "));

		if (!useFastAlignmentHeuristic && trees.length == 2) {
			final var bestOrdering = getLinearOrderingId(circularOrdering, idRho);

			if (progressListener != null)
				progressListener.setCancelable(true);

			var circularOrderingPair = new int[2][];
			circularOrderingPair[0] = circularOrdering;
			circularOrderingPair[1] = new int[circularOrdering.length];
			int lengthCircOrdering = circularOrdering.length;

			for (var i = 1; i < lengthCircOrdering; i++) {
				circularOrderingPair[1][i] = circularOrderingPair[0][lengthCircOrdering - i];
			}

			if (DEBUG) {
				//System.err.println("Best score: " + bestScore);
				System.err.print("Best bestOrderingFinal: ");
				for (var ii = 1; ii < bestOrdering.length; ii++) {
					System.err.print(" " + id2Taxon.get(bestOrdering[ii]));
				}
				System.err.println();
			}

			var forests = (Vector<PhyloTree>[]) new Vector[2];
			forests[0] = new Vector<>();
			forests[1] = new Vector<>();
			var tempOrder = (Vector<List<String>>[]) new Vector[2];
			tempOrder[0] = new Vector<>();
			tempOrder[1] = new Vector<>();

			var newOrder = (LinkedList<String>[]) new LinkedList[2];
			newOrder[0] = new LinkedList<>();
			newOrder[1] = new LinkedList<>();

			var currOrderingListNew = new LinkedList<String>();
			for (var i = 1; i < bestOrdering.length; i++) {
				currOrderingListNew.add(id2Taxon.get(bestOrdering[i]));
			}

			if (!currOrderingListNew.contains("rho****"))
				currOrderingListNew.add(0, "rho****");

			var best = Integer.MAX_VALUE;
			var swapTourNew = 0;
			while (swapTourNew < 5) {
				//System.err.println("swapTourNew " +swapTourNew);
				if (swapTourNew != 0) {
					forests[0].clear();
					forests[1].clear();
					currOrderingListNew = newOrder[swapTourNew % 2];
					tempOrder[0].clear();
					tempOrder[1].clear();
				}

				//System.err.println("currOrderingListNew:" + currOrderingListNew.toString());
				swapTourNew++;

				for (var s = 0; s < 2; s++) {
					//System.err.println("tree " +s);

					for (var node : trees[s].nodes()) {
						if (node.getInDegree() > 1) {
							var tempP = copySubtreeWithoutReticulations(trees[s], node);
							LSATree.computeNodeLSAChildrenMap(tempP);
							forests[s].add(tempP);
						}
					}
					var tempP = copySubtreeWithoutReticulations(trees[s], trees[s].getRoot());
					LSATree.computeNodeLSAChildrenMap(tempP);
					forests[s].add(tempP);

					for (var a = 0; a < forests[s].size(); a++) {
						OptimizeUtils.lsaOptimization(forests[s].get(a), currOrderingListNew, 0, null, null);
						final var tempOrd = new ArrayList<String>();
						OptimizeUtils.getLsaOrderRec(forests[s].get(a), forests[s].get(a).getRoot(), tempOrd);

						//todo : some trees have "?" as leaves (ex paper) solve this problem

						var sizeBefore = tempOrd.size();
						for (var ind = 0; ind < sizeBefore; ind++) {
							if (tempOrd.get(ind) == null) {
								tempOrd.remove(tempOrd.get(ind));
								sizeBefore--;
								ind--;
							}
						}

						tempOrder[s].add(tempOrd);
					}

					var bestOrdForNow = new LinkedList<>(tempOrder[s].get(0));

					//System.err.println("tempOrder[s].get(0) " + tempOrder[s].get(0).toString());

					for (var a = 1; a < tempOrder[s].size(); a++) {
						var copy = new LinkedList<String>();
						var bestOrdTemp = new LinkedList<String>();
						var toInsert = tempOrder[s].get(a);

						//not needed, it is seems to work the same without adding the rest of the order
						//List<String> rest = new LinkedList<String>();

						//for (int ss=a+1;ss<tempOrder[s].size();ss++)
						//    rest.addAll(tempOrder[s].get(ss));

						//System.err.println("rest " + rest.toString());

						var lastOneInsOfThisTree = 0; //to avoid to mess up the ordering in the tree

						//System.err.println("toInsert " + toInsert.toString());

						for (var sss = 0; sss < toInsert.size(); sss++) {

							var min = Integer.MAX_VALUE;

							var stringToInsert = toInsert.get(sss);

							if (stringToInsert.equalsIgnoreCase("rho****")) {
								bestOrdForNow.add(0, "rho****");
								lastOneInsOfThisTree = 1;
							} else {

								for (var p = lastOneInsOfThisTree; p < bestOrdForNow.size() + 1; p++) {  //to avoid to mes up the ordering in the tree
									copy.addAll(bestOrdForNow);
									copy.add(p, stringToInsert);
									int taxaSetsThatAgree = 0;

									//check if is not overlapping!

									var alreadyInsertedSets = new LinkedList<List<String>>();
									var alreadyInsertedForThisSet = new LinkedList<String>();

									for (var aa = 0; aa < a; aa++) {
										alreadyInsertedSets.add(tempOrder[s].get(aa)); //taxa of the trees of F(N) already inserted
									}

									for (var index = 0; index < sss + 1; index++) {
										alreadyInsertedForThisSet.add(toInsert.get(index));   //taxa of this tree  already inserted
									}

									if (alreadyInsertedSets.size() != 0 && sss != 0) {
										for (var in = 0; in < alreadyInsertedSets.size(); in++) {
											var inf = 0;
											var sup = 0;

											var indexPastY = -1;
											var trySupLoop = true;

											for (var ind = 0; ind < alreadyInsertedSets.get(in).size(); ind++) {
												var x = alreadyInsertedSets.get(in).get(ind); //it ensure a<b<c<d as in the paper
												if (copy.indexOf(x) > indexPastY) {
													for (var inde = 0; inde < alreadyInsertedForThisSet.size(); inde++) {  //only against the newly inserted
														var y = alreadyInsertedForThisSet.get(inde);
														if (copy.indexOf(x) < copy.indexOf(y)) {
															inf++;
															indexPastY = copy.indexOf(y);
															inde = alreadyInsertedForThisSet.size(); //it ensure a<b<c<d as in the paper
														}
														if (inf == 2) {
															ind = alreadyInsertedSets.get(in).size(); //uneusefull to continue
															trySupLoop = false;
														}
													}
												}
											}

											var indexPastX = -1;

											if (trySupLoop) {
												for (var inde = 0; inde < alreadyInsertedForThisSet.size(); inde++) {  //only against the newly inserted

													var y = alreadyInsertedForThisSet.get(inde);
													if (copy.indexOf(y) > indexPastX) {
														for (var ind = 0; ind < alreadyInsertedSets.get(in).size(); ind++) {
															var x = alreadyInsertedSets.get(in).get(ind); //it ensure a<b<c<d as in the paper
															if (copy.indexOf(y) < copy.indexOf(x)) {
																sup++;
																indexPastX = copy.indexOf(x);
																ind = alreadyInsertedSets.get(in).size(); //it ensure a<b<c<d as in the paper
															}
															if (sup == 2)
																inde = alreadyInsertedForThisSet.size(); //unusefull to continue
														}
													}
												}
											}
											if (inf > 1 || sup > 1) {
												in = alreadyInsertedSets.size(); //it is enough one taxa set that does not agree
											} else {
												taxaSetsThatAgree++;
											}
										}
									} else if (sss == 0) {
										taxaSetsThatAgree = alreadyInsertedSets.size(); //the first is always good
									}
									if (taxaSetsThatAgree == alreadyInsertedSets.size()) {
										var value = OptimizeUtils.computeCrossingNum(copy, currOrderingListNew);
										if (value <= min) {
											min = value;
											bestOrdTemp.clear();
											for (var in = 0; in < bestOrdForNow.size() + 1; in++) {  //I do not want to copy rest
												bestOrdTemp.add(copy.get(in));

											}
											lastOneInsOfThisTree = p + 1;  //to avoid to mes up the ordering in the tree
										}
									}
									copy.clear();
								}

								bestOrdForNow.clear();
								bestOrdForNow.addAll(bestOrdTemp);
							}
						}
					}
					newOrder[s] = bestOrdForNow;
					if (DEBUG) {
						{
							var set = new HashSet<>(newOrder[s]);
							if (set.size() != newOrder[s].size()) {
								var difference = new ArrayList<>(newOrder[s]);
								for (var str : set)
									difference.remove(str);
								System.err.println("Duplicates in newOrder[" + s + "]: " + difference);
							}
						}
						{
							var set = new HashSet<>(currOrderingListNew);
							if (set.size() != currOrderingListNew.size()) {
								var difference = new ArrayList<>(currOrderingListNew);
								for (var str : set)
									difference.remove(str);
								System.err.println("Duplicates in currOrderingListNew: " + difference);
							}
						}

						if (newOrder[s].size() != currOrderingListNew.size()) {
							System.err.println("\n\nERROR newOrder Partial\n\n");
							System.err.println(newOrder[s].size() + " newOrder:            " + newOrder[s].toString());
							System.err.println(currOrderingListNew.size() + " currOrderingListNew: " + currOrderingListNew);
							System.err.println("In newOrder[s], not currOrderingListNew: " + CollectionUtils.difference(newOrder[s], currOrderingListNew));
							System.err.println("In currOrderingListNew, not newOrder[s]: " + CollectionUtils.difference(currOrderingListNew, newOrder[s]));
						}
					}
				}
				var score = OptimizeUtils.computeCrossingNum(newOrder[0], newOrder[1]);
				if (score == best)
					break;
			}

			LSATree.computeNodeLSAChildrenMap(trees[0]);
			LSATree.computeNodeLSAChildrenMap(trees[1]);

			var finalScore = OptimizeUtils.computeCrossingNum(newOrder[0], newOrder[1]);   // the two orderings for Daniel

			// get rid of dummy leaves
			for (var v : dummyLeaves) {
				var tree = (PhyloTree) v.getOwner();
				for (var w : tree.nodes()) {
					var children = tree.getLSAChildrenMap().get(w);
					if (children != null)
						children.remove(v);
				}
				var root = v.getFirstAdjacentEdge().getOpposite(v);
				tree.deleteNode(v);
				v = root;
				if (v.getDegree() > 0)
					root = v.getFirstAdjacentEdge().getOpposite(v);
				tree.deleteNode(v);
				tree.setRoot(root);
			}
			newOrder[0].remove(0);
			newOrder[1].remove(0);


			if (DEBUG) {
				System.err.println("first: " + StringUtils.toString(newOrder[0], " "));
				System.err.println("second: " + StringUtils.toString(newOrder[1], " "));
			}

			if (DEBUG)
				System.err.println("Smallest crossing number found: " + finalScore);
			if (DEBUG) {
				for (var i = 0; i < trees.length; i++) {
					System.err.println("Order of the taxa in tree " + (i + 1) + ":");
					System.err.println(newOrder[i]);
				}
			}
			// reorder adjacencies to reflect the ordering:
			try {
				for (int i = 0; i < trees.length; i++) {
					var node2pos = EmbedderForOrderPrescribedNetwork.setupOrderingFromNames(trees[i], newOrder[i]);
					EmbedderForOrderPrescribedNetwork.apply(trees[i], node2pos);
				}
			} catch (IOException ex) {
				Basic.caught(ex);
			}

		} else {  // use fast alignment heuristic if number of trees !=2
			useFastAlignmentHeuristic(trees, circularOrdering, idRho, taxon2Id, dummyLeaves);
		}
	}

	/**
	 * fast heuristic that tries to rotate trees so that they match the given ordering
	 */
	private static void useFastAlignmentHeuristic(PhyloTree[] trees, int[] circularOrdering, int idRho, Map<String, Integer> taxon2Id, Vector<Node> dummyLeaves) {
		var bestOrdering = getLinearOrderingId(circularOrdering, idRho);
		var s = 0;

		for (var tree : trees) {
			var rho = dummyLeaves.get(s);
			var oldRoot = tree.getRoot();
			var newRoot = tree.getOpposite(oldRoot, tree.getRoot().getFirstOutEdge());

			var e = tree.getRoot().getFirstOutEdge();
			var e1 = rho.getFirstInEdge();
			tree.deleteEdge(e);
			tree.deleteEdge(e1);
			tree.setRoot(newRoot);

			tree.deleteNode(rho);
			tree.deleteNode(oldRoot);
			s++;
		}

		for (var tree : trees) {
			final var taxaBelow = new NodeArray<BitSet>(tree);
			for (Node v : tree.nodes()) {
				if (v.getOutDegree() == 0) {
					var id = taxon2Id.get(tree.getLabel(v));
					for (var z = 1; z <= bestOrdering.length; z++)
						if (bestOrdering[z] == id) {
							var below = new BitSet();
							below.set(z);
							taxaBelow.put(v, below);
							break;
						}
				}
			}
			computeTaxaBelowRec(tree.getRoot(), taxaBelow);
			rotateTreeByTaxaBelow(tree, taxaBelow);
			(new LayoutUnoptimized()).apply(tree);
		}
	}


	/**
	 * gets the linear ordering starting at id idRho and excluding idRho
	 *
	 * @return linear ordering
	 */
	private static int[] getLinearOrderingId(int[] circularOrdering, int idRho) {
		var start = 0;
		for (var src = 1; src < circularOrdering.length; src++) {
			if (circularOrdering[src] == idRho)
				start = src;
		}

		var ordering = new int[circularOrdering.length - 1];
		var tar = 1;
		for (var src = start + 1; src < circularOrdering.length; src++) {
			ordering[tar++] = circularOrdering[src];
		}
		for (var src = 1; src < start; src++) {
			ordering[tar++] = circularOrdering[src];
		}

		return ordering;
	}

	/**
	 * recursively extends the taxa below map from leaves to all nodes
	 */
	public static void computeTaxaBelowRec(Node v, NodeArray<BitSet> taxaBelow) {
		if (v.getOutDegree() > 0 && taxaBelow.get(v) == null) {
			var below = new BitSet();

			for (Edge e : v.outEdges()) {
				var w = e.getTarget();
				computeTaxaBelowRec(w, taxaBelow);
				below.or(taxaBelow.get(w));
			}
			taxaBelow.put(v, below);
		}
	}

	/**
	 * rotates all out edges so as to sort by the taxa-below sets
	 */
	public static void rotateTreeByTaxaBelow(PhyloTree tree, final NodeArray<BitSet> taxaBelow) {
		for (var v0 : tree.nodes()) {
			if (v0.getOutDegree() > 0) {
				final var sourceNode = v0;

                /*
                System.err.println("Source node: " +sourceNode+" "+tree.getLabel(v0));

                System.err.println("original order:");
                for (Edge e = v0.getFirstOutEdge(); e != null; e = v0.getNextOutEdge(e)) {
                    Node w=e.getOpposite(v0) ;
                    System.err.println(w +" "+tree.getLabel(w)+ " value: " + (Basic.toString(taxaBelow.get(w))));
                }
                */

				var adjacentEdges = IteratorUtils.asList(v0.adjacentEdges());
				adjacentEdges.sort((e, f) -> {
					if (e.getSource() == sourceNode && f.getSource() == sourceNode) // two out edges
					{
						var v = e.getTarget();
						var w = f.getTarget();

						// lexicographically smaller is smaller
						var taxaBelowV = taxaBelow.get(v);
						var taxaBelowW = taxaBelow.get(w);

						var i = taxaBelowV.nextSetBit(0);
						var j = taxaBelowW.nextSetBit(0);
						while (i != -1 && j != -1) {
							if (i < j)
								return -1;
							else if (i > j)
								return 1;
							i = taxaBelowV.nextSetBit(i + 1);
							j = taxaBelowW.nextSetBit(j + 1);
						}
						if (i == -1 && j != -1)
							return -1;
						else if (i != -1 && j == -1)
							return 1;

					} else if (e.getTarget() == sourceNode && f.getSource() == sourceNode)
						return -1;
					else if (e.getSource() == sourceNode && f.getTarget() == sourceNode)
						return 1;
					// no else here!
					return Integer.compare(e.getId(), f.getId());
				});
				v0.rearrangeAdjacentEdges(adjacentEdges);
			}
		}
	}

	/**
	 * compute a circular ordering using neighbor net
	 */
	//working
	public static int[] computerCircularOrderingHardwiredMatrix(PhyloTree[] trees, Map<String, Integer> taxon2ID, Map<Integer, String> id2Taxon) {
		if (taxon2ID.size() > 2) {
			double[][] distMat = null;

			var taxaTrees = new Taxa[2];
			if (trees.length == 2) {
				taxaTrees[0] = OptimizeUtils.getTaxaForTanglegram(trees[0]);
				taxaTrees[1] = OptimizeUtils.getTaxaForTanglegram(trees[1]);
			}

			var taxaNotInTrees = new Taxa[2];
			var newTrees = new PhyloTree[2];

			if (trees.length == 2 && (taxaTrees[0].size() != taxon2ID.size() || taxaTrees[1].size() != taxon2ID.size())) {

				for (var s = 0; s < 2; s++) {

					newTrees[s] = (PhyloTree) trees[s].clone();
					taxaNotInTrees[Math.abs(s - 1)] = new Taxa();

					for (var it = taxaTrees[s].iterator(); it.hasNext(); ) {
						var taxon = it.next();
						var contains = (taxaTrees[java.lang.Math.abs(s - 1)]).contains(taxon);
						if (!contains) {
							taxaNotInTrees[Math.abs(s - 1)].add(taxon);
						}
					}
					//if(DEBUG_Partial)
					//System.err.println("taxaNotInTrees[" + java.lang.Math.abs(s-1) + "] " + taxaNotInTrees[java.lang.Math.abs(s-1)].toString());
				}

				// we restrict the trees to the common taxa

				for (var s = 0; s < 2; s++) {
					for (Iterator<String> it = taxaNotInTrees[java.lang.Math.abs(s - 1)].iterator(); it.hasNext(); ) {
						String taxon = it.next();
						Node toDelete = null;
						for (Node node : newTrees[s].nodes()) {
							if (node.getOutDegree() == 0 && newTrees[s].getLabel(node) != null && Objects.equals(newTrees[s].getLabel(node), taxon)) {
								toDelete = node;
								break;
							}
						}
						if (toDelete != null)
							newTrees[s].deleteNode(toDelete);
						//System.err.println("taxon " + taxon + " " + newTrees[s].toBracketString());
					}
					var weird = true;  //todo : problem with delete!
					while (weird) {
						weird = false;
						for (Node node : newTrees[s].nodes()) {
							if (node.getOutDegree() == 0 && newTrees[s].getLabel(node) == null) {
								newTrees[s].deleteNode(node);
								weird = true;
							}
						}
					}
				}

				// we extract the clusters from the modified trees

				var clustersAll = OptimizeUtils.collectAllHardwiredClusters(newTrees[0]);
				clustersAll.addAll(OptimizeUtils.collectAllHardwiredClusters(newTrees[1]));

				var sys = OptimizeUtils.getSplitSystem(clustersAll, taxon2ID);
				distMat = OptimizeUtils.setMatForDiffSys(distMat, taxon2ID.size(), sys, false);
			} else {

// create a new distance matrix and update it for every split system induced by the given networks
				for (var tree : trees) {
					//System.err.println("tree ");

					var clusters = OptimizeUtils.collectAllHardwiredClusters(tree);
					var sys = OptimizeUtils.getSplitSystem(clusters, taxon2ID);

					distMat = OptimizeUtils.setMatForDiffSys(distMat, taxon2ID.size(), sys, false);
					if (false) {
						for (var taxon1 : taxon2ID.keySet()) {
							System.err.println(taxon1 + ":");
							for (var taxon2 : taxon2ID.keySet())
								System.err.printf(" %.0f", distMat[taxon2ID.get(taxon1)][taxon2ID.get(taxon2)]);
							System.err.println();
						}
					}
				}
			}

			// get the order using NN

			var ntax = taxon2ID.size();
			var ordering = NeighborNetCycleSplitsTree4.compute(ntax, distMat);
			if (trees.length == 2) {
				// we restrict the ordering to the common taxa. If solution zero exist, we will find it
				if (taxaNotInTrees[0] != null && taxaNotInTrees[1] != null) {
					var takeAway = taxaNotInTrees[0].size() + taxaNotInTrees[1].size();
					var newOrdering = new int[ordering.length - takeAway];

					int index = 0;
					for (int i : ordering) {
						if (i > 0 && !taxaNotInTrees[0].contains(id2Taxon.get(i)) && !taxaNotInTrees[1].contains(id2Taxon.get(i))) {
							newOrdering[++index] = i;
						}
					}
					return newOrdering;
				} else
					return ordering;
			} else
				return ordering;
		} else {
			var ordering = new int[taxon2ID.size() + 1];
			for (var i = 1; i <= taxon2ID.size(); i++)
				ordering[i] = i;
			return ordering;
		}
	}

	/**
	 * compute a circular ordering using neighbor net
	 */
	public static int[] computerCircularOrderingShortestPathMatrix(PhyloTree[] trees, Map<String, Integer> taxon2ID, Map<Integer, String> id2Taxon) {

		if (taxon2ID.size() > 2) {
			int count = 1;

// take taxa of all networks, assign a new ID and store both in a Map (allows use of networks
// with different taxa sets)

			for (var tree : trees) {
				var tax = OptimizeUtils.getTaxaForTanglegram(tree);
				for (Iterator<String> taxIt = tax.iterator(); taxIt.hasNext(); ) {
					String taxName = taxIt.next();
					if (!taxon2ID.containsKey(taxName)) {
						taxon2ID.put(taxName, count);
						id2Taxon.put(count, taxName);
						count++;
						if (DEBUG)
							System.err.print(taxName + " = " + taxon2ID.get(taxName) + " , ");
					}
				}
			}


			var max_num_nodes = 3 * taxon2ID.size() - 5; // why???????????????? why not????

			var distMat = new double[max_num_nodes][max_num_nodes]; //initialize to zeros

			for (var tree : trees) {
				OptimizeUtils.computeNumberNodesInTheShortestPath(tree, taxon2ID, distMat);
			}

			var numBTrees = trees.length;

			for (var ii = 0; ii < distMat.length; ii++) {
				for (int jj = 0; jj < distMat.length; jj++)
					distMat[ii][jj] /= numBTrees;
			}

			if (DEBUG) {
				for (var ii = 1; ii <= taxon2ID.size(); ii++) {
					System.err.print(id2Taxon.get(ii) + " ");
				}
				System.err.println();
				for (var ii = 1; ii <= taxon2ID.size(); ii++) {
					System.err.print(id2Taxon.get(ii) + "\t");
					for (var jj = 1; jj < taxon2ID.size(); jj++)
						System.err.print(" " + distMat[ii][jj]);
					System.err.println();
				}
			}

			// get the order using NN

			var ntax = taxon2ID.size();
			return NeighborNetCycleSplitsTree4.compute(ntax, distMat);
		} else {
			var ordering = new int[taxon2ID.size()];
			var i = 0;
			for (var key : taxon2ID.keySet()) {
				ordering[i++] = taxon2ID.get(key);
			}
			return ordering;
		}
	}

	private static PhyloTree copySubtreeWithoutReticulations(PhyloTree src, Node v) {
		var tar = new PhyloTree();
		try (var nodes = src.newNodeSet()) {
			var queue = new LinkedList<Node>();
			queue.add(v);
			nodes.add(v);
			while (queue.size() > 0) {
				var w = queue.pop();
				if (w.getInDegree() <= 1 || w == v) {
					nodes.add(w);
					queue.addAll(IteratorUtils.asList(w.children()));
				}
			}
			var src2tar = src.extract(nodes, null, tar);
			tar.setRoot(src2tar.get(v));
			for (var s : src2tar.keys()) {
				if (src.getLabel(s) != null)
					tar.setLabel(src2tar.get(s), src.getLabel(s));
			}
		}
		return tar;
	}
}
