/*
 *  DataBlock.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.workflow;

import jloda.util.Basic;
import splitstree6.cite.IHasCitations;

/**
 * splitstree data block
 * Daniel Huson, 10.2021
 */
public abstract class DataBlock extends jloda.fx.workflow.DataBlock implements IHasCitations {

	private DataNode node;

	public DataBlock() {
		setName(getClass().getSimpleName().replaceAll("Block$", ""));
	}

	public void clear() {
	}

	public abstract int size();

	public abstract DataTaxaFilter<? extends DataBlock, ? extends DataBlock> createTaxaDataFilter();

	/**
	 * creates a new instance
	 *
	 * @return new instance
	 */
	public DataBlock newInstance() {
		return newInstance(getClass());
	}

	/**
	 * creates a new instance
	 *
	 * @return new instance
	 */
	public static DataBlock newInstance(Class<? extends DataBlock> clazz) {
		try {
			return clazz.getConstructor().newInstance();
		} catch (Exception e) {
			Basic.caught(e);
			return null;
		}
	}

	public abstract String getBlockName();

	public DataNode getNode() {
		return node;
	}

	public void setNode(DataNode node) {
		this.node = node;
	}

	/**
	 * finds the data of the given type that this block was computed from, by walking back up the workflow
	 * <p>
	 * One step up is data node -&gt; the algorithm node that produced it -&gt; that algorithm's source data
	 * node. This takes that step repeatedly, but only across algorithms that did not change the type of
	 * the data, i.e. it looks through any number of same-type filters: a network that has passed through
	 * a network-to-network filter such as the stretch filter still finds the characters or distances it
	 * was ultimately computed from. The walk stops at the first ancestor of any other type, so this never
	 * reaches past an intervening conversion and reports data the block is only indirectly derived from
	 * (a network computed from distances that were computed from characters has no characters ancestor
	 * here). Use this rather than two hard-coded hops whenever a same-type filter may sit in between.
	 *
	 * @param clazz the type of block to look for
	 * @return that data, or null if this block has no workflow node or no such ancestor
	 */
	public <T extends DataBlock> T findAncestor(Class<T> clazz) {
		var dataNode = getNode();
		for (var count = 0; dataNode != null && count < 1000; count++) { // count: paranoia, a workflow is acyclic
			var algorithmNode = dataNode.getPreferredParent();
			if (algorithmNode == null)
				return null;
			dataNode = algorithmNode.getPreferredParent();
			if (dataNode == null)
				return null;
			var dataBlock = dataNode.getDataBlock();
			if (clazz.isInstance(dataBlock))
				return clazz.cast(dataBlock);
			if (!getClass().isInstance(dataBlock))
				return null; // a conversion, not a same-type filter: stop rather than look past it
		}
		return null;
	}

	public String getCitation() {
		return null;
	}
}
