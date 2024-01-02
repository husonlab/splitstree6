/*
 *  ExportTreesTask.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.xtra.genetreeview.io;

import javafx.collections.ObservableSet;
import javafx.concurrent.Task;
import splitstree6.data.TreesBlock;
import splitstree6.io.writers.trees.NewickWriter;
import splitstree6.xtra.genetreeview.model.Model;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;

public class ExportTreesTask extends Task<Void> {

	private final File file;
	private final Model model;
	private final ObservableSet<Integer> selection;

	public ExportTreesTask(File file, Model model, ObservableSet<Integer> selection) {
		this.file = file;
		this.model = model;
		this.selection = selection;
	}

	@Override
	protected Void call() throws Exception {
		var newickWriter = new NewickWriter();
		var writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file)));
		var selectedTreesBlock = new TreesBlock();
		selectedTreesBlock.copy(model.getTreesBlock());
		selectedTreesBlock.getTrees().removeAll(model.getTreesBlock().getTrees());
		for (int treeId : selection) {
			selectedTreesBlock.getTrees().add(model.getGeneTreeSet().getPhyloTree(treeId));
		}
		newickWriter.write(writer, model.getTaxaBlock(), selectedTreesBlock);
		writer.close();
		return null;
	}
}
