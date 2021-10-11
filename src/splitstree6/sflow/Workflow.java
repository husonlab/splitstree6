package splitstree6.sflow;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import splitstree6.algorithms.taxa.taxa2taxa.TaxaFilter;
import splitstree6.data.SourceBlock;
import splitstree6.data.TaxaBlock;

import java.util.Collection;

public class Workflow extends splitstree6.wflow.Workflow {
	private final SimpleObjectProperty<DataNode<SourceBlock>> sourceNode = new SimpleObjectProperty<>();
	private final SimpleObjectProperty<AlgorithmNode<SourceBlock, ? extends DataBlock>> loaderNode = new SimpleObjectProperty<>();

	private final SimpleObjectProperty<DataNode<TaxaBlock>> topTaxaNode = new SimpleObjectProperty<>();
	private final SimpleObjectProperty<DataNode<TaxaBlock>> workingTaxaNode = new SimpleObjectProperty<>();
	private final SimpleObjectProperty<AlgorithmNode<TaxaBlock, TaxaBlock>> taxaFilterNode = new SimpleObjectProperty<>();

	private final SimpleObjectProperty<DataNode<? extends DataBlock>> topDataNode = new SimpleObjectProperty<>();
	private final SimpleObjectProperty<DataNode<? extends DataBlock>> workingDataNode = new SimpleObjectProperty<>();
	private final SimpleObjectProperty<AlgorithmNode<? extends DataBlock, ? extends DataBlock>> taxaDataFilterNode = new SimpleObjectProperty<>();


	public <T extends DataBlock> void setupTopAndWorkingNodes(SourceBlock source, Loader<SourceBlock, T> loader, TaxaBlock topTaxaBlock, T topDataBlock) {
		setSourceNode((DataNode) newDataNode(source));
		setTopTaxaNode((DataNode) newDataNode(topTaxaBlock));
		setTopDataNode(newDataNode(topDataBlock));

		setLoaderNode(newAlgorithmNode(loader, null, getSourceNode(), getTopDataNode()));
		getLoaderNode().addChild(getTopTaxaNode());

		setWorkingTaxaNode((DataNode) newDataNode(topTaxaBlock.newInstance()));
		setWorkingDataNode(newDataNode(topDataBlock.newInstance()));
		setTaxaFilterNode((AlgorithmNode) newAlgorithmNode(new TaxaFilter(), null, getTopTaxaNode(), getWorkingTaxaNode()));

		var dataFilterNode = (AlgorithmNode<T, T>) newAlgorithmNode(topDataBlock.createTaxaDataFilter());
		dataFilterNode.getParents().add(getTopTaxaNode());
		dataFilterNode.getParents().add(getWorkingTaxaNode());
		dataFilterNode.getParents().add(getTopDataNode());
		dataFilterNode.getChildren().add(getWorkingDataNode());
		setTaxaDataFilterNode(dataFilterNode);
	}

	public DataNode<DataBlock> newDataNode(DataBlock dataBlock) {
		var v = new DataNode<>(this);
		v.setDataBlock(dataBlock);
		nodes().add(v);
		return v;
	}

	public AlgorithmNode<DataBlock, DataBlock> newAlgorithmNode() {
		var v = new AlgorithmNode<>(this);
		nodes().add(v);
		return v;
	}

	public <S extends DataBlock, T extends DataBlock> AlgorithmNode<S, T> newAlgorithmNode(Algorithm<S, T> algorithm) {
		var v = new AlgorithmNode<>(this);
		v.setAlgorithm(algorithm);
		nodes().add(v);
		return (AlgorithmNode<S, T>) v;
	}

	public AlgorithmNode<DataBlock, DataBlock> newAlgorithmNode(Algorithm<? extends DataBlock, ? extends DataBlock> algorithm, DataNode<TaxaBlock> taxa, DataNode<? extends DataBlock> inputData, DataNode<? extends DataBlock> outputData) {
		var v = new AlgorithmNode<>(this);
		v.setAlgorithm(algorithm);
		if (taxa != null)
			v.getParents().add(taxa);
		if (inputData != null)
			v.getParents().add(inputData);
		if (outputData != null)
			v.getChildren().add(outputData);
		nodes().add(v);
		return v;
	}

	public DataNode<SourceBlock> getSourceNode() {
		return sourceNode.get();
	}

	public SourceBlock getSourceBlock() {
		return (SourceBlock) getSourceNode().getDataBlock();
	}

