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

import splitstree6.algorithms.characters.characters2characters.CharactersFilter;
import splitstree6.algorithms.characters.characters2characters.CharactersTaxaFilter;
import splitstree6.algorithms.characters.characters2distances.*;
import splitstree6.algorithms.characters.characters2distances.nucleotide.*;
import splitstree6.algorithms.characters.characters2network.MedianJoining;
import splitstree6.algorithms.characters.characters2report.EstimateInvariableSites;
import splitstree6.algorithms.characters.characters2report.PhiTest;
import splitstree6.algorithms.characters.characters2report.TajimaD;
import splitstree6.algorithms.characters.characters2splits.BinaryToSplits;
import splitstree6.algorithms.characters.characters2splits.DnaToSplits;
import splitstree6.algorithms.characters.characters2splits.ParsimonySplits;
import splitstree6.algorithms.characters.characters2trees.ExternalProgram;
import splitstree6.algorithms.distances.distances2distances.DistancesTaxaFilter;
import splitstree6.algorithms.distances.distances2network.MinSpanningNetwork;
import splitstree6.algorithms.distances.distances2network.PCOA;
import splitstree6.algorithms.distances.distances2report.DeltaScore;
import splitstree6.algorithms.distances.distances2splits.BunemanTree;
import splitstree6.algorithms.distances.distances2splits.NeighborNet;
import splitstree6.algorithms.distances.distances2splits.SplitDecomposition;
import splitstree6.algorithms.distances.distances2trees.BioNJ;
import splitstree6.algorithms.distances.distances2trees.MinSpanningTree;
import splitstree6.algorithms.distances.distances2trees.NeighborJoining;
import splitstree6.algorithms.distances.distances2trees.UPGMA;
import splitstree6.algorithms.genomes.genome2distances.Mash;
import splitstree6.algorithms.genomes.genomes2genomes.GenomesTaxaFilter;
import splitstree6.algorithms.network.network2network.NetworkTaxaFilter;
import splitstree6.algorithms.network.network2view.ShowNetwork;
import splitstree6.algorithms.source.source2characters.CharactersLoader;
import splitstree6.algorithms.source.source2distances.DistancesLoader;
import splitstree6.algorithms.source.source2genomes.GenomesLoader;
import splitstree6.algorithms.source.source2network.NetworkLoader;
import splitstree6.algorithms.source.source2splits.SplitsLoader;
import splitstree6.algorithms.source.source2trees.TreesLoader;
import splitstree6.algorithms.splits.splits2report.ShapleyValues;
import splitstree6.algorithms.splits.splits2splits.*;
import splitstree6.algorithms.splits.splits2trees.GreedyTree;
import splitstree6.algorithms.splits.splits2view.ShowSplits;
import splitstree6.algorithms.taxa.taxa2taxa.TaxaFilter;
import splitstree6.algorithms.trees.trees2distances.AverageDistances;
import splitstree6.algorithms.trees.trees2report.ListOneRSPRTrees;
import splitstree6.algorithms.trees.trees2report.TreeDiversityIndex;
import splitstree6.algorithms.trees.trees2report.UnrootedShapleyValues;
import splitstree6.algorithms.trees.trees2splits.*;
import splitstree6.algorithms.trees.trees2trees.*;
import splitstree6.algorithms.trees.trees2view.ShowTrees;
import splitstree6.main.SplitsTree6;
import splitstree6.workflow.Algorithm;

