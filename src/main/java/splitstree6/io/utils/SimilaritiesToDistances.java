/*
 * SimilaritiesToDistances.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.io.utils;

import splitstree6.data.DistancesBlock;

import java.util.function.DoubleUnaryOperator;

/**
 * Convert similarities into distances
 * Daniel Huson, 1.2023
 */
public class SimilaritiesToDistances {
	public enum Method {
		log("-ln(S)", s -> Math.max(0, (s > 0 ? -Math.log(s) : 1))),
		log100("-ln(S/100)", s -> Math.max(0, (s > 0 ? -Math.log(s / 100) : 100))),
		one_minus("1-S", s -> (s >= 0 && s <= 1 ? 1 - s : 1)),
		one_minus100("1-S/100", s -> (s >= 0 && s <= 100 ? 1 - s / 100 : 1)),
		one_minus_normalize("(1-S)/S", s -> (s >= 0 && s <= 1 ? ((1 - s) / s) : 1)),
		one_minus_normalize100("(100-S)/S", s -> (s >= 0 && s <= 100 ? ((100 - s) / s) : 1)),
		sqrt("sqrt(1-S)", s -> (s >= 0 && s <= 1 ? Math.sqrt(1 - s) : 1)),
		sqrt_100("sqrt(1-S/100)", s -> (s >= 0 && s <= 100 ? Math.sqrt(1 - s / 100) : 1)),
		cos("arccos(S)", s -> (s >= 0 && s <= 1 ? Math.acos(s) : 1)),
		cos100("arccos(S/100)", s -> (s >= 0 && s <= 100 ? Math.acos(s / 100) : 1));

		private final String label;
		private final DoubleUnaryOperator operator;

		Method(String label, DoubleUnaryOperator operator) {
			this.label = label;
			this.operator = operator;
		}

		public String toString() {
			return this.label;
		}

		public DoubleUnaryOperator getOperator() {
			return operator;
		}
	}

	;

	/**
	 * apply the transformation to a given distances block
	 *
	 * @param distancesBlock the distances block
	 */
	public static void apply(Method method, DistancesBlock distancesBlock) {
		for (var s = 1; s <= distancesBlock.getNtax(); s++) {
			distancesBlock.set(s, s, 0);
			for (var t = s + 1; t <= distancesBlock.getNtax(); t++) {
				var value = 0.5 * (method.getOperator().applyAsDouble(distancesBlock.get(s, t)) + method.getOperator().applyAsDouble(distancesBlock.get(t, s)));
				distancesBlock.set(s, t, value);
				distancesBlock.set(t, s, value);
			}
		}
	}
}