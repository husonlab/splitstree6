package splitstree6.workflow;

import javafx.beans.property.LongProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.concurrent.Worker;

import java.util.Objects;

abstract public class WorkflowNode {
	private final int id;
	private final ObjectProperty<Worker.State> state = new SimpleObjectProperty<>(Worker.State.READY);
	private final ChangeListener<Worker.State> parentStateChangeListener = createParentStateChangeListener();

	private Workflow owner;

	private final LongProperty lastUpdate = new SimpleLongProperty(0);

	private final ObservableList<WorkflowNode> parents = FXCollections.observableArrayList();
	private final ObservableList<WorkflowNode> parentsUnmodifiable = FXCollections.unmodifiableObservableList(parents);
	private final ObservableList<WorkflowNode> children = FXCollections.observableArrayList();
	private final ObservableList<WorkflowNode> childrenUnmodifiable = FXCollections.unmodifiableObservableList(children);

	WorkflowNode(Workflow owner, int id) {
		this.owner = owner;
		this.id = id;

		getParentsUnmodifiable().addListener((ListChangeListener<? super WorkflowNode>) e -> {
			while (e.next()) {
				if (e.wasAdded()) {
					for (var node : e.getAddedSubList()) {
						node.stateProperty().addListener(parentStateChangeListener);
					}
				} else if (e.wasRemoved()) {
					for (var node : e.getRemoved()) {
						node.stateProperty().removeListener(parentStateChangeListener);
					}
				}
			}
		});
	}

	public ObservableList<WorkflowNode> getParentsUnmodifiable() {
		return parentsUnmodifiable;
	}

	public void addParent(WorkflowNode v) {
		owner.checkOwner(v.getOwner());
		assert this != v : "Self loop";

		if (!parents.contains(v))
			parents.add(v);
		if (!v.children.contains(this))
			v.children.add(this);
	}

	public void removeParent(WorkflowNode v) {
		owner.checkOwner(v.getOwner());
		v.children.remove(this);
		parents.remove(v);
	}

	public boolean isParent(WorkflowNode v) {
		return parents.contains(v);
	}

	public int getInDegree() {
		return parents.size();
	}

	public ObservableList<WorkflowNode> getChildrenUnmodifiable() {
		return childrenUnmodifiable;
	}

	public void addChild(WorkflowNode v) {
		owner.checkOwner(v.getOwner());
		assert this != v : "Self loop";

		if (!children.contains(v))
			children.add(v);
		if (!v.parents.contains(this))
			v.parents.add(this);
	}

	public void removeChild(WorkflowNode v) {
		owner.checkOwner(v.getOwner());

		v.parents.remove(this);
		children.remove(v);
	}

	public boolean isChild(WorkflowNode v) {
		return children.contains(v);
	}

	public int getOutDegree() {
		return children.size();
	}

	public int getDegree() {
		return parents.size() + children.size();
	}

	public Workflow getOwner() {
		return owner;
	}

	void unsetOwner() {
		owner = null;
	}

	public int getId() {
		return id;
	}

	abstract public String toReportString();

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof WorkflowNode that)) return false;
		return id == that.id;
	}

	@Override
	public int hashCode() {
		return Objects.hash(id);
	}

	public long getLastUpdate() {
		return lastUpdate.get();
	}

	public LongProperty lastUpdateProperty() {
		return lastUpdate;
	}

	public void setLastUpdate(long lastUpdate) {
		this.lastUpdate.set(lastUpdate);
	}

	public Worker.State getState() {
		return state.get();
	}

	public ObjectProperty<Worker.State> stateProperty() {
		return state;
	}

	public void setState(Worker.State state) {
		this.state.set(state);
	}

	abstract protected ChangeListener<Worker.State> createParentStateChangeListener();
}