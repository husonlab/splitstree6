/*
 *  SelectionModel.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.xtra.genetreeview;

import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.collections.ObservableSet;
import java.util.Collection;

public interface SelectionModel<T> {
    boolean select(T t);

    boolean setSelected(T t, boolean select);

    boolean selectAll(Collection<T> list);

    void clearSelection();

    boolean clearSelection(T t);

    boolean clearSelection(Collection<T> list);

    ObservableSet<T> getSelectedItems();

    int size();

    ReadOnlyIntegerProperty sizeProperty();
}
