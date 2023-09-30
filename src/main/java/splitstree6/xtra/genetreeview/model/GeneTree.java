/*
 *  GeneTree.java Copyright (C) 2023 Daniel H. Huson
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

import jloda.phylo.PhyloTree;

public class GeneTree {

    private final PhyloTree phyloTree;
    private final int id;
    private int position;

    public GeneTree(PhyloTree phyloTree, int id, int position) {
        this.phyloTree = phyloTree;
        this.id = id;
        this.position = position;
    }

    public PhyloTree getPhyloTree() {
        return phyloTree;
    }

    public int getId() {
        return id;
    }

    public String getGeneName() {
        return phyloTree.getName();
    }

    public int getPosition() {
        return position;
    }

    public void setGeneName(String geneName) {
        if (geneName != null) {
            phyloTree.setName(geneName);
        }
    }

    public void setPosition(int position) {
        this.position = position;
    }
}
