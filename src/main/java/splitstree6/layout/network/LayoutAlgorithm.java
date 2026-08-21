/*
 * LayoutAlgorithm.java Copyright (C) 2026 Daniel H. Huson
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

package splitstree6.layout.network;

/**
 * which algorithm to lay a network out with
 * <p>
 * The two behave very differently by size. On the networks this is usually pointed at - a haplotype network of
 * a few dozen nodes, close to planar - MDS gives fewer crossings and does it in a fraction of the time. Above
 * roughly 250 sparse nodes that reverses: the force-directed layout produces markedly fewer crossings, but
 * takes seconds where MDS takes milliseconds. Neither can do much with a network far from planar.
 * <p>
 * Daniel Huson, 8.2026
 */
public enum LayoutAlgorithm {
	/** stress majorization on shortest-path distances; the default, and the better choice on small networks */
	MDS,
	/** force-directed (Fruchterman-Reingold); slower, but better on large sparse networks */
	ForceDirected
}
