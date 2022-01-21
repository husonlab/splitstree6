/*
 *  SplitMatrix.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.algorithms.utils;

import splitstree6.data.SplitsBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.data.parts.ASplit;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Specially designed container to store many collections of splits.
 * <p/>
 * The rows correspond to splits and the columns to blocks (different sets of splits).
 * <p/>
 * The rows are indexed from 1 to number of splits
 * The blocks are indexed from 1 to nblocks.
 * Daniel Huson and David Bryant, 2008
 */
public class SplitMatrix {

	private final int ntax;

	private int nblocks;   //Number of Split sets
	private final SparseTable<Double> matrix;     //Split weights, indexed by split and then split set.

	private final Map<String, Integer> splitIndices; // Map from splits to indices
	private final SplitsBlock allSplits; //Splits block containing all splits

	/**
	 * Constructs a SplitMatrix from a set of trees
	 *
	 * @param trees
	 * @param taxa
	 */
	public SplitMatrix(TreesBlock trees, TaxaBlock taxa) throws SplitsException {
		ntax = taxa.getNtax();
		matrix = new SparseTable<>();
		splitIndices = new HashMap<>();
		allSplits = new SplitsBlock();

		for (int i = 0; i < trees.getNTrees(); i++) {
			SplitsBlock splitsBlock = new SplitsBlock();
			TreesUtilities.computeSplits(null, trees.getTrees().get(i), splitsBlock.getSplits());
			SplitsUtilities.verifySplits(splitsBlock.getSplits(), taxa);
		}
	}

	/**
	 * Searches for a split in the matrix. Returns -1 if the split is not found
	 *
	 * @return index (1..nsplits in matrix) or -1 if split is not found.
	 */
	public int findSplit(BitSet sp) {
		String s;

		if (sp.get(1))   //Index splits by their half not containing 1.
			s = getComplement(sp, getNtax()).toString();
		else
			s = sp.toString();

		return splitIndices.getOrDefault(s, -1);
	}

	/**
	 * Returns the index of a given split.
	 * If the split is not currently in the matrix then memory is allocated as necc.,
	 * the new split is inserted in allSplits, and
	 * the index of the new split position is returned.
	 *
	 * @return index
	 */
	private int findOrAddSplit(BitSet sp) {
		int newid = findSplit(sp);
		if (newid < 0) {
			newid = allSplits.getNsplits() + 1;
			String s;
			if (sp.get(1))
				s = getComplement(sp, getNtax()).toString();
			else
				s = sp.toString();
			splitIndices.put(s, newid);
			ASplit split = new ASplit(sp, getNtax());
			allSplits.getSplits().add(split);
		}
		return newid;
	}


	/**
	 * Adds a new block with a new set of splits and stores weights in a new block.
	 */
	public void add(SplitsBlock newSplits) {
		int newBlockId = getNblocks() + 1;
		for (int i = 0; i < newSplits.getNsplits(); i++) {
			BitSet sp = newSplits.getSplits().get(i).getA(); // todo A?
			int id = findOrAddSplit(sp);
			set(id, newBlockId, newSplits.getSplits().get(i).getWeight());
		}
		nblocks++;
	}

	/**
	 * Adds a block of splits, but does not create a new block. Essentially adds empty rows
	 * to the split matrix. Splits that are already present in the matrix will not be added,
	 * the other splits will be added in the order that they appear in newSplits.
	 */
	public void addSplitsWithoutBlock(SplitsBlock newSplits) {
		for (int i = 0; i < newSplits.getNsplits(); i++) {
			//TaxaSet sp = newSplits.get(i);
			BitSet sp = newSplits.getSplits().get(i).getA();
			findOrAddSplit(sp);
		}
	}

	/**
	 * Returns a split weight, or 0.0 if that block doesn't have that split.
	 *
	 * @return weight
	 */
	public double get(int split, int blockNum) {
		return matrix.get(split, blockNum);
	}


	/**
	 * Sets the weight for a particular split (here indexed 1... nsplits in matrix)
	 */
	public void set(int splitNum, int blockNum, double val) {
		matrix.set(splitNum, blockNum, val);
	}

	/**
	 * Return the split as indexed in matrix.
	 *
	 * @return TaxaSet
	 */
	public BitSet getSplit(int id) {
		return allSplits.getSplits().get(id).getA();
	}

	/**
	 * Returns a Splits block with all splits contained in the matrix.
	 *
	 * @return Splits
	 */
	public SplitsBlock getSplits() {
		return allSplits;
	}

	/**
	 * Return number of blocks currently stored. Blocks are indexed 1..nblocks
	 *
	 * @return int number of blocks
	 */
	public int getNblocks() {
		return nblocks;
	}

	/**
	 * Returns the number of taxa that these are splits for.
	 *
	 * @return int Number of taxa.
	 */
	public int getNtax() {
		return ntax;
	}

	/**
	 * Return number of splits currently stored
	 */
	public int getNsplits() {
		return allSplits.getNsplits();
	}

	/**
	 * Returns the complement of the set
	 *
	 * @return the set 1..ntax minus this set
	 */
	public BitSet getComplement(BitSet bits, int ntax) {
		BitSet result = new BitSet();
		for (int i = 1; i <= ntax; i++)
			if (!bits.get(i))
				result.set(i);
		return result;
	}


	public void print() {
		System.out.println("printText Split matrix:");
		for (int i = 0; i <= allSplits.getNsplits(); i++) {
			for (int j = 0; j <= nblocks; j++) {
				System.out.print(matrix.get(i, j) + " ");
			}
			System.out.println();
		}
	}
}
