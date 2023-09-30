/*
 * DistancesReader.java Copyright (C) 2023 Daniel H. Huson
 *
 * (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package splitstree6.io.readers.distances;

import jloda.fx.window.NotificationManager;
import splitstree6.data.DistancesBlock;
import splitstree6.data.DistancesFormat;
import splitstree6.data.TaxaBlock;
import splitstree6.io.utils.DataReaderBase;

public abstract class DistancesReader extends DataReaderBase<DistancesBlock> {
	public DistancesReader() {
		super(DistancesBlock.class);
	}

	public static void ensureSymmetric(TaxaBlock taxa, DistancesBlock distances) {
		if (distances.getFormat().getOptionTriangle() == DistancesFormat.Triangle.Both) {
			var changed = false;
			for (var s = 1; s <= taxa.getNtax(); s++) {
				for (var t = s + 1; t <= taxa.getNtax(); t++) {
					if (distances.get(s, t) != distances.get(t, s)) {
						var mean = 0.5 * (distances.get(s, t) + distances.get(t, s));
						distances.set(s, t, mean);
						distances.set(t, s, mean);
						changed = true;
					}
				}
			}
			if (changed)
				NotificationManager.showWarning("Distance matrix not symmetric, using mean values");
		}


	}
}
