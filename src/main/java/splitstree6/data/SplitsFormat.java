/*
 * EdgesFormat.java Copyright (C) 2024 Daniel H. Huson
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

/**
 * splits format
 * Daniel Huson, 12/29/16.
 */
public class SplitsFormat {
	private boolean optionLabels = false;
	private boolean optionWeights = true;
	private boolean optionConfidences = false;
	private boolean optionShowBothSides = false;

	/**
	 * Constructor
	 */
	public SplitsFormat() {
	}

	public SplitsFormat(SplitsFormat other) {
		this.optionLabels = other.optionLabels;
		this.optionWeights = other.optionWeights;
		this.optionConfidences = other.optionConfidences;
		this.optionShowBothSides = other.optionShowBothSides;
	}

	/**
	 * Show labels?
	 *
	 * @return true, if labels are to be printed
	 */
	public boolean isOptionLabels() {
		return optionLabels;
	}

	/**
	 * Show weights?
	 *
	 * @return true, if weights are to be printed
	 */
	public boolean isOptionWeights() {
		return optionWeights;
	}

	/**
	 * Show labels
	 *
	 * @param flag whether labels should be printed
	 */
	public void setOptionLabels(boolean flag) {
		optionLabels = flag;
	}

	/**
	 * Show weights
	 *
	 * @param flag whether weights should be printed
	 */
	public void setOptionWeights(boolean flag) {
		optionWeights = flag;
	}

	/**
	 * show confidences?
	 *
	 * @return confidence
	 */
	public boolean isOptionConfidences() {
		return optionConfidences;
	}

	/**
	 * show confidences?
	 */
	public void setOptionConfidences(boolean optionConfidences) {
		this.optionConfidences = optionConfidences;
	}

	public boolean isOptionShowBothSides() {
		return optionShowBothSides;
	}

	public void setOptionShowBothSides(boolean optionShowBothSides) {
		this.optionShowBothSides = optionShowBothSides;
	}
}
