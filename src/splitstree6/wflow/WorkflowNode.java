/*
 *  Copyright (C) 2018. Daniel H. Huson
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

package splitstree6.wflow;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import jloda.util.Basic;

import java.util.Objects;

/**
 * workflow node
 * Daniel Huson, 10.2021
 */
abstract public class WorkflowNode extends NamedBase {
	private final Workflow owner;
	private final int id;

	private final ObservableList<WorkflowNode> parents = FXCollections.observableArrayList();
	private final ObservableList<WorkflowNode> children = FXCollections.observableArrayList();

	private final BooleanProperty valid = new SimpleBooleanProperty(false);
	private final ChangeListener<Boolean> parentValidListener = createParentValidListener();

	WorkflowNode(Workflow owner) {
		this.owner = owner;
		id = owner.getNodeUID();

		parents.addListener((ListChangeListener<? super WorkflowNode>) e -> {
			while (e.next()) {
				if (e.wasAdded()) {
					for (var v : e.getAddedSubList()) {
						try {
							owner.checkOwner(v.getOwner());
							assert this != v : "Self loop";
							v.validProperty().addListener(parentValidListener);
							if (!v.getChildren().contains(this))
								v.getChildren().add(this);
						} catch (Exception ex) {
							Basic.caught(ex);
						}
					}
				} else if (e.wasRemoved()) {
					for (var v : e.getRemoved()) {
						try {
							owner.checkOwner(v.getOwner());
							v.getChildren().remove(this);
							v.validProperty().removeListener(parentValidListener);
						} catch (Exception ex) {
							Basic.caught(ex);
						}
					}
				}
			}
		});

		children.addListener((ListChangeListener<? super WorkflowNode>) e -> {
			while (e.next()) {
				if (e.wasAdded()) {
					for (var v : e.getAddedSubList()) {
						try {
							owner.checkOwner(v.getOwner());
							assert this != v : "Self loop";

							if (!v.getParents().contains(this))
								v.getParents().add(this);
						} catch (Exception ex) {
							Basic.caught(ex);
						}
					}
				} else if (e.wasRemoved()) {
					for (var v : e.getRemoved()) {
						try {
							owner.checkOwner(v.getOwner());
							v.getParents().remove(this);
						} catch (Exception ex) {
							Basic.caught(ex);
						}
					}
				}
			}
		});
	}

	public ObservableList<WorkflowNode> getParents() {
		return parents;
	}

	public int getInDegree() {
		return parents.size();
	}

	public ObservableList<WorkflowNode> getChildren() {
		return children;
	}

	public void addChild(WorkflowNode v) {
		owner.checkOwner(v.getOwner());
		assert this != v : "Self loop";

		if (!children.contains(v))
			children.add(v);
		if (!v.parents.contains(this))
			v.parents.add(this);
	}

	public int getOutDegree() {
		return children.size();
	}

	public int getDegree() {
		return parents.size() + children.size();
	}

	public int getId() {
		return id;
	}

	abstract public String toReportString(boolean full);

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

	public boolean isValid() {
		return valid.get();
	}

	public ReadOnlyBooleanProperty validProperty() {
		return valid;
	}

	public void setValid(boolean valid) {
		if (valid != isValid()) {
			// todo: debugging:
			System.err.println(toReportString(false) + " valid -> " + valid);
			this.valid.set(valid);
		}
	}

	public Workflow getOwner() {
		return owner;
	}

	abstract protected ChangeListener<Boolean> createParentValidListener();
}