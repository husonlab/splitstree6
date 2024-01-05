/*
 * LooseAndLacy.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.algorithms.trees.trees2trees;

import javafx.beans.property.*;
import jloda.graph.Edge;
import jloda.graph.Node;
import jloda.graph.NodeArray;
import jloda.phylo.PhyloTree;
import jloda.util.StringUtils;
import jloda.util.progress.ProgressListener;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TraitsBlock;
import splitstree6.data.TreesBlock;

import java.util.*;

/**
 * computes the loose and lacy species for a given tree and taxon trait
 * Daniel Huson, 2.2018
 */
public class LooseAndLacy extends Trees2Trees {
	public enum SpeciesDefinition {Loose, Lacy, Both}

	private final ObjectProperty<SpeciesDefinition> optionSpeciesDefinition = new SimpleObjectProperty<>(this, "optionSpeciesDefinition", SpeciesDefinition.Both);
	private final IntegerProperty optionTraitNumber = new SimpleIntegerProperty(this, "optionTraitNumber", 1);
	private final BooleanProperty optionUseAllTraits = new SimpleBooleanProperty(this, "optionUseAllTraits", false);

	@Override
	public String getCitation() {
		return "Hoppe et al (2018);Anica Hoppe, Sonja Türpitz, Mike Steel. Species notions that combine phylogenetic trees and phenotypic partitions. arXiv:1711.08145v1.";
	}

	@Override
	public List<String> listOptions() {
		return List.of(optionSpeciesDefinition.getName(), optionTraitNumber.getName(), optionUseAllTraits.getName());
	}

	@Override
	public String getToolTip(String optionName) {
		if (optionName.equals(optionSpeciesDefinition.getName())) {
			return "Species definition to use";
		} else if (optionName.equals(optionTraitNumber.getName())) {
			return "Number of specific trait to use";
		} else if (optionName.equals(optionUseAllTraits.getName())) {
			return "Use all traits";
		} else
			return optionName;
	}


	@Override
	public void compute(ProgressListener progress, TaxaBlock taxaBlock, TreesBlock parent, TreesBlock child) {
		final TraitsBlock traitsBlock = taxaBlock.getTraitsBlock();

		final int[] upper;
		final int[] lower;

		if (isOptionUseAllTraits()) {
			upper = computeLeastUpperBound(taxaBlock.getNtax(), traitsBlock);
			System.err.println("Upper: " + StringUtils.toString(upper, " "));
			lower = computeGreatestLowerBound(taxaBlock.getNtax(), traitsBlock);
			System.err.println("Lower: " + StringUtils.toString(lower, " "));
			System.err.printf("Species definitions based on all %d traits%n", traitsBlock.getNTraits());
		} else {
			upper = lower = computeTax2ValueForTrait(taxaBlock.getNtax(), getOptionTraitNumber(), traitsBlock);
			System.err.printf("Species definitions based on trait: [%d] %s%n", getOptionTraitNumber(), traitsBlock.getTraitLabel(getOptionTraitNumber()));
		}

		final PhyloTree tree = parent.getTrees().get(0);

		// compute loose species:
		if (getOptionSpeciesDefinition() != SpeciesDefinition.Lacy) {
			final NodeArray<BitSet> traitValuesBelow = new NodeArray<>(tree);
			final NodeArray<BitSet> taxaBelow = new NodeArray<>(tree);

			computeTaxaAndValuesBelowRec(tree, tree.getRoot(), upper, taxaBelow, traitValuesBelow);

			// compute loose species:
			final Set<BitSet> looseSpecies = new TreeSet<>(createComparator());
			computeLooseSpeciesRec(tree.getRoot(), taxaBelow, traitValuesBelow, looseSpecies);

			// compute loose species tree:
			PhyloTree looseTree = treeFromParition(taxaBlock, looseSpecies);
			looseTree.setName("Loose species");

			System.err.printf("Loose species (%d):%n", looseSpecies.size());
			{
				int count = 0;
				for (BitSet set : looseSpecies) {
					System.err.printf("[%d] %s%n", ++count, StringUtils.toString(set, " "));
				}
			}

			child.getTrees().add(looseTree);
		}

		// compute lacy species:
		if (getOptionSpeciesDefinition() != SpeciesDefinition.Loose) {
			final NodeArray<BitSet> traitValuesBelow = new NodeArray<>(tree);
			final NodeArray<BitSet> taxaBelow = new NodeArray<>(tree);

			computeTaxaAndValuesBelowRec(tree, tree.getRoot(), lower, taxaBelow, traitValuesBelow);

			final Set<BitSet> lacySpecies = new TreeSet<>(createComparator());
			computeLacySpeciesRec(tree.getRoot(), taxaBelow, traitValuesBelow, lacySpecies);

			PhyloTree lacyTree = treeFromParition(taxaBlock, lacySpecies);
			lacyTree.setName("Tight (lacy) species");

			System.err.printf("Tight (lacy) species (%d):%n", lacySpecies.size());
			{
				int count = 0;
				for (BitSet set : lacySpecies) {
					System.err.printf("[%d] %s%n", ++count, StringUtils.toString(set, " "));
				}
			}
			child.getTrees().add(lacyTree);

		}
	}

