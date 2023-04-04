/*
 *  GeneTreeViewPresenter.java Copyright (C) 2023 Daniel H. Huson
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

import javafx.application.Platform;
import javafx.stage.FileChooser;

import java.io.IOException;

public class GeneTreeViewPresenter {
	public GeneTreeViewPresenter(GeneTreeView geneTreeView) {
		var controller = geneTreeView.getController();
		var model = geneTreeView.getModel();

		controller.getOpenMenuItem().setOnAction(e -> {
			final var fileChooser = new FileChooser();
			fileChooser.setTitle("Open trees");

			var file = fileChooser.showOpenDialog(geneTreeView.getStage());
			if (file != null) {
				try {
					geneTreeView.getModel().load(file);
				} catch (IOException ex) {
					ex.printStackTrace();
				}
			}
		});

		controller.getCloseMenuItem().setOnAction(e -> Platform.exit());

		model.lastUpdateProperty().addListener(a -> {
			controller.getLabel().setText("Taxa: %,d, Trees: %,d".formatted(model.getTaxaBlock().getNtax(), model.getTreesBlock().getNTrees()));
		});


	}
}
