/*
 *  GeneTreeViewController.java Copyright (C) 2023 Daniel H. Huson
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

import javafx.fxml.FXML;
import javafx.scene.Group;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public class GeneTreeViewController {

	@FXML
	private Pane centerPane;

	@FXML
	private MenuItem closeMenuItem;

	@FXML
	private MenuBar menuBar;

	@FXML
	private MenuItem openMenuItem;

	@FXML
	private MenuItem importGeneNamesMenuItem;

	@FXML
	private MenuItem importFeaturesMenuItem;

	@FXML
	private MenuItem exportSubsetMenuItem;

	@FXML
	private ToolBar toolBar;

	@FXML
	private Slider zoomSlider;

	@FXML
	private ComboBox<String> searchGeneComboBox;

	@FXML
	private Slider slider;

	@FXML
	private VBox topPane;

	@FXML
	private Label label;

	@FXML
	private ToggleGroup layoutGroup;

	@FXML
	private RadioMenuItem stackMenuItem;

	@FXML
	private RadioMenuItem carouselMenuItem;

	@FXML
	private ToggleGroup treeLayoutGroup;

	@FXML
	private RadioMenuItem circularCladoMenuItem;

	@FXML
	private RadioMenuItem circularPhyloMenuItem;

	@FXML
	private RadioMenuItem radialCladoMenuItem;

	@FXML
	private RadioMenuItem radialPhyloMenuItem;

	@FXML
	private RadioMenuItem rectangularCladoMenuItem;

	@FXML
	private RadioMenuItem rectangularPhyloMenuItem;

	@FXML
	private RadioMenuItem triangularCladoMenuItem;

	@FXML
	private ToggleGroup orderGroup;

	@FXML
	private RadioMenuItem defaultOrderMenuItem;

	@FXML
	private Menu taxonOrderSubMenu;

	@FXML
	private Menu similarityOrderSubMenu;

	@FXML
	private RadioMenuItem topologyOrderMenuItem;

	@FXML
	private RadioMenuItem featureOrderMenuItem;

	@FXML
	private Button nextButton;

	@FXML
	private Button previousButton;

	@FXML
	private ProgressBar progressBar;

	@FXML
	private Label progressLabel;

	@FXML
	private VBox vBox;

	@FXML
	private void initialize() {
		label.setText("");
	}

	public Button getNextButton() {
		return nextButton;
	}

	public Button getPreviousButton() {
		return previousButton;
	}

	public ProgressBar getProgressBar() {
		return progressBar;
	}

	public Label getProgressLabel() {
		return progressLabel;
	}

	public RadioMenuItem getCircularCladoMenuItem() {
		return circularCladoMenuItem;
	}

	public RadioMenuItem getCircularPhyloMenuItem() {
		return circularPhyloMenuItem;
	}

	public RadioMenuItem getRadialCladoMenuItem() {
		return radialCladoMenuItem;
	}

	public RadioMenuItem getRadialPhyloMenuItem() {
		return radialPhyloMenuItem;
	}

	public RadioMenuItem getRectangularCladoMenuItem() {
		return rectangularCladoMenuItem;
	}

	public RadioMenuItem getRectangularPhyloMenuItem() {
		return rectangularPhyloMenuItem;
	}

	public RadioMenuItem getTriangularCladoMenuItem() {
		return triangularCladoMenuItem;
	}

	private final Group stack = new Group();

	public Pane getCenterPane() {
		return centerPane;
	}

	public MenuItem getCloseMenuItem() {
		return closeMenuItem;
	}

	public MenuItem getOpenMenuItem() {
		return openMenuItem;
	}

	public MenuItem getImportGeneNamesMenuItem() {
		return importGeneNamesMenuItem;
	}

	public MenuItem getImportFeaturesMenuItem() {
		return importFeaturesMenuItem;
	}

	public MenuItem getExportSubsetMenuItem() {
		return exportSubsetMenuItem;
	}

	public ToolBar getToolBar() {
		return toolBar;
	}

	public Label getLabel() {
		return label;
	}

	public Group getStack() {
		return stack;
	}

	public Slider getSlider() {
		return slider;
	}

	public void setSlider(Slider slider) {
		this.slider = slider;
	}

	public ToggleGroup getLayoutGroup() {
		return layoutGroup;
	}

	public RadioMenuItem getStackMenuItem() {
		return stackMenuItem;
	}

	public RadioMenuItem getCarouselMenuItem() {
		return carouselMenuItem;
	}

	public ToggleGroup getTreeLayoutGroup() {
		return treeLayoutGroup;
	}

	public Slider getZoomSlider() {
		return zoomSlider;
	}

	public void setZoomSlider(Slider zoomSlider) {
		this.zoomSlider = zoomSlider;
	}

	public ComboBox<String> getSearchGeneComboBox() {
		return searchGeneComboBox;
	}

	public ToggleGroup getOrderGroup() {
		return orderGroup;
	}

	public RadioMenuItem getDefaultOrderMenuItem() {
		return defaultOrderMenuItem;
	}

	public Menu getTaxonOrderSubMenu() {
		return taxonOrderSubMenu;
	}

	public Menu getSimilarityOrderSubMenu() {
		return similarityOrderSubMenu;
	}

	public RadioMenuItem getTopologyOrderMenuItem() {
		return topologyOrderMenuItem;
	}

	public RadioMenuItem getFeatureOrderMenuItem() {
		return featureOrderMenuItem;
	}

	public VBox getvBox() {return vBox;}
}
