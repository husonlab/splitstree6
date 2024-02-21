/*
 *  AlgorithmList.java Copyright (C) 2024 Daniel H. Huson
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

import jloda.fx.workflow.NamedBase;
import splitstree6.algorithms.trees.trees2trees.ALTSExternal;
import splitstree6.algorithms.trees.trees2trees.ALTSNetwork;
import splitstree6.main.SplitsTree6;
import splitstree6.workflow.Algorithm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * list all algorithms
 * Daniel Huson, 12.2023
 */
public class AlgorithmList {
	/**
	 * create a list of algorithm options
	 *
	 * @param names0 if non-empty, only list those classes whose simple name is contained in this
	 * @return algorithm objects
	 */
	public static List<Algorithm> list(String... names0) {
		var names = List.of(names0);
		var algorithms = new ArrayList<Algorithm>();
		add(algorithms, names, new splitstree6.algorithms.characters.characters2characters.CharactersFilter());
		add(algorithms, names, new splitstree6.algorithms.characters.characters2characters.CharactersTaxaFilter());
		add(algorithms, names, new splitstree6.algorithms.characters.characters2distances.BaseFreqDistance());
		add(algorithms, names, new splitstree6.algorithms.characters.characters2distances.Codominant());
		add(algorithms, names, new splitstree6.algorithms.characters.characters2distances.Dice());
		add(algorithms, names, new splitstree6.algorithms.characters.characters2distances.GapDist());
		add(algorithms, names, new splitstree6.algorithms.characters.characters2distances.GeneContentDistance());
		add(algorithms, names, new splitstree6.algorithms.characters.characters2distances.HammingDistances());
		add(algorithms, names, new splitstree6.algorithms.characters.characters2distances.HammingDistancesAmbigStates());
		add(algorithms, names, new splitstree6.algorithms.characters.characters2distances.Jaccard());
		add(algorithms, names, new splitstree6.algorithms.characters.characters2distances.LogDet());
		add(algorithms, names, new splitstree6.algorithms.characters.characters2distances.NeiMiller());
		add(algorithms, names, new splitstree6.algorithms.characters.characters2distances.Nei_Li_RestrictionDistance());
		add(algorithms, names, new splitstree6.algorithms.characters.characters2distances.ProteinMLdist());
		add(algorithms, names, new splitstree6.algorithms.characters.characters2distances.Uncorrected_P());
		add(algorithms, names, new splitstree6.algorithms.characters.characters2distances.Upholt());
		add(algorithms, names, new splitstree6.algorithms.characters.characters2distances.nucleotide.F81());
		add(algorithms, names, new splitstree6.algorithms.characters.characters2distances.nucleotide.F84());
		add(algorithms, names, new splitstree6.algorithms.characters.characters2distances.nucleotide.GTR());
		add(algorithms, names, new splitstree6.algorithms.characters.characters2distances.nucleotide.HKY85());
		add(algorithms, names, new splitstree6.algorithms.characters.characters2distances.nucleotide.JukesCantor());
		add(algorithms, names, new splitstree6.algorithms.characters.characters2distances.nucleotide.K2P());
		add(algorithms, names, new splitstree6.algorithms.characters.characters2distances.nucleotide.K3ST());
		add(algorithms, names, new splitstree6.algorithms.characters.characters2network.MedianJoining());
		add(algorithms, names, new splitstree6.algorithms.characters.characters2report.EstimateInvariableSites());
		add(algorithms, names, new splitstree6.algorithms.characters.characters2report.PhiTest());
		add(algorithms, names, new splitstree6.algorithms.characters.characters2splits.BinaryToSplits());
		add(algorithms, names, new splitstree6.algorithms.characters.characters2splits.DnaToSplits());
		add(algorithms, names, new splitstree6.algorithms.characters.characters2splits.ParsimonySplits());
		add(algorithms, names, new splitstree6.algorithms.characters.characters2trees.ExternalProgram());
		add(algorithms, names, new splitstree6.algorithms.distances.distances2distances.DistancesTaxaFilter());
		add(algorithms, names, new splitstree6.algorithms.distances.distances2network.MinSpanningNetwork());
		add(algorithms, names, new splitstree6.algorithms.distances.distances2network.PCoA());
		add(algorithms, names, new splitstree6.algorithms.distances.distances2report.DeltaScore());
		add(algorithms, names, new splitstree6.algorithms.distances.distances2splits.BunemanTree());
		add(algorithms, names, new splitstree6.algorithms.distances.distances2splits.NeighborNet());
		add(algorithms, names, new splitstree6.algorithms.distances.distances2splits.SplitDecomposition());
		add(algorithms, names, new splitstree6.algorithms.distances.distances2trees.BioNJ());
		add(algorithms, names, new splitstree6.algorithms.distances.distances2trees.MinSpanningTree());
		add(algorithms, names, new splitstree6.algorithms.distances.distances2trees.NeighborJoining());
		add(algorithms, names, new splitstree6.algorithms.distances.distances2trees.UPGMA());
		add(algorithms, names, new splitstree6.algorithms.genomes.genome2distances.Mash());
		add(algorithms, names, new splitstree6.algorithms.genomes.genomes2genomes.GenomesTaxaFilter());
		add(algorithms, names, new splitstree6.algorithms.network.network2network.NetworkTaxaFilter());
		add(algorithms, names, new splitstree6.algorithms.network.network2view.ShowNetwork());
		add(algorithms, names, new splitstree6.algorithms.source.source2characters.CharactersLoader());
		add(algorithms, names, new splitstree6.algorithms.source.source2distances.DistancesLoader());
		add(algorithms, names, new splitstree6.algorithms.source.source2genomes.GenomesLoader());
		add(algorithms, names, new splitstree6.algorithms.source.source2network.NetworkLoader());
		add(algorithms, names, new splitstree6.algorithms.source.source2splits.SplitsLoader());
		add(algorithms, names, new splitstree6.algorithms.source.source2trees.TreesLoader());
		add(algorithms, names, new splitstree6.algorithms.splits.splits2distances.LeastSquaresDistances());
		add(algorithms, names, new splitstree6.algorithms.splits.splits2report.PhylogeneticDiversity());
		add(algorithms, names, new splitstree6.algorithms.splits.splits2report.ShapleyValues());
		add(algorithms, names, new splitstree6.algorithms.splits.splits2splits.BootstrapSplits());
		add(algorithms, names, new splitstree6.algorithms.splits.splits2splits.DimensionFilter());
		add(algorithms, names, new splitstree6.algorithms.splits.splits2splits.SplitsFilter());
		add(algorithms, names, new splitstree6.algorithms.splits.splits2splits.SplitsTaxaFilter());
		add(algorithms, names, new splitstree6.algorithms.splits.splits2splits.WeightsSlider());
		add(algorithms, names, new splitstree6.algorithms.splits.splits2trees.GreedyTree());
		add(algorithms, names, new splitstree6.algorithms.splits.splits2view.ShowSplits());
		add(algorithms, names, new splitstree6.algorithms.taxa.taxa2taxa.TaxaFilter());
		add(algorithms, names, new splitstree6.algorithms.trees.trees2distances.AverageDistances());
		add(algorithms, names, new splitstree6.algorithms.trees.trees2report.ListOneRSPRTrees());
		add(algorithms, names, new splitstree6.algorithms.trees.trees2report.PhylogeneticDiversity());
		add(algorithms, names, new splitstree6.algorithms.trees.trees2report.TreeDiversityIndex());
		add(algorithms, names, new splitstree6.algorithms.trees.trees2report.UnrootedShapleyValues());
		add(algorithms, names, new splitstree6.algorithms.trees.trees2splits.AntiConsensusNetwork());
		add(algorithms, names, new splitstree6.algorithms.trees.trees2splits.AverageConsensus());
		add(algorithms, names, new splitstree6.algorithms.trees.trees2splits.BootstrapTreeSplits());
		add(algorithms, names, new splitstree6.algorithms.trees.trees2splits.ConsensusNetwork());
		add(algorithms, names, new splitstree6.algorithms.trees.trees2splits.ConsensusOutline());
		add(algorithms, names, new splitstree6.algorithms.trees.trees2splits.ConsensusSplits());
		add(algorithms, names, new splitstree6.algorithms.trees.trees2splits.CredibilityNetwork());
		add(algorithms, names, new splitstree6.algorithms.trees.trees2splits.FilteredSuperNetwork());
		add(algorithms, names, new splitstree6.algorithms.trees.trees2splits.SuperNetwork());
		add(algorithms, names, new splitstree6.algorithms.trees.trees2splits.TreeSelectorSplits());
		add(algorithms, names, new ALTSExternal());
		add(algorithms, names, new ALTSNetwork());
		add(algorithms, names, new splitstree6.algorithms.trees.trees2trees.AutumnAlgorithm());
		add(algorithms, names, new splitstree6.algorithms.trees.trees2trees.BootstrapTree());
		add(algorithms, names, new splitstree6.algorithms.trees.trees2trees.ClusterNetwork());
		add(algorithms, names, new splitstree6.algorithms.trees.trees2trees.ConsensusTree());
		add(algorithms, names, new splitstree6.algorithms.trees.trees2trees.EnumerateContainedTrees());
		add(algorithms, names, new splitstree6.algorithms.trees.trees2trees.LooseAndLacy());
		add(algorithms, names, new splitstree6.algorithms.trees.trees2trees.RerootOrReorderTrees());
		add(algorithms, names, new splitstree6.algorithms.trees.trees2trees.RootedConsensusTree());
		add(algorithms, names, new splitstree6.algorithms.trees.trees2trees.TreeSelector());
		add(algorithms, names, new splitstree6.algorithms.trees.trees2trees.TreesFilter());
		add(algorithms, names, new splitstree6.algorithms.trees.trees2trees.TreesFilter2());
		add(algorithms, names, new splitstree6.algorithms.trees.trees2trees.TreesTaxaFilter());
		add(algorithms, names, new splitstree6.algorithms.trees.trees2view.ShowTrees());
		algorithms.sort(Comparator.comparing(NamedBase::getName));
		return algorithms;
	}

	private static void add(Collection<Algorithm> algorithms, Collection<String> names, Algorithm algorithm) {
		if (SplitsTree6.isDesktop() || !(algorithm instanceof IDesktopOnly)) {
			var aname = algorithm.getClass().getSimpleName();
			if (names == null || names.isEmpty() || names.contains(aname))
				algorithms.add(algorithm);
		}
	}
}
