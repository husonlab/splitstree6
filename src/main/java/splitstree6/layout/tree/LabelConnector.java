/*
 *  LabelConnector.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.layout.tree;

import javafx.beans.binding.DoubleExpression;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;

/**
 * connects a label to the corresponding node
 */
public class LabelConnector extends Line {
	public LabelConnector() {
		setStroke(Color.DARKGRAY);
		getStrokeDashArray().addAll(2.0, 5.0);
	}

	public LabelConnector(double x1, double y1, double x2, double y2) {
		this();
		setStartX(x1);
		setStartY(y1);
		setEndX(x2);
		setEndY(y2);
	}

	public void update(double x1, double y1, double x2, double y2) {
		setStartX(x1);
		setStartY(y1);
		setEndX(x2);
		setEndY(y2);
	}

	public LabelConnector(DoubleExpression x1, DoubleExpression y1, DoubleExpression x2, DoubleExpression y2) {
		startXProperty().bind(x1);
		startYProperty().bind(y1);
		endXProperty().bind(x2);
		endYProperty().bind(y2);
		setStroke(Color.LIGHTGRAY);
		getStrokeDashArray().addAll(2.0, 5.0);
	}
}
