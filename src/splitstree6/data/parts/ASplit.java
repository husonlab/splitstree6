/*
 * ASplit.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.data.parts;

import jloda.util.BitSetUtils;

import java.util.BitSet;

/**
 * split implementation
 * Daniel Huson, 12/9/16.
 */
public final class ASplit extends BiPartition {
	private double weight;
	private double confidence;
	private String label;

	/**
	 * constructor
	 */
	public ASplit(BitSet A, BitSet B) {
		this(A, B, 1, -1, null);
	}

	/**
	 * constructor
	 */
	public ASplit(BitSet A, BitSet B, double weight) {
		this(A, B, weight, -1, null);
	}

	/**
	 * constructor
	 */
	public ASplit(BitSet A, BitSet B, double weight, double confidence) {
		this(A, B, weight, confidence, null);
	}

	public ASplit(BitSet A, BitSet B, double weight, double confidence, String label) {
		super(A, B);
		this.weight = weight;
		this.confidence = confidence;
		this.label = label;
	}

	/**
	 * constructor
	 */
	public ASplit(BitSet A, int ntax) {
		this(A, ntax, 1, -1, null);
	}

	/**
	 * constructor
	 */
	public ASplit(BitSet A, int ntax, double weight) {
		this(A, ntax, weight, -1, null);
	}

	/**
	 * constructor
	 */
	public ASplit(BitSet A, int ntax, double weight, double confidence) {
		this(A, ntax, weight, confidence, null);
	}

	/**
	 * constructor
	 */
	public ASplit(BitSet A, int ntax, double weight, double confidence, String label) {
		this(A, BitSetUtils.getComplement(A, 1, ntax + 1), weight, confidence, label);
	}


	public ASplit(ASplit src) {
		this(src.getA(), src.getB(), src.getWeight(), src.getConfidence(), src.getLabel());
	}

	public double getWeight() {
		return weight;
	}

	public void setWeight(double weight) {
		this.weight = weight;
	}

	public double getConfidence() {
		return confidence;
	}

	public void setConfidence(double confidence) {
		this.confidence = confidence;
	}

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public BitSet getAllTaxa() {
		return BitSetUtils.union(getA(), getB());
	}

	public String toString() {
		return super.toString() + " weight=" + getWeight() + " confidence=" + getConfidence() + " label=" + getLabel();
	}

	/**
	 * is this equals to the given split in terms of A and B
	 *
	 * @return true, if obj is instance of ASplit and has the sets A and B
	 */
	@Override
	public boolean equals(Object obj) {
		if (obj instanceof final ASplit that) {
			return getPartContaining(1).equals(that.getPartContaining(1)) && getPartNotContaining(1).equals(that.getPartNotContaining(1));
		} else
			return false;
	}

	public ASplit clone() {
		return new ASplit(getA(), ntax(), getWeight(), getConfidence(),getLabel());
	}
}
