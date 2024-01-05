/*
 * MedianJoining.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.algorithms.characters.characters2network;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import jloda.util.StringUtils;
import jloda.util.progress.ProgressListener;
import splitstree6.data.CharactersBlock;
import splitstree6.data.NetworkBlock;
import splitstree6.data.TaxaBlock;

import java.io.IOException;
import java.util.List;

/**
 * run the median joining algorithm
 * Daniel Huson, 2.2018
 */
public class MedianJoining extends Characters2Network {
	private final IntegerProperty optionEpsilon = new SimpleIntegerProperty(this, "optionEpsilon", 0);

	@Override
	public List<String> listOptions() {
		return List.of(optionEpsilon.getName());
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, CharactersBlock charactersBlock, NetworkBlock networkBlock) throws IOException {
		var medianJoiningCalculator = new MedianJoiningCalculator();
		medianJoiningCalculator.setOptionEpsilon(getOptionEpsilon());
		medianJoiningCalculator.apply(progress, taxaBlock, charactersBlock, networkBlock);
		networkBlock.setInfoString("Median-joining network" + (getOptionEpsilon() > 0 ?
				", epsilon=" + StringUtils.removeTrailingZerosAfterDot(getOptionEpsilon()) : ""));
	}

	/**
	 * Determine whether given method can be applied to given data.
	 *
	 * @param taxa  the taxa
	 * @param chars the characters matrix
	 * @return true, if method applies to given data
	 */
	@Override
	public boolean isApplicable(TaxaBlock taxa, CharactersBlock chars) {
		return taxa != null && chars != null && chars.getNcolors() < 8; // not too  many different states
	}

	@Override
	public String getCitation() {
		return "Bandelt et al, 1999;H. -J. Bandelt, P. Forster, and A. Röhl. Median-joining networks for inferring intraspecific phylogenies. Molecular Biology and Evolution, 16:37–48, 1999.";
	}

	public int getOptionEpsilon() {
		return optionEpsilon.get();
	}

	public IntegerProperty optionEpsilonProperty() {
		return optionEpsilon;
	}

	public void setOptionEpsilon(int optionEpsilon) {
		this.optionEpsilon.set(optionEpsilon);
	}
}