package splitstree6.workflow;

import javafx.beans.value.ChangeListener;
import javafx.concurrent.Worker;
import jloda.util.Basic;
import splitstree6.data.DataBlock;

import java.util.stream.Collectors;

public class DataNode<S extends DataBlock> extends WorkflowNode {
	private S dataBlock;

	public DataNode(Workflow owner, int id) {
		super(owner, id);
	}

	public DataNode(Workflow owner, int id, S dataBlock) {
		this(owner, id);
		this.dataBlock = dataBlock;
	}

	public S getDataBlock() {
		return dataBlock;
	}

	public void setDataBlock(S dataBlock) {
		this.dataBlock = dataBlock;
	}

	public String toReportString() {
		return String.format("DataNode-%d %s; parents: %s children: %s",
				getId(), getDataBlock().getClass().getSimpleName(),
				Basic.toString(getParentsUnmodifiable().stream().map(WorkflowNode::getId).collect(Collectors.toList()), ","),
				Basic.toString(getChildrenUnmodifiable().stream().map(WorkflowNode::getId).collect(Collectors.toList()), ","));
	}

	protected ChangeListener<Worker.State> createParentStateChangeListener() {
		return (v, o, n) -> {
			System.err.println(getDataBlock().getName() + " state: " + o + " -> " + n);
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
							setState(Worker.State.SUCCEEDED);
					}
				}
			}
		};
	}
}
