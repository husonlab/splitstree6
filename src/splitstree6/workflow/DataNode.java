/*
 *  DataNode.java Copyright (C) 2022 Daniel H. Huson
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

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * a workflow node that contains data
 * Daniel Huson, 10.2021
 *
 * @param <S> the data block type
 */
public class DataNode<S extends DataBlock> extends jloda.fx.workflow.DataNode {
	private final StringProperty title = new SimpleStringProperty();

	DataNode(Workflow workflow) {
		super(workflow);
		title.set(getName());

		validProperty().addListener((v, o, n) -> {
			if (!n && getDataBlock() != null)
				getDataBlock().clear();
		});

		dataBlockProperty().addListener((v, o, n) -> {
			if (n != null)
				shortDescriptionProperty().bind(n.shortDescriptionProperty());
			else
				shortDescriptionProperty().unbind();
		});
	}

	@Override
	public S getDataBlock() {
		return (S) super.getDataBlock();
	}

	public AlgorithmNode getPreferredParent() {
		for (var p : getParents()) {
			if (p instanceof AlgorithmNode algorithmNode)
				return algorithmNode;
		}
		return null;
	}

	public String getTitle() {
		return title.get();
	}

	public StringProperty titleProperty() {
		return title;
	}

	public void setTitle(String title) {
		this.title.set(title);
	}

	@Override
	public String toString() {
		return getTitle();
	}
}
