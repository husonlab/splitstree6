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
import java.util.function.Supplier;
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

	// The distinguished nodes of the skeleton, held by reference rather than looked up by title. The titles
	// above are still what a workflow file and the GUI show, but the user may rename a node, and a renamed
	// node must not become invisible to getInputTaxaNode() and friends. Set by setupInputAndWorkingNodes,
	// carried across by shallowCopy, dropped when the node is deleted or the workflow is cleared. The
	// accessors fall back to matching by title so a workflow assembled by some other route still resolves.
	private DataNode<SourceBlock> sourceNode;
	private DataNode<TaxaBlock> inputTaxaNode;
	private DataNode<? extends DataBlock> inputDataNode;
	private DataNode<TaxaBlock> workingTaxaNode;
	private DataNode<? extends DataBlock> workingDataNode;
	private AlgorithmNode<TaxaBlock, TaxaBlock> inputTaxaFilterNode;
	private AlgorithmNode<? extends DataBlock, ? extends DataBlock> inputDataFilterNode;
	private AlgorithmNode<? extends DataBlock, ? extends DataBlock> inputDataLoaderNode;

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
						forgetDistinguishedNode(node);
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

	/**
	 * sets up the standard skeleton, with a loader that reads the source into the input nodes
	 * <p>
	 * The source node is what the workflow knows about the file system: the file names, the acceptable
	 * extensions, whether several files may be given and whether the input editor is in use. It is also how
	 * loading is triggered - WorkflowSetup validates it, and the resulting cascade restarts the loader - and
	 * it is the input data block of every DataLoader. It is deliberately hidden in the workflow displays
	 * (WorkflowTabLayout, WorkflowTreeViewLayout), which is why it can look superfluous.
	 */
	public <T extends DataBlock> void setupInputAndWorkingNodes(SourceBlock source, DataLoader<SourceBlock, T> dataLoader, TaxaBlock inputTaxaBlock, T inputDataBlock) {
		sourceNode = newDataNode(source, INPUT_SOURCE);
		inputTaxaNode = newDataNode(inputTaxaBlock, INPUT_TAXA);
		inputDataNode = newDataNode(inputDataBlock, INPUT_PREFIX + inputDataBlock.getName());

		var loaderNode = newAlgorithmNode(dataLoader, null, sourceNode, inputDataNode, INPUT_DATA_LOADER);
		loaderNode.addChild(inputTaxaNode);
		inputDataLoaderNode = loaderNode;

		workingTaxaNode = newDataNode(inputTaxaBlock.newInstance(), WORKING_TAXA);
		var workingData = inputDataBlock.newInstance();
		workingDataNode = newDataNode(inputDataBlock.newInstance(), WORKING_PREFIX + workingData.getName());
		inputTaxaFilterNode = newAlgorithmNode(new TaxaFilter(), null, inputTaxaNode, workingTaxaNode, INPUT_TAXA_FILTER);

		var dataFilterNode = newAlgorithmNode(inputDataBlock.createTaxaDataFilter(), INPUT_TAXA_DATA_FILTER);
		dataFilterNode.addParent(inputTaxaNode);
		dataFilterNode.addParent(workingTaxaNode);
		dataFilterNode.addParent(inputDataNode);
		dataFilterNode.addChild(workingDataNode);
		inputDataFilterNode = dataFilterNode;
	}

	/**
	 * sets up the standard skeleton for data that is already in hand, with no file and hence no loader
	 * <p>
	 * The source node is created empty, so that every workflow has the same shape and getSourceNode() never
	 * returns null. Use this when driving the workflow programmatically.
	 */
	public <T extends DataBlock> void setupInputAndWorkingNodes(TaxaBlock inputTaxaBlock, T inputDataBlock) {
		sourceNode = newDataNode(new SourceBlock(), INPUT_SOURCE);
		inputTaxaNode = newDataNode(inputTaxaBlock, INPUT_TAXA);
		inputDataNode = newDataNode(inputDataBlock, INPUT_PREFIX + inputDataBlock.getName());

		workingTaxaNode = newDataNode(inputTaxaBlock.newInstance(), WORKING_TAXA);
		var workingData = inputDataBlock.newInstance();
		workingDataNode = newDataNode(inputDataBlock.newInstance(), WORKING_PREFIX + workingData.getName());
		inputTaxaFilterNode = newAlgorithmNode(new TaxaFilter(), null, inputTaxaNode, workingTaxaNode, INPUT_TAXA_FILTER);

		var dataFilterNode = newAlgorithmNode(inputDataBlock.createTaxaDataFilter(), INPUT_TAXA_DATA_FILTER);
		dataFilterNode.addParent(inputTaxaNode);
		dataFilterNode.addParent(workingTaxaNode);
		dataFilterNode.addParent(inputDataNode);
		dataFilterNode.addChild(workingDataNode);
		inputDataFilterNode = dataFilterNode;
	}

	/**
	 * sets up the standard skeleton from blocks that have already been parsed, as when reading a .stree6 file
	 * <p>
	 * There is deliberately no loader here: WorkflowNexusInput has already parsed the input taxa and data, and
	 * WorkflowDataLoader later writes new data straight into the input nodes rather than going through a
	 * loader. The source node therefore has no children in a loaded workflow, and that is expected.
	 */
	public <T extends DataBlock> void setupInputAndWorkingNodes(SourceBlock source, TaxaBlock inputTaxaBlock, TaxaFilter taxaFilter, TaxaBlock workingTaxaBlock,
																DataBlock inputDataBlock, DataTaxaFilter dataTaxaFilter, DataBlock workingDataBlock) {
		sourceNode = newDataNode(source, INPUT_SOURCE);
		inputTaxaNode = newDataNode(inputTaxaBlock, INPUT_TAXA);
		inputDataNode = newDataNode(inputDataBlock, INPUT_PREFIX + inputDataBlock.getName());

		workingTaxaNode = newDataNode(workingTaxaBlock, WORKING_TAXA);
		workingDataNode = newDataNode(workingDataBlock, WORKING_PREFIX + workingDataBlock.getName());
		inputTaxaFilterNode = newAlgorithmNode(taxaFilter, null, inputTaxaNode, workingTaxaNode, INPUT_TAXA_FILTER);

		var dataFilterNode = newAlgorithmNode(dataTaxaFilter, INPUT_TAXA_DATA_FILTER);
		dataFilterNode.addParent(inputTaxaNode);
		dataFilterNode.addParent(workingTaxaNode);
		dataFilterNode.addParent(inputDataNode);
		dataFilterNode.addChild(workingDataNode);
		inputDataFilterNode = dataFilterNode;
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
		} else if (node instanceof DataNode dataNode) {
			var title = dataNode.getName();
			var t = 1;
			// the data-block map, not the algorithm one: data-node and algorithm-node titles are separate
			// namespaces, and the node-removal listener above puts each back into its own map
			var list = dataBlockNameTitleMap.computeIfAbsent(dataNode.getName(), n -> new ArrayList<>());
			while (list.contains(title)) {
				title = dataNode.getName() + "-" + (++t);
			}
			list.add(title);
			dataNode.setTitle(title);
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

	/**
	 * resolves one of the distinguished nodes: by reference if we have it, else by title
	 * <p>
	 * The reference is what makes the accessors survive a rename. The title fall-back is for a workflow that
	 * was assembled without going through setupInputAndWorkingNodes.
	 *
	 * @param node       the remembered node, or null
	 * @param byTitle    how to find it by title, used only when node is null
	 * @return the node, or null if there is none
	 */
	private <N extends WorkflowNode> N resolve(N node, Supplier<N> byTitle) {
		if (node != null && nodes().contains(node))
			return node;
		return byTitle.get();
	}

	public DataNode<SourceBlock> getSourceNode() {
		return resolve(sourceNode, () -> (DataNode<SourceBlock>) dataNodesStream().filter(v -> v.getTitle().equals(INPUT_SOURCE)).findFirst().orElse(null));
	}

	public AlgorithmNode getLoaderNode() {
		return getInputDataLoaderNode();
	}


	public DataNode<TaxaBlock> getInputTaxaNode() {
		return resolve(inputTaxaNode, () -> (DataNode<TaxaBlock>) dataNodesStream().filter(v -> v.getTitle().equals(INPUT_TAXA)).findFirst().orElse(null));
	}

	public DataNode<? extends DataBlock> getInputDataNode() {
		return resolve(inputDataNode, () -> dataNodesStream().filter(v -> v.getTitle().startsWith(INPUT_PREFIX)).filter(v -> !v.getTitle().equals(INPUT_SOURCE))
				.filter(v -> !v.getTitle().equals(INPUT_TAXA)).findFirst().orElse(null));
	}

	public DataNode<TaxaBlock> getWorkingTaxaNode() {
		return resolve(workingTaxaNode, () -> (DataNode<TaxaBlock>) dataNodesStream().filter(v -> v.getTitle().equals(WORKING_TAXA)).findFirst().orElse(null));
	}

	public DataNode<? extends DataBlock> getWorkingDataNode() {
		return resolve(workingDataNode, () -> dataNodesStream().filter(v -> v.getTitle().startsWith(WORKING_PREFIX)).filter(v -> !v.getTitle().equals(WORKING_TAXA)).findFirst().orElse(null));
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
		return resolve(inputTaxaFilterNode, () -> (AlgorithmNode<TaxaBlock, TaxaBlock>) algorithmNodesStream().filter(v -> v.getTitle().startsWith(INPUT_TAXA_FILTER)).findFirst().orElse(null));
	}

	public AlgorithmNode<? extends DataBlock, ? extends DataBlock> getInputDataFilterNode() {
		return resolve(inputDataFilterNode, () -> algorithmNodesStream().filter(v -> v.getAlgorithm() instanceof DataTaxaFilter).findFirst().orElse(null));
	}

	public AlgorithmNode<? extends DataBlock, ? extends DataBlock> getInputDataLoaderNode() {
		return resolve(inputDataLoaderNode, () -> algorithmNodesStream().filter(v -> v.getTitle().startsWith(INPUT_DATA_LOADER)).findFirst().orElse(null));
	}

	/**
	 * drops a distinguished node reference when the node leaves the workflow
	 */
	private void forgetDistinguishedNode(WorkflowNode node) {
		if (node == sourceNode) sourceNode = null;
		else if (node == inputTaxaNode) inputTaxaNode = null;
		else if (node == inputDataNode) inputDataNode = null;
		else if (node == workingTaxaNode) workingTaxaNode = null;
		else if (node == workingDataNode) workingDataNode = null;
		else if (node == inputTaxaFilterNode) inputTaxaFilterNode = null;
		else if (node == inputDataFilterNode) inputDataFilterNode = null;
		else if (node == inputDataLoaderNode) inputDataLoaderNode = null;
	}

	@Override
	public void clear() {
		super.clear();
		sourceNode = null;
		inputTaxaNode = null;
		inputDataNode = null;
		workingTaxaNode = null;
		workingDataNode = null;
		inputTaxaFilterNode = null;
		inputDataFilterNode = null;
		inputDataLoaderNode = null;
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

		// carry the distinguished-node references across, otherwise the copy would have to fall back to
		// matching by title and would lose them again the moment a node is renamed
		sourceNode = (DataNode<SourceBlock>) nodeCopyNodeMap.get(src.getSourceNode());
		inputTaxaNode = (DataNode<TaxaBlock>) nodeCopyNodeMap.get(src.getInputTaxaNode());
		inputDataNode = (DataNode<? extends DataBlock>) nodeCopyNodeMap.get(src.getInputDataNode());
		workingTaxaNode = (DataNode<TaxaBlock>) nodeCopyNodeMap.get(src.getWorkingTaxaNode());
		workingDataNode = (DataNode<? extends DataBlock>) nodeCopyNodeMap.get(src.getWorkingDataNode());
		inputTaxaFilterNode = (AlgorithmNode<TaxaBlock, TaxaBlock>) nodeCopyNodeMap.get(src.getInputTaxaFilterNode());
		inputDataFilterNode = (AlgorithmNode<? extends DataBlock, ? extends DataBlock>) nodeCopyNodeMap.get(src.getInputDataFilterNode());
		inputDataLoaderNode = (AlgorithmNode<? extends DataBlock, ? extends DataBlock>) nodeCopyNodeMap.get(src.getInputDataLoaderNode());

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
