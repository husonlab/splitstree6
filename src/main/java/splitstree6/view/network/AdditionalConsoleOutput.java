/*
 * AdditionalConsoleOutput.java Copyright (C) 2025 Daniel H. Huson
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
 *
 */

package splitstree6.view.network;

import javafx.beans.InvalidationListener;
import jloda.fx.util.RunAfterAWhile;
import jloda.graph.Node;
import jloda.graph.algorithms.Dijkstra;
import jloda.util.IteratorUtils;
import splitstree6.data.CharactersBlock;
import splitstree6.data.DistancesBlock;
import splitstree6.data.NetworkBlock;
import splitstree6.data.parts.AmbiguityCodes;

public class AdditionalConsoleOutput {
	public static void setup(NetworkView view) {

		view.networkBlockProperty().addListener((v, o, n) -> {
			if (n != null && false) {
				reportAllDifferentDistances(view);
				reportAllDifferencesCharacters(view);
			}
		});

		var sync = new Object();

		var selectedItems = view.getMainWindow().getTaxonSelectionModel().getSelectedItems();
		selectedItems.addListener((InvalidationListener) e -> {
			RunAfterAWhile.applyInFXThread(sync, () -> {
				if (selectedItems.size() == 2) {
					if (view.getNetworkBlock().getNode().getPreferredParent().getPreferredParent().getDataBlock() instanceof DistancesBlock distancesBlock) {
						System.err.println(view.getNetworkBlock().getNode().getPreferredParent().getAlgorithm().getName() + ":");
						var taxaBlock = view.getMainWindow().getWorkingTaxa();
						var s = taxaBlock.indexOf(IteratorUtils.getFirst(selectedItems));
						var t = taxaBlock.indexOf(IteratorUtils.getLast(selectedItems));
						reportDifferentDistances(view, s, t);
					} else if (view.getNetworkBlock().getNode().getPreferredParent().getPreferredParent().getDataBlock() instanceof CharactersBlock) {
						System.err.println(view.getNetworkBlock().getNode().getPreferredParent().getAlgorithm().getName() + ":");
						var taxaBlock = view.getMainWindow().getWorkingTaxa();
						var s = taxaBlock.indexOf(IteratorUtils.getFirst(selectedItems));
						var t = taxaBlock.indexOf(IteratorUtils.getLast(selectedItems));
						reportAllDifferencesCharacters(view, s, t);
					}
				}
			});
		});
	}

	public static void reportAllDifferencesCharacters(NetworkView view) {
		if (view.getNetworkBlock().getNode().getPreferredParent().getPreferredParent().getDataBlock() instanceof CharactersBlock charactersBlock) {
			var label = view.getNetworkBlock().getNode().getPreferredParent().getAlgorithm().getName();
			System.err.println(label + ":");

			var surplusCharacterDistance = 0;
			var surplusPathDistance = 0;

			var taxaBlock = view.getMainWindow().getWorkingTaxa();
			for (var s = 1; s <= taxaBlock.getNtax(); s++) {
				for (var t = s + 1; t <= taxaBlock.getNtax(); t++) {
					var diff = reportAllDifferencesCharacters(view, s, t);
					if (diff > 0)
						surplusPathDistance += diff;
					else if (diff < 0)
						surplusCharacterDistance += Math.abs(diff);
				}
			}

			System.err.println(label + ": surplusInputDistance: " + surplusCharacterDistance);
			System.err.println(label + ": surplusPathDistance:  " + surplusPathDistance);
		}
	}