	/**
	 * compute the tax to value mapping
	 *
	 * @return taxon to value mapping
	 */
	public int[] computeTax2ValueForTrait(int ntax, int trait, TraitsBlock traitsBlock) {
		final var tax2value = new int[ntax + 1];
		for (int tax = 1; tax <= ntax; tax++) {
			tax2value[tax] = (int) traitsBlock.getTraitValue(tax, trait);
		}
		return tax2value;
	}

	/**
	 * Label each node by all trait values on or below it
	 */
	private void computeTaxaAndValuesBelowRec(PhyloTree tree, Node v, int[] tax2value, NodeArray<BitSet> taxaBelow, NodeArray<BitSet> traitValuesBelow) {
		final BitSet taxa = new BitSet();
		final BitSet values = new BitSet();
		if (tree.getNumberOfTaxa(v) > 0) {
			for (int t : tree.getTaxa(v)) {
				taxa.set(t);
				values.set(tax2value[t]);
			}
		}
		for (Node w : v.children()) {
			computeTaxaAndValuesBelowRec(tree, w, tax2value, taxaBelow, traitValuesBelow);
			taxa.or(taxaBelow.get(w));
			values.or(traitValuesBelow.get(w));
		}
		taxaBelow.put(v, taxa);
		traitValuesBelow.put(v, values);
	}

	/**
	 * compute the least upper bound for all traits
	 * Runtime is O(ntax*ntax*nTraits)
	 */
	private int[] computeLeastUpperBound(int ntax, TraitsBlock traitsBlock) {
		final ArrayList<BitSet> sets = new ArrayList<>(); // all sets defined by all traits
		for (int trait = 1; trait <= traitsBlock.getNTraits(); trait++) {
			final Map<Integer, BitSet> state2set = new HashMap<>();
			for (int tax = 1; tax <= ntax; tax++) {
				final int state = (int) traitsBlock.getTraitValue(tax, trait);
				final BitSet set = state2set.computeIfAbsent(state, k -> new BitSet());
				set.set(tax);
			}
			sets.addAll(state2set.values());
		}

		final int[] tax2part = new int[ntax + 1]; // initially, each taxon is mapped to its own part
		for (int t = 1; t <= ntax; t++) {
			tax2part[t] = t;
		}

		// any taxa in the same set are mapped to the same part (the part of the smallest taxon in the set)
		for (BitSet set : sets) {
			final int part = tax2part[set.nextSetBit(1)];
			for (int tax = set.nextSetBit(2); tax != -1; tax = set.nextSetBit(tax + 1)) {
				tax2part[tax] = part;
			}
		}
		return tax2part;
	}


	/**
	 * compute the greatest lower bound for all traits
	 * Runtime is O(ntax*log(ntax)*nTraits)
	 */
	private int[] computeGreatestLowerBound(int ntax, TraitsBlock traitsBlock) {

		// for each trait, map each taxon to the trait-defined set that contains it
		final Map<Integer, BitSet>[] trait2tax2set = new HashMap[traitsBlock.getNTraits() + 1];
		for (int trait = 1; trait <= traitsBlock.getNTraits(); trait++) {
			trait2tax2set[trait] = new HashMap<>();
			final Map<Integer, BitSet> state2set = new HashMap<>();
			for (int tax = 1; tax <= ntax; tax++) {
				final int state = (int) traitsBlock.getTraitValue(tax, trait);
				final BitSet set = state2set.computeIfAbsent(state, k -> new BitSet());
				set.set(tax);
				trait2tax2set[trait].put(tax, set);
			}
		}

		final int[] tax2part = new int[ntax + 1];

		for (int tax = 1; tax <= ntax; tax++) {
			if (tax2part[tax] == 0) {
				final BitSet intersection = new BitSet();
				intersection.set(1, ntax + 1);

				for (int trait = 1; trait <= traitsBlock.getNTraits(); trait++) {
					final BitSet set = trait2tax2set[trait].get(tax);
					intersection.and(set);
				}
				for (int t = intersection.nextSetBit(1); t != -1; t = intersection.nextSetBit(t + 1))
					tax2part[t] = tax;
			}
		}
		return tax2part;
	}


