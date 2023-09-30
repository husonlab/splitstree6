/*
 * DataBlock.java Copyright (C) 2023 Daniel H. Huson
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

	public String getCitation() {
		return null;
	}
}
