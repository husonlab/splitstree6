/*
 * TreeFilterTableItem.java Copyright (C) 2022 Daniel H. Huson
 *
 * (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package splitstree6.tabs.algorithms.treefilter;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import jloda.phylo.PhyloTree;

public class TreeFilterTableItem {
	private final int id;
	private final PhyloTree tree;
	private final BooleanProperty active = new SimpleBooleanProperty(this, "Active", false);
	private final StringProperty name = new SimpleStringProperty();

	public TreeFilterTableItem(int id, PhyloTree tree) {
		this.id = id;
		this.tree = tree;
		name.set(tree.getName());
	}

	public int getId() {
		return id;
	}

	public PhyloTree getTree() {
		return tree;
	}

	public boolean isActive() {
		return active.get();
	}

	public BooleanProperty activeProperty() {
		return active;
	}

	public void setActive(boolean active) {
		this.active.set(active);
	}

	public String getName() {
		return name.get();
	}

	public StringProperty nameProperty() {
		return name;
	}

	public void setName(String name) {
		this.name.set(name);
	}
}
