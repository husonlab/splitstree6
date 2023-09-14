/*
 *  GeneTreeSet.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.xtra.genetreeview.model;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import jloda.phylo.PhyloTree;
import splitstree6.data.TreesBlock;

 import java.util.*;

public class GeneTreeSet extends LinkedHashMap<Integer, GeneTree> {

    private final ObservableList<String> orderedGeneNames = FXCollections.observableArrayList();
    private final HashMap<String,Integer> geneName2treeId = new HashMap<>();
    private int maxId = 0;

    public GeneTreeSet(TreesBlock treesBlock) {
        initialize(treesBlock);
    }

    private void initialize(TreesBlock treesBlock) {
        orderedGeneNames.clear();
        geneName2treeId.clear();
        for (int id = 1; id <= treesBlock.getNTrees(); id++) {
            var phyloTree = treesBlock.getTree(id);
            var geneTree = new GeneTree(phyloTree, id, id-1);
            this.put(id, geneTree);
            String treeName = phyloTree.getName();
            orderedGeneNames.add(treeName);
            geneName2treeId.put(treeName, id);
        }
        maxId = treesBlock.getNTrees();
    }

    public void resetTreeOrder() {
        TreeMap<Integer, GeneTree> orderedGeneTrees = new TreeMap<>(this);
        this.clear();
        orderedGeneNames.clear();
        int index = 0;
        for (int id : orderedGeneTrees.keySet()) {
            var geneTree = orderedGeneTrees.get(id);
            geneTree.setPosition(index);
            this.put(id, geneTree);
            orderedGeneNames.add(geneTree.getGeneName());
            index++;
        }
    }

    public void setTreeOrder(TreeMap<? extends Number,Integer> position2treeId) {
        if (position2treeId.size() != this.size()) {
            System.out.println("Could not set new tree order");
            return;
        }
        HashMap<Integer, GeneTree> geneTreeSetCopy = new HashMap<>(this);
        this.clear();
        orderedGeneNames.clear();
        int index = 0;
        for (var position : position2treeId.keySet()) {
            int treeId = position2treeId.get(position);
            var geneTree = geneTreeSetCopy.get(treeId);
            orderedGeneNames.add(geneTree.getGeneName());
            geneTree.setPosition(index);
            this.put(treeId, geneTree);
            index++;
        }
    }

    public void setTreeOrder(ArrayList<Integer> newTreeOrder) {
        if (newTreeOrder.size() == this.size()) {
            HashMap<Integer, GeneTree> geneTreeSetCopy = new HashMap<>(this);
            this.clear();
            orderedGeneNames.clear();
            int index = 0;
            for (var treeId : newTreeOrder) {
                var geneTree = geneTreeSetCopy.get(treeId);
                orderedGeneNames.add(geneTree.getGeneName());
                geneTree.setPosition(index);
                this.put(treeId, geneTree);
                index++;
            }
        }
        else
            System.out.println("Could not set new tree order");
    }

    public void setGeneNames(String[] geneNames) {
        if (geneNames.length == this.size()) {
            orderedGeneNames.clear();
            geneName2treeId.clear();
            int index = 0;
            for (int id : this.keySet()) {
                String geneName = geneNames[index];
                orderedGeneNames.add(geneName);
                geneName2treeId.put(geneName, id);
                this.get(id).setGeneName(geneName);
            }
        }
    }

    public boolean addTree(GeneTree geneTree) {
        String geneName = geneTree.getGeneName();
        int treeId = geneTree.getId();
        int position = geneTree.getPosition();
        if (orderedGeneNames.contains(geneName) | this.containsKey(treeId) | position < 0 | position > this.size()) {
            return false;
        }
        geneName2treeId.put(geneName, treeId);
        orderedGeneNames.add(position, geneName);
        if (position < this.size()) {
            for (int id : this.keySet()) {
                var followingGeneTree = this.get(id);
                if (followingGeneTree.getPosition() >= position) {
                    followingGeneTree.setPosition(followingGeneTree.getPosition()+1);
                }
            }
        }
        this.put(position, treeId, geneTree);
        return true;
    }

    // Appends the given tree to the end
    public int addTree(PhyloTree phyloTree) {
        maxId++;
        int treeId = maxId;
        String geneName = phyloTree.getName();
        if (orderedGeneNames.get(orderedGeneNames.size()-1).startsWith("tree-") & geneName.startsWith("tree-"))
            geneName = "tree-" + (maxId);
        int suffix = 2;
        while (orderedGeneNames.contains(geneName)) {
            geneName = phyloTree.getName()+"-"+suffix;
            suffix+=1;
        }
        phyloTree.setName(geneName);

        int position = orderedGeneNames.size();
        var geneTree = new GeneTree(phyloTree, treeId, position);
        boolean addedSuccessfully = addTree(geneTree);
        if (addedSuccessfully) return treeId;
        else return -1;
    }

    public void removeTree(int treeId) {
        if (!this.containsKey(treeId)) {
            System.out.println("Could not find tree with id " + treeId);
            return;
        }
        GeneTree removedTree = this.get(treeId);
        this.remove(treeId);
        orderedGeneNames.remove(removedTree.getGeneName());
        geneName2treeId.remove(removedTree.getGeneName());
        if (removedTree.getPosition() < this.size()) {
            for (int id : this.keySet()) {
                var followingGeneTree = this.get(id);
                if (followingGeneTree.getPosition() > removedTree.getPosition()) {
                    followingGeneTree.setPosition(followingGeneTree.getPosition()-1);
                }
            }
        }
        //System.out.println("Removed tree "+removedTree.getGeneName()+" with id "+treeId+" and position "+removedTree.getPosition());
    }

    public GeneTree getGeneTree(int treeId) {
        if (!this.containsKey(treeId)) return null;
        return this.get(treeId);
    }

    public PhyloTree getPhyloTree(int treeId) {
        if (!this.containsKey(treeId)) return null;
        return this.get(treeId).getPhyloTree();
    }

    public PhyloTree getPhyloTree(String treeName) {
        if (!geneName2treeId.containsKey(treeName)) return null;
        int treeId = geneName2treeId.get(treeName);
        return this.get(treeId).getPhyloTree();
    }

    public int getTreeId(String treeName) {
        if (!geneName2treeId.containsKey(treeName)) return -1;
        return geneName2treeId.get(treeName);
    }

    public int getPosition(int treeId) {
        if (!this.containsKey(treeId)) return -1;
        return this.get(treeId).getPosition();
    }

    public ObservableList<String> getOrderedGeneNames() {
        return orderedGeneNames;
    }

    public ArrayList<Integer> getTreeOrder() {
        return new ArrayList<>(this.keySet());
    }

    public ArrayList<PhyloTree> getPhyloTrees() {
        ArrayList<PhyloTree> phyloTrees = new ArrayList<>(this.size());
        for (int id : this.keySet()) {
            phyloTrees.add(this.get(id).getPhyloTree());
        }
        return phyloTrees;
    }
    public ArrayList<GeneTree> getGeneTrees() {
        ArrayList<GeneTree> geneTrees = new ArrayList<>(this.size());
        for (int id : this.keySet()) {
            geneTrees.add(this.get(id));
        }
        return geneTrees;
    }

    public boolean containsGeneTree(GeneTree geneTree) {
        return this.containsValue(geneTree);
    }

    private void put(int position, int keyId, GeneTree valueGeneTree) {
        if (position == this.size()) this.put(keyId, valueGeneTree);
        else if (position >= 0 & position < this.size()) {
            LinkedHashMap<Integer,GeneTree> geneTreeSetCopy = new LinkedHashMap<>(this);
            this.clear();
            int index = 0;
            for (int treeId : geneTreeSetCopy.keySet()) {
                if (index == position) this.put(keyId, valueGeneTree);
                this.put(treeId, geneTreeSetCopy.get(treeId));
                index++;
            }
        }
    }
}
