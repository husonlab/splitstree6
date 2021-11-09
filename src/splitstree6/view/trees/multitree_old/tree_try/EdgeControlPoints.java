/*
 * EdgeControlPoints.java Copyright (C) 2021. Daniel H. Huson
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

package splitstree6.view.trees.multitree_old.tree_try;

import javafx.geometry.Point2D;

/**
 * points used to determine how to draw an edge
 * Daniel Huson, 10.2017
 */
public class EdgeControlPoints {
    private Point2D control1;
    private Point2D mid;
    private Point2D control2;
    private Point2D support;

    public EdgeControlPoints(Point2D control1, Point2D mid, Point2D control2) {
        this.control1 = control1;
        this.mid = mid;
        this.control2 = control2;
        this.support = null;
    }

    public EdgeControlPoints(Point2D control1, Point2D mid, Point2D control2, Point2D support) {
        this.control1 = control1;
        this.mid = mid;
        this.control2 = control2;
        this.support = support;
    }

    public Point2D getControl1() {
        return control1;
    }

    public Point2D getMid() {
        return mid;
    }

    public Point2D getControl2() {
        return control2;
    }

    public Point2D getSupport() {
        return support;
    }

    public void normalize(float minX, float factorX, float minY, float factorY) {
        control1 = new Point2D(factorX * (control1.getX() - minX), factorY * (control1.getY() - minY));
        mid = new Point2D(factorX * (mid.getX() - minX), factorY * (mid.getY() - minY));
        control2 = new Point2D(factorX * (control2.getX() - minX), factorY * (control2.getY() - minY));
        if (support != null)
            support = new Point2D(factorX * (support.getX() - minX), factorY * (support.getY() - minY));

    }
}
