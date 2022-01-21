/*
 * TreesFormat.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.data;

import jloda.util.ProgramProperties;
import splitstree6.io.nexus.INexusFormat;

/**
 * trees block nexus format
 * Daniel Huson, 12/30/16.
 */
public class TreesFormat implements INexusFormat {
	private boolean optionTranslate = true; // use translation table
	private boolean optionWeights = true;

	public TreesFormat() {
	}

	public TreesFormat(TreesFormat other) {
		setOptionTranslate(other.isOptionTranslate());
		setOptionWeights(other.isOptionWeights());
	}

	public boolean isOptionTranslate() {
		return optionTranslate;
	}

	public void setOptionTranslate(boolean optionTranslate) {
		this.optionTranslate = optionTranslate;
		ProgramProperties.put("NexusTreeOptionTranslate", optionTranslate);
	}

	public boolean isOptionWeights() {
		return optionWeights;
	}

	public void setOptionWeights(boolean optionWeights) {
		this.optionWeights = optionWeights;
		ProgramProperties.put("NexusTreeOptionWeights", optionWeights);
	}
}
