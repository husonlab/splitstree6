/*
 *  NetNode.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.algorithms.distances.distances2splits.neighbornet;

import java.util.Objects;

public class NetNode {
	int id = 0;
	NetNode nbr = null; // adjacent node
	NetNode ch1 = null; // first child
	NetNode ch2 = null; // second child
	NetNode next = null; // next in list of active nodes
	NetNode prev = null; // prev in list of active nodes
	double Rx = 0;
	double Sx = 0;

	public String toString() {
		String str = "[id=" + id;
		str += " nbr=" + (nbr == null ? "null" : ("" + nbr.id));
		str += " ch1=" + (ch1 == null ? "null" : ("" + ch1.id));
		str += " ch2=" + (ch2 == null ? "null" : ("" + ch2.id));
		str += " prev=" + (prev == null ? "null" : ("" + prev.id));
		str += " next=" + (next == null ? "null" : ("" + next.id));
		str += " Rx=" + Rx;
		str += " Sx=" + Sx;
		str += "]";
		return str;
	}


	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		NetNode netNode = (NetNode) o;
		return id == netNode.id;
	}

	@Override
	public int hashCode() {
		return Objects.hash(id);
	}
}
