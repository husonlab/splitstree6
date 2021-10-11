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

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.concurrent.Worker;
import jloda.fx.util.AService;
import jloda.util.Basic;

import java.util.stream.Collectors;

/**
 * a workflow node that contains an algorithm
 * Daniel Huson, 10.2021
 */
public class AlgorithmNode extends WorkflowNode {
	private final AService<Boolean> service = new AService<>();
	private final ObjectProperty<Algorithm> algorithm = new SimpleObjectProperty<>(null);

	public AlgorithmNode(Workflow owner) {
		super(owner);

		service.setCallable(() -> {
			var algorithm = getAlgorithm();
			if (algorithm != null) {
				var inputData = getParents().stream().filter(d -> d instanceof DataNode).map(d -> (DataNode) d).map(DataNode::getDataBlock).collect(Collectors.toList());
				var outputData = getChildren().stream().filter(d -> d instanceof DataNode).map(d -> (DataNode) d).map(DataNode::getDataBlock).collect(Collectors.toList());
				algorithm.compute(service.getProgressListener(), inputData, outputData);
				return true;
			}
			return false;
		});
		service.stateProperty().addListener((v, o, n) -> {
			setValid(n.equals(Worker.State.SUCCEEDED));

			if (n == Worker.State.FAILED)
				System.err.println("Error in " + getAlgorithm().getClass().getSimpleName() + ": Calculation failed: " + service.getException());
		});

		validProperty().addListener((v, o, n) -> {
			if (!n)
				service.cancel();
		});
	}

	@Override
	protected ChangeListener<Boolean> createParentValidListener() {
		return (v, o, n) -> {
			if (n && getParents().stream().allMatch(WorkflowNode::isValid)) {
				System.err.println(toReportString(false) + " restarting, n=" + n + " parents: " + (getParents().size()) + ", all=" + getParents().stream().allMatch(WorkflowNode::isValid));
				restart();
			} else
				setValid(false);
		};
	}

	public Algorithm getAlgorithm() {
		return algorithm.get();
	}

	public void setAlgorithm(Algorithm algorithm) {
		this.algorithm.set(algorithm);
	}

	public ReadOnlyObjectProperty<Algorithm> algorithmProperty() {
		return algorithm;
	}

	public AService<Boolean> getService() {
		return service;
	}

	public void restart() {
		try {
			service.restart();
		} catch (Exception ex) {
			Basic.caught(ex);
			throw ex;
		}
	}

	@Override
	public String toReportString(boolean full) {
		if (full)
			return String.format("%s (%s); parents: %s children: %s",
					toReportString(false), isValid(),
					Basic.toString(getParents().stream().map(WorkflowNode::getId).collect(Collectors.toList()), ","),
					Basic.toString(getChildren().stream().map(WorkflowNode::getId).collect(Collectors.toList()), ","));
		else
			return String.format("%02d AlgorithmNode '%s'", getId(),
					(getAlgorithm() != null ? getAlgorithm().getName() : getName()));
	}
}
