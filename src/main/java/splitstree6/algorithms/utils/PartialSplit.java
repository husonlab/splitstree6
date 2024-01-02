/*
 * PartialSplit.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.algorithms.utils;


import jloda.util.Basic;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.BitSet;
import java.util.Comparator;

/**
 * This is a simple implementation of a partial split
 *
 * @author huson
 * Date: 19-Feb-2004
 */
public class PartialSplit implements Comparator<PartialSplit>, Cloneable {

	BitSet A;
	BitSet B;
	float weight = 1;
	float confidence = 1;

	private static final int NOT_SET_YET = -1;
	private int hashCode = NOT_SET_YET;

	/**
	 * default constructor
	 */
	public PartialSplit() {
	}

	/**
	 * sets only one half of the split, assumes the rest will be set later
	 */
	public PartialSplit(BitSet A) {
		this.A = A;
	}

	/**
	 * sets only one half of the split, assumes the rest will be set later
	 * and weight and confidence
	 */
	public PartialSplit(BitSet A, float weight, float confidence) {
		this.A = A;
		this.weight = weight;
		this.confidence = confidence;
	}

	/**
	 * constructor given both sides A and B, a weight and confidence
	 */
	public PartialSplit(BitSet A, BitSet B) {
		set(A, B);
	}

	/**
	 * constructor given both sides A and B, a weight and confidence
	 */
	public PartialSplit(BitSet A, BitSet B, float weight, float confidence) {
		set(A, B, weight, confidence);
	}

	/**
	 * set boths sides
	 */
	public void set(BitSet A, BitSet B) {
		if (compareSides(A, B) <= 0) {
			this.A = A;
			this.B = B;
		} else {
			this.A = B;
			this.B = A;
		}
	}

	/**
	 * set boths sides and the weight and confidence
	 */
	public void set(BitSet A, BitSet B, float weight, float confidence) {
		set(A, B);
		this.weight = weight;
		this.confidence = confidence;
	}

	/**
	 * assuming only the first side has been set, sets the second as the complement in all
	 */
	public void setComplement(BitSet all) {
		BitSet C = (BitSet) all.clone();
		C.andNot(this.A);
		set(this.A, C);
	}

	/**
	 * compare the two sides of a split
	 *
	 * @return -1 if A lexicographically first, 1 if B first, and 0 if equals
	 */
	public static int compareSides(BitSet A, BitSet B) {
		//BitSet sa = A.getBits();
		int a = A.nextSetBit(0);
		//BitSet sb = B.getBits();
		int b = B.nextSetBit(0);

		do {
			if (a < b)
				return -1;
			else if (a > b)
				return 1;
			a = A.nextSetBit(a + 1);
			b = B.nextSetBit(b + 1);
		} while (a >= 0 || b >= 0);
		return 0;
	}

	/**
	 * returns string representation
	 */
	public String toString() {
		Writer w = new StringWriter();
		try {
			write(w);
		} catch (IOException ex) {
			Basic.caught(ex);
		}
		return w.toString();
	}

	/**
	 * writes the partial split
	 */
	public void write(Writer w) throws IOException {
		//BitSet bs = A.getBits();
		int t;
		for (t = A.nextSetBit(0); t >= 0; t = A.nextSetBit(t + 1))
			w.write(" " + t);
		w.write(" |");
		//bs = B.getBits();
		for (t = B.nextSetBit(0); t >= 0; t = B.nextSetBit(t + 1))
			w.write(" " + t);
		w.write(" w " + weight + " c " + confidence);
	}

	public BitSet getA() {
		return A;
	}

	public void setA(BitSet a) {
		A = a;
	}

	public BitSet getB() {
		return B;
	}

	public void setB(BitSet b) {
		B = b;
	}

	public float getWeight() {
		return weight;
	}

	public void setWeight(float weight) {
		this.weight = weight;
	}

	public float getConfidence() {
		return confidence;
	}

	public void setConfidence(float confidence) {
		this.confidence = confidence;
	}

	/**
	 * gets the size of the set X covered by both sides of the split
	 *
	 * @return size of ground set X
	 */
	public int getXsize() {
		return A.cardinality() + B.cardinality();
	}

	/**
	 * gets the support set X
	 */
	public BitSet getX() {
		BitSet X = new BitSet();
		X.or(A);
		X.or(B);
		return X;
	}

	/**
	 * comparator
	 *
	 * @return -1, 0 or 1
	 */
	public int compare(PartialSplit ps1, PartialSplit ps2) {
		int comp1 = PartialSplit.compareSides(ps1.getA(), ps2.getA());
		if (comp1 != 0)
			return comp1;
		return PartialSplit.compareSides(ps1.getB(), ps2.getB());
	}

