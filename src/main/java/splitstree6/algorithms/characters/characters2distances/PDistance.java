/*
 * PDistance.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.algorithms.characters.characters2distances;


/**
 * P-Distance is just another name for normalized Hamming distances
 * Daniel Huson, 2006
 */
public class PDistance extends HammingDistance {
	public PDistance() {
		super();
		setOptionNormalize(true);
	}

	@Override
	public String getShortDescription() {
		return "Computes the normalized Hamming distance.";
	}
}
