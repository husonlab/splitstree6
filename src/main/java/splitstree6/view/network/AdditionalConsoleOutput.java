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
import jloda.util.IteratorUtils;
import jloda.util.StringUtils;
import splitstree6.data.NetworkBlock;

public class AdditionalConsoleOutput {
	// Temporary guard (off by default): the per-taxon-pair "Input distance / Path distance" dumps below are
	// verbose debugging output. Set true to re-enable. The one-line "Total length / distortion" summary and the
	// network's info label are unaffected.
	public static boolean verbose = false;

	public static void setup(NetworkView view) {

		view.networkBlockProperty().addListener((v, o, networkBlock) -> {
			if (networkBlock != null && verbose) {
				reportAllDifferentDistances(view);
				reportAllDifferencesCharacters(view);
			}
			if (networkBlock != null && networkBlock.getInfoString().isBlank()) {
				var info = infoFor(networkBlock);
				if (!info.isBlank()) {
					System.err.println(info);
					networkBlock.setInfoString(info);
				}
			}
		});

		var sync = new Object();

		var selectedItems = view.getMainWindow().getTaxonSelectionModel().getSelectedItems();
		selectedItems.addListener((InvalidationListener) e -> {
			RunAfterAWhile.applyInFXThread(sync, () -> {
				if (verbose && selectedItems.size() == 2) {
					var networkBlock = view.getNetworkBlock();
					if (NetworkSequencesAnalyzer.isApplicable(networkBlock)) {
						System.err.println(networkBlock.getNode().getPreferredParent().getAlgorithm().getName() + ":");
						var taxaBlock = view.getMainWindow().getWorkingTaxa();
						var s = taxaBlock.indexOf(IteratorUtils.getFirst(selectedItems));
						var t = taxaBlock.indexOf(IteratorUtils.getLast(selectedItems));
						var charactersBlock = NetworkSequencesAnalyzer.findCharactersBlock(networkBlock);
						if (charactersBlock != null) {
							var analyzer = new NetworkSequencesAnalyzer(networkBlock);
							analyzer.reportAllDifferences(s, t, taxaBlock, charactersBlock, networkBlock);
						}
					} else if (NetworkDistancesAnalyzer.isApplicable(networkBlock)) {
						System.err.println(networkBlock.getNode().getPreferredParent().getAlgorithm().getName() + ":");
						var taxaBlock = view.getMainWindow().getWorkingTaxa();
						var s = taxaBlock.indexOf(IteratorUtils.getFirst(selectedItems));
						var t = taxaBlock.indexOf(IteratorUtils.getLast(selectedItems));
						var analyzer = new NetworkDistancesAnalyzer();
						analyzer.reportDifferentDistances(s, t, taxaBlock, networkBlock);
					}
				}
			});
		});
	}

	/**
	 * The info line for a network -- what the viewer shows under the drawing -- chosen by the kind of network it
	 * declares itself to be. Public so that a headless tool can report exactly what the application would.
	 * <p>
	 * The kind is the authority (see {@link NetworkBlock.Type}), and only a network that does not know what it is
	 * falls back to inspecting the workflow, as everything here used to do. That fallback is why a network read
	 * from a file used to report NOTHING: both analyzers need a characters or distances block among its
	 * ancestors, and a loaded network has neither. Its total length needs no ancestor at all, so it is reported
	 * either way, and the measure that does need one is appended only when it can be had.
	 */
	public static String infoFor(NetworkBlock networkBlock) {
		return switch (networkBlock.getNetworkType()) {
			case HaplotypeNetwork -> haplotypeInfo(networkBlock);
			case DistanceNetwork -> distanceInfo(networkBlock);
			case Points -> ""; // a point cloud has no lengths to add up
			case Other -> NetworkSequencesAnalyzer.isApplicable(networkBlock) ? haplotypeInfo(networkBlock)
					: NetworkDistancesAnalyzer.isApplicable(networkBlock) ? distanceInfo(networkBlock) : "";
		};
	}

	/** length in mutations, plus the excess over the sequence differences when the alignment can be reached */
	private static String haplotypeInfo(NetworkBlock networkBlock) {
		if (!NetworkSequencesAnalyzer.isApplicable(networkBlock))
			return "Total length: %s".formatted(StringUtils.trim(totalLength(networkBlock)));
		var analyzer = new NetworkSequencesAnalyzer(networkBlock);
		var excess = analyzer.realizedPairwiseDistances(networkBlock) - analyzer.inputPairwiseDistances(networkBlock);
		return "Total length: %d, excess: %d".formatted(analyzer.totalEdgeDistances(networkBlock), excess);
	}

	/** length, plus the distortion against the input distances when those can be reached */
	private static String distanceInfo(NetworkBlock networkBlock) {
		if (!NetworkDistancesAnalyzer.isApplicable(networkBlock))
			return "Total length: %s".formatted(StringUtils.trim(totalLength(networkBlock)));
		var analyzer = new NetworkDistancesAnalyzer();
		return "Total length: %s, distortion: %s".formatted(StringUtils.trim(analyzer.totalEdgeDistances(networkBlock)),
				StringUtils.trim(analyzer.distortion(networkBlock)));
	}

	/** the one thing every network can always say about itself */
	private static double totalLength(NetworkBlock networkBlock) {
		var graph = networkBlock.getGraph();
		return graph.edgeStream().mapToDouble(graph::getWeight).sum();
	}

	public static void reportAllDifferencesCharacters(NetworkView view) {
		var networkBlock = view.getNetworkBlock();
		if (NetworkSequencesAnalyzer.isApplicable(networkBlock)) {
			var charactersBlock = NetworkSequencesAnalyzer.findCharactersBlock(networkBlock);
			var label = view.getNetworkBlock().getNode().getPreferredParent().getAlgorithm().getName();
			System.err.println("\n\n" + label + ":");

			var surplusCharacterDistance = 0;
			var surplusPathDistance = 0;

			var analyzer = new NetworkSequencesAnalyzer(networkBlock);
			var taxaBlock = view.getMainWindow().getWorkingTaxa();
			var buf = new StringBuilder();
			for (var s = 1; s <= taxaBlock.getNtax(); s++) {
				for (var t = s + 1; t <= taxaBlock.getNtax(); t++) {
					var diff = analyzer.reportAllDifferences(s, t, taxaBlock, charactersBlock, networkBlock);
					if (diff > 0)
						surplusPathDistance += diff;
					else if (diff < 0)
						surplusCharacterDistance += Math.abs(diff);
					if (!buf.isEmpty())
						buf.append("+");
					buf.append(diff);
				}
			}

			System.err.println(label + ": surplusInputDistance: " + surplusCharacterDistance);
			System.err.println(label + ": surplusPathDistance:  " + surplusPathDistance);

			System.err.println(surplusPathDistance + "=" + buf);
		}
	}



	public static void reportAllDifferentDistances(NetworkView view) {
		var networkBlock = view.getNetworkBlock();
		if (NetworkDistancesAnalyzer.isApplicable(networkBlock)) {
			var label = networkBlock.getNode().getPreferredParent().getAlgorithm().getName();
			System.err.println(label + ":");

			var surplusDistances = 0.0;
			var surplusPathDistances = 0.0;

			var taxaBlock = view.getMainWindow().getWorkingTaxa();
			var analyzer = new NetworkDistancesAnalyzer();
			for (var s = 1; s <= taxaBlock.getNtax(); s++) {
				for (var t = s + 1; t <= taxaBlock.getNtax(); t++) {
					var diff = analyzer.reportDifferentDistances(s, t, taxaBlock, networkBlock);
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
}