	public SimpleObjectProperty<DataNode<SourceBlock>> sourceNodeProperty() {
		return sourceNode;
	}

	public void setSourceNode(DataNode<SourceBlock> sourceNode) {
		this.sourceNode.set(sourceNode);
	}

	public AlgorithmNode getLoaderNode() {
		return loaderNode.get();
	}

	public SimpleObjectProperty<AlgorithmNode<SourceBlock, ? extends DataBlock>> loaderNodeProperty() {
		return loaderNode;
	}

	public void setLoaderNode(AlgorithmNode loaderNode) {
		this.loaderNode.set(loaderNode);
	}

	public DataNode<TaxaBlock> getTopTaxaNode() {
		return topTaxaNode.get();
	}

	public ReadOnlyObjectProperty<DataNode<TaxaBlock>> topTaxaNodeProperty() {
		return topTaxaNode;
	}

	public void setTopTaxaNode(DataNode<TaxaBlock> node) {
		this.topTaxaNode.set(node);
	}

	public DataNode<? extends DataBlock> getTopDataNode() {
		return topDataNode.get();
	}

	public ReadOnlyObjectProperty<DataNode<? extends DataBlock>> topDataNodeProperty() {
		return topDataNode;
	}

	public void setTopDataNode(DataNode<? extends DataBlock> node) {
		this.topDataNode.set(node);
	}

	public DataNode<TaxaBlock> getWorkingTaxaNode() {
		return workingTaxaNode.get();
	}

	public ReadOnlyObjectProperty<DataNode<TaxaBlock>> workingTaxaNodeProperty() {
		return workingTaxaNode;
	}

	public void setWorkingTaxaNode(DataNode<TaxaBlock> node) {
		this.workingTaxaNode.set(node);
	}

	public DataNode<? extends DataBlock> getWorkingDataNode() {
		return workingDataNode.get();
	}

	public ReadOnlyObjectProperty<DataNode<? extends DataBlock>> workingDataNodeProperty() {
		return workingDataNode;
	}

	public void setWorkingDataNode(DataNode<? extends DataBlock> node) {
		this.workingDataNode.set(node);
	}

	public AlgorithmNode<TaxaBlock, TaxaBlock> getTaxaFilterNode() {
		return taxaFilterNode.get();
	}

	public ReadOnlyObjectProperty<AlgorithmNode<TaxaBlock, TaxaBlock>> taxaFilterNodeProperty() {
		return taxaFilterNode;
	}

	public void setTaxaFilterNode(AlgorithmNode<TaxaBlock, TaxaBlock> node) {
		this.taxaFilterNode.set(node);
	}

	public AlgorithmNode<? extends DataBlock, ? extends DataBlock> getTaxaDataFilterNode() {
		return taxaDataFilterNode.get();
	}

	public ReadOnlyObjectProperty<AlgorithmNode<? extends DataBlock, ? extends DataBlock>> taxaDataFilterNodeProperty() {
		return taxaDataFilterNode;
	}

	public void setTaxaDataFilterNode(AlgorithmNode<? extends DataBlock, ? extends DataBlock> node) {
		this.taxaDataFilterNode.set(node);
	}

	public TaxaBlock getTopTaxonBlock() {
		if (getTopTaxaNode() != null)
			return (TaxaBlock) getTopTaxaNode().getDataBlock();
		else
			return null;
	}

	public TaxaBlock getWorkingTaxaBlock() {
		if (getWorkingTaxaNode() != null)
			return (TaxaBlock) getWorkingTaxaNode().getDataBlock();
		else
			return null;
	}

	public DataBlock getTopDataBlock() {
		if (getTopDataNode() != null)
			return (DataBlock) getTopDataNode().getDataBlock();
		else
			return null;
	}


	public DataBlock getWorkingDataBlock() {
		if (getWorkingDataNode() != null)
			return (DataBlock) getWorkingDataNode().getDataBlock();
		else
			return null;
	}

	public TaxaFilter getTaxaFilter() {
		if (getTaxaFilterNode() != null)
			return (TaxaFilter) getTaxaFilterNode().getAlgorithm();
		else
			return null;
	}

	public <S extends DataBlock, T extends DataBlock> Collection<AlgorithmNode<S, T>> getNodes(Class<? extends Algorithm<S, T>> clazz) {
		return nodeStream().filter(n -> n instanceof AlgorithmNode algorithmNode && algorithmNode.getAlgorithm().getClass().equals(clazz)).map(n -> (AlgorithmNode<S, T>) n).toList();
	}
}
