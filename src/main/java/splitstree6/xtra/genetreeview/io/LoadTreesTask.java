/*
 *  LoadTreesTask.java Copyright (C) 2024 Daniel H. Huson
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

import javafx.concurrent.Task;
import splitstree6.utils.Stabilizer;
import splitstree6.view.trees.tanglegram.optimize.LayoutUnoptimized;
import splitstree6.xtra.genetreeview.model.Model;

import java.io.File;

public class LoadTreesTask extends Task<Void> {

	private final File file;
	private final Model model;
	private final Stabilizer stabilizer;

	public LoadTreesTask(File file, Model model, Stabilizer stabilizer) {
		this.file = file;
		this.model = model;
		this.stabilizer = stabilizer;
	}

	@Override
	protected Void call() throws Exception {
		model.load(file);
		if (model.getTreesBlock().isReticulated()) {
			var layoutUnoptmized = new LayoutUnoptimized();
			for (var tree : model.getTreesBlock().getTrees()) {
				if (tree.isReticulated()) {
					layoutUnoptmized.apply(tree);
				}
			}
		}
		if (!model.getTreesBlock().isReticulated()) {
			stabilizer.setup(model.getTreesBlock().getTrees());
			stabilizer.apply(model.getTreesBlock().getTrees());
		}
		return null;
	}
}