	public static int reportAllDifferencesCharacters(NetworkView view, int s, int t) {
		var diff = 0;
		if (view.getNetworkBlock().getNode().getPreferredParent().getPreferredParent().getDataBlock() instanceof CharactersBlock charactersBlock) {
			var taxaBlock = view.getMainWindow().getWorkingTaxa();

			var characterDifferences = 0;
			{
				var topBuf = new StringBuilder();
				var midBuf = new StringBuilder();
				var botBuf = new StringBuilder();
				for (var pos = 1; pos <= charactersBlock.getNchar(); pos++) {
					var cs = charactersBlock.get(s, pos);
					var ct = charactersBlock.get(t, pos);
					if (!AmbiguityCodes.codesOverlap(cs, ct)) {
						characterDifferences++;
						topBuf.append("%5d".formatted(pos));
						midBuf.append("  %c  ".formatted(cs));
						botBuf.append("  %s  ".formatted(ct));
					}
				}
				System.err.printf("Input differences %s - %s: %,d%n", taxaBlock.getLabel(s), taxaBlock.getLabel(t), characterDifferences);
				System.err.println(topBuf);
				System.err.println(midBuf);
				System.err.println(botBuf);
			}

			var network = view.getNetworkBlock().getGraph();

			var v = network.nodeStream().filter(u -> network.getTaxon(u) == s).findAny().orElse(null);
			var w = network.nodeStream().filter(u -> network.getTaxon(u) == t).findAny().orElse(null);
			if (v != null && w != null) {
				var shortestPath = Dijkstra.compute(network, v, w, network::getWeight, true);
				Node prev = null;
				var pathDifferences = 0;
				for (var q : shortestPath) {
					if (prev != null) {
						var sp = view.getNetworkBlock().getNodeData(prev).get(NetworkBlock.NODE_STATES_KEY);
						var sq = view.getNetworkBlock().getNodeData(q).get(NetworkBlock.NODE_STATES_KEY);
						for (var i = 0; i < Math.max(sp.length(), sq.length()); i++) {
							if (i >= sp.length() || i >= sq.length() || sp.charAt(i) != sq.charAt(i)) {
								pathDifferences++;
							}
						}
					}
					prev = q;
				}
				System.err.printf("Path differences %s - %s: %,d%n%n", taxaBlock.getLabel(s), taxaBlock.getLabel(t), pathDifferences);

				diff = (pathDifferences - characterDifferences);
				if (diff > 0) {
					System.err.println("Path differences larger:  " + pathDifferences + " > " + characterDifferences);
				} else if (diff < 0) {
					System.err.println("Path differences smaller: " + pathDifferences + " < " + characterDifferences);
				}
			}
		}
		return diff;
	}

	public static void reportAllDifferentDistances(NetworkView view) {
		if (view.getNetworkBlock().getNode().getPreferredParent().getPreferredParent().getDataBlock() instanceof DistancesBlock) {
			var label = view.getNetworkBlock().getNode().getPreferredParent().getAlgorithm().getName();
			System.err.println(label + ":");

			var surplusDistances = 0.0;
			var surplusPathDistances = 0.0;

			var taxaBlock = view.getMainWindow().getWorkingTaxa();
			for (var s = 1; s <= taxaBlock.getNtax(); s++) {
				for (var t = s + 1; t <= taxaBlock.getNtax(); t++) {
					var diff = reportDifferentDistances(view, s, t);
					if (diff > 0)
						surplusPathDistances += diff;
					else if (diff < 0)
						surplusDistances += Math.abs(diff);
				}
			}
			System.err.println(label + ": surplusInputDistance: " + surplusDistances);
			System.err.println(label + ": surplusPathDistance:  " + surplusPathDistances);
		}

	}

	public static double reportDifferentDistances(NetworkView view, int s, int t) {
		var diff = 0.0;
		if (view.getNetworkBlock().getNode().getPreferredParent().getPreferredParent().getDataBlock() instanceof DistancesBlock distancesBlock) {
			var taxaBlock = view.getMainWindow().getWorkingTaxa();
			var inputDistance = distancesBlock.get(s, t);
			var pathDistance = 0.0;
			var network = view.getNetworkBlock().getGraph();

			var v = network.nodeStream().filter(u -> network.getTaxon(u) == s).findAny().orElse(null);
			var w = network.nodeStream().filter(u -> network.getTaxon(u) == t).findAny().orElse(null);
			if (v != null && w != null) {
				var shortestPath = Dijkstra.compute(network, v, w, network::getWeight, true);
				Node prev = null;
				for (var q : shortestPath) {
					if (prev != null) {
						var e = q.getCommonEdge(prev);
						pathDistance += network.getWeight(e);
					}
					prev = q;
				}
				System.err.printf("Input distance %s - %s: %f%n", taxaBlock.getLabel(s), taxaBlock.getLabel(t), inputDistance);
				System.err.printf("Path distance  %s - %s: %f%n", taxaBlock.getLabel(s), taxaBlock.getLabel(t), pathDistance);

				diff = pathDistance - inputDistance;
				if (diff > 0) {
					System.err.println("Path distance larger:  " + pathDistance + " > " + inputDistance);
				} else if (diff < 0) {
					System.err.println("Path distance smaller: " + pathDistance + " < " + inputDistance);
				}
			}
		}
		return diff;
	}
}
