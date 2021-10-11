package splitstree6.data;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import jloda.phylo.PhyloTree;
import splitstree6.algorithms.trees.trees2trees.TreesTopFilter;
import splitstree6.sflow.DataBlock;
import splitstree6.sflow.TopFilter;

public class TreesBlock extends DataBlock {
	private final ObservableList<PhyloTree> trees;
	private boolean partial = false; // are partial trees present?
	private boolean rooted = false; // are the trees explicitly rooted?

	public TreesBlock() {
		trees = FXCollections.observableArrayList();
		trees.addListener((ListChangeListener<? super PhyloTree>) c -> setShortDescription(getInfo()));
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

	/**
	 * get t-th tree, starting with 1
	 *
	 * @param t index
	 * @return tree
	 */
	public PhyloTree getTree(int t) {
		return trees.get(t - 1);
	}

	@Override
	public TopFilter<? extends DataBlock, ? extends DataBlock> createTaxaDataFilter() {
		return new TreesTopFilter(TreesBlock.class, TreesBlock.class);
	}

	@Override
	public void clear() {
		super.clear();
		trees.clear();
		partial = false;
		rooted = false;
	}

	@Override
	public int size() {
		return trees.size();
	}

	@Override
	public String getShortDescription() {
		return "Number of trees: " + getTrees().size();
	}


	@Override
	public String getInfo() {
		return (getNTrees() == 1 ? "one tree" : getNTrees() + " trees") + (isPartial() ? ", partial" : "");
	}
}
