/*
 * FixUndefinedDistances.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.algorithms.characters.characters2distances.utils;

import jloda.fx.window.NotificationManager;
import jloda.util.StringUtils;
import splitstree6.data.DistancesBlock;

import java.util.Arrays;

/**
 * fix undefined distances
 * Daniel Huson, 2.2018
 */
public class FixUndefinedDistances {
	/**
	 * fix undefined distances
	 */
	public static void apply(DistancesBlock distancesBlock) {

		var maxValue = 0.0;
		var numUndefined = 0;

		var ntax = distancesBlock.getNtax();
		for (int s = 1; s <= ntax; s++) {
			for (int t = 1; t <= ntax; t++) {
				if (distancesBlock.get(s, t) == -1)
					numUndefined++;
				else
					maxValue = Math.max(maxValue, distancesBlock.get(s, t));
			}
		}

		if (numUndefined > 0) {
			var largeValue = 1.0;
			if (maxValue > 1) {
				maxValue *= 1.1;
				var power = (int) Math.ceil(Math.log10(maxValue));
				var firstDigit = Math.ceil(maxValue / Math.pow(10, power - 1));
				largeValue = firstDigit * Math.pow(10, power - 1);
			}
			for (var t = 1; t <= ntax; t++) {
				for (var s = t + 1; s <= ntax; s++) {
					if (distancesBlock.get(s, t) == -1) {
						distancesBlock.set(s, t, largeValue);
						distancesBlock.set(t, s, largeValue);
					}
				}
			}
			NotificationManager.showWarning("Distance calculation produced " + numUndefined + " saturated or missing entries.\n" +
											"These have been replaced by the value '" + StringUtils.removeTrailingZerosAfterDot(largeValue) + "'.");
		}

	}
}
