/*
 *  RemoveTaxon.java Copyright (C) 2022 Daniel H. Huson
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
package splitstree6.autumn.hybridnetwork;

import splitstree6.autumn.Root;

/**
 * removes a taxon from a tree.
 * Daniel Huson, 5.2011
 */
public class RemoveTaxon {
    /**
     * remove the named taxon. Children are kept lexicographically sorted
     *
     * @param root
     * @param treeId
     * @param taxon  @return true, if changed
     */
    public static boolean apply(Root root, int treeId, int taxon) {
        if (root.getTaxa().get(taxon)) {
            root.getTaxa().set(taxon, false);
            root.getRemovedTaxa().set(taxon, true);
            var changed = false;
            for (var e : root.outEdges()) {
                if (apply((Root) e.getTarget(), treeId, taxon))
                    changed = true;
            }
            if (changed)
                root.reorderChildren();
            if (root.getOutDegree() == 0 && root.getInDegree() > 0) {
                var f = root.getFirstInEdge();
                f.setInfo(treeId);
            }
            return true;
        }
        return false;
    }

    /**
     * un-remove the named taxon. Children are kept lexicographically sorted
     *
     * @param root
     * @param taxon @return true, if changed
     */
    public static boolean unapply(Root root, int taxon) {
        if (root.getRemovedTaxa().get(taxon)) {
            root.getRemovedTaxa().set(taxon, false);
            root.getTaxa().set(taxon, true);
            var changed = false;
            var isBelow = false;
            for (var e = root.getFirstOutEdge(); !isBelow && e != null; e = root.getNextOutEdge(e)) {
                var w = (Root) e.getTarget();
                if (w.getTaxa().get(taxon) || w.getRemovedTaxa().get(taxon))
                    isBelow = true;
            }
            if (root.getDegree() > 0 && !isBelow) {
                var u = root.newNode();
                u.getTaxa().set(taxon);
                root.newEdge(root, u);
                changed = true;
            } else // is nothing below, add leaf node
            {
                for (var e : root.outEdges()) {
                    if (unapply((Root) e.getTarget(), taxon))
                        changed = true;
                }
            }
            if (changed)
                root.reorderChildren();
            return true;
        }
        return false;
    }

}
