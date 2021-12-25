/*
 * LoopView.java Copyright (C) 2021. Daniel H. Huson
 *
 * (Some code written by other authors, as named in code.)
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
 *
 */

package splitstree6.view.splits.viewer;

import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.property.DoubleProperty;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;
import jloda.graph.Node;
import jloda.graph.NodeArray;

import java.util.ArrayList;

/**
 * loops displayed when using outline algorithm
 * Daniel Huson, 1.2020
 */
public class LoopView {
    private final Polygon polygon = new Polygon();
    private final ArrayList<Node> nodes;

    private final NodeArray<DoubleProperty> nodeXMap;
    private final NodeArray<DoubleProperty> nodeYMap;

    private final InvalidationListener invalidationListener;

    public LoopView(ArrayList<Node> nodes, NodeArray<DoubleProperty> nodeXMap, NodeArray<DoubleProperty> nodeYMap) {
        this.nodes = nodes;
        this.nodeXMap = nodeXMap;
        this.nodeYMap = nodeYMap;
        polygon.setFill(Color.WHITESMOKE);
        polygon.setStroke(Color.TRANSPARENT);
        update();

        invalidationListener = e -> update();

        for (var v : nodes) {
            nodeXMap.get(v).addListener(new WeakInvalidationListener(invalidationListener));
            nodeYMap.get(v).addListener(new WeakInvalidationListener(invalidationListener));
        }
    }

    public void update() {
        polygon.getPoints().clear();
        for (Node v : nodes) {
            polygon.getPoints().addAll(nodeXMap.get(v).get(), nodeYMap.get(v).get());
        }
    }

    public Polygon getShape() {
        return polygon;
    }

    public ArrayList<Node> getNodes() {
        return nodes;
    }
}
