/*
 *  MenusToLaTeX.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.xtra.latex;

import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.SeparatorMenuItem;
import splitstree6.main.SplitsTree6;

/**
 * write out all items in the menu bar in LaTeX
 */
public class MenusToLaTeX {
	public static boolean active = false;

	public static void main(String[] args) {
		active = true;
		SplitsTree6.main(args);
	}

	public static String apply(MenuBar menuBar) {
		var buf = new StringBuilder();
		buf.append("""
				\\chapter{The main menu bar}
								
				All functionality of the program can be used
				directly from the main window. In addition,
				the program provides menus to access the most
				often used features.
								
				""");
		for (var menu : menuBar.getMenus()) {
			buf.append(applyRec(menu, 0));
		}
		return buf.toString();
	}

	public static String applyRec(Menu menu, int level) {
		var buf = new StringBuilder();
		if (level == 0) {
			buf.append("\\section{The %s menu}\\index{%s menu}%n%n%n".formatted(menu.getText(), menu.getText()));
			buf.append("This menu has the following items:\n\n");
		} else {
			buf.append("This %s-menu has the following items:\n\n".formatted("sub".repeat(level)));
		}
		buf.append("\\begin{itemize}\n");
		for (var item : menu.getItems()) {
			if (!(item instanceof SeparatorMenuItem) && !(item.getText().equals("Untitled"))) {
				if (item instanceof Menu subMenu) {
					applyRec(subMenu, level + 1);
				} else {
					var text = "%s \\index{%s menu item}".formatted(item.getText(), item.getText());
					if (item.getUserData() != null)
						text += " - " + item.getUserData().toString();
					buf.append("\\item %s%n".formatted(text));
				}
			}
		}
		buf.append("\\end{itemize}\n\n");
		return buf.toString().replaceAll("\\.\\.\\.", "\\\\dots");
	}
}
