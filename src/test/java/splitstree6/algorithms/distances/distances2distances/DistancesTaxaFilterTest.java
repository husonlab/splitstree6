/*
 *  DistancesTaxaFilterTest.java Copyright (C) 2026 Daniel H. Huson
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

package splitstree6.algorithms.distances.distances2distances;

import jloda.util.progress.ProgressSilent;
import org.junit.jupiter.api.Test;
import splitstree6.data.DistancesBlock;
import splitstree6.data.TaxaBlock;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static splitstree6.testing.TestUtils.*;

public class DistancesTaxaFilterTest {
	/**
	 * keeping taxa 1, 3, 4 and 6 of six keeps their rows and columns, in that order
	 */
	@Test
	void theDistancesOfTheRemainingTaxaAreKept() throws Exception {
		var n = 6;
		var d = metric(randomTree(n, new Random(19)), n);
		var some = new TaxaBlock();
		some.addTaxaByNames(List.of("t1", "t3", "t4", "t6"));
		var out = new DistancesBlock();
		new DistancesTaxaFilter().filter(new ProgressSilent(), taxa(n), some, distances(d), out);
		var keep = new int[]{0, 2, 3, 5};
		assertEquals(4, out.getNtax());
		for (var i = 0; i < 4; i++)
			for (var j = 0; j < 4; j++)
				assertEquals(d[keep[i]][keep[j]], out.get(i + 1, j + 1), 0, (i + 1) + "," + (j + 1));
	}
}
