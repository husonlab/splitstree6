/*
 *  TaxaFilterTest.java Copyright (C) 2026 Daniel H. Huson
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

package splitstree6.algorithms.taxa.taxa2taxa;

import jloda.util.progress.ProgressSilent;
import org.junit.jupiter.api.Test;
import splitstree6.data.TaxaBlock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static splitstree6.testing.TestUtils.taxa;

/**
 * the filter that removes taxa
 */
public class TaxaFilterTest {
	@Test
	void disabledTaxaAreRemovedAndTheOthersKeepTheirOrder() {
		var filter = new TaxaFilter();
		var output = new TaxaBlock();
		filter.compute(new ProgressSilent(), null, taxa(6), output);
		assertEquals(taxa(6).getLabels(), output.getLabels());

		filter.setDisabled(List.of("t2", "t5"), true);
		assertTrue(filter.isDisabled("t5"));
		output = new TaxaBlock();
		filter.compute(new ProgressSilent(), null, taxa(6), output);
		assertEquals(List.of("t1", "t3", "t4", "t6"), output.getLabels());
		assertEquals(3, output.indexOf("t4"));

		filter.setDisabled("t5", false);
		output = new TaxaBlock();
		filter.compute(new ProgressSilent(), null, taxa(6), output);
		assertEquals(List.of("t1", "t3", "t4", "t5", "t6"), output.getLabels());

		filter.clear();
		assertEquals(0, filter.getNumberDisabledTaxa());
	}

	/**
	 * a name that is not among the taxa has no effect
	 */
	@Test
	void unknownNames() {
		var filter = new TaxaFilter();
		filter.setOptionDisabledTaxa(new String[]{"nobody"});
		var output = new TaxaBlock();
		filter.compute(new ProgressSilent(), null, taxa(3), output);
		assertEquals(List.of("t1", "t2", "t3"), output.getLabels());
	}
}
