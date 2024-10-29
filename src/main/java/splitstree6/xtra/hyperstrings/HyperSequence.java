/*
 *  HyperSequence.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.xtra.hyperstrings;

import jloda.util.BitSetUtils;
import jloda.util.StringUtils;

import java.util.ArrayList;
import java.util.BitSet;

public record HyperSequence(ArrayList<BitSet> members) {

	/**
	 * default constructor
	 */
	public HyperSequence() {
		this(new ArrayList<>());
	}

	/**
	 * parse a string description,
	 *
	 * @param values e.g. "1 : 3 : 4 6 : 2 : 5"
	 * @return hyper sequence
	 */
	public static HyperSequence parse(String values) {
		var sequence = new HyperSequence();
		for (var word : StringUtils.split(values, ':')) {
			sequence.add(BitSetUtils.asBitSet(StringUtils.parseArrayOfIntegers(word)));
		}
		return sequence;
	}


	/**
	 * write out in parsable format
	 *
	 * @return string, e.g. "1 : 3 : 4 6 : 2 : 5"
	 */
	public String toString() {
		var buf = new StringBuilder();
		for (var set : members) {
			if (!buf.isEmpty())
				buf.append(" : ");
			buf.append(StringUtils.toString(set));
		}
		return buf.toString();
	}


	public BitSet get(int i) {
		return members.get(i);
	}

	public void set(int i, BitSet set) {
		members.set(i, set);
	}

	public void add(BitSet set) {
		members.add(set);
	}

	public int size() {
		return members.size();
	}

	public void removeEmptyElements() {
		members.removeAll(members.stream().filter(BitSet::isEmpty).toList());
	}

	public HyperSequence induce(BitSet taxa) {
		var hypersequence = new HyperSequence();
		for (var element : members) {
			var set = BitSetUtils.intersection(element, taxa);
			if (set.cardinality() > 0 && !hypersequence.members().contains(set))
				hypersequence.members().add(set);
		}
		return hypersequence;
	}
}
