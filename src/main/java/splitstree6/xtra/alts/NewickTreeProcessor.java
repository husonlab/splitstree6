package splitstree6.xtra.alts;

import jloda.graph.Node;
import jloda.phylo.PhyloTree;
import splitstree6.xtra.hyperstrings.HyperSequence;

import java.util.*;


public class NewickTreeProcessor {
    private HashMap<String, Integer> labelTaxonIdMap;
    private LinkedList<Integer> order;

    public NewickTreeProcessor(HashMap<String, Integer> labelTaxonIdMap, LinkedList<Integer> order) {
        this.labelTaxonIdMap = labelTaxonIdMap;
        this.order = order;
    }

    /**
     * Finds the smallest taxons under each internal node in the given tree based on the specified order.
     * @param tree
     * @return
     */
    public HashMap<Node, List<Integer>> findSmallestTaxons(PhyloTree tree) {
        HashMap<Node, List<Integer>> result = new HashMap<>();
        for (Node node : tree.nodes()) {
            if (node.outEdges().iterator().hasNext()) {
                List<Integer> smallestTaxons = findSmallestTaxonsUnderNode(node);
                result.put(node, smallestTaxons);
            }
        }
        return result;
    }

    /**
     * Finds the smallest taxons under a given node based on the specified order.
     * @param node
     * @return
     */
    public List<Integer> findSmallestTaxonsUnderNode(Node node) {
        List<Integer> smallestTaxons = new ArrayList<>();
        for (Node child : node.children()) {
            smallestTaxons.add(findSmallestTaxonInSubtree(child));
        }
        return smallestTaxons;
    }

    /**
     * Recursively finds the smallest taxon in the subtree rooted at the given node.
     * @param node
     * @return
     */
    private int findSmallestTaxonInSubtree(Node node) {
        if (node.children().iterator().hasNext()) {
            int smallestTaxon = Integer.MAX_VALUE;
            for (Node child : node.children()) {
                int childTaxon = findSmallestTaxonInSubtree(child);
                if (compareTaxons(childTaxon, smallestTaxon) < 0) {
                    smallestTaxon = childTaxon;
                }
            }
            return smallestTaxon;
        } else {
            return labelTaxonIdMap.getOrDefault(node.getLabel(), Integer.MAX_VALUE);
        }
    }

    private int compareTaxons(int taxon1, int taxon2) {
        int index1 = order.indexOf(taxon1);
        int index2 = order.indexOf(taxon2);

        if (index1 == -1) index1 = Integer.MAX_VALUE; // Taxon not in order list
        if (index2 == -1) index2 = Integer.MAX_VALUE; // Taxon not in order list

        return Integer.compare(index1, index2);
    }


    public HyperSequence findPathFromLeafToRoot(Node leaf) {
        int leafTaxonId = labelTaxonIdMap.getOrDefault(leaf.getLabel(), Integer.MAX_VALUE);
        StringBuilder pathBuilder = new StringBuilder();

        Node currentNode = leaf;
        while (currentNode.getParent() != null) {
            Node parentNode = currentNode.getParent();

            //Finding the smallest taxa of each subtree under the children of the node
            List<Integer> smallestTaxons = new ArrayList<>(findSmallestTaxonsUnderNode(parentNode));
            //Smallest taxon of the smallest taxa should be removes (selecting non-smallest one)
            int smallestTaxon = Collections.min(smallestTaxons, this::compareTaxons);
            smallestTaxons.remove(Integer.valueOf(smallestTaxon));

            //If leaf node id matches the biggers of the smallests stop
            if (smallestTaxons.contains(leafTaxonId)) {
                break;
            }

            boolean addedValues = false;
            for (int taxon : smallestTaxons) {
                if (taxon != leafTaxonId) {
                    pathBuilder.append(taxon).append(' ');
                    addedValues = true;
                }
            }

            if (addedValues) {
                if (pathBuilder.length() > 0 && pathBuilder.charAt(pathBuilder.length() - 1) == ' ') {
                    pathBuilder.setLength(pathBuilder.length() - 1); // Remove trailing space
                }
                //Add colon to separate node
                pathBuilder.append(" : ");
            }

            currentNode = parentNode;
        }

        if (pathBuilder.length() > 0 && pathBuilder.charAt(pathBuilder.length() - 3) == ' ') {
            pathBuilder.setLength(pathBuilder.length() - 3); // Remove trailing colon and spaces
        }

        return HyperSequence.parse(pathBuilder.toString());
    }


}

