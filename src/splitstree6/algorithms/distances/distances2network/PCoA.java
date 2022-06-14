/*
 * MinSpanningNetwork.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.algorithms.distances.distances2network;

import Jama.EigenvalueDecomposition;
import Jama.Matrix;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import jloda.phylo.PhyloGraph;
import jloda.util.StringUtils;
import jloda.util.progress.ProgressListener;
import splitstree6.data.DistancesBlock;
import splitstree6.data.NetworkBlock;
import splitstree6.data.TaxaBlock;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * computes a minimum spanning network
 */
public class PCoA extends Distances2Network {
	private Matrix distanceMatrix;
	private double totalSquaredDistance;
	private int rank;
	private int numberOfPositiveEigenValues;
	private double[] eigenValues;
	private double[] percentExplained;
	private final Map<String, double[]> name2vector = new HashMap<>();
	private double[][] vectors;
	private boolean done = false;

	private final IntegerProperty optionFirstCoordinate = new SimpleIntegerProperty(this, "optionFirstCoordinate", 1);
	private final IntegerProperty optionSecondCoordinate = new SimpleIntegerProperty(this, "optionSecondCoordinate", 2);


	@Override
	public String getCitation() {
		return "Gower 1966; Gower, J. C. (1966). Some distance properties of latent root and vector methods used in multivariate analysis. Biometrika, 53(3-4), 325-338.";
	}

	public List<String> listOptions() {
		return List.of(optionFirstCoordinate.getName(), optionSecondCoordinate.getName());
	}

	@Override
	public String getToolTip(String optionName) {
		return switch (optionName) {
			case "FirstCoordinate" -> "Choose principal component for the x Axis";
			case "SecondCoordinate" -> "Choose principal component for the y Axis";
			default -> optionName;
		};
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, DistancesBlock distancesBlock, NetworkBlock networkBlock) throws IOException {
		progress.setMaximum(6);
		progress.setProgress(0);

		rank = taxaBlock.getNtax();
		distanceMatrix = new Matrix(rank, rank);
		double sum = 0;
		for (int i = 0; i < rank; i++) {
			for (int j = 0; j < rank; j++) {
				if (i == j)
					distanceMatrix.set(i, j, 0);
				else {
					double d = distancesBlock.get(i + 1, j + 1);
					distanceMatrix.set(i, j, d);
					sum += d * d;
				}
			}
		}
		totalSquaredDistance = 2 * sum;
		vectors = new double[rank][];

		progress.incrementProgress();

		final Matrix centered = computeDoubleCenteringOfSquaredMatrix(distanceMatrix);

		final EigenvalueDecomposition eigenValueDecomposition = centered.eig();
		final Matrix eigenVectors = eigenValueDecomposition.getV();

		numberOfPositiveEigenValues = 0;
		Matrix positiveEigenValues = eigenValueDecomposition.getD();
		for (int i = 0; i < rank; i++) {
			if (positiveEigenValues.get(i, i) > 0)
				numberOfPositiveEigenValues++;
			else
				positiveEigenValues.set(i, i, 0);
		}

		progress.incrementProgress();

		// multiple eigenvectors by sqrt of eigenvalues
		Matrix scaledEigenVectors = (Matrix) eigenVectors.clone();
		for (int i = 0; i < rank; i++) {
			for (int j = 0; j < rank; j++) {
				double v = scaledEigenVectors.get(i, j);
				v = v * Math.sqrt(positiveEigenValues.get(j, j));
				scaledEigenVectors.set(i, j, v);
			}
		}

		progress.incrementProgress();

		final int[] indices = sortValues(positiveEigenValues);

		eigenValues = new double[numberOfPositiveEigenValues];
		percentExplained = new double[numberOfPositiveEigenValues];

		double total = 0;
		for (int j = 0; j < numberOfPositiveEigenValues; j++) {
			total += eigenValues[j] = positiveEigenValues.get(indices[j], indices[j]);
		}
		System.err.println("Positive eigenvalues:");
		System.err.println(StringUtils.toString("%.6f", eigenValues, ", "));

		if (total > 0) {
			for (int j = 0; j < eigenValues.length; j++) {
				percentExplained[j] = 100.0 * eigenValues[j] / total;
			}

			System.err.println("Percent explained:");
			System.err.println(StringUtils.toString("%.1f%%", percentExplained, ", "));
		}

		progress.incrementProgress();

		for (int i = 0; i < rank; i++) {
			String name = taxaBlock.getLabel(i + 1);
			double[] vector = new double[numberOfPositiveEigenValues];
			name2vector.put(name, vector);
			vectors[i] = vector;
			for (int j = 0; j < numberOfPositiveEigenValues; j++) {
				vector[j] = scaledEigenVectors.get(i, indices[j]);
			}
		}
		done = true;

		setOptionFirstCoordinate(Math.min(numberOfPositiveEigenValues, getOptionFirstCoordinate()));
		setOptionSecondCoordinate(Math.min(numberOfPositiveEigenValues, getOptionSecondCoordinate()));

		progress.incrementProgress();

		networkBlock.setNetworkType(NetworkBlock.Type.Points);

		final PhyloGraph graph = networkBlock.getGraph();
		System.err.printf("Stress: %.6f%n", getStress(getOptionFirstCoordinate() - 1, getOptionSecondCoordinate() - 1));


		for (var t = 1; t <= taxaBlock.getNtax(); t++) {
			final double[] coordinates = getProjection(getOptionFirstCoordinate() - 1, getOptionSecondCoordinate() - 1, taxaBlock.get(t).getName());
			final var v = graph.newNode(t);
			graph.addTaxon(v, t);
			graph.setLabel(v, taxaBlock.get(t).getDisplayLabelOrName());
			networkBlock.getNodeData(v).put(NetworkBlock.NodeData.BasicKey.x.name(), String.valueOf(100 * coordinates[0]));
			networkBlock.getNodeData(v).put(NetworkBlock.NodeData.BasicKey.y.name(), String.valueOf(100 * coordinates[1]));
		}
		progress.incrementProgress();

		networkBlock.setInfoString("PCoA on %,d taxa,  PC-%d (%s%%) vs PC-%d (%s%%)".formatted(taxaBlock.getNtax(),
				getOptionFirstCoordinate(), StringUtils.removeTrailingZerosAfterDot("%.1f", percentExplained[getOptionFirstCoordinate() - 1]),
				getOptionSecondCoordinate(), StringUtils.removeTrailingZerosAfterDot("%.1f", percentExplained[getOptionSecondCoordinate() - 1])));
	}