import java.util.ArrayList;
import java.util.Collection;
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

		add(algorithms, names, new PDistance());
		add(algorithms, names, new HammingDistance());
		add(algorithms, names, new JukesCantorDistance());
		add(algorithms, names, new K2PDistance());
		add(algorithms, names, new F81Distance());
		add(algorithms, names, new HKY85Distance());
		add(algorithms, names, new F84Distance());
		add(algorithms, names, new GTRDistance());
		add(algorithms, names, new LogDet());
		add(algorithms, names, new ProteinMLDistance());

		add(algorithms, names, new DiceDistance());
		add(algorithms, names, new JaccardDistance());
		add(algorithms, names, new GeneContentDistance());
		add(algorithms, names, new GeneSharingDistance());
		add(algorithms, names, new UpholtRestrictionDistance());
		add(algorithms, names, new NeiLiRestrictionDistance());

		add(algorithms, names, new Mash());
		add(algorithms, names, new BaseFreqDistance());

		add(algorithms, names, new BinaryToSplits());
		add(algorithms, names, new DnaToSplits());

		add(algorithms, names, new NeighborJoining());
		add(algorithms, names, new BioNJ());
		add(algorithms, names, new UPGMA());

		add(algorithms, names, new NeighborNet());
		add(algorithms, names, new SplitDecomposition());
		add(algorithms, names, new BunemanTree());

		add(algorithms, names, new MedianJoining());
		add(algorithms, names, new MinSpanningNetwork());
		add(algorithms, names, new MinSpanningTree());

		add(algorithms, names, new PCOA());

		add(algorithms, names, new EstimateInvariableSites());
		add(algorithms, names, new PhiTest());
		add(algorithms, names, new DeltaScore());
		add(algorithms, names, new splitstree6.algorithms.splits.splits2report.PhylogeneticDiversity());
		add(algorithms, names, new ShapleyValues());
		add(algorithms, names, new AverageDistances());
		add(algorithms, names, new splitstree6.algorithms.trees.trees2report.PhylogeneticDiversity());
		add(algorithms, names, new TreeDiversityIndex());
		add(algorithms, names, new TajimaD());
		add(algorithms, names, new UnrootedShapleyValues());

		add(algorithms, names, new ParsimonySplits());

		add(algorithms, names, new GreedyTree());
		add(algorithms, names, new AntiConsensusNetwork());
		add(algorithms, names, new AverageConsensus());
		add(algorithms, names, new BootstrapSplits());
		add(algorithms, names, new BootstrapTreeSplits());
		add(algorithms, names, new BootstrapTree());
		add(algorithms, names, new ConsensusNetwork());
		add(algorithms, names, new ConsensusOutline());
		add(algorithms, names, new ConsensusSplits());
		add(algorithms, names, new RootedConsensusTree());

		add(algorithms, names, new CredibilityNetwork());
		add(algorithms, names, new FilteredSuperNetwork());
		add(algorithms, names, new SuperNetwork());
		add(algorithms, names, new TreeSelectorSplits());

		add(algorithms, names, new ALTSExternal());
		add(algorithms, names, new ALTSNetwork());
		add(algorithms, names, new AutumnAlgorithm());
		add(algorithms, names, new ClusterNetwork());
		add(algorithms, names, new ConsensusTree());

		add(algorithms, names, new RerootOrReorderTrees());

		add(algorithms, names, new LooseAndLacy());
		add(algorithms, names, new EnumerateContainedTrees());
		add(algorithms, names, new ListOneRSPRTrees());

		add(algorithms, names, new CharactersFilter());
		add(algorithms, names, new CharactersLoader());
		add(algorithms, names, new CharactersTaxaFilter());
		add(algorithms, names, new DimensionFilter());
		add(algorithms, names, new DistancesLoader());
		add(algorithms, names, new DistancesTaxaFilter());
		add(algorithms, names, new ExternalProgram());
		add(algorithms, names, new GenomesLoader());
		add(algorithms, names, new GenomesTaxaFilter());
		add(algorithms, names, new NetworkLoader());
		add(algorithms, names, new NetworkTaxaFilter());
		add(algorithms, names, new ShowNetwork());
		add(algorithms, names, new ShowSplits());
		add(algorithms, names, new ShowTrees());
		add(algorithms, names, new SplitsFilter());
		add(algorithms, names, new SplitsLoader());
		add(algorithms, names, new SplitsTaxaFilter());
		add(algorithms, names, new TaxaFilter());
		add(algorithms, names, new TreeSelector());
		add(algorithms, names, new TreesFilter());
		add(algorithms, names, new TreesFilter2());
		add(algorithms, names, new TreesLoader());
		add(algorithms, names, new TreesTaxaFilter());
		add(algorithms, names, new WeightsSlider());
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
