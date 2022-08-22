/*
 * Model.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.xtra.densitree_old;

import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;

@Deprecated
public class Model {
	private final TaxaBlock taxaBlock = new TaxaBlock();
	private final TreesBlock treesBlock = new TreesBlock();
	private int[] circularOrdering;

	public TaxaBlock getTaxaBlock() {
		return taxaBlock;
	}

	public TreesBlock getTreesBlock() {
		return treesBlock;
	}

	public int[] getCircularOrdering() {
		return circularOrdering;
	}

	public void clear() {
		taxaBlock.clear();
		treesBlock.clear();
		circularOrdering = new int[0];
	}

	public void setCircularOrdering(int[] circularOrdering) {
		this.circularOrdering = circularOrdering;
	}
}
