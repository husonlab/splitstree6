/*
 *  RerootByHybridNumber.java Copyright (C) 2024 Daniel H. Huson
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
package splitstree6.autumn.hybridnumber;


import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.phylo.PhyloTree;
import jloda.util.*;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.utils.RerootingUtils;
import splitstree6.data.TaxaBlock;

import javax.swing.*;
import java.io.IOException;
import java.util.*;

/**
 * determines best rooting of two trees by hybridization number
 * Daniel Huson, 7.2011
 */
public class RerootByHybridNumber {

	/**
	 * reroot both trees so as to minimize the hybrid number
	 *
	 * @return hybrid number
	 */
	// todo: can delete this, too slow
	public static int apply(PhyloTree origTree1, PhyloTree origTree2, ProgressListener progressListener) throws IOException, CanceledException {
		long startTime = System.currentTimeMillis();

		progressListener.setTasks("Rooting trees by hybrid number", "Initialization");

		PhyloTree tree1 = (PhyloTree) origTree1.clone();
		PhyloTree tree2 = (PhyloTree) origTree2.clone();

		while (tree1.getRoot() != null && tree1.getRoot().getOutDegree() == 1) {
			Node w = tree1.getRoot().getFirstOutEdge().getTarget();
			tree1.deleteNode(tree1.getRoot());
			tree1.setRoot(w);
		}

		while (tree2.getRoot() != null && tree2.getRoot().getOutDegree() == 1) {
			Node w = tree2.getRoot().getFirstOutEdge().getTarget();
			tree2.deleteNode(tree2.getRoot());
			tree2.setRoot(w);
		}

		Edge[] number2edge1 = new Edge[tree1.getNumberOfEdges()];
		Map<Edge, Integer> edge2number1 = new HashMap<>();
		int count1 = 0;
		for (Edge e = tree1.getFirstEdge(); e != null; e = tree1.getNextEdge(e)) {
			number2edge1[count1] = e;
			edge2number1.put(e, count1);
			count1++;
		}

		Edge[] number2edge2 = new Edge[tree2.getNumberOfEdges()];
		Map<Edge, Integer> edge2number2 = new HashMap<>();
		int count2 = 0;
		for (Edge e = tree2.getFirstEdge(); e != null; e = tree2.getNextEdge(e)) {
			number2edge2[count2] = e;
			edge2number2.put(e, count2);
			count2++;
		}

		// sorted list of all pairs of rooting in increasing sum of rooting unbalancedness
		SortedSet<Pair<Triplet<Integer, Float, Float>, Triplet<Integer, Float, Float>>> allPairs =
				new TreeSet<>
						((a, b) -> {
							double scoreA = Math.max(Math.abs(a.getFirst().getSecond() - a.getFirst().getThird()), Math.abs(a.getSecond().getSecond() - a.getSecond().getThird()));
							double scoreB = Math.max(Math.abs(b.getFirst().getSecond() - b.getFirst().getThird()), Math.abs(b.getSecond().getSecond() - b.getSecond().getThird()));
							if (scoreA < scoreB)
								return -1;
							else if (scoreA > scoreB)
								return 1;
							else if (a.getFirst().getFirst() < b.getFirst().getFirst())
								return -1;
							else if (a.getFirst().getFirst() > b.getFirst().getFirst())
								return 1;
							else return a.getSecond().getFirst().compareTo(b.getSecond().getFirst());
						});

		// create all pairs of rootings
		{
			var rerootingTriplets1 = RerootingUtils.computeRootingRecords(tree1);
			var rerootingTriplets2 = RerootingUtils.computeRootingRecords(tree2);

			System.err.println("Determining all pairs of possible rootings");
			for (var triplet1 : rerootingTriplets1) {
				var newTriplet1 = new Triplet<>(edge2number1.get(triplet1.edge()), (float) (triplet1.targetToLeafMaxDistance() - triplet1.weight()), (float) (triplet1.sourceToLeafMaxDistance() - triplet1.weight()));
				for (var triplet2 : rerootingTriplets2) {
					if (triplet2.edge().getTarget().getOutDegree() > 0) {
						var newTriplet2 = new Triplet<>(edge2number2.get(triplet2.edge()), (float) (triplet2.targetToLeafMaxDistance() - triplet2.weight()), (float) (triplet1.sourceToLeafMaxDistance() - triplet2.weight()));

						var pair = new Pair<>(newTriplet1, newTriplet2);
						allPairs.add(pair);
					}
				}
			}
			rerootingTriplets1.clear();
			rerootingTriplets2.clear();
		}

		int bestScore = ComputeHybridNumber.LARGE;
		int originalH = bestScore;
		int bestE1 = -1;
		float bestSourceLength1 = 0;
		float bestTargetLength1 = 0;
		float bestSourceLength2 = 0;
		float bestTargetLength2 = 0;

		int bestE2 = -1;

		if (ProgramProperties.isUseGUI()) {
			String result = JOptionPane.showInputDialog(null, "Enter max h", "" + bestScore);
			if (result != null && NumberUtils.isInteger(result))
				bestScore = Integer.parseInt(result);
		}
		System.err.println("Rooting trees by hybrid number");
		progressListener.setTasks("Rooting trees by hybrid number", "Comparing trees");
		progressListener.setMaximum(allPairs.size());
		progressListener.setProgress(0);

		ComputeHybridNumber computeHybridNumber = null;

		try {
			computeHybridNumber = new ComputeHybridNumber(progressListener);
			computeHybridNumber.silent = true;

			int count = 0;
			var allTaxa = new TaxaBlock();

			progressListener.setSubtask(count + " of " + allPairs.size() + (bestScore < 1000 ? ", best h=" + bestScore : ""));

			originalH = computeHybridNumber.run(tree1, tree2, allTaxa);
			System.err.println("Original rooting has hybridization number: " + originalH);
			bestScore = originalH;

			for (Pair<Triplet<Integer, Float, Float>, Triplet<Integer, Float, Float>> pair : allPairs) {
				count++;

				Integer ie1 = pair.getFirst().getFirst();
				Integer ie2 = pair.getSecond().getFirst();

				Edge e1 = number2edge1[ie1];
				if (e1 == null || e1.getOwner() == null)
					System.err.println("ie1 " + ie1 + ": " + e1);
				float weight1 = (float) tree1.getWeight(e1);
				float halfOfTotal1 = (pair.getFirst().getSecond() + pair.getFirst().getThird() + weight1) / 2;
				float sourceLength1 = halfOfTotal1 - pair.getFirst().getSecond();
				float targetLength1 = pair.getFirst().getSecond() + weight1 - halfOfTotal1;
				tree1.setRoot(null);
				tree1.setRoot(e1, sourceLength1, targetLength1, null);
				tree1.redirectEdgesAwayFromRoot();


				Edge e2 = number2edge2[ie2];
				if (e2 == null || e2.getOwner() == null)
					System.err.println("ie2 " + ie2 + ": " + e2);
				float weight2 = (float) tree2.getWeight(e2);
				float halfOfTotal2 = (pair.getSecond().getSecond() + pair.getSecond().getThird() + weight2) / 2;
				float sourceLength2 = halfOfTotal2 - pair.getSecond().getSecond();
				float targetLength2 = pair.getSecond().getSecond() + weight2 - halfOfTotal2;
				tree2.setRoot(null);
				tree2.setRoot(e2, sourceLength2, targetLength2, null);
				tree2.redirectEdgesAwayFromRoot();

				try {
					progressListener.setSubtask(count + " of " + allPairs.size() + (bestScore < 1000 ? ", best h=" + bestScore : ""));

					int h = computeHybridNumber.run(tree1, tree2, allTaxa);

					progressListener.setMaximum(allPairs.size());
					progressListener.setProgress(count);

					// System.err.println("+++"+ie1+" "+ie2+" nested="+triplet.getThird()+" h="+h);

					if (h < bestScore) {
						bestE1 = ie1;
						bestSourceLength1 = sourceLength1;
						bestTargetLength1 = targetLength1;
						bestSourceLength2 = sourceLength2;
						bestTargetLength2 = targetLength2;

						bestE2 = ie2;
						if (bestScore < ComputeHybridNumber.LARGE)
							System.err.println("Improving best score from: " + bestScore + " to " + h);
						bestScore = h;
					}
				} finally {
					number2edge1[ie1] = tree1.delDivertex(tree1.getRoot());
					tree1.setWeight(number2edge1[ie1], weight1);
					number2edge2[ie2] = tree2.delDivertex(tree2.getRoot());
					tree2.setWeight(number2edge2[ie2], weight2);
				}
			}
		} catch (CanceledException ex) {
			if (bestScore == 1000)
				throw ex;
			progressListener.close();
			System.err.println("USER CANCELED, result not necessarily optimal");
		} finally {
			if (computeHybridNumber != null)
				computeHybridNumber.done();
		}
		if (bestScore < originalH) {
			tree1.setRoot(number2edge1[bestE1], bestSourceLength1, bestTargetLength1, null);
			tree1.redirectEdgesAwayFromRoot();
			Set<Node> divertices1 = new HashSet<>();
			for (Node v = tree1.getFirstNode(); v != null; v = tree1.getNextNode(v)) {
				if (v.getInDegree() == 1 && v.getOutDegree() == 1 && tree1.getLabel(v) == null)
					divertices1.add(v);
			}
			for (Node v : divertices1) {
				tree1.delDivertex(v);
			}
			tree2.setRoot(number2edge2[bestE2], bestSourceLength2, bestTargetLength2, null);
			tree2.redirectEdgesAwayFromRoot();
			Set<Node> divertices2 = new HashSet<>();
			for (Node v = tree2.getFirstNode(); v != null; v = tree2.getNextNode(v)) {
				if (v.getInDegree() == 1 && v.getOutDegree() == 1 && tree2.getLabel(v) == null)
					divertices2.add(v);
			}
			for (Node v : divertices2) {
				tree2.delDivertex(v);
			}
			origTree1.copy(tree1);
			origTree2.copy(tree2);
		}
		System.out.println("Best hybridization number: " + bestScore);
		System.err.println("Time: " + ((System.currentTimeMillis() - startTime) / 1000) + " secs");
		System.gc();
		return bestScore;
	}
}
