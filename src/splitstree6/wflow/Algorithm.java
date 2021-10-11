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

import jloda.util.ProgressListener;

import java.io.IOException;
import java.util.Collection;

/**
 * algorithm
 * Daniel Huson, 10.2021
 */
public abstract class Algorithm extends NamedBase {
	public abstract void compute(ProgressListener progress, Collection<DataBlock> inputData, Collection<DataBlock> outputData) throws IOException;

	public boolean isApplicable(Collection<DataNode> inputNodes) {
		return inputNodes.stream().allMatch(WorkflowNode::isValid);
	}

	public void clear() {
	}
}
