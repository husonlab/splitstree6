/*
 *  WorkflowTreeItem.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.treeview;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.RotateTransition;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.concurrent.Worker;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import jloda.fx.util.ResourceManagerFX;
import splitstree6.window.MainWindow;
import splitstree6.workflow.AlgorithmNode;
import splitstree6.workflow.DataNode;

/**
 * work flow node in tree
 * Daniel Huson, 10.21
 */
public class WorkflowTreeItem extends TreeItem<String> {
	private final Tooltip tooltip = new Tooltip();
	private final BooleanProperty disable = new SimpleBooleanProperty();
	private final ChangeListener<Worker.State> stateChangeListener;

	public WorkflowTreeItem(MainWindow mainWindow) {
		final Label label = new Label();
		label.textProperty().bind(mainWindow.nameProperty());
		setGraphic(label);
		label.setGraphic(ResourceManagerFX.getIconAsImageView("sun/War16.gif", 16));
		setValue("");

		label.setOnMouseClicked(e -> {
			if (mainWindow.isEmpty() && e.getClickCount() == 2) {
				mainWindow.getPresenter().showInputEditor();
				e.consume();
			}
		});

		stateChangeListener = null;
	}

	public WorkflowTreeItem(AlgorithmNode node) {
		final Label label = new Label();
		setGraphic(label);

		label.textProperty().bind(node.nameProperty());

		disable.bind(node.validProperty().not());
		tooltip.textProperty().bind(node.shortDescriptionProperty());
		Tooltip.install(getGraphic(), new Tooltip(node.getShortDescription()));

		final Image icon = ResourceManagerFX.getIcon(node.getName().endsWith("Filter") ? "Filter16.gif" : "Algorithm16.gif");

		final ImageView imageView = new ImageView(icon);
		var rotateTransition = new RotateTransition(Duration.millis(1000), imageView);
		rotateTransition.setByAngle(360);
		rotateTransition.setCycleCount(Animation.INDEFINITE);
		rotateTransition.setInterpolator(Interpolator.LINEAR);
		label.setGraphic(imageView);

		stateChangeListener = (c, o, n) -> {
			// System.err.println("State change: " + workflowNode.getName() + ": " + n);
			switch (n) {
				case SCHEDULED, RUNNING -> {
					label.setTextFill(Color.BLACK);
					label.setStyle("-fx-background-color: LIGHTBLUE;");
					rotateTransition.play();
				}
				case CANCELLED, FAILED -> {
					label.setStyle("");
					label.setTextFill(Color.DARKRED);
					rotateTransition.stop();
					rotateTransition.getNode().setRotate(0);
				}
				default -> {
					label.setTextFill(Color.BLACK);
					label.setStyle("");
					rotateTransition.stop();
					rotateTransition.getNode().setRotate(0);
				}
			}
		};
		node.getService().stateProperty().addListener(new WeakChangeListener<>(stateChangeListener));

		label.setOnMouseClicked((e) -> {
			if (e.getClickCount() == 2) {
				showView();
				e.consume();
			}
		});

		disable.addListener((c, o, n) -> {
			if (n)
				label.setTextFill(Color.LIGHTGRAY);
			else
				label.setTextFill(Color.BLACK);
		});
	}

	public WorkflowTreeItem(DataNode node) {
		final Label label = new Label();
		setGraphic(label);

		label.textProperty().bind(node.nameProperty());

		disable.bind(node.validProperty().not());
		tooltip.textProperty().bind(node.shortDescriptionProperty());
		Tooltip.install(getGraphic(), new Tooltip(node.getShortDescription()));

		stateChangeListener = null;

		label.setOnMouseClicked((e) -> {
			if (e.getClickCount() == 2) {
				showView();
				e.consume();
			}
		});

		disable.addListener((c, o, n) -> {
			if (n)
				label.setTextFill(Color.LIGHTGRAY);
			else
				label.setTextFill(Color.BLACK);
		});
	}

	/**
	 * show the view for this node
	 */
	public void showView() {
		System.err.println("Not implemented");
	}

}
