/*
 * NetworkViewController.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.view.network;

import javafx.beans.binding.When;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import jloda.fx.control.CopyableLabel;
import jloda.fx.control.ZoomableScrollPane;
import jloda.fx.icons.MaterialIcons;
import jloda.fx.util.BasicFX;
import jloda.fx.util.DraggableLabel;
import splitstree6.layout.network.DiagramType;

public class NetworkViewController {
	@FXML
	private AnchorPane anchorPane;

	@FXML
	private BorderPane borderPane;

	@FXML
	private VBox vBox;

	@FXML
	private ToolBar toolBar;

	@FXML
	private ComboBox<DiagramType> diagramCBox;

	@FXML
	private Button rotateLeftButton;

	@FXML
	private Button rotateRightButton;

	@FXML
	private Button flipButton;

	@FXML
	private Button zoomInButton;

	@FXML
	private Button zoomOutButton;

	@FXML
	private AnchorPane outerAnchorPane;

	@FXML
	private AnchorPane innerAnchorPane;

	@FXML
	private StackPane centerPane;

	@FXML
	private VBox formatVBox;

	@FXML
	private ToggleButton settingsToggleButton;

	@FXML
	private ToggleButton formatToggleButton;


	private final ZoomableScrollPane zoomableScrollPane = new ZoomableScrollPane(null);

	private final CopyableLabel infoLabel = new CopyableLabel();

	@FXML
	private void initialize() {
		MaterialIcons.setIcon(rotateLeftButton, "rotate_left");
		MaterialIcons.setIcon(rotateRightButton, "rotate_right");
		MaterialIcons.setIcon(flipButton, "flip");
		MaterialIcons.setIcon(settingsToggleButton, "tune");
		MaterialIcons.setIcon(formatToggleButton, "format_shapes");
		MaterialIcons.setIcon(zoomInButton, "zoom_in");
		MaterialIcons.setIcon(zoomOutButton, "zoom_out");

		zoomableScrollPane.setFitToWidth(true);
		zoomableScrollPane.setFitToHeight(true);
		zoomableScrollPane.setPannable(true);

		centerPane.getChildren().add(zoomableScrollPane);

		outerAnchorPane.getChildren().remove(formatVBox);
		outerAnchorPane.getChildren().add(formatVBox);


		AnchorPane.setTopAnchor(infoLabel, 5.0);
		AnchorPane.setLeftAnchor(infoLabel, 20.0);
		innerAnchorPane.getChildren().add(infoLabel);
		infoLabel.setFont(Font.font(infoLabel.getFont().getFamily(), 16));

		DraggableLabel.makeDraggable(infoLabel);

		settingsToggleButton.setSelected(false);

		toolBar.setMinHeight(ToolBar.USE_PREF_SIZE);
		toolBar.setMaxHeight(ToolBar.USE_COMPUTED_SIZE);
		toolBar.visibleProperty().bind(settingsToggleButton.selectedProperty());
		toolBar.prefHeightProperty().bind(new When(settingsToggleButton.selectedProperty()).then(32.0).otherwise(0.0));

		formatToggleButton.setSelected(false);
		formatVBox.visibleProperty().bind(formatToggleButton.selectedProperty());
		formatVBox.visibleProperty().addListener((v, o, n) -> {
			for (var titledPane : BasicFX.getAllRecursively(formatVBox, TitledPane.class)) {
				if (!titledPane.isDisable())
					titledPane.setExpanded(n);
			}
		});

		DraggableLabel.makeDraggable(formatVBox);
	}

	public AnchorPane getAnchorPane() {
		return anchorPane;
	}

	public BorderPane getBorderPane() {
		return borderPane;
	}

	public VBox getvBox() {
		return vBox;
	}

	public ToolBar getToolBar() {
		return toolBar;
	}

	public ComboBox<DiagramType> getDiagramCBox() {
		return diagramCBox;
	}

	public Button getRotateLeftButton() {
		return rotateLeftButton;
	}

	public Button getRotateRightButton() {
		return rotateRightButton;
	}

	public Button getFlipButton() {
		return flipButton;
	}

	public Button getZoomInButton() {
		return zoomInButton;
	}

	public Button getZoomOutButton() {
		return zoomOutButton;
	}

	public AnchorPane getInnerAnchorPane() {
		return innerAnchorPane;
	}

	public StackPane getCenterPane() {
		return centerPane;
	}

	public VBox getFormatVBox() {
		return formatVBox;
	}

	public ToggleButton getSettingsToggleButton() {
		return settingsToggleButton;
	}

	public ToggleButton getFormatToggleButton() {
		return formatToggleButton;
	}

	public ZoomableScrollPane getScrollPane() {
		return zoomableScrollPane;
	}

	public CopyableLabel getInfoLabel() {
		return infoLabel;
	}
}
