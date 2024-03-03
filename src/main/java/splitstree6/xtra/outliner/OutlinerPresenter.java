/*
 *  OutlinerPresenter.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.xtra.outliner;

import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.Image;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import jloda.fx.util.ClipboardUtils;

public class OutlinerPresenter {

	public OutlinerPresenter(Outliner outliner) {
		var controller = outliner.getController();
		var model = outliner.getModel();

		var emptyProperty = new SimpleBooleanProperty(true);
		model.lastUpdateProperty().addListener(e -> Platform.runLater(() -> emptyProperty.set(model.getTreesBlock().getNTrees() == 0)));
		model.lastUpdateProperty().addListener(e -> Platform.runLater(() -> redraw(outliner)));

		// MenuBar
		controller.getOpenMenuItem().setOnAction(e -> openFile(outliner.getStage(), controller, model));
		controller.getOpenMenuItem().disableProperty().bind(controller.getProgressBar().visibleProperty());

		controller.getCloseMenuItem().setOnAction(e -> Platform.exit());

		controller.getCopyMenuItem().setOnAction(e -> {
			ClipboardUtils.putImage(createImage(controller.getStackPane()));
		});

		controller.getReferenceCheckbox().selectedProperty().addListener(e -> redraw(outliner));
		controller.getReferenceCheckbox().disableProperty().bind(emptyProperty);

		controller.getOthersCheckBox().selectedProperty().addListener(e -> redraw(outliner));
		controller.getOthersCheckBox().disableProperty().bind(emptyProperty);

		controller.getRedrawButton().setOnAction(e -> redraw(outliner));
		controller.getRedrawButton().disableProperty().bind(emptyProperty);

		controller.getOutlineTreeToggleButton().selectedProperty().addListener(e -> redraw(outliner));

	}

	public void redraw(Outliner outliner) {
		var model = outliner.getModel();
		var controller = outliner.getController();

		try {
			var width = outliner.getStage().getWidth() - 10;
			var height = outliner.getStage().getHeight() - 80;
			controller.getStackPane().getChildren().clear();
			if (controller.getOutlineTreeToggleButton().isSelected()) {
				controller.getStackPane().getChildren().add(OutlineTree.apply(model, width, height));
			} else {
				controller.getStackPane().getChildren().add(ComputeOutlineAndReferenceTree.apply(model, controller.getReferenceCheckbox().isSelected(),
						controller.getOthersCheckBox().isSelected(), width, height));
			}
		} catch (Exception ex) {
			controller.getLabel().setText("Error: " + ex.getMessage());
		}
	}

	private void openFile(Stage stage, OutlinerController controller, Model model) {
		final var fileChooser = new FileChooser();
		fileChooser.setTitle("Open trees");

		var file = fileChooser.showOpenDialog(stage);
		if (file != null) {
			var service = new Service<Integer>() {
				@Override
				protected Task<Integer> createTask() {
					return new Task<>() {
						@Override
						protected Integer call() throws Exception {
							model.load(file);
							return model.getTreesBlock().getNTrees();
						}
					};
				}
			};
			controller.getProgressBar().visibleProperty().bind(service.runningProperty());
			controller.getProgressBar().progressProperty().bind(service.progressProperty());
			service.setOnSucceeded(v -> {
				System.out.println("Loading succeeded");
				controller.getLabel().setText("Taxa: %,d, Trees: %,d".formatted(model.getTaxaBlock().getNtax(),
						model.getTreesBlock().getNTrees()));
			});
			service.setOnFailed(u -> {
				System.out.println("Loading trees failed");
				controller.getLabel().setText("Loading trees failed");
			});
			service.start();

		}
	}

	private Image createImage(Node node) {
		var parameters = new SnapshotParameters();
		parameters.setTransform(javafx.scene.transform.Transform.scale(2, 2));
		return node.snapshot(parameters, null);
	}
}
