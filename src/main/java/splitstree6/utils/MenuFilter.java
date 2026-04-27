/*
 * MenuFilter.java Copyright (C) 2026 Daniel H. Huson
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
 *
 */

package splitstree6.utils;

import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Hides menu items whose fx:id is not in a whitelist, collapses consecutive
 * separators, and hides menus whose items are all hidden. Operates in place
 * by toggling visibility and clearing accelerators on hidden items, so
 * existing bindings to menu-item properties remain valid.
 */
public class MenuFilter {

	public static void apply(MenuBar menuBar, Collection<String> keepIds) {
		var keep = Set.copyOf(keepIds);
		for (var menu : menuBar.getMenus()) {
			applyToMenu(menu, keep);
		}
	}

	private static void applyToMenu(Menu menu, Set<String> keep) {
		// Recurse into submenus first so empty submenus get hidden before
		// we evaluate this menu's visibility.
		for (var item : menu.getItems()) {
			if (item instanceof Menu submenu) {
				applyToMenu(submenu, keep);
			}
		}
		// Hide items whose id isn't in the whitelist (skip separators and submenus).
		for (var item : menu.getItems()) {
			if (item instanceof SeparatorMenuItem) continue;
			if (item instanceof Menu) continue;
			var id = item.getId();
			if (id != null && !keep.contains(id)) {
				hide(item);
			}
		}
		collapseSeparators(menu.getItems());
		if (!hasVisibleContent(menu.getItems()) && (menu.getId() == null || !keep.contains(menu.getId()))) {
			hide(menu);
		}
	}

	private static void collapseSeparators(List<MenuItem> items) {
		boolean lastVisibleWasSeparator = true;   // treat start as separator -> hides leading
		MenuItem lastVisible = null;
		for (var item : items) {
			if (!item.isVisible()) continue;
			if (item instanceof SeparatorMenuItem) {
				if (lastVisibleWasSeparator) {
					hide(item);
				} else {
					lastVisibleWasSeparator = true;
					lastVisible = item;
				}
			} else {
				lastVisibleWasSeparator = false;
				lastVisible = item;
			}
		}
		if (lastVisible instanceof SeparatorMenuItem) {
			hide(lastVisible);
		}
	}

	private static boolean hasVisibleContent(List<MenuItem> items) {
		for (var item : items) {
			if (!item.isVisible()) continue;
			if (item instanceof SeparatorMenuItem) continue;
			return true;
		}
		return false;
	}

	private static void hide(MenuItem item) {
		item.setVisible(false);
		item.setAccelerator(null);
	}
}