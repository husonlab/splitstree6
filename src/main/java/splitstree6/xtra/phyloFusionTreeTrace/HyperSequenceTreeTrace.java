package splitstree6.xtra.phyloFusionTreeTrace;

import jloda.util.BitSetUtils;
import jloda.util.StringUtils;

import java.util.*;

/**
 * HyperSequence with taxa sets and per-taxon tree-origin metadata.
 * Taxa determine all algorithmic behaviour; treeIds are metadata that must be preserved
 * through copy, move, restrict, and merge operations.
 */
public record HyperSequenceTreeTrace(ArrayList<HyperSequenceTreeTrace.Element> elements) {

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

        /**
         * deep copy
         */
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

        /**
         * remove the given taxa from this element and their metadata
         */
        public void removeTaxa(BitSet toRemove) {
            taxa.andNot(toRemove);
            treeIdsPerTaxon.keySet().removeIf(toRemove::get);
        }

        /**
         * keep only the given taxa
         */
        public void restrictToTaxa(BitSet allowed) {
            taxa.and(allowed);
            treeIdsPerTaxon.keySet().removeIf(t -> !allowed.get(t));
        }

        /**
         * add one tree id to one taxon
         */
        public void addTree(int taxon, int treeId) {
            taxa.set(taxon);
            treeIdsPerTaxon.computeIfAbsent(taxon, k -> new BitSet()).set(treeId);
        }

        /**
         * set/merge an explicit tree-id set for one taxon
         */
        public void addTrees(int taxon, BitSet treeIds) {
            taxa.set(taxon);
            treeIdsPerTaxon.computeIfAbsent(taxon, k -> new BitSet()).or(treeIds);
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
         * union of taxa + taxon-wise metadata union
         */
        public Element mergedWith(Element other) {
            var result = this.copy();
            result.mergeMetadata(other);
            return result;
        }

        /**
         * whether the element contains a taxon
         */
        public boolean containsTaxon(int taxon) {
            return taxa.get(taxon);
        }

        /**
         * get a cloned copy of the tree ids for the given taxon, or empty if absent
         */
        public BitSet getTreeIds(int taxon) {
            var trees = treeIdsPerTaxon.get(taxon);
            return trees != null ? (BitSet) trees.clone() : new BitSet();
        }

        /**
         * whether any taxon in this element carries the given tree id
         */
        public boolean containsTree(int treeId) {
            for (var trees : treeIdsPerTaxon.values()) {
                if (trees.get(treeId))
                    return true;
            }
            return false;
        }

        /**
         * true if no taxa
         */
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
                    buf.append("(");
                    buf.append(StringUtils.toString(trees));
                    buf.append(")");
                }
            }
            return buf.toString();
        }
    }

    /**
     * default constructor
     */
    public HyperSequenceTreeTrace() {
        this(new ArrayList<>());
    }

    /**
     * deep copy constructor helper
     */
    public HyperSequenceTreeTrace copy() {
        var result = new HyperSequenceTreeTrace();
        for (var element : elements) {
            result.add(element.copy());
        }
        return result;
    }

    /**
     * parse a plain taxa-only string description
     * e.g. "1 : 3 : 4 6 : 2 : 5"
     */
    public static HyperSequenceTreeTrace parse(String values) {
        var sequence = new HyperSequenceTreeTrace();
        for (var word : StringUtils.split(values, ':')) {
            var taxa = BitSetUtils.asBitSet(StringUtils.parseArrayOfIntegers(word));
            sequence.add(new Element(taxa));
        }
        return sequence;
    }

    /**
     * create a one-element sequence where every taxon carries treeId
     */
    public static HyperSequenceTreeTrace singleton(BitSet taxa, int treeId) {
        var sequence = new HyperSequenceTreeTrace();
        sequence.add(Element.fromTaxaAndTree(taxa, treeId));
        return sequence;
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
     * taxa only access, used by alignment logic
     */
    public BitSet get(int i) {
        return elements.get(i).taxa();
    }

    public Element getElement(int i) {
        return elements.get(i);
    }

    public void set(int i, BitSet set) {
        elements.set(i, new Element(set));
    }

    public void set(int i, Element element) {
        elements.set(i, element);
    }

    public void add(BitSet set) {
        elements.add(new Element(set));
    }

    public void add(BitSet set, int treeId) {
        elements.add(Element.fromTaxaAndTree(set, treeId));
    }

    public void add(Element element) {
        elements.add(element);
    }

    public void addFirst(Element element) {
        elements.add(0, element);
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

    /**
     * induce the sequence on a subset of taxa, preserving metadata
     */
    public HyperSequenceTreeTrace induce(BitSet taxa) {
        var result = new HyperSequenceTreeTrace();

        for (var element : elements) {
            var induced = element.inducedBy(taxa);
            if (!induced.isEmpty()) {
                if (result.elements().stream().noneMatch(e -> e.taxa().equals(induced.taxa()))) {
                    result.add(induced);
                }
            }
        }
        return result;
    }

    /**
     * prepend a copied element restricted to the given taxa
     */
    public void prependRestricted(Element source, BitSet taxa) {
        var induced = source.inducedBy(taxa);
        if (!induced.isEmpty()) {
            addFirst(induced);
        }
    }

    /**
     * append a copied element restricted to the given taxa
     */
    public void appendRestricted(Element source, BitSet taxa) {
        var induced = source.inducedBy(taxa);
        if (!induced.isEmpty()) {
            add(induced);
        }
    }
}