/*
 *  TraitsBlock.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.data;

import javafx.scene.paint.Color;
import jloda.fx.util.ColorUtilsFX;
import jloda.util.IteratorUtils;
import splitstree6.workflow.DataBlock;
import splitstree6.workflow.DataNode;
import splitstree6.workflow.DataTaxaFilter;

import java.util.*;

/**
 * traits block
 * daniel Huson, 2.2018
 */
public class TraitsBlock extends DataBlock implements IAdditionalDataBlock {
	public static final String BLOCK_NAME = "TRAITS";

	private double[][] matrix = {};  // computation is done on values
	private String[][] matrixOfLabels = null; // values have labels
	private String[] labels = {};
	private float[] traitLatitude = null;
	private float[] traitLongitude = null;
	private Color[] traitColor = null;
	private Color[] taxonColor = null;
	private TraitsNexusFormat format;

	/**
	 * default constructor
	 */
	public TraitsBlock() {
		format = new TraitsNexusFormat();
	}

	public TraitsBlock(TaxaBlock srcTaxa, TraitsBlock srcTraits) {
		this();
		setInducedTraits(srcTaxa, srcTraits, srcTaxa);
	}

	public void setDimensions(int ntax, int ntraits) {
		matrix = new double[ntax][ntraits];
		labels = new String[ntraits];
	}

	public void clear() {
		setDimensions(0, 0);
		matrixOfLabels = null;
		traitLatitude = null;
		traitLongitude = null;
		traitColor = null;
		taxonColor = null;
	}

	public int addTrait(String traitLabel) {
		var oldNTax = matrix.length;
		var oldNTraits = matrix.length == 0 ? 0 : matrix[0].length;
		{
			var tmp = new double[oldNTax][oldNTraits + 1];
			for (var tax = 0; tax < oldNTax; tax++) {
				System.arraycopy(matrix[tax], 0, tmp[tax], 0, oldNTraits);
			}
			matrix = tmp;
		}
		if (matrixOfLabels != null) {
			var tmp = new String[oldNTax][oldNTraits + 1];
			for (var tax = 0; tax < oldNTax; tax++) {
				System.arraycopy(matrixOfLabels[tax], 0, tmp[tax], 0, oldNTraits);
			}
			matrixOfLabels = tmp;
		}
		if (labels != null) {
			var tmp = new String[oldNTraits + 1];
			System.arraycopy(labels, 0, tmp, 0, oldNTraits);
			labels = tmp;
			labels[oldNTraits] = traitLabel;
		}
		if (traitLatitude != null) {
			var tmp = new float[oldNTraits + 1];
			System.arraycopy(traitLatitude, 0, tmp, 0, oldNTraits);
			traitLatitude = tmp;
		}
		if (traitLongitude != null) {
			var tmp = new float[oldNTraits + 1];
			System.arraycopy(traitLongitude, 0, tmp, 0, oldNTraits);
			traitLongitude = tmp;
		}
		if (traitColor != null) {
			var tmp = new Color[oldNTraits + 1];
			System.arraycopy(traitColor, 0, tmp, 0, oldNTraits);
			traitColor = tmp;
		}
		return oldNTraits + 1;
	}

	public void setTraitValue(int taxonId, int traitId, double value) {
		matrix[taxonId - 1][traitId - 1] = value;
	}

	public void setTraitValueLabel(int taxonId, int traitId, String label) {
		if (matrixOfLabels == null)
			matrixOfLabels = new String[matrix.length][getNTraits()]; // lazy create
		matrixOfLabels[taxonId - 1][traitId - 1] = label;
	}

	public double getTraitValue(int taxonId, int traitId) {
		if (taxonId <= 0 || taxonId > matrix.length || traitId <= 0 || traitId > matrix[0].length)
			return 0;
		else
			return matrix[taxonId - 1][traitId - 1];
	}

	public String getTraitValueLabel(int taxonId, int traitId) {
		if (matrixOfLabels == null || matrixOfLabels[taxonId - 1][traitId - 1] == null)
			return "" + getTraitValue(taxonId, traitId);
		else
			return matrixOfLabels[taxonId - 1][traitId - 1];
	}

	public String getTraitLabel(int traitId) {
		return labels[traitId - 1];
	}

	/**
	 * set trait label
	 *
	 * @param traitId id, 1-based
	 * @param label   label
	 */
	public void setTraitLabel(int traitId, String label) {
		labels[traitId - 1] = label;
	}

	public Collection<String> getTraitLabels() {
		return List.of(labels);
	}

	public float getTraitLongitude(int traitId) {
		return traitLongitude == null ? 0 : traitLongitude[traitId - 1];
	}

	public void setTraitLongitude(int traitId, float value) {
		if (!isSetLatitudeLongitude()) {
			traitLatitude = new float[getNTraits()];
			traitLongitude = new float[getNTraits()];
		}
		traitLongitude[traitId - 1] = value;
	}

	public float getTraitLatitude(int traitId) {
		return traitLatitude == null ? 0 : traitLatitude[traitId - 1];
	}

	public void setTraitLatitude(int traitId, float value) {
		if (!isSetLatitudeLongitude()) {
			traitLatitude = new float[getNTraits()];
			traitLongitude = new float[getNTraits()];
		}
		traitLatitude[traitId - 1] = value;
	}

	public String getTraitColorName(int traitId) {
		if (!isSetTraitColors() || traitColor[traitId - 1] == null)
			return null;
		else
			return ColorUtilsFX.getName(traitColor[traitId - 1]).replace("0x", "#");
	}

	public Color getTraitColor(int traitId) {
		if (!isSetTraitColors())
			return null;
		else
			return traitColor[traitId - 1];
	}