	/**
	 * get coordinates for given name
	 *
	 * @return coordinates
	 */
	public double[] getCoordinates(String name) {
		return name2vector.get(name);
	}

	/**
	 * get i-th and j-th coordinates for given name
	 *
	 * @return (i, j)
	 */
	public double[] getProjection(int i, int j, String name) {
		double[] vector = name2vector.get(name);
		return new double[]{vector[i], vector[j]};
	}

	/**
	 * given i-th, j-th and k-th coordinates for given name
	 *
	 * @return (i, j, k)
	 */
	public double[] getProjection(int i, int j, int k, String name) {
		double[] vector = name2vector.get(name);
		return new double[]{vector[i], vector[j], vector[k]};
	}

	/**
	 * get rank
	 *
	 * @return rank
	 */
	public int getRank() {
		return rank;
	}


	/**
	 * compute centered inner product matrix
	 *
	 * @return new matrix
	 */
	private Matrix computeDoubleCenteringOfSquaredMatrix(Matrix matrix) {
		int size = matrix.getColumnDimension();
		Matrix result = new Matrix(matrix.getColumnDimension(), matrix.getRowDimension());
		for (int i = 0; i < size; i++) {
			for (int j = 0; j < size; j++) {
				double v1 = 0;
				for (int k = 0; k < size; k++) {
					v1 += matrix.get(k, j) * matrix.get(k, j) / size;
				}
				double v2 = 0;
				for (int k = 0; k < size; k++) {
					v2 += matrix.get(i, k) * matrix.get(i, k) / size;
				}
				double v3 = 0;
				for (int k = 0; k < size; k++) {
					for (int l = 0; l < size; l++) {
						v3 += matrix.get(k, l) * matrix.get(k, l) / (size * size);
					}
				}
				double v4 = matrix.get(i, j);
				result.set(i, j, 0.5 * (v1 + v2 - v3 - (v4 * v4)));
			}
		}
		return result;
	}

	/**
	 * sort indices by values
	 *
	 * @return sorted indices
	 * todo: replace by proper sorting
	 */
	private int[] sortValues(Matrix m) {
		double[] v = new double[m.getColumnDimension()];
		int[] index = new int[v.length];
		for (int i = 0; i < v.length; i++) {
			v[i] = m.get(i, i);
			index[i] = i;
		}

		for (int i = 0; i < v.length; i++) {
			for (int j = i + 1; j < v.length; j++) {
				if (Math.abs(v[i]) < Math.abs(v[j])) {
					double tmpValue = v[j];
					v[j] = v[i];
					v[i] = tmpValue;
					int tmpIndex = index[j];
					index[j] = index[i];
					index[i] = tmpIndex;
				}
			}
		}

		return index;
	}

	public boolean isDone() {
		return done;
	}

	public double[] getEigenValues() {
		return eigenValues;
	}

	public double getStress(int i, int j) {
		return getStress(new int[]{i, j});
	}

	public double getStress(int i, int j, int k) {
		return getStress(new int[]{i, j, k});
	}

	public double getStress(int[] indices) {
		double squaredSum = 0;
		for (int a = 0; a < rank; a++) {
			for (int b = 0; b < rank; b++) {
				if (a != b) {
					double d = 0;
					for (int z : indices) {
						d += (vectors[a][z] - vectors[b][z]) * (vectors[a][z] - vectors[b][z]);
					}
					d = Math.sqrt(d);
					squaredSum += (d - distanceMatrix.get(a, b)) * (d - distanceMatrix.get(a, b));
				}
			}
		}
		return Math.sqrt(squaredSum / totalSquaredDistance);
	}

	public int getOptionFirstCoordinate() {
		return optionFirstCoordinate.getValue();
	}

	public IntegerProperty optionFirstCoordinateProperty() {
		return optionFirstCoordinate;
	}

	public void setOptionFirstCoordinate(int optionFirstCoordinate) {
		if (optionFirstCoordinate > 0)
			this.optionFirstCoordinate.setValue(optionFirstCoordinate);
	}

	public int getOptionSecondCoordinate() {
		return optionSecondCoordinate.getValue();
	}

	public IntegerProperty optionSecondCoordinateProperty() {
		return optionSecondCoordinate;
	}

	public void setOptionSecondCoordinate(int optionSecondCoordinate) {
		if (optionSecondCoordinate > 0)
			this.optionSecondCoordinate.setValue(optionSecondCoordinate);
	}

}