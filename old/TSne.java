/*
 *  TSne.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.algorithms.distances.distances2network;

import com.jujutsu.tsne.FastTSne;
import com.jujutsu.tsne.SimpleTSne;
import com.jujutsu.tsne.barneshut.BHTSne;
import com.jujutsu.tsne.barneshut.ParallelBHTsne;
import com.jujutsu.utils.TSneUtils;
import javafx.beans.property.*;
import jloda.util.StringUtils;
import jloda.util.progress.ProgressListener;
import splitstree6.data.DistancesBlock;
import splitstree6.data.NetworkBlock;
import splitstree6.data.TaxaBlock;

import java.io.IOException;
import java.util.List;

/**
 * runs tSNE on a editDistance matrix
 * Daniel Huson, 5.2021
 */
public class TSne extends Distances2Network {
	public enum Method {ParallelBarnesHutSne, BarnesHutTSne, FastTSne, SimpleTSne}

	private final ObjectProperty<Method> optionMethod = new SimpleObjectProperty<>(this, "optionMethod", Method.ParallelBarnesHutSne);

	private final DoubleProperty optionPerplexity = new SimpleDoubleProperty(this, "optionPerplexity", -1);
	private final IntegerProperty optionIterations = new SimpleIntegerProperty(this, "optionIterations", 1000);

	@Override
	public String getCitation() {
		return "Maaten and Hinton 2008;Van der Maaten  and Hinton 2008. Visualizing data using t-SNE. Journal of machine learning research, 9(11)";
	}


	public List<String> listOptions() {
		return List.of(optionMethod.getName(), optionPerplexity.getName(), optionIterations.getName());
	}

	@Override
	public String getToolTip(String optionName) {
		if (optionName.equals(optionMethod.getName())) {
			return "Choose the calculation method";
		} else if (optionName.equals(optionPerplexity.getName())) {
			return "Set the perplexity parameter, for -1 will use square-root of number of taxa";
		} else if (optionName.equals(optionIterations.getName())) {
			return "Number of iterations";
		}
		return optionName;
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, DistancesBlock distancesBlock, NetworkBlock networkBlock) throws IOException {
		final var ntax = taxaBlock.getNtax();

		com.jujutsu.tsne.TSne tSne = switch (getOptionMethod()) {
			case SimpleTSne -> new SimpleTSne();
			case FastTSne -> new FastTSne();
			case BarnesHutTSne -> new BHTSne();
			case ParallelBarnesHutSne -> new ParallelBHTsne();
		};

		var perplexity = (getOptionPerplexity() <= 0 ? Double.parseDouble(StringUtils.removeTrailingZerosAfterDot("%.1f", Math.sqrt(taxaBlock.getNtax()))) : getOptionPerplexity());

		//    public static TSneConfiguration buildConfig(double[][] xin, int outputDims, int initial_dims, double perplexity, int max_iter, boolean use_pca, double theta, boolean silent, boolean printError) {

		var config = TSneUtils.buildConfig(distancesBlock.getDistances(), 2, taxaBlock.getNtax(), perplexity, getOptionIterations(), true, 0.5D, true, true);
		var points = tSne.tsne(config);

		networkBlock.setNetworkType(NetworkBlock.Type.Points);

		final var graph = networkBlock.getGraph();

		for (var t = 1; t <= ntax; t++) {
			final var v = graph.newNode(t);
			graph.addTaxon(v, t);
			graph.setLabel(v, taxaBlock.get(t).getDisplayLabelOrName());
			networkBlock.getNodeData(v).put(NetworkBlock.NodeData.BasicKey.x.name(), String.valueOf(points[t - 1][0]));
			networkBlock.getNodeData(v).put(NetworkBlock.NodeData.BasicKey.y.name(), String.valueOf(points[t - 1][1]));
		}

		networkBlock.setInfoString("tSNE on %,d taxa,  perplexity=".formatted(taxaBlock.getNtax()) + StringUtils.removeTrailingZerosAfterDot("%.1f", perplexity));
	}

	public double getOptionPerplexity() {
		return optionPerplexity.getValue();
	}

	public DoubleProperty optionPerplexityProperty() {
		return optionPerplexity;
	}

	public void setOptionPerplexity(double optionPerplexity) {
		this.optionPerplexity.setValue(optionPerplexity);
	}

	public int getOptionIterations() {
		return optionIterations.getValue();
	}

	public IntegerProperty optionIterationsProperty() {
		return optionIterations;
	}

	public void setOptionIterations(int optionIterations) {
		this.optionIterations.setValue(optionIterations);
	}

	public Method getOptionMethod() {
		return optionMethod.get();
	}

	public ObjectProperty<Method> optionMethodProperty() {
		return optionMethod;
	}

	public void setOptionMethod(Method optionMethod) {
		this.optionMethod.set(optionMethod);
	}
}