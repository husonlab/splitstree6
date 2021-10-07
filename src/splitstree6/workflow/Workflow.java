package splitstree6.workflow;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import splitstree6.algorithms.taxa.taxa2taxa.TaxaFilter;
import splitstree6.data.DataBlock;
import splitstree6.data.SourceBlock;
import splitstree6.data.TaxaBlock;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Workflow {
	private final ObservableList<WorkflowNode> nodes = FXCollections.observableArrayList();
	private int numberOfNodesCreated = 0;

	private final SimpleObjectProperty<DataNode<SourceBlock>> sourceNode = new SimpleObjectProperty<>();
	private final SimpleObjectProperty<AlgorithmNode> loaderNode = new SimpleObjectProperty<>();

	private final SimpleObjectProperty<DataNode<TaxaBlock>> topTaxaNode = new SimpleObjectProperty<>();
	private final SimpleObjectProperty<DataNode<? extends DataBlock>> topDataNode = new SimpleObjectProperty<>();


	private final SimpleObjectProperty<DataNode<TaxaBlock>> workingTaxaNode = new SimpleObjectProperty<>();
	private final SimpleObjectProperty<DataNode<? extends DataBlock>> workingDataNode = new SimpleObjectProperty<>();


	private final IntegerProperty numberOfEdges = new SimpleIntegerProperty(0);
	private final ListChangeListener<WorkflowNode> parentsChangedListener;

	public Workflow() {
		parentsChangedListener = change -> {
			var count = 0;
			while (change.next()) {
				count += change.getAddedSize();
				count -= change.getRemovedSize();
			}
			numberOfEdges.set(numberOfEdges.get() + count);
		};
	}

	public <S extends DataBlock> void setupTopAndWorkingNodes(TaxaBlock topTaxaBlock, S topDataBlock) {
		setTopTaxaNode(newDataNode(topTaxaBlock));
		setWorkingTaxaNode(newDataNode((TaxaBlock) topTaxaBlock.newInstance()));
		setTopDataNode(newDataNode(topDataBlock));
		var workingDataBlock = topDataBlock.newInstance();
		setWorkingDataNode(newDataNode(workingDataBlock));
		var topFilter = topDataBlock.createTopFilter();
		var topFilterNode = newAlgorithmNode(topFilter);
		topFilterNode.addParent(getTopTaxaNode());
		topFilterNode.addParent(getWorkingTaxaNode());
		newAlgorithmNode(new TaxaFilter(), null, getTopTaxaNode(), getWorkingTaxaNode());
		topFilterNode.addParent(getTopDataNode());
		topFilterNode.addChild(getWorkingDataNode());
	}

	public DataNode newDataNode() {
		var v = new DataNode(this, ++numberOfNodesCreated);
		v.getParentsUnmodifiable().addListener(parentsChangedListener);
		nodes.add(v);
		return v;
	}

	public <S extends DataBlock> DataNode<S> newDataNode(S dataBlock) {
		var v = new DataNode<S>(this, ++numberOfNodesCreated);
		v.getParentsUnmodifiable().addListener(parentsChangedListener);
		nodes.add(v);
		v.setDataBlock(dataBlock);
		return v;
	}

	public DataNode<SourceBlock> newSourceNode(SourceBlock sourceBlock) {
		var v = newDataNode(sourceBlock);
		setSourceNode(v);
		return v;
	}

	public AlgorithmNode newAlgorithmNode() {
		var v = new AlgorithmNode(this, ++numberOfNodesCreated);
		v.getParentsUnmodifiable().addListener(parentsChangedListener);
		nodes.add(v);
		return v;
	}

	public <S extends DataBlock, T extends DataBlock> AlgorithmNode<S, T> newAlgorithmNode(Algorithm<S, T> algorithm) {
		var v = new AlgorithmNode<S, T>(this, ++numberOfNodesCreated);
		v.setAlgorithm(algorithm);
		v.getParentsUnmodifiable().addListener(parentsChangedListener);
		nodes.add(v);
		return v;
	}

	public AlgorithmNode newAlgorithmNode(Algorithm algorithm, DataNode<TaxaBlock> taxaNode, DataNode sourceNode, DataNode targetNode) {
		if (algorithm instanceof Loader)
			throw new RuntimeException("newAlgorithmNode: applied to Loader");

		var v = new AlgorithmNode(this, ++numberOfNodesCreated);
		v.setAlgorithm(algorithm);
		if (taxaNode != null) // is null for algorithm equal to TaxaFilter
			v.addParent(taxaNode);
		v.addParent(sourceNode);
		v.addChild(targetNode);
		v.getParentsUnmodifiable().addListener(parentsChangedListener);
		nodes.add(v);
		return v;
	}

	public AlgorithmNode newLoaderNode(Loader loader, DataNode<SourceBlock> sourceNode, DataNode<TaxaBlock> targetTaxaNode, DataNode targetNode) {
		var v = new AlgorithmNode(this, ++numberOfNodesCreated);
		v.setAlgorithm(loader);
		v.addParent(sourceNode);
		v.addChild(targetTaxaNode);
		v.addChild(targetNode);
		v.getParentsUnmodifiable().addListener(parentsChangedListener);
		nodes.add(v);
		setLoaderNode(v);
		return v;
	}

	public Iterable<WorkflowNode> nodes() {
		return nodes;
	}

	public Stream<WorkflowNode> nodeStream() {
		return nodes.stream();
	}

	public void deleteNode(WorkflowNode v) {
		checkOwner(v.getOwner());

		for (var w : v.getParentsUnmodifiable()) {
			w.removeChild(v);
		}

		for (var w : v.getParentsUnmodifiable()) {
			w.removeParent(v);
		}

		nodes.remove(v);
		v.unsetOwner();
	}

	public int getNumberOfNodes() {
		return nodes.size();
	}

	public int getNumberOfEdges() {
		return numberOfEdges.get();
	}

	public boolean isDAG() {
		return true;
	}

	public boolean isConnected() {
		return true;
	}

	public Iterable<WorkflowNode> roots() {
		return () -> nodeStream().filter(v -> v.getInDegree() == 0).iterator();
	}

	public Iterable<WorkflowNode> leaves() {
		return () -> nodeStream().filter(v -> v.getOutDegree() == 0).iterator();
	}

	public void checkOwner(Workflow owner) {
		assert owner != null : "Owner is null";
		assert owner == this : "Wrong owner";
	}

	public DataNode<SourceBlock> getSourceNode() {
		return sourceNode.get();
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

	public SimpleObjectProperty<AlgorithmNode> loaderNodeProperty() {
		return loaderNode;
	}

	public void setLoaderNode(AlgorithmNode loaderNode) {
		this.loaderNode.set(loaderNode);
	}

	public DataNode<TaxaBlock> getTopTaxaNode() {
		return topTaxaNode.get();
	}

	public SimpleObjectProperty<DataNode<TaxaBlock>> topTaxaNodeProperty() {
		return topTaxaNode;
	}

	public void setTopTaxaNode(DataNode<TaxaBlock> topTaxaNode) {
		this.topTaxaNode.set(topTaxaNode);
	}

	public DataNode<? extends DataBlock> getTopDataNode() {
		return topDataNode.get();
	}

	public SimpleObjectProperty<DataNode<? extends DataBlock>> topDataNodeProperty() {
		return topDataNode;
	}

	public void setTopDataNode(DataNode<? extends DataBlock> topDataNode) {
		this.topDataNode.set(topDataNode);
	}

	public DataNode<TaxaBlock> getWorkingTaxaNode() {
		return workingTaxaNode.get();
	}

	public SimpleObjectProperty<DataNode<TaxaBlock>> workingTaxaNodeProperty() {
		return workingTaxaNode;
	}

	public void setWorkingTaxaNode(DataNode<TaxaBlock> workingTaxaNode) {
		this.workingTaxaNode.set(workingTaxaNode);
	}

	public DataNode<? extends DataBlock> getWorkingDataNode() {
		return workingDataNode.get();
	}

	public SimpleObjectProperty<DataNode<? extends DataBlock>> workingDataNodeProperty() {
		return workingDataNode;
	}

	public void setWorkingDataNode(DataNode<? extends DataBlock> workingDataNode) {
		this.workingDataNode.set(workingDataNode);
	}

	public String toReportString() {
		var buf = new StringBuilder();
		var seen = new HashSet<WorkflowNode>();
		var queue = nodeStream().filter(n -> n.getInDegree() == 0).collect(Collectors.toCollection(LinkedList::new));
		while (queue.size() > 0) {
			var node = queue.pop();
			if (!seen.contains(node)) {
				seen.add(node);
				buf.append(node.toReportString());
				buf.append("\n");
				queue.addAll(node.getChildrenUnmodifiable().stream().filter(n -> !seen.contains(n)).collect(Collectors.toList()));
			}
		}
		return buf.toString();
	}
}
