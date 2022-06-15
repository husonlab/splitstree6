/*
 * AlgorithmNode.java Copyright (C) 2022 Daniel H. Huson
 *
 * (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package splitstree6.workflow;


import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import splitstree6.data.TaxaBlock;


/**
 * a workflow node that contains an algorithm
 * Daniel Huson, 10.2021
 *
 * @param <S> input data
 * @param <T> output data
 */
public class AlgorithmNode<S extends DataBlock, T extends DataBlock> extends jloda.fx.workflow.AlgorithmNode {
	private final StringProperty title = new SimpleStringProperty();

	AlgorithmNode(Workflow owner) {
		super(owner);
		title.set(getName());
		try {
			if (owner.getServiceConfigurator() != null)
				owner.getServiceConfigurator().accept(getService());
		} catch (Exception ignored) {
		}
	}

	public void setAlgorithm(Algorithm<S, T> algorithm) {
		super.setAlgorithm(algorithm);
		algorithm.setNode(this);
	}

	public Algorithm getAlgorithm() {
		return (Algorithm) super.getAlgorithm();
	}

	public TaxaBlock getTaxaBlock() {
		for (var parent : getParents()) {
			if (parent instanceof DataNode dataNode && dataNode.getDataBlock() instanceof TaxaBlock taxaBlock) {
				return taxaBlock;
			}
		}
		return null;
	}

	public S getSourceBlock() {
		for (var parent : getParents()) {
			if (parent instanceof DataNode dataNode
				&& (!(dataNode.getDataBlock() instanceof TaxaBlock)
					|| (getAlgorithm() != null && super.getAlgorithm() instanceof Algorithm algorithm && algorithm.getFromClass().equals(TaxaBlock.class)))) {
				return (S) dataNode.getDataBlock();
			}
		}
		return null;
	}

	public DataNode<T> getTargetNode() {
		for (var child : getChildren()) {
			if (child instanceof DataNode dataNode
				&& (!(dataNode.getDataBlock() instanceof TaxaBlock)
					|| (getAlgorithm() != null && super.getAlgorithm() instanceof Algorithm algorithm && algorithm.getToClass().equals(TaxaBlock.class)))) {
				return (DataNode<T>) dataNode;
			}
		}
		return null;
	}

	public DataNode getPreferredParent() {
		for (var p : getParents()) {
			if (p instanceof DataNode dataNode && !(dataNode.getDataBlock() instanceof TaxaBlock))
				return dataNode;
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

	public Workflow getOwner() {
		return (Workflow) super.getOwner();
	}

	public String toString() {
		return getTitle();
	}

	@Override
	public void restart() {
		if (getAlgorithm().getNode() == null)
			getAlgorithm().setNode(this);
		super.restart();
	}
}