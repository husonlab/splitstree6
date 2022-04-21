/*
 *  ColorScheme.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.view.alignment;

import javafx.scene.paint.Color;
import jloda.fx.util.ColorSchemeManager;

public enum ColorScheme {
	Nucleotide, CINEMA, ClustalX, Diamond11, MAEditor, PDNA, Zappo, Random, None;

	public Color apply(char ch) {
		return apply(this, ch);
	}

	public static Color apply(ColorScheme colorScheme, char ch) {
		var otherColor = Color.WHITESMOKE;

		ch = Character.toUpperCase(ch);
		return switch (colorScheme) {
			case Nucleotide -> switch (ch) {
				case 'A' -> Color.web("0x64F73F");
				case 'C' -> Color.web("0xFFB340");
				case 'G' -> Color.web("0xEB413C");
				case 'T', 'U' -> Color.web("0x3C88EE");
				case '-' -> Color.GRAY;
				default -> otherColor;
			};
			case Diamond11 -> switch (ch) {
				case 'K', 'R', 'E', 'D', 'Q', 'N' -> Color.web("0xba7bbd");
				case 'C' -> Color.web("0xf27e75");
				case 'G' -> Color.web("0xbfb8da");
				case 'H' -> Color.web("0xfbf074");
				case 'I', 'L', 'V' -> Color.web("0xf9b666");
				case 'M' -> Color.web("0xf8cbe5");
				case 'F' -> Color.web("0xceedc5");
				case 'Y' -> Color.web("0xfdffb6");
				case 'W' -> Color.web("0xb3e46c");
				case 'P' -> Color.web("0x95d6c8");
				case 'S', 'T', 'A' -> Color.web("0x86b0d2");
				default -> otherColor;
			};
			case Zappo -> switch (ch) {
				case 'I', 'L', 'V', 'A', 'M' -> Color.web("0xFFAFAF");
				case 'F', 'Y', 'W' -> Color.web("0xFFC800");
				case 'H', 'K', 'R' -> Color.web("0x6464FF");
				case 'D', 'E' -> Color.web("0xFF0000");
				case 'S', 'T', 'N', 'Q' -> Color.web("0x00FF00");
				case 'G', 'P' -> Color.web("0xFFFFFF");
				case 'C' -> Color.web("0xFFFF00");
				default -> otherColor;
			};

			case PDNA -> switch (ch) {
				case 'I', 'L', 'V', 'M', 'C' -> Color.web("0x15C015");   //green
				case 'A', 'G', 'S', 'T', 'P' -> Color.web("0xF09048"); // orange
				case 'F', 'Y', 'W' -> Color.web("0x80A0F0"); // blue
				case 'R', 'N', 'D', 'Q', 'E', 'H', 'K' -> Color.web("0xF01505"); // red
				default -> otherColor;
			};
			case CINEMA -> switch (ch) {
				case 'H', 'K', 'R' -> Color.web("0x00FFFF");
				case 'D', 'E' -> Color.web("0xFF0000");
				case 'S', 'T', 'N', 'Q' -> Color.web("0x00FF00");
				case 'A', 'V', 'I', 'L', 'M' -> Color.web("0xBBBBBB");
				case 'F', 'W', 'Y' -> Color.web("0xFF00FF");
				case 'P', 'G' -> Color.web("0x996600");
				case 'C' -> Color.web("0xFFFF00");
				default -> otherColor;
			};
			case ClustalX -> switch (ch) {
				case 'A', 'C', 'I', 'L', 'M', 'F', 'W', 'V' -> Color.web("0x80A0F0");
				case 'K', 'R' -> Color.web("0xF01505");
				case 'N', 'Q', 'S', 'T' -> Color.web("0x15C015");
				case 'D', 'E' -> Color.web("0xC048C0");
				case 'G' -> Color.web("0xF09048");
				case 'P' -> Color.web("0xC0C000");
				case 'H', 'Y' -> Color.web("0x15A4A4");
				default -> otherColor;
			};
			case MAEditor -> switch (ch) {
				case 'A', 'G' -> Color.web("0x77DD88");
				case 'C' -> Color.web("0x99EE66");
				case 'D', 'E', 'N', 'Q' -> Color.web("0x55BB33");
				case 'I', 'L', 'M', 'V' -> Color.web("0x66BBFF");
				case 'F', 'W', 'Y' -> Color.web("0x9999FF");
				case 'H' -> Color.web("0x5555FF");
				case 'K', 'R' -> Color.web("0xFFCC77");
				case 'P' -> Color.web("0xEEAAAA");
				case 'S', 'T' -> Color.web("0xFF4455");
				default -> otherColor;
			};
			case Random -> {
				var i = ch - 'A';
				if (i >= 0 && i < 26) {
					var colors = ColorSchemeManager.getInstance().getColorScheme("Twenty");
					yield colors.get(i % colors.size()).deriveColor(1.0, 0.4, 1.0, 1.0);
				} else
					yield otherColor;
			}
			case None -> otherColor;
		};
	}
}
