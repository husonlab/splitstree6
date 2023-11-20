/*
 *  AlgorithmList.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.algorithms;

import jloda.util.PluginClassLoader;
import splitstree6.workflow.Algorithm;

import java.util.ArrayList;
import java.util.List;

public class AlgorithmList {
	public static List<Algorithm> list() {
		var algorithms = new ArrayList<Algorithm>();

		if (true) {
			algorithms.addAll(PluginClassLoader.getInstances(Algorithm.class, "splitstree6.algorithms"));
		} else {
			algorithms.add(new splitstree6.algorithms.characters.characters2characters.CharactersFilter());
			algorithms.add(new splitstree6.algorithms.characters.characters2characters.CharactersTaxaFilter());
			algorithms.add(new splitstree6.algorithms.characters.characters2distances.BaseFreqDistance());
			algorithms.add(new splitstree6.algorithms.characters.characters2distances.Codominant());
			algorithms.add(new splitstree6.algorithms.characters.characters2distances.Dice());
			algorithms.add(new splitstree6.algorithms.characters.characters2distances.GapDist());
			algorithms.add(new splitstree6.algorithms.characters.characters2distances.GeneContentDistance());
			algorithms.add(new splitstree6.algorithms.characters.characters2distances.HammingDistances());
			algorithms.add(new splitstree6.algorithms.characters.characters2distances.HammingDistancesAmbigStates());
			algorithms.add(new splitstree6.algorithms.characters.characters2distances.Jaccard());
			algorithms.add(new splitstree6.algorithms.characters.characters2distances.LogDet());
			algorithms.add(new splitstree6.algorithms.characters.characters2distances.NeiMiller());
			algorithms.add(new splitstree6.algorithms.characters.characters2distances.Nei_Li_RestrictionDistance());
			algorithms.add(new splitstree6.algorithms.characters.characters2distances.ProteinMLdist());
			algorithms.add(new splitstree6.algorithms.characters.characters2distances.Uncorrected_P());
			algorithms.add(new splitstree6.algorithms.characters.characters2distances.Upholt());
			algorithms.add(new splitstree6.algorithms.characters.characters2network.MedianJoining());
			algorithms.add(new splitstree6.algorithms.characters.characters2report.EstimateInvariableSites());
			algorithms.add(new splitstree6.algorithms.characters.characters2report.PhiTest());
			algorithms.add(new splitstree6.algorithms.characters.characters2splits.BinaryToSplits());
			algorithms.add(new splitstree6.algorithms.characters.characters2splits.DnaToSplits());
			algorithms.add(new splitstree6.algorithms.characters.characters2splits.ParsimonySplits());
			algorithms.add(new splitstree6.algorithms.characters.characters2trees.ExternalProgram());
			algorithms.add(new splitstree6.algorithms.distances.distances2distances.DistancesTaxaFilter());
			algorithms.add(new splitstree6.algorithms.distances.distances2network.MinSpanningNetwork());
			algorithms.add(new splitstree6.algorithms.distances.distances2network.PCoA());
			algorithms.add(new splitstree6.algorithms.distances.distances2report.DeltaScore());
			algorithms.add(new splitstree6.algorithms.distances.distances2splits.BunemanTree());
			algorithms.add(new splitstree6.algorithms.distances.distances2splits.NeighborNet());
			algorithms.add(new splitstree6.algorithms.distances.distances2splits.SplitDecomposition());
			algorithms.add(new splitstree6.algorithms.distances.distances2trees.BioNJ());
			algorithms.add(new splitstree6.algorithms.distances.distances2trees.MinSpanningTree());
			algorithms.add(new splitstree6.algorithms.distances.distances2trees.NeighborJoining());
			algorithms.add(new splitstree6.algorithms.distances.distances2trees.UPGMA());
			algorithms.add(new splitstree6.algorithms.genomes.genome2distances.Mash());
			algorithms.add(new splitstree6.algorithms.genomes.genomes2genomes.GenomesTaxaFilter());
			algorithms.add(new splitstree6.algorithms.network.network2network.NetworkTaxaFilter());
			algorithms.add(new splitstree6.algorithms.network.network2view.ShowNetwork());
			algorithms.add(new splitstree6.algorithms.source.source2characters.CharactersLoader());
			algorithms.add(new splitstree6.algorithms.source.source2distances.DistancesLoader());
			algorithms.add(new splitstree6.algorithms.source.source2genomes.GenomesLoader());
			algorithms.add(new splitstree6.algorithms.source.source2network.NetworkLoader());
			algorithms.add(new splitstree6.algorithms.source.source2splits.SplitsLoader());
		}
		return algorithms;
	}
}
