/*
 *  ValuesList.java Copyright (C) 2024 Daniel H. Huson
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
package splitstree6.autumn;

import java.util.LinkedList;


/**
 * list of values
 * Daniel Huson, 7.2011
 */
public class ValuesList extends LinkedList<Value> {
	/**
	 * make a copy that also contains the given value
	 *
	 * @return copy with value added
	 */
	public ValuesList copyWithAdditionalElement(Value value) {
		ValuesList copy = new ValuesList();
		copy.addAll(this);
		copy.add(value);
		return copy;
	}

	/**
	 * gets the total value
	 *
	 * @return total
	 */
	public int sum() {
		var total = 0;
		for (var value : this) {
			total += value.get();
		}
		return total;
	}
}
