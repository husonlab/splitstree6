/*
 *  TracedHyperSequence.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.compute.phylofusion;

import jloda.util.BitSetUtils;
import jloda.util.StringUtils;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

/**
 * a HyperSequence whose elements additionally carry, per taxon, the ids of the input trees that contributed it.
 * Taxa determine all algorithmic behaviour; the tree ids are metadata preserved through copy, restrict and merge.
 * <p>
 * Banu Cetinkaya, 2026
 */
public record TracedHyperSequence(ArrayList<TracedHyperSequence.Element> elements) {

	public record Element(BitSet taxa, Map<Integer, BitSet> treeIdsPerTaxon) {

		public Element(BitSet taxa) {
			this(taxa, new HashMap<>());
		}

		public Element(BitSet taxa, Map<Integer, BitSet> treeIdsPerTaxon) {
			this.taxa = (BitSet) taxa.clone();
			this.treeIdsPerTaxon = new HashMap<>();
			for (var e : treeIdsPerTaxon.entrySet()) {
				this.treeIdsPerTaxon.put(e.getKey(), (BitSet) e.getValue().clone());
			}
		}

		/**
		 * create an element in which every taxon carries the same tree id
		 */
		public static Element fromTaxaAndTree(BitSet taxa, int treeId) {
			var map = new HashMap<Integer, BitSet>();
			for (int taxon = taxa.nextSetBit(0); taxon >= 0; taxon = taxa.nextSetBit(taxon + 1)) {
				var trees = new BitSet();
				trees.set(treeId);
				map.put(taxon, trees);
			}
			return new Element(taxa, map);
		}

		public Element copy() {
			return new Element(taxa, treeIdsPerTaxon);
		}

		/**
		 * deep copy restricted to the given subset of taxa
		 */
		public Element inducedBy(BitSet subset) {
			var kept = BitSetUtils.intersection(taxa, subset);
			var map = new HashMap<Integer, BitSet>();
			for (int taxon = kept.nextSetBit(0); taxon >= 0; taxon = kept.nextSetBit(taxon + 1)) {
				var trees = treeIdsPerTaxon.get(taxon);
				if (trees != null) {
					map.put(taxon, (BitSet) trees.clone());
				}
			}
			return new Element(kept, map);
		}

		public void removeTaxa(BitSet toRemove) {
			taxa.andNot(toRemove);
			treeIdsPerTaxon.keySet().removeIf(toRemove::get);
		}

		public void restrictToTaxa(BitSet allowed) {
			taxa.and(allowed);
			treeIdsPerTaxon.keySet().removeIf(t -> !allowed.get(t));
		}

		public void addTree(int taxon, int treeId) {
			taxa.set(taxon);
			treeIdsPerTaxon.computeIfAbsent(taxon, k -> new BitSet()).set(treeId);
		}

		/**
		 * merge another element's metadata into this one taxon-wise
		 */
		public void mergeMetadata(Element other) {
			for (int taxon = other.taxa.nextSetBit(0); taxon >= 0; taxon = other.taxa.nextSetBit(taxon + 1)) {
				taxa.set(taxon);
				var trees = other.treeIdsPerTaxon.get(taxon);
				if (trees != null) {
					treeIdsPerTaxon.computeIfAbsent(taxon, k -> new BitSet()).or(trees);
				}
			}
		}

		/**
		 * a cloned copy of the tree ids for the given taxon, or empty if absent
		 */
		public BitSet getTreeIds(int taxon) {
			var trees = treeIdsPerTaxon.get(taxon);
			return trees != null ? (BitSet) trees.clone() : new BitSet();
		}

		public boolean isEmpty() {
			return taxa.isEmpty();
		}

		@Override
		public String toString() {
			var buf = new StringBuilder();
			var first = true;
			for (int taxon = taxa.nextSetBit(0); taxon >= 0; taxon = taxa.nextSetBit(taxon + 1)) {
				if (!first)
					buf.append(" ");
				first = false;
				buf.append(taxon);
				var trees = treeIdsPerTaxon.get(taxon);
				if (trees != null && !trees.isEmpty()) {
					buf.append("(").append(StringUtils.toString(trees)).append(")");
				}
			}
			return buf.toString();
		}
	}

	public TracedHyperSequence() {
		this(new ArrayList<>());
	}

	public TracedHyperSequence copy() {
		var result = new TracedHyperSequence();
		for (var element : elements) {
			result.add(element.copy());
		}
		return result;
	}

	@Override
	public String toString() {
		var buf = new StringBuilder();
		for (var element : elements) {
			if (!buf.isEmpty())
				buf.append(" : ");
			buf.append(element);
		}
		return buf.toString();
	}

	/**
	 * taxa-only access, used by the alignment logic
	 */
	public BitSet get(int i) {
		return elements.get(i).taxa();
	}

	public Element getElement(int i) {
		return elements.get(i);
	}

	public void add(BitSet set) {
		elements.add(new Element(set));
	}

	public void add(Element element) {
		elements.add(element);
	}

	public int size() {
		return elements.size();
	}

	public boolean isEmpty() {
		return elements.isEmpty();
	}

	public void removeEmptyElements() {
		elements.removeIf(Element::isEmpty);
	}
}
