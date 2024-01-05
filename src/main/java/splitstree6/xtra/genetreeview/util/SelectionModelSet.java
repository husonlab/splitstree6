/*
 *  SelectionModelSet.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.xtra.genetreeview.util;

import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.SimpleSetProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;

import java.util.Collection;

public class SelectionModelSet<T> implements SelectionModel<T> {

	private final ObservableSet<T> initialValue = FXCollections.observableSet();
	SimpleSetProperty<T> selection = new SimpleSetProperty<>(initialValue);

	@Override
	public boolean select(T t) {
		return selection.getValue().add(t);
	}

	@Override
	public boolean setSelected(T t, boolean select) {
		if (select & !selection.contains(t)) {
			return selection.getValue().add(t);
		} else if (!select & selection.contains(t)) {
			return selection.getValue().remove(t);
		} else return false;
	}

	@Override
	public boolean selectAll(Collection<T> list) {
		return selection.getValue().addAll(list);
	}

	@Override
	public void clearSelection() {
		selection.getValue().clear();
	}

	@Override
	public boolean clearSelection(T t) {
		if (selection.contains(t)) {
			selection.getValue().remove(t);
			return true;
		}
		return false;
	}

	@Override
	public boolean clearSelection(Collection<T> list) {
		return selection.getValue().removeAll(list);
	}

	@Override
	public ObservableSet<T> getSelectedItems() {
		return selection;
	}

	@Override
	public int size() {
		return selection.getValue().size();
	}

	public ReadOnlyIntegerProperty sizeProperty() {
		return selection.sizeProperty();
	}
}

