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

public class AdditionalConsoleOutput {
	public static void setup(NetworkView view) {

		view.networkBlockProperty().addListener((v, o, networkBlock) -> {
			if (networkBlock != null && true) {
				reportAllDifferentDistances(view);
				reportAllDifferencesCharacters(view);
			}
			if (networkBlock != null && networkBlock.getInfoString().isBlank()) {
				String info = "";
				if (NetworkSequencesAnalyzer.isApplicable(networkBlock)) {
					var analyzer = new NetworkSequencesAnalyzer(networkBlock);
					var totalEdgeDistances = analyzer.totalEdgeDistances(networkBlock);
					var realizedPairwiseDistances = analyzer.realizedPairwiseDistances(networkBlock);
					var inputPairwiseDistances = analyzer.inputPairwiseDistances(networkBlock);
					;
					var excessDistance = realizedPairwiseDistances - inputPairwiseDistances;
					info = "Total length: %d, excess: %d".formatted(totalEdgeDistances, excessDistance);
				} else if (NetworkDistancesAnalyzer.isApplicable(networkBlock)) {
					var analyzer = new NetworkDistancesAnalyzer();
					var totalDistances = analyzer.inputPairwiseDistances(networkBlock);
					var excessDistance = analyzer.inputPairwiseDistances(networkBlock) - totalDistances;
					info = "Length: %s, excess: %s".formatted(StringUtils.trim(totalDistances), StringUtils.trim(excessDistance));
				}
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
				if (selectedItems.size() == 2) {
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
