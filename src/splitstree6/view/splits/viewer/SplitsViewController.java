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

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import jloda.fx.control.CopyableLabel;
import jloda.fx.control.ZoomableScrollPane;
import jloda.fx.util.DraggableLabel;
import jloda.fx.util.ScaleBar;
import splitstree6.view.trees.treepages.LayoutOrientation;

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
	private Button findButton;

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
	private ToggleButton useWeightsToggleButton;

	@FXML
	private ToggleButton showBootstrapValuesToggleButton;
	@FXML
	private AnchorPane innerAnchorPane;

	@FXML
	private TitledPane formatTitledPane;

	@FXML
	private VBox formatVBox;


	private final ZoomableScrollPane zoomableScrollPane = new ZoomableScrollPane(null);

	private final ScaleBar scaleBar = new ScaleBar();

	private final CopyableLabel fitLabel = new CopyableLabel();

	@FXML
	private void initialize() {
		centerPane.getChildren().add(zoomableScrollPane);
		innerAnchorPane.getChildren().add(scaleBar);
		AnchorPane.setTopAnchor(scaleBar, 2.0);
		AnchorPane.setLeftAnchor(scaleBar, 5.0);

		AnchorPane.setTopAnchor(fitLabel, 25.0);
		AnchorPane.setLeftAnchor(fitLabel, 2.0);
		innerAnchorPane.getChildren().add(fitLabel);

		formatVBox.setMinHeight(0);
		formatVBox.setMaxHeight(formatVBox.getPrefHeight());

		if (!formatTitledPane.isExpanded()) {
			formatVBox.setVisible(false);
			formatVBox.setMaxHeight(0);
		} else {
			formatVBox.setVisible(true);
			formatVBox.setMaxHeight(formatVBox.getPrefHeight());
		}

		formatTitledPane.expandedProperty().addListener((v, o, n) -> {
			formatVBox.setVisible(n);
			formatVBox.setMaxHeight(n ? formatVBox.getPrefHeight() : 0);
		});

		AnchorPane.setTopAnchor(formatTitledPane, AnchorPane.getTopAnchor(formatTitledPane) + 30);

		innerAnchorPane.getChildren().remove(formatVBox);
		innerAnchorPane.getChildren().add(formatVBox);

		DraggableLabel.makeDraggable(fitLabel);
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

	public Button getFindButton() {
		return findButton;
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

	public ToggleButton getUseWeightsToggleButton() {
		return useWeightsToggleButton;
	}

	public ToggleButton showConfidenceToggleButton() {
		return showBootstrapValuesToggleButton;
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

	public VBox getFormatVbox() {
		return formatVBox;
	}
}