	/**
	 * compute the loose species
	 */
	private void computeLooseSpeciesRec(Node v, NodeArray<BitSet> taxaBelow, NodeArray<BitSet> traitValuesBelow, Set<BitSet> looseSpecies) {
		final int sizeV = traitValuesBelow.get(v).cardinality();

		int sumSizeChildren = 0;
		for (Node w : v.children()) {
			sumSizeChildren += traitValuesBelow.get(w).cardinality();
		}

		if (sumSizeChildren == 0 || sizeV < sumSizeChildren)
			looseSpecies.add(taxaBelow.get(v));
		else for (Node w : v.children())
			computeLooseSpeciesRec(w, taxaBelow, traitValuesBelow, looseSpecies);
	}

	/**
	 * compute the lacy species
	 */
	private void computeLacySpeciesRec(Node v, NodeArray<BitSet> taxaBelow, NodeArray<BitSet> traitValuesBelow, Set<BitSet> lacySpecies) {
		final int sizeV = traitValuesBelow.get(v).cardinality();
		if (sizeV == 1)
			lacySpecies.add(taxaBelow.get(v));
		else
			for (Node w : v.children())
				computeLacySpeciesRec(w, taxaBelow, traitValuesBelow, lacySpecies);
	}


	/**
	 * computes a tree from a partition of taxa
	 *
	 * @return tree
	 */
	private PhyloTree treeFromParition(TaxaBlock taxaBlock, Set<BitSet> partition) {
		final PhyloTree tree = new PhyloTree();
		final Node root = tree.newNode();
		tree.setRoot(root);

		for (BitSet set : partition) {
			Node v = tree.newNode();
			Edge e = tree.newEdge(root, v);

			if (set.cardinality() == 1) {
				tree.setWeight(e, 2.0);
				int taxon = set.nextSetBit(0);
				tree.setLabel(v, taxaBlock.get(taxon).getName());
				tree.addTaxon(v, taxon);
			} else {
				tree.setWeight(e, 1.0);
				for (var t = set.nextSetBit(1); t != -1; t = set.nextSetBit(t + 1)) {
					Node w = tree.newNode();
					tree.addTaxon(w, t);
					tree.setLabel(w, taxaBlock.getLabel(t));
					Edge f = tree.newEdge(v, w);
					tree.setWeight(f, 1.0);
				}
			}
		}
		return tree;
	}

	private static Comparator<BitSet> createComparator() {
		return (s1, s2) -> {
			int a1 = -1;
			int a2 = -1;
			while (true) {
				a1 = s1.nextSetBit(a1 + 1);
				a2 = s2.nextSetBit(a2 + 1);
				if (a1 < a2)
					return a1 == -1 ? 1 : -1;
				else if (a1 > a2)
					return 1;
				else if (a1 == -1) // equals and both over
					return 0;

			}
		};
	}

	@Override
	public boolean isApplicable(TaxaBlock taxaBlock, TreesBlock parent) {
		return taxaBlock.getTraitsBlock() != null && taxaBlock.getTraitsBlock().getNTraits() > 0 && parent.getNTrees() > 0 && !parent.isPartial() && !parent.isReticulated();
	}

	public SpeciesDefinition getOptionSpeciesDefinition() {
		return optionSpeciesDefinition.get();
	}

	public ObjectProperty<SpeciesDefinition> optionSpeciesDefinitionProperty() {
		return optionSpeciesDefinition;
	}

	public void setOptionSpeciesDefinition(SpeciesDefinition optionSpeciesDefinition) {
		this.optionSpeciesDefinition.set(optionSpeciesDefinition);
	}

	public int getOptionTraitNumber() {
		return optionTraitNumber.get();
	}

	public IntegerProperty optionTraitNumberProperty() {
		return optionTraitNumber;
	}

	public void setOptionTraitNumber(int optionTraitNumber) {
		this.optionTraitNumber.set(optionTraitNumber);
	}

	public boolean isOptionUseAllTraits() {
		return optionUseAllTraits.get();
	}

	public BooleanProperty optionUseAllTraitsProperty() {
		return optionUseAllTraits;
	}

	public void setOptionUseAllTraits(boolean optionUseAllTraits) {
		this.optionUseAllTraits.set(optionUseAllTraits);
	}
}
