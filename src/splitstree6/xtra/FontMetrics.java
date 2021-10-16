/*
 *  FontMetrics.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.xtra;

import javafx.geometry.Bounds;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

/**
 * computes font metrics
 * Adapted from: http://werner.yellowcouch.org/log/fontmetrics-jdk9/
 * Daniel Huson, 10.2021
 */
public class FontMetrics {
	final private Text text;
	final private double ascent, descent, height;

	public FontMetrics(Font font) {
		text = new Text();
		text.setFont(font);
		Bounds b = text.getLayoutBounds();
		height = b.getHeight();
		ascent = -b.getMinY();
		descent = b.getMaxY();
	}

	public double computeStringWidth(String txt) {
		text.setText(txt);
		return text.getLayoutBounds().getWidth();
	}

	public double getAscent() {
		return ascent;
	}

	public double getDescent() {
		return descent;
	}

	public double getHeight() {
		return height;
	}
}

