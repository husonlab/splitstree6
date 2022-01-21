/*
 * NetworkComparator.java Copyright (C) 2022 Daniel H. Huson
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
package splitstree6.autumn.hybridnetwork;

import splitstree6.autumn.Root;

import java.util.Comparator;

/**
 * comparator for networks
 * Daniel Huson, 10.2011
 */

public class NetworkComparator implements Comparator<Root> {
	public int compare(Root root1, Root root2) {
		var string1 = root1.toStringNetwork();
		var string2 = root2.toStringNetwork();
		return string1.compareTo(string2);
	}
}
