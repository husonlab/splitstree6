/*
 * DrawNewick.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.tools.server;

import jloda.util.progress.ProgressSilent;
import splitstree6.algorithms.distances.distances2splits.NeighborNet;
import splitstree6.algorithms.distances.distances2splits.SplitDecomposition;
import splitstree6.algorithms.distances.distances2trees.BioNJ;
import splitstree6.algorithms.distances.distances2trees.NeighborJoining;
import splitstree6.algorithms.distances.distances2trees.UPGMA;
import splitstree6.data.DistancesBlock;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.io.readers.distances.NexusReader;
import splitstree6.io.readers.distances.PhylipReader;
import splitstree6.splits.SplitNewick;

import java.io.IOException;
import java.util.List;

import static jloda.util.FileLineIterator.PREFIX_TO_INDICATE_TO_PARSE_FILENAME_STRING;

/**
 * handles draw_distances requests
 * Daniel Huson, 12/2024
 */
public class DrawDistances {
	public static String apply(String matrix, String output, String algorithm, String layout, double width, double height) throws IOException {
		var input = PREFIX_TO_INDICATE_TO_PARSE_FILENAME_STRING + matrix;

		Utilities.checkValue("output", output, List.of("coordinates", "newick"));
		Utilities.checkValue("algorithm", output, List.of("nj", "bionj", "upgma", "nnet", "splitdecomposition"));

		var taxaBlock = new TaxaBlock();
		var distancesBlock = new DistancesBlock();

		for (var reader : List.of(new PhylipReader(), new NexusReader())) {
			if (reader.acceptsFirstLine(matrix)) {
				reader.read(new ProgressSilent(), input, taxaBlock, distancesBlock);
				break;
			}
		}
		if (taxaBlock.size() == 0)
			throw new IOException("Failed to read distances");

		return switch (algorithm) {
			case "nj" -> {
				var trees = new TreesBlock();
				(new NeighborJoining()).compute(new ProgressSilent(), taxaBlock, distancesBlock, trees);
				var newick = trees.getTree(1).toBracketString(true) + ";";
				yield (output.equals("newick") ? newick : DrawNewick.applyTreeNewick(newick, layout, width, height));
			}
			case "bionj" -> {
				var trees = new TreesBlock();
				(new BioNJ()).compute(new ProgressSilent(), taxaBlock, distancesBlock, trees);
				var newick = trees.getTree(1).toBracketString(true) + ";";
				yield (output.equals("newick") ? newick : DrawNewick.applyTreeNewick(newick, layout, width, height));
			}
			case "upgma" -> {
				var trees = new TreesBlock();
				(new UPGMA()).compute(new ProgressSilent(), taxaBlock, distancesBlock, trees);
				var newick = trees.getTree(1).toBracketString(true) + ";";
				yield (output.equals("newick") ? newick : DrawNewick.applyTreeNewick(newick, layout, width, height));
			}
			case "nnet" -> {
				var splitsBlock = new SplitsBlock();
				(new NeighborNet()).compute(new ProgressSilent(), taxaBlock, distancesBlock, splitsBlock);
				var newick = SplitNewick.toString(taxaBlock::getLabel, splitsBlock.getSplits(), true, false) + ";";
				yield (output.equals("newick") ? newick : DrawNewick.applySplitNewick(newick, layout, width, height));
			}
			case "splitdecomposition" -> {
				var splitsBlock = new SplitsBlock();
				(new SplitDecomposition()).compute(new ProgressSilent(), taxaBlock, distancesBlock, splitsBlock);
				var newick = SplitNewick.toString(taxaBlock::getLabel, splitsBlock.getSplits(), true, false) + ";";
				yield (output.equals("newick") ? newick : DrawNewick.applySplitNewick(newick, layout, width, height));
			}
			default -> throw new IOException("Unsupported algorithm: " + algorithm);
		};
	}
}