	public void setTraitColorName(int traitId, String color) {
		if (!isSetTraitColors()) {
			traitColor = new Color[getNTraits()];
		}
		traitColor[traitId - 1] = color == null ? null : Color.web(color);
	}

	public void setTraitColor(String traitName, Color color) {
		var id = getTraitId(traitName);
		if (id != -1)
			setTraitColorName(id, ColorUtilsFX.toStringCSS(color));
	}

	public String getTaxaColorName(int taxId) {
		if (!isSetTaxonColors() || taxonColor[taxId - 1] == null)
			return null;
		else return ColorUtilsFX.getName(taxonColor[taxId - 1]).replace("0x", "#");
	}

	public Color getTaxaColor(int taxId) {
		if (!isSetTaxonColors())
			return null;
		else return taxonColor[taxId - 1];
	}

	public void setTaxaColorName(int taxId, String color) {
		setTaxaColor(taxId, (color == null ? null : Color.web(color)));
	}

	public void setTaxaColor(int taxonId, Color color) {
		if (!isSetTaxonColors()) {
			taxonColor = new Color[matrix.length];
		}
		taxonColor[taxonId - 1] = color;
	}
	@Override
	public int size() {
		return getNTraits();
	}

	public int getNTraits() {
		return matrix.length == 0 ? 0 : matrix[0].length;
	}

	public boolean isSetLatitudeLongitude() {
		return traitLatitude != null;
	}

	public void clearLatitudeLongitude() {
		traitLatitude = null;
		traitLongitude = null;
	}

	public boolean isSetTraitColors() {
		return traitColor != null;
	}

	public void clearTraitColors() {
		traitColor = null;
	}

	public boolean isSetTaxonColors() {
		return taxonColor != null;
	}

	public void clearTaxonColors() {
		taxonColor = null;
	}

	/**
	 * is this trait numerical? We assume so if it doesn't have a label
	 *
	 * @param traitId the trait
	 * @return true, if unlabeled trait
	 */
	public boolean isNumerical(int traitId) {
		return matrixOfLabels == null || matrixOfLabels[0][traitId - 1] == null;
	}

	public Iterable<Integer> numericalTraits() {
		return () -> new Iterator<>() {
			private int t = 1;

			{
				while (t <= getNTraits() && !isNumerical(t))
					t++;
			}

			@Override
			public boolean hasNext() {
				return t <= getNTraits();
			}

			@Override
			public Integer next() {
				var result = t;
				do {
					t++;
				}
				while (t <= getNTraits() && !isNumerical(t));
				return result;
			}
		};
	}

	public Collection<String> getNumericalTraitLabels() {
		var list = new ArrayList<String>();
		for (var t : numericalTraits())
			list.add(getTraitLabel(t));
		return list;
	}


	public int getNumberNumericalTraits() {
		return IteratorUtils.count(numericalTraits());
	}

	public void setInducedTraits(TaxaBlock srcTaxa, TraitsBlock srcTraits, TaxaBlock targetTaxa) {
		clear();
		labels = Arrays.copyOf(srcTraits.labels, srcTraits.getNTraits());
		traitLongitude = (srcTraits.traitLongitude == null ? null : Arrays.copyOf(srcTraits.traitLongitude, srcTraits.getNTraits()));
		traitLatitude = (srcTraits.traitLatitude == null ? null : Arrays.copyOf(srcTraits.traitLatitude, srcTraits.getNTraits()));
		traitColor = (srcTraits.traitColor == null ? null : Arrays.copyOf(srcTraits.traitColor, srcTraits.getNTraits()));
		taxonColor = (srcTraits.taxonColor == null ? null : Arrays.copyOf(srcTraits.taxonColor, srcTraits.taxonColor.length));
		matrix = new double[targetTaxa.size()][srcTraits.getNTraits()];
		matrixOfLabels = null; // will be set in setTraitValueLabel if required
		for (var tarId = 1; tarId <= targetTaxa.getNtax(); tarId++) {
			var label = targetTaxa.getLabel(tarId);
			final int srcId = srcTaxa.indexOf(label);
			for (int traitId = 1; traitId <= srcTraits.getNTraits(); traitId++) {
				var value = srcTraits.getTraitValue(srcId, traitId);
				setTraitValue(tarId, traitId, srcTraits.getTraitValue(srcId, traitId));
				if (srcTraits.matrixOfLabels != null) {
					setTraitValueLabel(tarId, traitId, srcTraits.getTraitValueLabel(srcId, traitId));
				}
			}
		}
	}

	@Override
	public DataTaxaFilter<? extends DataBlock, ? extends DataBlock> createTaxaDataFilter() {
		return null;
	}

	@Override
	public TraitsBlock newInstance() {
		return (TraitsBlock) super.newInstance();
	}

	public TraitsNexusFormat getFormat() {
		return format;
	}

	public void setFormat(TraitsNexusFormat format) {
		this.format = format;
	}


	@Override
	public void updateShortDescription() {
		setShortDescription(String.format("%,d traits", size()));
	}

	@Override
	public String getBlockName() {
		return BLOCK_NAME;
	}

	@Override
	public void setNode(DataNode node) {
		super.setNode(node);
	}

	public double getMax(String traitLabel) {
		var traitId = getTraitId(traitLabel);
		var max = 0.0;
		for (var row : matrix) {
			max = Math.max(max, row[traitId - 1]);
		}
		return max;
	}

	public int getTraitId(String label) {
		for (var i = 0; i < labels.length; i++)
			if (labels[i].equals(label))
				return i + 1;
		return -1;
	}
}
