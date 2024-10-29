/*
 * FixedCapacitySortedSet.java Copyright (C) 2024 Daniel H. Huson
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
 *
 */

package splitstree6.compute.phylofusion;

import java.util.Comparator;
import java.util.TreeSet;

/**
 * a fixed capacity sorted set. If capacity exceeded, then the largest element is removed
 *
 * @param <E>
 */
public class FixedCapacitySortedSet<E> extends TreeSet<E> {
	private int capacity;


	public FixedCapacitySortedSet(int capacity, Comparator<? super E> comparator) {
		super(comparator);
		this.capacity = capacity;
	}

	@Override
	public synchronized boolean add(E e) {
		if (size() >= capacity) {
			E highest = this.last();
			if (comparator() == null) {
				if (((Comparable<E>) e).compareTo(highest) >= 0) {
					return false;
				}
			} else {
				if (comparator().compare(e, highest) >= 0) {
					return false;
				}
			}
			this.pollLast();
		}
		return super.add(e);
	}

	public void changeCapacity(int capacity) {
		if (capacity <= 0)
			throw new IllegalArgumentException();
		if (capacity < this.capacity) {
			while (size() > capacity)
				pollLast();
		}
		this.capacity = capacity;
	}
}
