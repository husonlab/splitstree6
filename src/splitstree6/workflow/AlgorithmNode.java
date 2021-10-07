package splitstree6.workflow;

import javafx.beans.value.ChangeListener;
import javafx.concurrent.Worker;
import jloda.fx.util.AService;
import jloda.util.Basic;
import splitstree6.data.DataBlock;
import splitstree6.data.TaxaBlock;

import java.util.stream.Collectors;


public class AlgorithmNode<S extends DataBlock, T extends DataBlock> extends WorkflowNode {
	private final AService<Boolean> service = new AService<>();
	private Algorithm<S, T> algorithm;

	public AlgorithmNode(Workflow owner, int id) {
		super(owner, id);
		service.setCallable(() -> {
			var algorithm = getAlgorithm();
			if (getAlgorithm() != null) {
				if (algorithm instanceof TopFilter<S, T> taxonFilter) {
					taxonFilter.filter(getService().getProgressListener(), getTaxaBlock(),
							getSecondTaxaBlock(), getSourceBlock(), getTargetBlock());
				} else if (algorithm instanceof Loader<S, T> source) {
					source.load(getService().getProgressListener(), getSourceBlock(), getTargetTaxaBlock(), getTargetBlock());
				} else
					algorithm.compute(service.getProgressListener(), getTaxaBlock(), getSourceBlock(), getTargetBlock());
				return true;
			}
			return false;
		});
		service.stateProperty().addListener((v, o, n) -> {
			setState(n);
			if (n == Worker.State.FAILED)
				System.err.println("Error in " + getAlgorithm().getClass().getSimpleName() + ": Calculation failed: " + service.getException());
		});
		stateProperty().addListener((v, o, n) -> {
			if (n == Worker.State.CANCELLED)
				service.cancel();
		});
	}

	public AlgorithmNode(Workflow owner, int id, Algorithm<S, T> algorithm) {
		this(owner, id);
		this.algorithm = algorithm;
	}

	public Algorithm<S, T> getAlgorithm() {
		return algorithm;
	}

	public void setAlgorithm(Algorithm<S, T> algorithm) {
		this.algorithm = algorithm;
	}

	public AService<Boolean> getService() {
		return service;
	}

	public void restart() {
		service.restart();
	}

	public TaxaBlock getTaxaBlock() {
		for (var parent : getParentsUnmodifiable()) {
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
		for (var parent : getParentsUnmodifiable()) {
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
		for (var parent : getParentsUnmodifiable()) {
			if (parent instanceof DataNode dataNode && (algorithm.getFromClass().equals(TaxaBlock.class) || !(dataNode.getDataBlock() instanceof TaxaBlock))) {
				return (S) dataNode.getDataBlock();
			}
		}
		return null;
	}

	public T getTargetBlock() {
		for (var child : getChildrenUnmodifiable()) {
			if (child instanceof DataNode dataNode && (algorithm.getToClass().equals(TaxaBlock.class) || !(dataNode.getDataBlock() instanceof TaxaBlock))) {
				return (T) dataNode.getDataBlock();
			}
		}
		return null;
	}

	public TaxaBlock getTargetTaxaBlock() {
		for (var child : getChildrenUnmodifiable()) {
			if (child instanceof DataNode dataNode && dataNode.getDataBlock() instanceof TaxaBlock taxaBlock) {
				return taxaBlock;
			}
		}
		return null;
	}

	@Override
	public String toReportString() {
		return String.format("AlgorithmNode-%d %s; parents: %s children: %s",
				getId(), getAlgorithm().getClass().getSimpleName(),
				Basic.toString(getParentsUnmodifiable().stream().map(WorkflowNode::getId).collect(Collectors.toList()), ","),
				Basic.toString(getChildrenUnmodifiable().stream().map(WorkflowNode::getId).collect(Collectors.toList()), ","));
	}

	protected ChangeListener<Worker.State> createParentStateChangeListener() {
		return (v, o, n) -> {
			switch (n) {
				case SCHEDULED -> setState(Worker.State.READY);
				case CANCELLED -> setState(Worker.State.CANCELLED);
				case FAILED -> setState(Worker.State.FAILED);
				case SUCCEEDED -> {
					if (getState() == Worker.State.READY) {
						var ok = true;
						for (var parent : getParentsUnmodifiable()) {
							if (parent.getState() != Worker.State.SUCCEEDED) {
								ok = false;
								break;
							}
						}
						if (ok)
							restart();
					}
				}
			}
		};
	}
}
