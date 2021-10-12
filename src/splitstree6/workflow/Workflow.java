/*
 *  Workflow.java Copyright (C) 2021 Daniel H. Huson
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

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import splitstree6.algorithms.taxa.taxa2taxa.TaxaFilter;
import splitstree6.data.SourceBlock;
import splitstree6.data.TaxaBlock;

import java.util.Collection;

/**
 * splitstree workflow
 * Daniel Huson, 10.2021
 */
public class Workflow extends jloda.fx.workflow.Workflow {
	private final SimpleObjectProperty<DataNode<SourceBlock>> sourceNode = new SimpleObjectProperty<>();
	private final SimpleObjectProperty<AlgorithmNode<SourceBlock, ? extends DataBlock>> loaderNode = new SimpleObjectProperty<>();

	private final SimpleObjectProperty<DataNode<TaxaBlock>> inputTaxaNode = new SimpleObjectProperty<>();
	private final SimpleObjectProperty<DataNode<TaxaBlock>> workingTaxaNode = new SimpleObjectProperty<>();
	private final SimpleObjectProperty<AlgorithmNode<TaxaBlock, TaxaBlock>> taxaFilterNode = new SimpleObjectProperty<>();

	private final SimpleObjectProperty<DataNode<? extends DataBlock>> inputDataNode = new SimpleObjectProperty<>();
	private final SimpleObjectProperty<DataNode<? extends DataBlock>> workingDataNode = new SimpleObjectProperty<>();
	private final SimpleObjectProperty<AlgorithmNode<? extends DataBlock, ? extends DataBlock>> taxaDataFilterNode = new SimpleObjectProperty<>();

	public <T extends DataBlock> void setupInputAndWorkingNodes(SourceBlock source, DataLoader<SourceBlock, T> dataLoader, TaxaBlock topTaxaBlock, T topDataBlock) {
		setSourceNode(newDataNode(source));
		setInputTaxaNode(newDataNode(topTaxaBlock));
		setInputDataNode(newDataNode(topDataBlock));

		setLoaderNode(newAlgorithmNode(dataLoader, null, getSourceNode(), getInputDataNode()));
		getLoaderNode().addChild(getInputTaxaNode());

		setWorkingTaxaNode(newDataNode(topTaxaBlock.newInstance()));
		setWorkingDataNode(newDataNode(topDataBlock.newInstance()));
		setTaxaFilterNode(newAlgorithmNode(new TaxaFilter(), null, getInputTaxaNode(), getWorkingTaxaNode()));

		var dataFilterNode = newAlgorithmNode(topDataBlock.createTaxaDataFilter());
		dataFilterNode.addParent(getInputTaxaNode());
		dataFilterNode.addParent(getWorkingTaxaNode());
		dataFilterNode.addParent(getInputDataNode());
		dataFilterNode.addChild(getWorkingDataNode());
		setTaxaDataFilterNode(dataFilterNode);
	}

	public <D extends DataBlock> DataNode<D> newDataNode(D dataBlock) {
		var v = new DataNode<D>(this);
		v.setDataBlock(dataBlock);
		addDataNode(v);
		return v;
	}

	public AlgorithmNode<DataBlock, DataBlock> newAlgorithmNode() {
		var v = new AlgorithmNode<>(this);
		addAlgorithmNode(v);
		return v;
	}

	public <S extends DataBlock, T extends DataBlock> AlgorithmNode<S, T> newAlgorithmNode(Algorithm<S, T> algorithm) {
		var v = new AlgorithmNode<>(this);
		v.setAlgorithm(algorithm);
		addAlgorithmNode(v);
		return (AlgorithmNode<S, T>) v;
	}

	public AlgorithmNode newAlgorithmNode(Algorithm algorithm, DataNode<TaxaBlock> taxa, DataNode<? extends DataBlock> inputData, DataNode<? extends DataBlock> outputData) {
		if (inputData != null && !algorithm.getFromClass().isAssignableFrom(inputData.getDataBlock().getClass()))
			throw new IllegalArgumentException("newAlgorithmNode(): algorithm and inputData mismatch");
		if (outputData != null && !algorithm.getToClass().isAssignableFrom(outputData.getDataBlock().getClass()))
			throw new IllegalArgumentException("newAlgorithmNode(): algorithm and outputData mismatch");

		var v = newAlgorithmNode(algorithm);
		if (taxa != null)
			v.addParent(taxa);
		if (inputData != null)
			v.addParent(inputData);
		if (outputData != null)
			v.addChild(outputData);
		return v;
	}

	public DataNode<SourceBlock> getSourceNode() {
		return sourceNode.get();
	}

	public SourceBlock getSourceBlock() {
		return getSourceNode().getDataBlock();
	}

	public ReadOnlyObjectProperty<DataNode<SourceBlock>> sourceNodeProperty() {
		return sourceNode;
	}

	public void setSourceNode(DataNode<SourceBlock> sourceNode) {
		this.sourceNode.set(sourceNode);
	}

	public AlgorithmNode getLoaderNode() {
		return loaderNode.get();
	}

	public ReadOnlyObjectProperty<AlgorithmNode<SourceBlock, ? extends DataBlock>> loaderNodeProperty() {
		return loaderNode;
	}

	public void setLoaderNode(AlgorithmNode loaderNode) {
		this.loaderNode.set(loaderNode);
	}

	public DataNode<TaxaBlock> getInputTaxaNode() {
		return inputTaxaNode.get();
	}

	public ReadOnlyObjectProperty<DataNode<TaxaBlock>> inputTaxaNodeProperty() {
		return inputTaxaNode;
	}

	public void setInputTaxaNode(DataNode<TaxaBlock> node) {
		this.inputTaxaNode.set(node);
	}

	public DataNode<? extends DataBlock> getInputDataNode() {
		return inputDataNode.get();
	}

	public ReadOnlyObjectProperty<DataNode<? extends DataBlock>> inputDataNodeProperty() {
		return inputDataNode;
	}

	public void setInputDataNode(DataNode<? extends DataBlock> node) {
		this.inputDataNode.set(node);
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

	public TaxaBlock getInputTaxonBlock() {
		if (getInputTaxaNode() != null)
			return getInputTaxaNode().getDataBlock();
		else
			return null;
	}

	public TaxaBlock getWorkingTaxaBlock() {
		if (getWorkingTaxaNode() != null)
			return getWorkingTaxaNode().getDataBlock();
		else
			return null;
	}

	public DataBlock getInputDataBlock() {
		if (getInputDataNode() != null)
			return getInputDataNode().getDataBlock();
		else
			return null;
	}


	public DataBlock getWorkingDataBlock() {
		if (getWorkingDataNode() != null)
			return getWorkingDataNode().getDataBlock();
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
