/*
 *  PhylogeneticOutline.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.algorithms.splits.splits2network;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import jloda.phylo.PhyloSplitsGraph;
import jloda.util.progress.ProgressListener;
import splitstree6.data.NetworkBlock;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.Compatibility;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * computes a phylogenetic outline
 * Daniel Huson, 10.2021
 */
public class PhylogeneticOutline extends Splits2Network {
	public enum Layout {Circular, MidPointRooted, MidPointRootedAlt, RootBySelectedOutgroup, RootBySelectedOutgroupAlt}

	private final ObjectProperty<Layout> optionLayout = new SimpleObjectProperty<>(Layout.Circular);

	private final BooleanProperty optionUseWeights = new SimpleBooleanProperty(true);

	private final PhyloSplitsGraph graph = new PhyloSplitsGraph();

	public List<String> listOptions() {
		return Arrays.asList("optionUseWeights", "optionLayout");
	}

	@Override
	public String getCitation() {
		return "Huson et al, 2021; D.H. Huson, C. Bagci, B. Centikaya and D.J. Bryant (2021). Phylogenetic outlines. In preparation.";
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, SplitsBlock inputData, NetworkBlock outputData) throws IOException {
		System.err.println(getName() + ": not implemented");
	}

	public Layout getOptionLayout() {
		return optionLayout.get();
	}

	public ObjectProperty<Layout> optionLayoutProperty() {
		return optionLayout;
	}

	public boolean isOptionUseWeights() {
		return optionUseWeights.get();
	}

	public BooleanProperty optionUseWeightsProperty() {
		return optionUseWeights;
	}

	public PhyloSplitsGraph getGraph() {
		return graph;
	}

	@Override
	public boolean isApplicable(TaxaBlock taxa, SplitsBlock datablock) {
		return super.isApplicable(taxa, datablock) &&
			   (datablock.getCompatibility() == Compatibility.compatible
				|| datablock.getCompatibility() == Compatibility.cyclic);
	}
}
