/*
 *  WorldMapController.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.view.worldmap;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import jloda.fx.control.ZoomableScrollPane;
import jloda.fx.icons.MaterialIcons;
import jloda.fx.util.BasicFX;
import jloda.fx.util.DraggableLabel;

public class WorldMapController {
	@FXML
	private AnchorPane anchorPane;

	@FXML
	private CheckMenuItem continentNamesCheckMenuItem;

	@FXML
	private CheckMenuItem countryNamesCheckMenuItem;

	@FXML
	private CheckMenuItem gridCheckMenuItem;

	@FXML
	private AnchorPane outerAnchorPane;

	@FXML
	private AnchorPane innerAnchorPane;

	@FXML
	private StackPane centerPane;

	@FXML
	private VBox formatVBox;

	@FXML
	private CheckMenuItem oceansCheckMenuItem;

	@FXML
	private ToggleButton twoCopiesToggleButton;

	@FXML
	private ToggleButton showDataButton;

	@FXML
	private Button zoomInButton;

	@FXML
	private Button zoomOutButton;

	@FXML
	private ToggleButton settingsToggleButton;

	@FXML
	private ToggleButton formatToggleButton;

	private final ZoomableScrollPane zoomableScrollPane = new ZoomableScrollPane(null);

	@FXML
	private VBox vbox;

	@FXML
	private void initialize() {
		MaterialIcons.setIcon(twoCopiesToggleButton, MaterialIcons.indeterminate_check_box, "-fx-rotate: 90;", true);
		MaterialIcons.setIcon(zoomInButton, MaterialIcons.zoom_in);
		MaterialIcons.setIcon(zoomOutButton, MaterialIcons.zoom_out);
		MaterialIcons.setIcon(showDataButton, MaterialIcons.pie_chart);
		MaterialIcons.setIcon(settingsToggleButton, MaterialIcons.tune);
		MaterialIcons.setIcon(formatToggleButton, MaterialIcons.format_shapes);

		zoomableScrollPane.setPannable(true);
		zoomableScrollPane.setLockAspectRatio(true);
		centerPane.getChildren().add(zoomableScrollPane);

		outerAnchorPane.getChildren().remove(formatVBox);
		outerAnchorPane.getChildren().add(formatVBox);

		settingsToggleButton.setVisible(false); // not used at present

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

	public CheckMenuItem getContinentNamesCheckMenuItem() {
		return continentNamesCheckMenuItem;
	}

	public CheckMenuItem getCountryNamesCheckMenuItem() {
		return countryNamesCheckMenuItem;
	}

	public CheckMenuItem getGridCheckMenuItem() {
		return gridCheckMenuItem;
	}

	public StackPane getCenterPane() {
		return centerPane;
	}

	public CheckMenuItem getOceansCheckMenuItem() {
		return oceansCheckMenuItem;
	}

	public ToggleButton getTwoCopiesToggleButton() {
		return twoCopiesToggleButton;
	}

	public Button getZoomInButton() {
		return zoomInButton;
	}

	public Button getZoomOutButton() {
		return zoomOutButton;
	}

	public VBox getVbox() {
		return vbox;
	}

	public AnchorPane getInnerAnchorPane() {
		return innerAnchorPane;
	}

	public ZoomableScrollPane getZoomableScrollPane() {
		return zoomableScrollPane;
	}

	public ToggleButton getShowDataButton() {
		return showDataButton;
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
}
