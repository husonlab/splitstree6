/*
 * SparseTable.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.algorithms.utils;

import jloda.util.Pair;

import java.util.Collection;
import java.util.HashMap;
import java.util.Set;

/**
 * SparseArray
 * <p/>
 * Implementation of a sparse 2D array of doubles ints or strings. Uses a hashmap
 * instead of an array.
 *
 * @author bryant
 */
public class SparseTable<V> {

	private final HashMap<Key, V> map;

	/**
	 * Create empty array.
	 */
	public SparseTable() {
		map = new HashMap<>();
	}

	/**
	 * Sets entry (i,j) to the given object. If there is already an entry, it is replaced.
	 *
	 * @param i     row
	 * @param j     column
	 * @param value to go in the array
	 */
	public void set(int i, int j, V value) {
		map.put(new Key(i, j), value);
	}

	/**
	 * erase
	 * <p/>
	 * DeleteCommand all entries
	 */
	public void clear() {
		map.clear();
	}

	/**
	 * Gets the entry at position i,j
	 *
	 * @param i int index
	 * @param j int index
	 * @return Object
	 */
	public V get(int i, int j) {
		return map.get(new Key(i, j));
	}

	/**
	 * Gets the entry for a given key
	 *
	 * @param key a key
	 * @return Object
	 */
	public V get(Key key) {
		return map.get(key);
	}

	/**
	 * Clear a single entry
	 *
	 * @param i int index
	 * @param j int index
	 */
	public void clear(int i, int j) {
		map.remove(new Key(i, j));
	}

	/**
	 * Check whether there is an entry in this position
	 *
	 * @param i int index
	 * @param j int index
	 * @return boolean - true if there is an entry in this position.
	 */
	public boolean hasEntry(int i, int j) {
		return map.containsKey(new Key(i, j));
	}

	public Set<Key> keyset() {
		return map.keySet();
	}

	public Collection<V> values() {
		return map.values();
	}

	public static class Key extends Pair<Integer, Integer> {
        public Key(int i, int j) {
            super(i, j);
        }

        public Integer getFirst() {
            return super.getFirst();
        }

        public Integer getSecond() {
            return super.getSecond();
		}

		@Override
		public int hashCode() {
			return super.hashCode();
		}
	}
}
