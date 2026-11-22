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

package splitstree6.options;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import splitstree6.algorithms.characters.characters2distances.nucleotide.HKY85Distance;
import splitstree6.algorithms.distances.distances2splits.NeighborNet;
import splitstree6.algorithms.splits.splits2splits.SplitsFilter;
import splitstree6.algorithms.taxa.taxa2taxa.TaxaFilter;
import splitstree6.algorithms.trees.trees2splits.ConsensusNetwork;
import splitstree6.algorithms.trees.trees2splits.ConsensusSplits;

import static org.junit.jupiter.api.Assertions.*;
import static splitstree6.testing.TestUtils.optionValues;

/**
 * settings that the user asks to be remembered for an algorithm, and the factory settings
 */
public class OptionDefaultsTest {
	@AfterEach
	void forget() {
		OptionDefaults.forgetUserDefaults(new ConsensusNetwork());
		OptionDefaults.forgetUserDefaults(new SplitsFilter());
		OptionDefaults.forgetUserDefaults(new TaxaFilter());
		OptionDefaults.forgetUserDefaults(new HKY85Distance());
	}

	/**
	 * remembered settings are applied only when asked for: a new instance has the factory settings
	 */
	@Test
	void rememberApplyAndForget() throws Exception {
		var factory = optionValues(new ConsensusNetwork());
		assertFalse(OptionDefaults.hasUserDefaults(new ConsensusNetwork()));
		assertFalse(OptionDefaults.applyUserDefaults(new ConsensusNetwork()));

		var algorithm = new ConsensusNetwork();
		assertFalse(OptionDefaults.differsFromFactoryDefaults(algorithm));
		algorithm.setOptionThresholdPercent(12.5);
		algorithm.setOptionEdgeWeights(ConsensusSplits.EdgeWeights.Median);
		algorithm.setOptionHighDimensionFilter(false);
		assertTrue(OptionDefaults.differsFromFactoryDefaults(algorithm));
		var chosen = optionValues(algorithm);
		OptionDefaults.storeAsUserDefaults(algorithm);

		assertTrue(OptionDefaults.hasUserDefaults(new ConsensusNetwork()));
		assertFalse(OptionDefaults.hasUserDefaults(new ConsensusSplits()), "another algorithm");
		var fresh = new ConsensusNetwork();
		assertEquals(factory, optionValues(fresh), "a new instance has the factory settings");
		assertTrue(OptionDefaults.applyUserDefaults(fresh));
		assertEquals(chosen, optionValues(fresh));

		OptionDefaults.applyFactoryDefaults(fresh);
		assertEquals(factory, optionValues(fresh));
		assertTrue(OptionDefaults.hasUserDefaults(fresh), "factory settings do not forget the remembered ones");

		OptionDefaults.forgetUserDefaults(fresh);
		assertFalse(OptionDefaults.hasUserDefaults(new ConsensusNetwork()));
	}

	/**
	 * all kinds of values: numbers, switches, choices; lists are not remembered, they belong to the data
	 */
	@Test
	void kindsOfValues() throws Exception {
		var filter = new SplitsFilter();
		filter.setOptionWeightThreshold(0.25f);
		filter.setOptionMaximumDimension(2);
		filter.setOptionFilterAlgorithm(SplitsFilter.FilterAlgorithm.GreedyCircular);
		filter.setOptionRecomputeCycle(true);
		OptionDefaults.storeAsUserDefaults(filter);
		var fresh = new SplitsFilter();
		OptionDefaults.applyUserDefaults(fresh);
		assertEquals(optionValues(filter), optionValues(fresh));

		var hky = new HKY85Distance();
		var factory = optionValues(hky);
		hky.setOptionTsTvRatio(3.5);
		hky.setOptionBaseFrequencies(new double[]{0.1, 0.2, 0.3, 0.4});
		OptionDefaults.storeAsUserDefaults(hky);
		var freshHky = new HKY85Distance();
		OptionDefaults.applyUserDefaults(freshHky);
		assertEquals(3.5, freshHky.getOptionTsTvRatio());
		assertEquals(factory.get("optionBaseFrequenciesProperty"), optionValues(freshHky).get("optionBaseFrequenciesProperty"), "a list of numbers is not remembered");

		var taxa = new TaxaFilter();
		taxa.setOptionDisabledTaxa(new String[]{"t1"});
		assertTrue(OptionDefaults.storableOptions(taxa).isEmpty());
		OptionDefaults.storeAsUserDefaults(taxa);
		assertFalse(OptionDefaults.hasUserDefaults(taxa));
	}

	/**
	 * the factory settings are those of a new instance, also for an algorithm whose constructor sets them
	 */
	@Test
	void factorySettings() throws Exception {
		for (Object algorithm : new Object[]{new ConsensusNetwork(), new NeighborNet(), new SplitsFilter(), new HKY85Distance()}) {
			var carrier = (IOptionsCarrier) algorithm;
			var factory = optionValues(algorithm);
			OptionDefaults.applyFactoryDefaults(carrier);
			assertEquals(factory, optionValues(algorithm), algorithm.getClass().getSimpleName());
			assertFalse(OptionDefaults.differsFromFactoryDefaults(carrier));
		}
	}
}
