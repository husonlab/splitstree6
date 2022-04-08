/*
 *  FindReplaceTaxa.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.view.findreplace;

import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.SelectionMode;
import jloda.fx.find.FindToolBar;
import jloda.fx.find.Searcher;
import jloda.fx.undo.UndoManager;
import splitstree6.data.parts.Taxon;
import splitstree6.window.MainWindow;

import java.util.ArrayList;

/**
 * create the find and replace toolbar for taxa
 * Daniel Huson, 3.2022
 */
public class FindReplaceTaxa {
	/**
	 * create
	 *
	 * @param mainWindow
	 */
	public static FindToolBar create(MainWindow mainWindow, UndoManager undoManager) {
		var changes = new ArrayList<TaxonOldLabelNewLabel>();

		var searcher = new Searcher<>(mainWindow.getActiveTaxa(),
				i -> mainWindow.getTaxonSelectionModel().isSelected(mainWindow.getActiveTaxa().get(i)),
				(i, select) -> {
					if (select)
						mainWindow.getTaxonSelectionModel().select(mainWindow.getActiveTaxa().get(i));
					else
						mainWindow.getTaxonSelectionModel().clearSelection(mainWindow.getActiveTaxa().get(i));
				},
				new SimpleObjectProperty<>(SelectionMode.MULTIPLE),
				i -> mainWindow.getActiveTaxa().get(i).getNameAndDisplayLabel("===="),
				label -> label.replaceAll(".*====", ""),
				(i, label) -> {
					var taxon = mainWindow.getActiveTaxa().get(i);
					changes.add(new TaxonOldLabelNewLabel(taxon, taxon.getDisplayLabel(), label.replaceAll(".*====", "")));
				}, changes::clear, () -> undoManager.doAndAdd("replace", () -> changes.forEach(c -> c.taxon().setDisplayLabel(c.oldLabel())),
				() -> changes.forEach(c -> c.taxon().setDisplayLabel(c.newLabel()))));
		searcher.setSelectionFindable(true);

		return new FindToolBar(mainWindow.getStage(), searcher);
	}

	private record TaxonOldLabelNewLabel(Taxon taxon, String oldLabel, String newLabel) {
	}
}
