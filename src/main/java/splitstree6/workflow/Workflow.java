/*
 *  Workflow.java Copyright (C) 2024 Daniel H. Huson
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

import javafx.collections.ListChangeListener;
import jloda.fx.selection.SelectionModel;
import jloda.fx.selection.SetSelectionModel;
import jloda.fx.util.AService;
import jloda.fx.workflow.WorkflowNode;
import splitstree6.algorithms.taxa.taxa2taxa.TaxaFilter;
import splitstree6.data.SourceBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.ViewBlock;
import splitstree6.view.alignment.AlignmentView;
import splitstree6.window.MainWindow;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * splitstree workflow
 * Daniel Huson, 10.2021
 */
public class Workflow extends jloda.fx.workflow.Workflow {
	public static final String INPUT_SOURCE = "Input Source";
	public static final String INPUT_TAXA = "Input Taxa";
	public static final String INPUT_TAXA_FILTER = "Taxa Filter";
	public static final String INPUT_TAXA_DATA_FILTER = "Input Data Filter";
	public static final String WORKING_TAXA = "Working Taxa";
	public static final String INPUT_DATA_LOADER = "Input Data Loader";
	public static final String INPUT_PREFIX = "Input ";
	public static final String WORKING_PREFIX = "Working ";

	private final Map<String, List<String>> dataBlockNameTitleMap = new HashMap<>();
	private final Map<String, List<String>> algorithmNameTitleMap = new HashMap<>();

	private final SelectionModel<WorkflowNode> selectionModel = new SetSelectionModel<>();

	private Consumer<AService<Boolean>> serviceConfigurator;

	private final MainWindow mainWindow;

