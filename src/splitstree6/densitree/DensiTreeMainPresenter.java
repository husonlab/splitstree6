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

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextInputDialog;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import jloda.fx.util.AService;
import jloda.fx.util.RunAfterAWhile;
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

		controller.getLabelsGroup().selectToggle(controller.getMeanMenuItem());

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
				service.setOnScheduled(a -> model.clear());
				service.setOnSucceeded(a -> controller.getMessageLabel().setText(String.format("Trees: %,d", service.getValue())));
				service.setOnFailed(a -> controller.getMessageLabel().setText("Failed: " + service.getException()));
				service.start();
			}
		});

		final String[] specTrees = new String[1];

		controller.getSpecificTreesMenuItem().setOnAction(e -> {
			var dialog = new TextInputDialog();
			dialog.setTitle("Highlight specific Trees");
			dialog.setHeaderText("Enter the numbers of the trees you want to be highlighted, trees start at 1.\nE.g.: 1,2,3");
			dialog.setContentText("Trees:");

			dialog.showAndWait().ifPresent(trees -> specTrees[0] = trees);
		});

		controller.getQuitMenuItem().setOnAction(e -> {
			var alert = new Alert(Alert.AlertType.CONFIRMATION);
			alert.setTitle("Confirm Quit");
			alert.setHeaderText("Closing open document");
			alert.setContentText("Do you really want to quit?");

			final ButtonType buttonTypeCancel = new ButtonType("No", ButtonBar.ButtonData.CANCEL_CLOSE);
			final ButtonType buttonTypeYes = new ButtonType("Yes", ButtonBar.ButtonData.OK_DONE);
			alert.getButtonTypes().setAll(buttonTypeCancel, buttonTypeYes);

			if (alert.showAndWait().orElse(null) == buttonTypeYes) {
				stage.close();
			} else {
				e.consume();
			}
		});

		stage.setOnCloseRequest(e -> {
			var alert = new Alert(Alert.AlertType.CONFIRMATION);
			alert.setTitle("Confirm Quit");
			alert.setHeaderText("Closing open document");
			alert.setContentText("Do you really want to quit?");

			final ButtonType buttonTypeCancel = new ButtonType("No", ButtonBar.ButtonData.CANCEL_CLOSE);
			final ButtonType buttonTypeYes = new ButtonType("Yes", ButtonBar.ButtonData.OK_DONE);
			alert.getButtonTypes().setAll(buttonTypeCancel, buttonTypeYes);

			if (alert.showAndWait().orElse(null) == buttonTypeYes) {
				stage.close();
			} else {
				e.consume();
			}
		});


		final InvalidationListener listener;

		if (true) {
			listener = observable -> DensiTree.draw(new DensiTree.Parameters(controller.getScaleCheckBox().isSelected(), controller.getJitterCheckBox().isSelected(),
					controller.getConsensusMenuItem().isSelected(), specTrees[0],
					controller.getLabelsGroup().getSelectedToggle().toString()), model, controller.getCanvas(), controller.getPane());
		} else { // DHH: only draw once user has stopped using controls
			listener = observable -> RunAfterAWhile.apply(controller, () ->
					Platform.runLater(() -> DensiTree.draw(new DensiTree.Parameters(controller.getScaleCheckBox().isSelected(), controller.getJitterCheckBox().isSelected(),
							controller.getConsensusMenuItem().isSelected(), specTrees[0],
							controller.getLabelsGroup().getSelectedToggle().toString()), model, controller.getCanvas(), controller.getPane())));
		}

		controller.getDrawButton().setOnAction(e -> {
			var parameters = new DensiTree.Parameters(controller.getScaleCheckBox().isSelected(), controller.getJitterCheckBox().isSelected(),
					controller.getConsensusMenuItem().isSelected(), specTrees[0], controller.getLabelsGroup().getSelectedToggle().toString());
			DensiTree.draw(parameters, model, controller.getCanvas(), controller.getPane());
			controller.getMainPane().widthProperty().addListener(listener);
			controller.getMainPane().heightProperty().addListener(listener);
			controller.getScaleCheckBox().selectedProperty().addListener(listener);
			controller.getJitterCheckBox().selectedProperty().addListener(listener);
			controller.getConsensusMenuItem().selectedProperty().addListener(listener);
		});

		controller.getDrawButton().disableProperty().bind(Bindings.isEmpty(model.getTreesBlock().getTrees()));

		controller.getClearButton().setOnAction(e -> {
			DensiTree.clear(controller.getCanvas(), controller.getPane());
			controller.getMainPane().widthProperty().removeListener(listener);
			controller.getMainPane().heightProperty().removeListener(listener);
			controller.getScaleCheckBox().selectedProperty().removeListener(listener);
			controller.getJitterCheckBox().selectedProperty().removeListener(listener);
			controller.getConsensusMenuItem().selectedProperty().removeListener(listener);
		});
	}
}
