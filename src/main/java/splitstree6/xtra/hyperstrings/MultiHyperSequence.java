/*
 *  MultiHyperSequence.java Copyright (C) 2024 Daniel H. Huson
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

import java.util.*;

public record MultiHyperSequence(ArrayList<Set<BitSet>> array) {
	/**
	 * default constructor
	 */
	public MultiHyperSequence() {
		this(new ArrayList<>());
	}

	/**
	 * parses a multi-hyper sequence from a string
	 * Sets are written as integers, components separated by ':', different sets for the same component separated by '|'
	 *
	 * @param values string of values, e.g. "1 2; 3; 4 5;  7 | 8; 9;"
	 * @return the corresponding multi-hyper set
	 */
	public static MultiHyperSequence parse(String values) {
		var sequence = new MultiHyperSequence();
		for (var word : StringUtils.split(values, ';')) {
			var tokens = StringUtils.split(word, '|');
			var set = new TreeSet<BitSet>(Comparator.comparing(StringUtils::toString));
			for (var part : tokens) {
				set.add(BitSetUtils.asBitSet(StringUtils.parseArrayOfIntegers(part)));
			}
			sequence.add(set);
		}
		return sequence;
	}


	/**
	 * to string method in a format that can be parsed
	 *
	 * @return string, e.g. "1 2; 3; 4 5;  7 | 8; 9;"
	 */
	public String toString() {
		var buf = new StringBuilder();
		for (var sets : array) {
			if (!buf.isEmpty())
				buf.append(" : ");
			buf.append(toString(sets));
		}
		return buf.toString();
	}

	/**
	 * helper for writing out sets
	 */
	public static String toString(Set<BitSet> sets) {
		var buf = new StringBuilder();
		var first = true;
		for (var set : sets) {
			if (first)
				first = false;
			else
				buf.append(" | ");
			buf.append(StringUtils.toString(set));
		}
		return buf.toString();
	}

	/**
	 * flattens to a hyper sequence
	 *
	 * @return hyper sequence
	 */
	public HyperSequence flatten() {
		var sequence = new HyperSequence();
		for (var component : array) {
			sequence.array().add(BitSetUtils.union(component.stream().toList()));
		}
		return sequence;
	}

	public int size() {
		return array.size();
	}

	public Set<BitSet> get(int i) {
		return array.get(i);
	}

	public void set(int i, Set<BitSet> sets) {
		array.set(i, sets);
	}

	public void add(Set<BitSet> sets) {
		array.add(sets);
	}
}
