/*
 *  CharactersUtilities.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.algorithms.utils;

import jloda.util.BitSetUtils;
import splitstree6.data.CharactersBlock;
import splitstree6.data.SplitsBlock;
import splitstree6.splits.ASplit;

import java.util.BitSet;
import java.util.Collection;

/**
 * character utilities
 * Daniel Huson, 9.2022
 */
public class CharactersUtilities {
	/**
	 * a character is deemed compatible with a given split, unless there exists a state that covers some, but not all, taxa on each side of the split
	 *
	 * @param charactersBlock characters
	 * @param split           the split
	 * @return characters compatible with the split
	 */
	public static BitSet computeAllCompatible(CharactersBlock charactersBlock, ASplit split) {
		var compatibleSites = new BitSet();
		for (var c = 1; c <= charactersBlock.getNchar(); c++) {
			var colorA1 = -1;
			var colorA2 = -1;
			for (var t : BitSetUtils.members(split.getA())) {
				var color = charactersBlock.getColor(t, c);
				if (color != -1) {
					if (colorA1 == -1)
						colorA1 = color;
					else if (color != colorA1 && colorA2 == -1)
						colorA2 = color;
				}
			}
			if (colorA1 != -1) {
				if (colorA2 == -1) {
					compatibleSites.set(c);
				} else {
					var colorB1 = -1;
					var colorB2 = -1;
					for (var t : BitSetUtils.members(split.getB())) {
						var color = charactersBlock.getColor(t, c);
						if (color != -1) {
							if (colorB1 == -1)
								colorB1 = color;
							else if (color != colorB1 && colorB2 == -1)
								colorB2 = color;
						}
					}
					if (colorB2 == -1) {
						compatibleSites.set(c);
					}
				}
			}
		}
		return compatibleSites;
	}

	public static BitSet computeAllCompatible(CharactersBlock charactersBlock, SplitsBlock splitsBlock, Collection<Integer> splits) {
		var compatibleSites = BitSetUtils.asBitSet(BitSetUtils.range(1, charactersBlock.getNchar() + 1));
		for (var i : splits) {
			var split = splitsBlock.get(i);
			compatibleSites.and(computeAllCompatible(charactersBlock, split));
		}
		return compatibleSites;
	}
}