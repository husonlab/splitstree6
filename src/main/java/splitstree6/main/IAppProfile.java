/*
 * IAppProfile.java Copyright (C) 2026 Daniel H. Huson
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
 *
 */

package splitstree6.main;

import splitstree6.data.SourceBlock;
import splitstree6.workflow.Algorithm;
import splitstree6.workflow.DataBlock;
import splitstree6.workflow.Workflow;

import java.util.function.Predicate;

public interface IAppProfile {
	String getName();

	default Predicate<Algorithm<?, ?>> getAlgorithmFilter() {
		return null;
	}

	/**
	 * Gives the host application an opportunity to build the workflow for a given
	 * input type. If the method sets up nodes on the workflow and returns true,
	 * SplitsTree6 uses that pipeline. If it returns false, SplitsTree6's built-in
	 * default pipeline is used.
	 */
	default boolean setupWorkflow(Workflow workflow, Class<? extends DataBlock> inputType, SourceBlock sourceBlock) {
		return false;
	}
}