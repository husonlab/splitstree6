/*
 *  LoopView.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.view.splits.viewer;

import javafx.beans.property.DoubleProperty;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;
import jloda.fx.window.MainWindowManager;
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

    public LoopView(ArrayList<Node> nodes, NodeArray<DoubleProperty> nodeXMap, NodeArray<DoubleProperty> nodeYMap) {
        this.nodes = nodes;
        polygon.setFill(MainWindowManager.isUseDarkTheme() ? Color.GREY : Color.WHITESMOKE);
        polygon.setStroke(Color.TRANSPARENT);

        for (var i = 0; i < nodes.size(); i++) {
            var v = nodes.get(i);
            var x = nodeXMap.get(v);
            var y = nodeYMap.get(v);
            polygon.getPoints().addAll(x.get(), y.get());
            var ix = 2 * i;
            x.addListener(e -> polygon.getPoints().set(ix, x.get()));
            var iy = 2 * i + 1;
            x.addListener(e -> polygon.getPoints().set(iy, y.get()));
        }
    }

    public Polygon getShape() {
        return polygon;
    }

    public ArrayList<Node> getNodes() {
        return nodes;
    }
}
