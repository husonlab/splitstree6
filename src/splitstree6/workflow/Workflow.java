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

import jloda.fx.util.AService;
import jloda.fx.workflow.WorkflowNode;
import splitstree6.algorithms.taxa.taxa2taxa.TaxaFilter;
import splitstree6.data.SourceBlock;
import splitstree6.data.TaxaBlock;

import java.util.Collection;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * splitstree workflow
 * Daniel Huson, 10.2021
 */
public class Workflow extends jloda.fx.workflow.Workflow {
	public static final String INPUT_SOURCE = "Input Source";
	public static final String INPUT_TAXA = "Input Taxa";
	public static final String INPUT_TAXA_FILTER = "Input Taxa Filter";
	public static final String INPUT_TAXA_DATA_FILTER = "Input Data Filter";
	public static final String WORKING_TAXA = "Working Taxa";
	public static final String INPUT_DATA_LOADER = "Input Data Loader";
	public static final String INPUT_PREFIX = "Input ";
	public static final String WORKING_PREFIX = "Working ";

	private Consumer<AService<Boolean>> serviceConfigurator;

	public Workflow() {
	}

	public <T extends DataBlock> void setupInputAndWorkingNodes(SourceBlock source, DataLoader<SourceBlock, T> dataLoader, TaxaBlock inputTaxaBlock, T inputDataBlock) {
		var sourceNode = newDataNode(source, INPUT_SOURCE);
		var inputTaxaNode = newDataNode(inputTaxaBlock, INPUT_TAXA);
		var inputDataNode = newDataNode(inputDataBlock, INPUT_PREFIX + inputDataBlock.getName());

		var loaderNode = newAlgorithmNode(dataLoader, null, sourceNode, inputDataNode, INPUT_DATA_LOADER);
		loaderNode.addChild(inputTaxaNode);

		var workingTaxaNode = newDataNode(inputTaxaBlock.newInstance(), WORKING_TAXA);
		var workingData = inputDataBlock.newInstance();
		var workingDataNode = newDataNode(inputDataBlock.newInstance(), WORKING_PREFIX + workingData.getName());
		newAlgorithmNode(new TaxaFilter(), null, inputTaxaNode, workingTaxaNode, INPUT_TAXA_FILTER);

		var dataFilterNode = newAlgorithmNode(inputDataBlock.createTaxaDataFilter(), INPUT_TAXA_DATA_FILTER);
		dataFilterNode.addParent(inputTaxaNode);
		dataFilterNode.addParent(workingTaxaNode);
		dataFilterNode.addParent(inputDataNode);
		dataFilterNode.addChild(workingDataNode);
	}

	public <D extends DataBlock> DataNode<D> newDataNode(D dataBlock) {
		return newDataNode(dataBlock, null);
	}

	public <D extends DataBlock> DataNode<D> newDataNode(D dataBlock, String name) {
		var v = new DataNode<D>(this);
		v.setDataBlock(dataBlock);
		if (name != null)
			v.setName(name);
		addNode(v);
		return v;
	}

	public <S extends DataBlock, T extends DataBlock> AlgorithmNode<S, T> newAlgorithmNode(Algorithm<S, T> algorithm) {
		var v = new AlgorithmNode<>(this);
		v.setAlgorithm(algorithm);
		addNode(v);
		return (AlgorithmNode<S, T>) v;
	}

	public <S extends DataBlock, T extends DataBlock> AlgorithmNode<S, T> newAlgorithmNode(Algorithm<S, T> algorithm, String name) {
		var v = new AlgorithmNode<>(this);
		v.setAlgorithm(algorithm);
		if (name != null)
			algorithm.setName(name);
		addNode(v);
		return (AlgorithmNode<S, T>) v;
	}

	public AlgorithmNode newAlgorithmNode(Algorithm algorithm, DataNode<TaxaBlock> taxa, DataNode<? extends DataBlock> inputData, DataNode<? extends DataBlock> outputData) {
		return newAlgorithmNode(algorithm, taxa, inputData, outputData, null);
	}

	public AlgorithmNode newAlgorithmNode(Algorithm algorithm, DataNode<TaxaBlock> taxa, DataNode<? extends DataBlock> inputData, DataNode<? extends DataBlock> outputData, String name) {
		if (inputData != null && !algorithm.getFromClass().isAssignableFrom(inputData.getDataBlock().getClass()))
			throw new IllegalArgumentException("newAlgorithmNode(): algorithm and inputData mismatch");
		if (outputData != null && !algorithm.getToClass().isAssignableFrom(outputData.getDataBlock().getClass()))
			throw new IllegalArgumentException("newAlgorithmNode(): algorithm and outputData mismatch");

		var v = newAlgorithmNode(algorithm, name);
		if (taxa != null)
			v.addParent(taxa);
		if (inputData != null)
			v.addParent(inputData);
		if (outputData != null)
			v.addChild(outputData);
		return v;
	}