	/**
	 * equals partial splits?
	 *
	 * @return true, if equals as partial splits
	 */
	public boolean equals(Object ps) {
		if (!(ps instanceof PartialSplit))
			return false;
		if (ps.hashCode() != this.hashCode()) return false;
		//System.err.println("EQ");

		boolean equals = compare(this, (PartialSplit) ps) == 0;
		//if (!equals) {
		if (false) {
			System.err.println("x: " + this.toString());
			System.err.println("y: " + ps.toString());

			Thread.dumpStack();
		}
		return equals;
	}

	/**
	 * returns true, if this is an extension (or equals) ps
	 *
	 * @return true if extension
	 */
	public boolean isExtensionOf(PartialSplit ps) {
		return (contains(getA(), ps.getA()) && contains(getB(), ps.getB())
				&& (getA().cardinality() != ps.getA().cardinality() || getB().cardinality() != ps.getB().cardinality()))
			   || (contains(getA(), ps.getB()) && contains(getB(), ps.getA())
				   && (getA().cardinality() != ps.getB().cardinality() || getB().cardinality() != ps.getA().cardinality()));
	}

	/**
	 * computeConsensusAndCycle the zig-zag rule, if applicable. That is, replaces A1/B1 and A2/B2
	 * by  A1/(B1uB2)  and (A1uA2)/B2
	 *
	 * @param ps1 input partial split 1
	 * @param ps2 input partial split 2
	 * @param qs1 output 1
	 * @param qs2 output2
	 * @return true, if rule was applied and resulting splits differ from the original
	 * ones
	 */
	public static boolean applyZigZagRule(PartialSplit ps1, PartialSplit ps2,
										  PartialSplit qs1, PartialSplit qs2) {
		for (int i = 0; i <= 1; i++) {
			BitSet A1 = ps1.getSide(i);
			BitSet B1 = ps1.getSide(1 - i);
			for (int j = 0; j <= 1; j++) {
				BitSet A2 = ps2.getSide(j);
				BitSet B2 = ps2.getSide(1 - j);

				if (A1.intersects(A2) && A2.intersects(B1) && B1.intersects(B2) && !A1.intersects(B2)) {
					BitSet B1uB2 = union(B1, B2);
					qs1.set(A1, B1uB2);

					BitSet A1uA2 = union(A1, A2);
					qs2.set(A1uA2, B2);
					// System.err.println("[" + i + "," + j + "] " + ps1 + " + " + ps2 + " -> " + qs1 + " + " + qs2);
					return !((ps1.equals(qs1) && ps2.equals(qs2)) || (ps1.equals(qs2) && ps2.equals(qs1)));
				}
			}
		}
		return false;
	}

	/**
	 * computeConsensusAndCycle the zig-zag rule, if applicable. That is, replaces A1/B1 and A2/B2
	 * by  A1/(B1uB2)  and (A1uA2)/B2
	 *
	 * @param ps1 input partial split 1
	 * @param ps2 input partial split 2
	 * @param qs1 output 1
	 * @param qs2 output2
	 * @return true, if rule was applied and resulting splits differ from the original
	 * ones
	 */
	public static boolean applyZigZagRuleAlsoOnInconsistent(PartialSplit ps1, PartialSplit ps2,
															PartialSplit qs1, PartialSplit qs2, PartialSplit qs3, PartialSplit qs4) {
		for (int i = 0; i <= 1; i++) {
			BitSet A1 = ps1.getSide(i);
			BitSet B1 = ps1.getSide(1 - i);
			for (int j = 0; j <= 1; j++) {
				BitSet A2 = ps2.getSide(j);
				BitSet B2 = ps2.getSide(1 - j);

				if (A1.intersects(A2) && A2.intersects(B1) && B1.intersects(B2)) {
					if (!A1.intersects(B2)) {
						//this is the original rule
						BitSet B1uB2 = union(B1, B2);
						qs1.set(A1, B1uB2);

						BitSet A1uA2 = union(A1, A2);
						qs2.set(A1uA2, B2);
					} else {
						//we got incompatibility !
						{
							BitSet A1_mod = (BitSet) A1.clone();
							BitSet B1uB2 = union(B1, B2);
							A1_mod.andNot(B2);
							qs1.set(A1_mod, B1uB2);
						}
						{
							BitSet B1uB2_mod = union(B1, B2);
							B1uB2_mod.andNot(A1);
							qs1.set(A1, B1uB2_mod);
						}

						{
							BitSet A1uA2_mod = union(A1, A2);
							A1uA2_mod.andNot(B2);
							qs2.set(A1uA2_mod, B2);
						}
						{
							BitSet A1uA2 = union(A1, A2);
							BitSet B2_mod = (BitSet) B2.clone();
							B2_mod.andNot(A1);
							qs2.set(A1uA2, B2_mod);
						}
					}
					// System.err.println("[" + i + "," + j + "] " + ps1 + " + " + ps2 + " -> " + qs1 + " + " + qs2);
					return !((ps1.equals(qs1) && ps2.equals(qs2)) || (ps1.equals(qs2) && ps2.equals(qs1)));
				}
			}
		}
		return false;
	}


