/*
 * IntArray.java Copyright (C) 2021. Daniel H. Huson
 *
 * (Some code written by other authors, as named in code.)
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
 *
 */

package splitstree6.viewers.multitreesviewer.tree_try;

import java.util.Arrays;

/**
 * dynamic int array
 * Daniel Huson, 10.2017
 */
public class IntArray {
	private int[] array;

	public IntArray() {
		this(1024);
	}

	public IntArray(int initialCapacity) {
		array = new int[initialCapacity];
	}

	public void clear() {
		Arrays.fill(array, 0);
	}

	public int get(int index) {
		if (index >= array.length)
			return 0;
		return array[index];
	}

	public void set(int index, int value) {
		if (index >= array.length) {
			var tmp = new int[2 * index];
			System.arraycopy(array, 0, tmp, 0, array.length);
			array = tmp;
		}
		array[index] = value;
	}
}
