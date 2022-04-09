/*
 * SplitsViewController.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.view.splits.viewer;

import javafx.beans.binding.When;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import jloda.fx.control.CopyableLabel;
import jloda.fx.control.ZoomableScrollPane;
import jloda.fx.util.DraggableLabel;
import jloda.fx.util.ScaleBar;
import splitstree6.layout.splits.SplitsDiagramType;
import splitstree6.layout.splits.SplitsRooting;
import splitstree6.layout.tree.LayoutOrientation;

public class SplitsViewController {

	@FXML
	private AnchorPane anchorPane;

	@FXML
	private BorderPane borderPane;

	@FXML
	private VBox vBox;

	@FXML
	private ToolBar toolBar;

	@FXML
	private ToggleButton findToggleButton;

	@FXML
	private StackPane centerPane;

	@FXML
	private ComboBox<SplitsDiagramType> diagramCBox;

	@FXML
	private ComboBox<SplitsRooting> rootingCBox;

	@FXML
	private ComboBox<LayoutOrientation> orientationCBox;

	@FXML
	private Button zoomInButton;

	@FXML
	private Button zoomOutButton;

	@FXML
	private Button increaseFontButton;

	@FXML
	private Button decreaseFontButton;

	@FXML
	private ToggleButton showInternalLabelsToggleButton;

	@FXML
	private AnchorPane outerAnchorPane;

	@FXML
	private AnchorPane innerAnchorPane;

	@FXML
	private VBox formatVBox;

	@FXML
	private ToggleButton settingsToggleButton;

	@FXML
	private ToggleButton formatToggleButton;

	private final ZoomableScrollPane zoomableScrollPane = new ZoomableScrollPane(null);

	private final ScaleBar scaleBar = new ScaleBar();

	private final CopyableLabel fitLabel = new CopyableLabel();

	@FXML
	private void initialize() {
		zoomableScrollPane.setFitToWidth(true);
		zoomableScrollPane.setFitToHeight(true);
		zoomableScrollPane.setPannable(true);

		centerPane.getChildren().add(zoomableScrollPane);

		innerAnchorPane.getChildren().add(scaleBar);
		AnchorPane.setTopAnchor(scaleBar, 2.0);
		AnchorPane.setLeftAnchor(scaleBar, 5.0);
		AnchorPane.setTopAnchor(fitLabel, 5.0);
		AnchorPane.setLeftAnchor(fitLabel, 180.0);
		innerAnchorPane.getChildren().add(fitLabel);

		DraggableLabel.makeDraggable(fitLabel);

		outerAnchorPane.getChildren().remove(formatVBox);
		outerAnchorPane.getChildren().add(formatVBox);

		settingsToggleButton.setSelected(true);
		toolBar.setMinHeight(ToolBar.USE_PREF_SIZE);
		toolBar.setMaxHeight(ToolBar.USE_COMPUTED_SIZE);
		toolBar.visibleProperty().bind(settingsToggleButton.selectedProperty());
		toolBar.prefHeightProperty().bind(new When(settingsToggleButton.selectedProperty()).then(32.0).otherwise(0.0));

		formatToggleButton.setSelected(false);
		formatVBox.visibleProperty().bind(formatToggleButton.selectedProperty());
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

	public ToggleButton getFindToggleButton() {
		return findToggleButton;
	}

	public ComboBox<SplitsDiagramType> getDiagramCBox() {
		return diagramCBox;
	}

	public ComboBox<SplitsRooting> getRootingCBox() {
		return rootingCBox;
	}

	public ComboBox<LayoutOrientation> getOrientationCBox() {
		return orientationCBox;
	}

	public Button getZoomInButton() {
		return zoomInButton;
	}

	public Button getZoomOutButton() {
		return zoomOutButton;
	}

	public Button getIncreaseFontButton() {
		return increaseFontButton;
	}

	public Button getDecreaseFontButton() {
		return decreaseFontButton;
	}

	public ZoomableScrollPane getScrollPane() {
		return zoomableScrollPane;
	}

	public ToggleButton showInternalLabelsToggleButton() {
		return showInternalLabelsToggleButton;
	}

	public ScaleBar getScaleBar() {
		return scaleBar;
	}

	public CopyableLabel getFitLabel() {
		return fitLabel;
	}

	public AnchorPane getInnerAnchorPane() {
		return innerAnchorPane;
	}

	public VBox getFormatVBox() {
		return formatVBox;
	}

	public ToggleButton getFormatToggleButton() {
		return formatToggleButton;
	}
}

