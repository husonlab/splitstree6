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

public enum ColorScheme {
	CINEMA, ClustalX, MAEditor, PDNA, Zappo, DNA, None;

	public Color apply(char ch) {
		return apply(this, ch);
	}

	public static Color apply(ColorScheme colorScheme, char ch) {
		ch = Character.toUpperCase(ch);
		return switch (colorScheme) {
			case DNA -> switch (ch) {
				case 'A' -> Color.web("0x64F73F");
				case 'C' -> Color.web("0xFFB340");
				case 'G' -> Color.web("0xEB413C");
				case 'T', 'U' -> Color.web("0x3C88EE");
				case '-' -> Color.GRAY;
				default -> Color.web("0x778899"); // Light Slate Gray
			};
			case Zappo -> switch (ch) {
				case 'I', 'L', 'V', 'A', 'M' -> Color.web("0xFFAFAF");
				case 'F', 'Y', 'W' -> Color.web("0xFFC800");
				case 'H', 'K', 'R' -> Color.web("0x6464FF");
				case 'D', 'E' -> Color.web("0xFF0000");
				case 'S', 'T', 'N', 'Q' -> Color.web("0x00FF00");
				case 'G', 'P' -> Color.web("0xFFFFFF");
				case 'C' -> Color.web("0xFFFF00");
				default -> Color.web("0x778899"); // Light Slate Gray
			};

			case PDNA -> switch (ch) {
				case 'I', 'L', 'V', 'M', 'C' -> Color.web("0x15C015");   //green
				case 'A', 'G', 'S', 'T', 'P' -> Color.web("0xF09048"); // orange
				case 'F', 'Y', 'W' -> Color.web("0x80A0F0"); // blue
				case 'R', 'N', 'D', 'Q', 'E', 'H', 'K' -> Color.web("0xF01505"); // red
				default -> Color.web("0x778899"); // Light Slate Gray
			};
			case CINEMA -> switch (ch) {
				case 'H', 'K', 'R' -> Color.web("0x00FFFF");
				case 'D', 'E' -> Color.web("0xFF0000");
				case 'S', 'T', 'N', 'Q' -> Color.web("0x00FF00");
				case 'A', 'V', 'I', 'L', 'M' -> Color.web("0xBBBBBB");
				case 'F', 'W', 'Y' -> Color.web("0xFF00FF");
				case 'P', 'G' -> Color.web("0x996600");
				case 'C' -> Color.web("0xFFFF00");
				default -> Color.web("0x778899"); // Light Slate Gray
			};
			case ClustalX -> switch (ch) {
				case 'A', 'C', 'I', 'L', 'M', 'F', 'W', 'V' -> Color.web("0x80A0F0");
				case 'K', 'R' -> Color.web("0xF01505");
				case 'N', 'Q', 'S', 'T' -> Color.web("0x15C015");
				case 'D', 'E' -> Color.web("0xC048C0");
				case 'G' -> Color.web("0xF09048");
				case 'P' -> Color.web("0xC0C000");
				case 'H', 'Y' -> Color.web("0x15A4A4");
				default -> Color.web("0x778899"); // Light Slate Gray
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
				default -> Color.web("0x778899"); // Light Slate Gray
			};
			case None -> Color.TRANSPARENT;
		};
	}
}