	public Workflow(MainWindow mainWindow) {
		this.mainWindow = mainWindow;

		nodes().addListener((ListChangeListener<? super WorkflowNode>) e -> {
			while (e.next()) {
				if (e.wasRemoved()) {
					for (var node : e.getRemoved()) {
						selectionModel.clearSelection(node);
						if (node instanceof DataNode dataNode) {
							var names = dataBlockNameTitleMap.get(dataNode.getName());
							if (names != null)
								names.remove(dataNode.getTitle());
						} else if (node instanceof AlgorithmNode algorithmNode) {
							var names = algorithmNameTitleMap.get(algorithmNode.getName());
							if (names != null)
								names.remove(algorithmNode.getTitle());
						}
					}

					if (getNumberOfNodes() == 0) {
						dataBlockNameTitleMap.clear();
						algorithmNameTitleMap.clear();
					}
				}
			}
		});
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

	public <T extends DataBlock> void setupInputAndWorkingNodes(SourceBlock source, TaxaBlock inputTaxaBlock, TaxaFilter taxaFilter, TaxaBlock workingTaxaBlock,
																DataBlock inputDataBlock, DataTaxaFilter dataTaxaFilter, DataBlock workingDataBlock) {
		var sourceNode = newDataNode(source, INPUT_SOURCE); // todo: what is the purpose of the source node?
		var inputTaxaNode = newDataNode(inputTaxaBlock, INPUT_TAXA);
		var inputDataNode = newDataNode(inputDataBlock, INPUT_PREFIX + inputDataBlock.getName());

		// todo: create nexus loader
		/*
		var loaderNode = newAlgorithmNode(dataLoader, null, sourceNode, inputDataNode, INPUT_DATA_LOADER);
		loaderNode.addChild(inputTaxaNode);
		 */

		var workingTaxaNode = newDataNode(workingTaxaBlock, WORKING_TAXA);
		var workingDataNode = newDataNode(workingDataBlock, WORKING_PREFIX + workingDataBlock.getName());
		newAlgorithmNode(taxaFilter, null, inputTaxaNode, workingTaxaNode, INPUT_TAXA_FILTER);

		var dataFilterNode = newAlgorithmNode(dataTaxaFilter, INPUT_TAXA_DATA_FILTER);
		dataFilterNode.addParent(inputTaxaNode);
		dataFilterNode.addParent(workingTaxaNode);
		dataFilterNode.addParent(inputDataNode);
		dataFilterNode.addChild(workingDataNode);
	}

	public <D extends DataBlock> DataNode<D> newDataNode(D dataBlock) {
		return newDataNode(dataBlock, null);
	}

	public <D extends DataBlock> DataNode<D> newDataNode(D dataBlock, String title) {
		var node = new DataNode<D>(this);
		node.setDataBlock(dataBlock);
		dataBlock.setNode(node);

		if (title != null) {
			node.setTitle(title);
			dataBlockNameTitleMap.computeIfAbsent(dataBlock.getName(), n -> new ArrayList<>()).add(title);
		} else {
			updateTitle(node);
		}

		addNode(node);
		return node;
	}

	public <S extends DataBlock, T extends DataBlock> AlgorithmNode<S, T> newAlgorithmNode(Algorithm<S, T> algorithm) {
		return newAlgorithmNode(algorithm, algorithm.getName());
	}

	public <S extends DataBlock, T extends DataBlock> AlgorithmNode<S, T> newAlgorithmNode(Algorithm<S, T> algorithm, String title) {
		var node = new AlgorithmNode<>(this);
		node.setAlgorithm(algorithm);
		if (title != null) {
			node.setTitle(title);
			algorithmNameTitleMap.computeIfAbsent(algorithm.getName(), n -> new ArrayList<>()).add(title);
		} else {
			updateTitle(node);
		}
		addNode(node);
		return (AlgorithmNode<S, T>) node;
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

	public void updateTitle(WorkflowNode node) {
		if (node instanceof AlgorithmNode algorithmNode) {
			var algorithm = algorithmNode.getAlgorithm();
			var title = algorithm.getName();
			var t = 1;
			var list = algorithmNameTitleMap.computeIfAbsent(algorithm.getName(), n -> new ArrayList<>());
			while (list.contains(title)) {
				title = algorithm.getName() + "-" + (++t);
			}
			list.add(title);
			algorithmNode.setTitle(title);
		} else if (node instanceof DataNode dataName) {
			var title = dataName.getName();
			var t = 1;
			var list = algorithmNameTitleMap.computeIfAbsent(dataName.getName(), n -> new ArrayList<>());
			while (list.contains(title)) {
				title = dataName.getName() + "-" + (++t);
			}
			list.add(title);
			dataName.setTitle(title);
		}
	}

	public <S extends DataBlock, T extends DataBlock> Collection<AlgorithmNode<S, T>> getNodes(Class<? extends Algorithm> clazz) {
		return nodeStream().filter(n -> n instanceof AlgorithmNode algorithmNode && algorithmNode.getAlgorithm().getClass().equals(clazz)).map(n -> (AlgorithmNode<S, T>) n).toList();
	}

	public Stream<? extends DataNode> dataNodesStream() {
		return nodeStream().filter(v -> v instanceof DataNode).map(v -> (DataNode) v);
	}

	public Stream<? extends AlgorithmNode> algorithmNodesStream() {
		return nodeStream().filter(v -> v instanceof AlgorithmNode).map(v -> (AlgorithmNode) v);
	}

	public DataNode<SourceBlock> getSourceNode() {
		return dataNodesStream().filter(v -> v.getTitle().equals(INPUT_SOURCE)).findFirst().orElse(null);
	}

	public AlgorithmNode getLoaderNode() {
		return algorithmNodesStream().filter(v -> v.getTitle().equals(INPUT_DATA_LOADER)).findFirst().orElse(null);
	}


	public DataNode<TaxaBlock> getInputTaxaNode() {
		return dataNodesStream().filter(v -> v.getTitle().equals(INPUT_TAXA)).findFirst().orElse(null);
	}

	public DataNode<? extends DataBlock> getInputDataNode() {
		return dataNodesStream().filter(v -> v.getTitle().startsWith(INPUT_PREFIX)).filter(v -> !v.getTitle().equals(INPUT_SOURCE))
				.filter(v -> !v.getTitle().equals(INPUT_TAXA)).findFirst().orElse(null);
	}

	public DataNode<TaxaBlock> getWorkingTaxaNode() {
		return dataNodesStream().filter(v -> v.getTitle().equals(WORKING_TAXA)).findFirst().orElse(null);
	}

	public DataNode<? extends DataBlock> getWorkingDataNode() {
		return dataNodesStream().filter(v -> v.getTitle().startsWith(WORKING_PREFIX)).filter(v -> !v.getTitle().equals(WORKING_TAXA)).findFirst().orElse(null);
	}

	public DataNode<? extends DataBlock> getAlignmentViewNode() {
		return dataNodesStream().filter(v -> v.getDataBlock() instanceof ViewBlock viewBlock && viewBlock.getView() instanceof AlignmentView).findFirst().orElse(null);
	}

	public Object getInputDataBlock() {
		return getInputDataNode() == null ? null : getInputDataNode().getDataBlock();
	}

	public Object getWorkingDataBlock() {
		return getWorkingDataNode() == null ? null : getWorkingDataNode().getDataBlock();
	}

	public AlgorithmNode<TaxaBlock, TaxaBlock> getInputTaxaFilterNode() {
		return algorithmNodesStream().filter(v -> v.getTitle().startsWith(INPUT_TAXA_FILTER)).findFirst().orElse(null);
	}

	public AlgorithmNode<? extends DataBlock, ? extends DataBlock> getInputDataFilterNode() {
		return algorithmNodesStream().filter(v -> v.getAlgorithm() instanceof DataTaxaFilter).findFirst().orElse(null);
	}

	public AlgorithmNode<? extends DataBlock, ? extends DataBlock> getInputDataLoaderNode() {
		return algorithmNodesStream().filter(v -> v.getTitle().startsWith(INPUT_DATA_LOADER)).findFirst().orElse(null);
	}


	public SourceBlock getSourceBlock() {
		var sourceNode = getSourceNode();
		if (sourceNode != null)
			return sourceNode.getDataBlock();
		else
			return null;
	}

	public TaxaBlock getInputTaxaBlock() {
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

	public boolean isInputSourceNode(WorkflowNode v) {
		return v != null && v == getSourceNode();
	}

	public boolean isInputTaxaNode(WorkflowNode v) {
		return v != null && v == getInputTaxaNode();
	}

	public boolean isInputDataNode(WorkflowNode v) {
		return v != null && v == getInputDataNode();
	}

	public boolean isWorkingTaxaNode(WorkflowNode v) {
		return v != null && v == getWorkingTaxaNode();

	}

	public boolean isWorkingDataNode(WorkflowNode v) {
		return v != null && v == getWorkingDataNode();
	}

	public boolean isInputTaxaFilterNode(WorkflowNode v) {
		return v != null && v == getInputTaxaFilterNode();
	}

	public boolean isInputDataLoader(WorkflowNode v) {
		return v != null && v == getInputDataLoaderNode();
	}

	public boolean isInputDataFilter(WorkflowNode v) {
		return v != null && v == getInputDataFilterNode();
	}

	public boolean isDerivedNode(WorkflowNode v) {
		return !isInputSourceNode(v) && !isInputTaxaNode(v) && !isInputDataNode(v) && !isWorkingTaxaNode(v) && !isWorkingDataNode(v)
			   && !isInputTaxaFilterNode(v) && !isInputDataLoader(v) && !isInputDataFilter(v);
	}

	public Consumer<AService<Boolean>> getServiceConfigurator() {
		return serviceConfigurator;
	}

	public void setServiceConfigurator(Consumer<AService<Boolean>> serviceConfigurator) {
		this.serviceConfigurator = serviceConfigurator;
	}

	public SelectionModel<WorkflowNode> getSelectionModel() {
		return selectionModel;
	}

	/**
	 * make a copy that is shallow in the sense that we reference the original datablocks and algorithms, rather than copy them
	 *
	 * @param src source to copy from
	 */
	public void shallowCopy(Workflow src) {
		clear();
		setValid(false);

		var nodeCopyNodeMap = new HashMap<WorkflowNode, WorkflowNode>();

		for (var node : src.nodes()) {
			var nodeCopy = nodeCopyNodeMap.get(node);
			if (nodeCopy == null) {
				if (node instanceof DataNode dataNode) {
					nodeCopyNodeMap.put(node, newDataNode(dataNode.getDataBlock(), dataNode.getTitle()));
				} else if (node instanceof AlgorithmNode algorithmNode) {
					nodeCopyNodeMap.put(node, newAlgorithmNode(algorithmNode.getAlgorithm(), algorithmNode.getTitle()));
				}
			}
		}

		for (var node : src.nodes()) {
			var nodeCopy = nodeCopyNodeMap.get(node);
			for (var parent : node.getParents()) {
				var parentCopy = nodeCopyNodeMap.get(parent);
				if (!nodeCopy.getParents().contains(parentCopy)) {
					nodeCopy.getParents().add(parentCopy);
				}
			}
		}
		setValid(true);
	}

	public MainWindow getMainWindow() {
		return mainWindow;
	}


	/**
	 * finds a data node by title
	 *
	 * @return node by title
	 */
	public DataNode findDataNode(String title) {
		for (var node : dataNodes()) {
			if (node instanceof DataNode dataNode) {
				if (dataNode.getTitle() != null && dataNode.getTitle().equals(title))
					return dataNode;
			}
		}
		// fall back: use type as name:
		for (var node : dataNodes()) {
			if (node instanceof DataNode dataNode) {
				if (dataNode.getName() != null && dataNode.getName().equals(title))
					return dataNode;
			}
		}
		return null;
	}

	public void clearData() {
		if (getInputTaxaBlock() != null)
			getInputTaxaBlock().setComments(null);
		for (var dataNode : dataNodes()) {
			dataNode.getDataBlock().clear();
			dataNode.getDataBlock().updateShortDescription();
		}
	}
}
