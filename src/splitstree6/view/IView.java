/*
 * IView.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.view;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.scene.Node;
import jloda.fx.undo.UndoManager;
import splitstree6.options.IOptionsCarrier;
import splitstree6.tabs.IDisplayTabPresenter;
import splitstree6.tabs.viewtab.ViewTab;

public interface IView extends IOptionsCarrier {
	String getName();

	Node getRoot();

	void setupMenuItems();

	void setViewTab(ViewTab viewTab);

	int size();

	UndoManager getUndoManager();

	ReadOnlyBooleanProperty emptyProperty();

	Node getImageNode();

	void clear();

	IDisplayTabPresenter getPresenter();

	String getCitation();
}
