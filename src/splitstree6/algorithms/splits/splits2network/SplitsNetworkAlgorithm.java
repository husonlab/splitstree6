/*
 *  SplitsNetworkAlgorithm.java Copyright (C) 2021 Daniel H. Huson
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

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class SplitsNetworkAlgorithm extends Splits2Network {

	public enum Algorithm {EqualAngleConvexHull, EqualAngleOnly, ConvexHullOnly}

	private final ObjectProperty<Algorithm> optionAlgorithm = new SimpleObjectProperty<>(Algorithm.EqualAngleConvexHull);

	public enum Layout {Circular, MidPointRooted, MidPointRootedAlt, RootBySelectedOutgroup, RootBySelectedOutgroupAlt}

	private final ObjectProperty<Layout> optionLayout = new SimpleObjectProperty<>(Layout.Circular);

	private final BooleanProperty optionUseWeights = new SimpleBooleanProperty(true);

	private final PhyloSplitsGraph graph = new PhyloSplitsGraph();

	public List<String> listOptions() {
		return Arrays.asList("optionAlgorithm", "optionUseWeights", "optionLayout", "optionBoxOpenIterations", "optionDaylightIterations");
	}

	@Override
	public String getCitation() {
		return "Dress & Huson 2004; " +
			   "A.W.M. Dress and D.H. Huson, Constructing splits graphs, " +
			   "IEEE/ACM Transactions on Computational Biology and Bioinformatics 1(3):109-115, 2004.";
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, SplitsBlock inputData, NetworkBlock outputData) throws IOException {
		System.err.println(getName() + ": not implemented");
	}

	public Algorithm getOptionAlgorithm() {
		return optionAlgorithm.get();
	}

	public ObjectProperty<Algorithm> optionAlgorithmProperty() {
		return optionAlgorithm;
	}

	public void setOptionAlgorithm(Algorithm optionAlgorithm) {
		this.optionAlgorithm.set(optionAlgorithm);
	}

	public Layout getOptionLayout() {
		return optionLayout.get();
	}

	public ObjectProperty<Layout> optionLayoutProperty() {
		return optionLayout;
	}

	public void setOptionLayout(Layout optionLayout) {
		this.optionLayout.set(optionLayout);
	}

	public boolean isOptionUseWeights() {
		return optionUseWeights.get();
	}

	public BooleanProperty optionUseWeightsProperty() {
		return optionUseWeights;
	}

	public void setOptionUseWeights(boolean optionUseWeights) {
		this.optionUseWeights.set(optionUseWeights);
	}
}