	public DataNode<SourceBlock> getSourceNode() {
		return dataNodesStream().filter(v -> v.getName().equals(INPUT_SOURCE)).findFirst().orElse(null);
	}

	public AlgorithmNode getLoaderNode() {
		return algorithmNodesStream().filter(v -> v.getName().equals(INPUT_DATA_LOADER)).findFirst().orElse(null);
	}


	public DataNode<TaxaBlock> getInputTaxaNode() {
		return dataNodesStream().filter(v -> v.getName().equals(INPUT_TAXA)).findFirst().orElse(null);
	}


	public DataNode<? extends DataBlock> getInputDataNode() {
		return dataNodesStream().filter(v -> v.getName().startsWith(INPUT_PREFIX)).filter(v -> !v.getName().equals(INPUT_SOURCE))
				.filter(v -> !v.getName().equals(INPUT_TAXA)).findFirst().orElse(null);
	}

	public DataNode<TaxaBlock> getWorkingTaxaNode() {
		return dataNodesStream().filter(v -> v.getName().equals(WORKING_TAXA)).findFirst().orElse(null);
	}

	public DataNode<? extends DataBlock> getWorkingDataNode() {
		return dataNodesStream().filter(v -> v.getName().startsWith(WORKING_PREFIX)).filter(v -> !v.getName().equals(WORKING_TAXA)).findFirst().orElse(null);

	}

	public AlgorithmNode<TaxaBlock, TaxaBlock> getInputTaxaFilterNode() {
		return algorithmNodesStream().filter(v -> v.getName().startsWith(INPUT_TAXA_FILTER)).findFirst().orElse(null);
	}

	public AlgorithmNode<? extends DataBlock, ? extends DataBlock> getInputDataFilterNode() {
		return algorithmNodesStream().filter(v -> v.getName().startsWith(INPUT_TAXA_DATA_FILTER)).findFirst().orElse(null);
	}

	public SourceBlock getSourceBlock() {
		var sourceNode = getSourceNode();
		if (sourceNode != null)
			return sourceNode.getDataBlock();
		else
			return null;
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

	public <S extends DataBlock, T extends DataBlock> Collection<AlgorithmNode<S, T>> getNodes(Class<? extends Algorithm<S, T>> clazz) {
		return nodeStream().filter(n -> n instanceof AlgorithmNode algorithmNode && algorithmNode.getAlgorithm().getClass().equals(clazz)).map(n -> (AlgorithmNode<S, T>) n).toList();
	}

	public Stream<? extends DataNode> dataNodesStream() {
		return nodeStream().filter(v -> v instanceof DataNode).map(v -> (DataNode) v);
	}

	public Stream<? extends AlgorithmNode> algorithmNodesStream() {
		return nodeStream().filter(v -> v instanceof AlgorithmNode).map(v -> (AlgorithmNode) v);
	}

	public boolean isInputSourceNode(WorkflowNode v) {
		return v instanceof DataNode dataNode && dataNode.getName().equals(INPUT_SOURCE);
	}

	public boolean isInputTaxaNode(WorkflowNode v) {
		return v instanceof DataNode dataNode && dataNode.getName().equals(INPUT_TAXA);
	}

	public boolean isInputDataNode(WorkflowNode v) {
		return v instanceof DataNode dataNode && dataNode.getName().startsWith(INPUT_PREFIX) && !isInputTaxaNode(v);
	}

	public boolean isWorkingTaxaNode(WorkflowNode v) {
		return v instanceof DataNode dataNode && dataNode.getName().equals(WORKING_TAXA);

	}

	public boolean isWorkingDataNode(WorkflowNode v) {
		return v instanceof DataNode dataNode && dataNode.getName().startsWith(WORKING_PREFIX) && !isWorkingTaxaNode(v);
	}

	public boolean isInputTaxaFilter(WorkflowNode v) {
		return v instanceof AlgorithmNode algorithmNode && algorithmNode.getName().equals(INPUT_TAXA_FILTER);
	}

	public boolean isInputDataLoader(WorkflowNode v) {
		return v instanceof AlgorithmNode algorithmNode && algorithmNode.getName().equals(INPUT_DATA_LOADER);
	}

	public boolean isInputTaxaDataFilter(WorkflowNode v) {
		return v instanceof AlgorithmNode algorithmNode && algorithmNode.getName().equals(INPUT_TAXA_DATA_FILTER);
	}

	public boolean isDerivedNode(WorkflowNode v) {
		return !isInputSourceNode(v) && !isInputTaxaNode(v) && !isInputDataNode(v) && !isWorkingTaxaNode(v) && !isWorkingDataNode(v)
			   && !isInputTaxaFilter(v) && !isInputDataLoader(v) && !isInputTaxaDataFilter(v);
	}

	public Consumer<AService<Boolean>> getServiceConfigurator() {
		return serviceConfigurator;
	}

	public void setServiceConfigurator(Consumer<AService<Boolean>> serviceConfigurator) {
		this.serviceConfigurator = serviceConfigurator;
	}
}
