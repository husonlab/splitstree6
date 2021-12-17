/*
 *  TreesBlock.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.data;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import jloda.phylo.PhyloTree;
import splitstree6.algorithms.trees.trees2trees.TreesTaxaFilter;
import splitstree6.workflow.DataBlock;
import splitstree6.workflow.DataTaxaFilter;

public class TreesBlock extends DataBlock {
	private final ObservableList<PhyloTree> trees;
	private boolean partial = false; // are partial trees present?
	private boolean rooted = false; // are the trees explicitly rooted?
	private boolean network = false;

	private TreesFormat format = new TreesFormat();

	public TreesBlock() {
		trees = FXCollections.observableArrayList();
	}

	/**
	 * shallow copy
	 *
	 * @param that other trees
	 */
	public void copy(TreesBlock that) {
		clear();
		trees.addAll(that.getTrees());
		partial = that.isPartial();
		rooted = that.isRooted();
		network = that.isNetwork();
		format = new TreesFormat();
	}

	/**
	 * next the trees
	 *
	 * @return trees
	 */
	public ObservableList<PhyloTree> getTrees() {
		return trees;
	}

	public int getNTrees() {
		return trees.size();
	}

	public boolean isPartial() {
		return partial;
	}

	public void setPartial(boolean partial) {
		this.partial = partial;
	}

	public boolean isRooted() {
		return rooted;
	}

	public void setRooted(boolean rooted) {
		this.rooted = rooted;
	}

	public boolean isNetwork() {
		return network;
	}

	public void setNetwork(boolean network) {
		this.network = network;
	}

	/**
	 * get t-th tree
	 *
	 * @param t index, 1-based
	 * @return tree
	 */
	public PhyloTree getTree(int t) {
		return trees.get(t - 1);
	}

	@Override
	public DataTaxaFilter<? extends DataBlock, ? extends DataBlock> createTaxaDataFilter() {
		return new TreesTaxaFilter();
	}

	@Override
	public void clear() {
		super.clear();
		trees.clear();
		partial = false;
		rooted = false;
		network=false;
	}

	@Override
	public int size() {
		return trees.size();
	}

	@Override
	public TreesBlock newInstance() {
		return (TreesBlock) super.newInstance();
	}

	public TreesFormat getFormat() {
		return format;
	}

	public void setFormat(TreesFormat format) {
		this.format = format;
	}

	public static final String BLOCK_NAME = "TREES";

	@Override
	public void updateShortDescription() {
		setShortDescription((getNTrees() == 1 ? "one tree" : String.format("%,d trees", getNTrees())) + (isPartial() ? ", partial" : "") + (isNetwork() ? ", network" : ""));
	}

	@Override
	public String getBlockName() {
		return BLOCK_NAME;
	}

}
