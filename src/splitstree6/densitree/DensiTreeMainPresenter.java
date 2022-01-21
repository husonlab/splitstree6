/*
 * DensiTreeMainPresenter.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.densitree;

import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import jloda.fx.util.AService;
import jloda.util.ProgramProperties;
import splitstree6.io.readers.trees.NewickReader;

import java.io.File;
import java.io.IOException;

/**
 * the presenter
 */
public class DensiTreeMainPresenter {

	public DensiTreeMainPresenter(Stage stage, DensiTreeMainController controller, Model model) {

		controller.getCanvas().widthProperty().bind(controller.getMainPane().widthProperty());
		controller.getCanvas().heightProperty().bind(controller.getMainPane().heightProperty());

		//controller.getCanvas().prefWidth(controller.getMainPane().getPrefWidth());
		//controller.getCanvas().prefHeight(controller.getMainPane().getPrefHeight());

		controller.getMessageLabel().setText("");

		controller.getOpenMenuItem().setOnAction(e -> {
			final var previousDir = new File(ProgramProperties.get("InputDir", ""));
			final var fileChooser = new FileChooser();
			if (previousDir.isDirectory())
				fileChooser.setInitialDirectory(previousDir);
			fileChooser.setTitle("Open input file");
			fileChooser.getExtensionFilters().addAll(Utilities.getExtensionFilter());
			final var selectedFile = fileChooser.showOpenDialog(stage);
			if (selectedFile != null) {
				stage.setTitle(selectedFile.getName());
				if (selectedFile.getParentFile().isDirectory())
					ProgramProperties.put("InputDir", selectedFile.getParent());
				var service = new AService<Integer>(controller.getBottomFlowPane());
				service.setCallable(() -> {
					var newickReader = new NewickReader();
					newickReader.read(service.getProgressListener(), selectedFile.getPath(), model.getTaxaBlock(), model.getTreesBlock());
					if (model.getTreesBlock().isPartial())
						throw new IOException("Partial trees not acceptable");
					model.setCircularOrdering(Utilities.computeCycle(model.getTaxaBlock().getTaxaSet(), model.getTreesBlock()));
					return model.getTreesBlock().getNTrees();
				});
				service.setOnScheduled(a -> {
					model.clear();
				});
				service.setOnSucceeded(a -> {
					controller.getMessageLabel().setText(String.format("Trees: %,d", service.getValue()));
				});
				service.setOnFailed(a -> controller.getMessageLabel().setText("Failed: " + service.getException()));
				service.start();
			}
		});


		InvalidationListener listener = observable -> DensiTree.draw(new DensiTree.Parameters(controller.getCheckBox().isSelected()), model, controller.getCanvas(), controller.getPane());

		controller.getDrawButton().setOnAction(e -> {
			var parameters = new DensiTree.Parameters(controller.getCheckBox().isSelected());
			DensiTree.draw(parameters, model, controller.getCanvas(), controller.getPane());
			controller.getMainPane().widthProperty().addListener(listener);
			controller.getMainPane().heightProperty().addListener(listener);
			controller.getCheckBox().selectedProperty().addListener(listener);
		});
		controller.getDrawButton().disableProperty().bind(Bindings.isEmpty(model.getTreesBlock().getTrees()));
		controller.getClearButton().setOnAction(e -> {
			DensiTree.clear(controller.getCanvas(), controller.getPane());
			controller.getMainPane().widthProperty().removeListener(listener);
			controller.getMainPane().heightProperty().removeListener(listener);
			controller.getCheckBox().selectedProperty().removeListener(listener);
		});
	}
}
