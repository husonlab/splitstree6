/*
 *  OptionDefaultsTest.java Copyright (C) 2026 Daniel H. Huson
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

import org.junit.jupiter.api.Test;
import splitstree6.algorithms.characters.characters2distances.NeiLiRestrictionDistance;
import splitstree6.algorithms.characters.characters2distances.UpholtRestrictionDistance;
import splitstree6.algorithms.characters.characters2report.EstimateInvariableSites;
import splitstree6.algorithms.characters.characters2report.TajimaD;
import splitstree6.algorithms.trees.trees2distances.AverageDistances;
import splitstree6.algorithms.trees.trees2splits.ConsensusNetwork;
import splitstree6.algorithms.trees.trees2splits.ConsensusOutline;
import splitstree6.algorithms.trees.trees2splits.ConsensusSplits;
import splitstree6.algorithms.trees.trees2trees.AutumnAlgorithm;
import splitstree6.algorithms.trees.trees2trees.ClusterNetwork;
import splitstree6.algorithms.trees.trees2trees.PhyloFusion;
import splitstree6.workflow.Algorithm;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static splitstree6.testing.TestUtils.optionValues;

/**
 * the default of an option is fixed: it is not a value that was used earlier on this computer.
 * Until 11.2026 the options listed here took the value last used, which was kept in the preferences of the user
 */
public class OptionDefaultsTest {
	@Test
	void defaultsThatWereOnceTakenFromThePreferences() throws Exception {
		assertEquals("true", optionValues(new TajimaD()).get("optionExcludeGapSitesProperty"));
		assertEquals("20", optionValues(new EstimateInvariableSites()).get("optionFullTaxaCutoffProperty"));
		assertEquals("false", optionValues(new AverageDistances()).get("optionUseTopologicalDistanceProperty"));
		assertEquals("1.0", optionValues(new UpholtRestrictionDistance()).get("optionRestrictionSiteLengthProperty"));
		assertEquals("1.0", optionValues(new NeiLiRestrictionDistance()).get("optionRestrictionSiteLengthProperty"));
		assertEquals("Count", optionValues(new ConsensusNetwork()).get("optionEdgeWeightsProperty"));
		assertEquals("Count", optionValues(new ConsensusOutline()).get("optionEdgeWeightsProperty"));
		assertEquals("Mean", optionValues(new ConsensusSplits()).get("optionEdgeWeightsProperty"));
		assertEquals("Count", optionValues(new ClusterNetwork()).get("optionEdgeWeightsProperty"));
		assertEquals(Map.of("optionFirstTreeProperty", "1", "optionSecondTreeProperty", "2", "optionOnlyOneNetworkProperty", "true", "optionRerootToMinimizeProperty", "false", "optionTreeChildOnlyProperty", "false"),
				optionValues(new AutumnAlgorithm()));
		var phyloFusion = optionValues(new PhyloFusion());
		assertEquals("true", phyloFusion.get("optionMutualRefinementProperty"));
		assertEquals("true", phyloFusion.get("optionCladeReductionProperty"));
		assertEquals("true", phyloFusion.get("optionOnlyOneNetworkProperty"));
		assertEquals("Normal", phyloFusion.get("optionReticulatePlacementProperty"));
		assertEquals("LeastSquares", phyloFusion.get("optionEdgeWeightsProperty"));
		assertEquals("true", phyloFusion.get("optionGroupNonSeparatedProperty"));
		assertEquals("true", phyloFusion.get("optionMissingTaxaHeuristicProperty"));
		assertEquals("false", phyloFusion.get("optionRefinementHeuristicProperty"));
	}

	/**
	 * changing an option of one instance has no effect on an instance created later
	 */
	@Test
	void aChangedOptionIsNotRemembered() throws Exception {
		Algorithm<?, ?>[] first = {new TajimaD(), new ConsensusNetwork(), new ClusterNetwork(), new AutumnAlgorithm(), new PhyloFusion(), new AverageDistances()};
		var before = new java.util.ArrayList<Map<String, String>>();
		for (var algorithm : first) before.add(optionValues(algorithm));
		((TajimaD) first[0]).optionExcludeGapSitesProperty().set(false);
		((ConsensusNetwork) first[1]).setOptionEdgeWeights(ConsensusSplits.EdgeWeights.Sum);
		((ClusterNetwork) first[2]).setOptionEdgeWeights(ClusterNetwork.EdgeWeights.Sum);
		((AutumnAlgorithm) first[3]).setOptionOnlyOneNetwork(false);
		((PhyloFusion) first[4]).setOptionMutualRefinement(false);
		((AverageDistances) first[5]).optionUseTopologicalDistanceProperty().set(true);
		assertFalse(before.get(0).equals(optionValues(first[0])));
		Algorithm<?, ?>[] second = {new TajimaD(), new ConsensusNetwork(), new ClusterNetwork(), new AutumnAlgorithm(), new PhyloFusion(), new AverageDistances()};
		for (var i = 0; i < second.length; i++) assertEquals(before.get(i), optionValues(second[i]), second[i].getClass().getSimpleName());
	}
}
