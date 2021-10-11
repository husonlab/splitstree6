package splitstree6.sflow;


import splitstree6.data.TaxaBlock;


/**
 * /**
 * a workflow node that contains an algorithm
 * Daniel Huson, 10.2021
 *
 * @param <S> input data
 * @param <T> output data
 */
public class AlgorithmNode<S extends DataBlock, T extends DataBlock> extends splitstree6.wflow.AlgorithmNode {

	public AlgorithmNode(Workflow owner) {
		super(owner);
	}

	public void setAlgorithm(Algorithm<S, T> algorithm) {
		super.setAlgorithm(algorithm);
	}

	public Algorithm getAlgorithm() {
		return (Algorithm) super.getAlgorithm();
	}

	public TaxaBlock getTaxaBlock() {
		for (var parent : getParents()) {
			if (parent instanceof DataNode dataNode && dataNode.getDataBlock() instanceof TaxaBlock taxaBlock) {
				return taxaBlock;
			}
		}
		return null;
	}

	/**
	 * a filter requires a second taxon block
	 *
	 * @return second taxon block, containing a subset of taxa
	 */
	public TaxaBlock getSecondTaxaBlock() {
		var foundFirst = false;
		for (var parent : getParents()) {
			if (parent instanceof DataNode dataNode && dataNode.getDataBlock() instanceof TaxaBlock taxaBlock) {
				if (!foundFirst)
					foundFirst = true;
				else
					return taxaBlock;
			}
		}
		return null;
	}

	public S getSourceBlock() {
		for (var parent : getParents()) {
			if (parent instanceof DataNode dataNode
					&& (!(dataNode.getDataBlock() instanceof TaxaBlock)
					|| (getAlgorithm() != null && super.getAlgorithm() instanceof Algorithm algorithm && algorithm.getFromClass().equals(TaxaBlock.class)))) {
				return (S) dataNode.getDataBlock();
			}
		}
		return null;
	}


	public DataNode<T> getTargetNode() {
		for (var child : getChildren()) {
			if (child instanceof DataNode dataNode
					&& (!(dataNode.getDataBlock() instanceof TaxaBlock)
					|| (getAlgorithm() != null && super.getAlgorithm() instanceof Algorithm algorithm && algorithm.getToClass().equals(TaxaBlock.class)))) {
				return (DataNode<T>) dataNode;
			}
		}
		return null;
	}
}