	/**
	 * applies the one-sided rule: if A1=A2 and B1!=B2, then replace both by
	 * A1 vs B1 u B2
	 *
	 * @param ps1 input partial split
	 * @param ps2 input partial split
	 * @param qs1 output partial split to replace ps1 and ps2
	 * @return true, if rule applies, false else
	 */
	public static boolean applyOneSideRule(PartialSplit ps1, PartialSplit ps2, PartialSplit qs1) {
		for (int i = 0; i <= 1; i++) {
			BitSet A1 = ps1.getSide(i);
			BitSet B1 = ps1.getSide(1 - i);
			for (int j = 0; j <= 1; j++) {
				BitSet A2 = ps2.getSide(j);
				BitSet B2 = ps2.getSide(1 - j);

				if (A1.equals(A2) && !B1.equals(B2)) {
					BitSet B1uB2 = union(B1, B2);
					qs1.set(A1, B1uB2);
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * get the 0 or 1 side of the split
	 *
	 * @return A, if i=0 and B, else
	 */
	private BitSet getSide(int i) {

		if (i == 0)
			return getA();
		else
			return getB();
	}


	/**
	 * is split non-trivial?
	 *
	 * @return true, if non-trivial
	 */
	public boolean isNonTrivial() {
		boolean result = true;
		if ((A == null) || (B == null)) return false;
		try {
			result = A.cardinality() > 1 && B.cardinality() > 1;
		} catch (Exception e) {
			e.printStackTrace();  //To change body of catch statement use OptionIO | File Templates.
		}

		return result;
	}

	/**
	 * returns the split induced by the given BitSet.
	 * Returns null, if A=0 or B=0
	 *
	 * @return projected split or null
	 */
	public PartialSplit getInduced(BitSet bitSet) {
		BitSet Ap = (BitSet) getA().clone();
		BitSet Bp = (BitSet) getB().clone();
		Ap.and(bitSet);
		Bp.and(bitSet);
		if (Ap.cardinality() > 0 && Bp.cardinality() > 0
			&& Ap.cardinality() + Bp.cardinality() == bitSet.cardinality())
			return new PartialSplit(Ap, Bp, getWeight(), getConfidence());
		else
			return null;
	}

	/**
	 * gets a clone
	 *
	 * @return a clone
	 */
	public Object clone() {
		return new PartialSplit(getA(), getB(), getWeight(), getConfidence());
	}

	/**
	 * returns a hash code
	 *
	 * @return hash code
	 */
	public int hashCode() {
		//System.err.println("HASH");
		//calc lazy
		if (this.hashCode == NOT_SET_YET) {
			int a = A.hashCode();
			int b = B.hashCode();
			int smaller = Math.min(a, b);
			int larger = Math.max(a, b);
			this.hashCode = smaller * 1111 + larger;
		}
		return this.hashCode;
	}

	/**
	 * is this partial split compatible to the one given?
	 *
	 * @return true, if splits are compatible
	 */
	public boolean isCompatible(PartialSplit ps) {
		return (!getA().intersects(ps.getA())
				|| !getA().intersects(ps.getB())
				|| !getB().intersects(ps.getA())
				|| !getB().intersects(ps.getB()));
	}

	/**
	 * returns the union of two sets
	 *
	 * @return union
	 */
	public static BitSet union(BitSet b1, BitSet b2) {
		BitSet result = new BitSet();
		result.or(b1);
		result.or(b2);
		return result;
	}

	/**
	 * does this set contain the given set ts?
	 *
	 * @return true, if this set contains set ts
	 */
	public static boolean contains(BitSet thisSet, BitSet ts) {
		BitSet tmp = (BitSet) ts.clone();
		tmp.and(thisSet);
		return tmp.cardinality() == ts.cardinality();
	}
}
