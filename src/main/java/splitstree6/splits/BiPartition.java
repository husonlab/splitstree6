/*
 *  BiPartition.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.splits;

import jloda.util.BitSetUtils;
import jloda.util.StringUtils;

import java.util.BitSet;
import java.util.Objects;

/**
 * bipartitioning
 * Daniel Huson, 5.2022
 */
public class BiPartition implements Comparable<BiPartition> {
	private final BitSet A;
	private final BitSet B;

	public BiPartition(BitSet A, BitSet B) {
		if (A.cardinality() == 0 || B.cardinality() == 0)
			System.err.println("Internal error: A.size()=" + A.cardinality() + ", B.size()=" + B.cardinality());
		if (A.nextSetBit(1) < B.nextSetBit(1)) {
			this.A = BitSetUtils.copy(A);
			this.B = BitSetUtils.copy(B);
		} else {
			this.A = BitSetUtils.copy(B);
			this.B = BitSetUtils.copy(A);
		}
	}

	public int ntax() {
		return A.cardinality() + B.cardinality();
	}

	public int size() {
		return Math.min(A.cardinality(), B.cardinality());
	}

	/**
	 * get part A
	 *
	 * @return A
	 */
	public BitSet getA() {
		return A;
	}

	/**
	 * get part B
	 *
	 * @return B
	 */
	public BitSet getB() {
		return B;
	}

	/**
	 * gets the split part that contains the given taxon, or A, if none contains it
	 *
	 * @return split part containing taxon t
	 */
	public BitSet getPartContaining(int t) {
		if (B.get(t))
			return B;
		else
			return A;
	}

	/**
	 * returns A, if A doesn't contain t, else B
	 *
	 * @return set not containing t
	 */
	public BitSet getPartNotContaining(int t) {
		if (!A.get(t))
			return A;
		else
			return B;
	}

	/**
	 * gets the smaller part. In the case of a tie, returns the set that contains the smallest element
	 *
	 * @return smaller part
	 */
	public BitSet getSmallerPart() {
		if (A.cardinality() < B.cardinality())
			return A;
		else if (A.cardinality() > B.cardinality())
			return B;
		if (A.nextSetBit(1) < B.nextSetBit(1))
			return A;
		else
			return B;
	}

	/**
	 * is taxon t contained in part A?
	 *
	 * @param t number from 1 to ntax
	 * @return true, if contained
	 */
	public boolean isContainedInA(int t) {
		return A.get(t);
	}

	/**
	 * is taxon t contained in part B?
	 *
	 * @param t number from 1 to ntax
	 * @return true, if contained
	 */
	public boolean isContainedInB(int t) {
		return B.get(t);
	}

	public static int compare(BiPartition a, BiPartition b) {
		var com = BitSetUtils.compare(a.getA(), b.getA());
		if (com == 0)
			com = BitSetUtils.compare(a.getB(), b.getB());
		return com;
	}


	/**
	 * determines whether two splits on the same taxa set are compatible
	 *
	 * @return true, if split1 and split2 are compatible
	 */
	public static boolean areCompatible(BiPartition split1, BiPartition split2) {
		var A1 = split1.getA();
		var B1 = split1.getB();
		var A2 = split2.getA();
		var B2 = split2.getB();
		return !A1.intersects(A2) || !A1.intersects(B2) || !B1.intersects(A2) || !B1.intersects(B2);
	}

	/**
	 * determines whether three splits on the same taxa set are weakly compatible
	 *
	 * @return true, if all three are weakly compatible
	 */
	public static boolean areWeaklyCompatible(BiPartition split1, BiPartition split2, BiPartition split3) {
		final BitSet A1 = split1.getA();
		final BitSet B1 = split1.getB();
		final BitSet A2 = split2.getA();
		final BitSet B2 = split2.getB();
		final BitSet A3 = split3.getA();
		final BitSet B3 = split3.getB();

		return !((BitSetUtils.intersects(A1, A2, A3) && BitSetUtils.intersects(A1, B2, B3) && BitSetUtils.intersects(B1, A2, B3) && BitSetUtils.intersects(B1, B2, A3))
				 || (BitSetUtils.intersects(B1, B2, B3) && BitSetUtils.intersects(B1, A2, A3) && BitSetUtils.intersects(A1, B2, A3) && BitSetUtils.intersects(A1, A2, B3)));
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) return true;
		if (obj == null || obj.getClass() != this.getClass()) return false;
		var that = (BiPartition) obj;
		return Objects.equals(this.A, that.A) && Objects.equals(this.B, that.B);
	}

	public boolean isTrivial() {
		return getSmallerPart().cardinality() == 1;
	}

	public boolean separates(int a, int b) {
		return getPartContaining(a) != getPartContaining(b);
	}

	@Override
	public int hashCode() {
		return Objects.hash(A, B);
	}


	@Override
	protected Object clone() {
		return new BiPartition(A, B);
	}

	@Override
	public String toString() {
		return "{" + StringUtils.toString(A, ",") + "} | {" + StringUtils.toString(B, ",") + "}";
	}

	public int compareTo(BiPartition other) {
		var result = BitSetUtils.compare(A, other.A);
		if (result == 0)
			result = BitSetUtils.compare(B, other.B);
		return result;
	}
}
