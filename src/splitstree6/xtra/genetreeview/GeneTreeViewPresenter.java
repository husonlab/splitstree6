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
import javafx.beans.property.*;
import javafx.collections.ObservableList;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import jloda.fx.selection.SelectionModel;
import jloda.fx.selection.SetSelectionModel;
import jloda.phylo.PhyloTree;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.data.parts.Taxon;
import splitstree6.layout.tree.TreeDiagramType;
import splitstree6.xtra.genetreeview.io.*;
import splitstree6.xtra.genetreeview.layout.*;

import java.io.*;
import java.util.ArrayList;
import java.util.TreeMap;

public class GeneTreeViewPresenter {
	private final SelectionModel<Integer> taxonSelection = new SetSelectionModel<Integer>();

	Group trees = new Group();
	Group treeSnapshots = new Group();
	double treeHeight = 250;
	double treeWidth = 200;
	LayoutType layoutType = LayoutType.Stack;
	MultipleFramesLayout currentLayout = new MultipleFramesLayout() {};
	TreeDiagramType treeDiagramType = TreeDiagramType.RectangularCladogram;;
	Tooltip currentTreeToolTip = new Tooltip("");
	Tooltip previousTreeToolTip = new Tooltip("");
	Tooltip nextTreeToolTip = new Tooltip("");
	PerspectiveCamera camera = new PerspectiveCamera(true);
	ColorBar colorBar;
	BooleanProperty[] treeSelectionProperties;
	IntegerProperty selectedTreesCount = new SimpleIntegerProperty(0);
	IntegerProperty treesCount = new SimpleIntegerProperty(0);

	public GeneTreeViewPresenter(GeneTreeView geneTreeView) {

		var controller = geneTreeView.getController();
		Model model = geneTreeView.getModel();

		// Setting up a subScene with camera (further camera settings are done by the layout)
		var subScene = new SubScene(trees, 600, 600, true, SceneAntialiasing.BALANCED);
		subScene.widthProperty().bind(controller.getCenterPane().widthProperty());
		subScene.heightProperty().bind(controller.getCenterPane().heightProperty());
		subScene.setCamera(camera);
		controller.getCenterPane().getChildren().add(subScene);


		// MenuBar
		controller.getOpenMenuItem().setOnAction(e -> {
			openFile(geneTreeView.getStage(),controller,model,subScene);
		});

		// Does not work anymore as trees are loaded in a Task
		/*model.lastUpdateProperty().addListener(a -> {
			controller.getLabel().setText("Taxa: %,d, Trees: %,d".formatted(model.getTaxaBlock().getNtax(),
					model.getTreesBlock().getNTrees()));
		});*/

		controller.getImportGeneNamesMenuItem().disableProperty().bind(controller.getSlider().disableProperty());
		controller.getImportGeneNamesMenuItem().setOnAction(e ->
				importGeneNames(geneTreeView.getStage(),controller,model));

		controller.getImportFeaturesMenuItem().disableProperty().bind(controller.getSlider().disableProperty());
		controller.getImportFeaturesMenuItem().setOnAction(e ->
				importFeatures(geneTreeView.getStage(),controller,model));

		controller.getExportSubsetMenuItem().disableProperty().bind(selectedTreesCount.isEqualTo(0));
		controller.getExportSubsetMenuItem().setOnAction(e ->
		{
			try {
				exportTreeSubset(geneTreeView.getStage(),controller,model);
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		});

		controller.getCloseMenuItem().setOnAction(e -> Platform.exit());

		controller.getLayoutGroup().selectedToggleProperty().addListener((InvalidationListener) -> {
			updateLayout(controller.getCenterPane().getBoundsInParent().getWidth(),
					controller.getCenterPane().getBoundsInParent().getHeight(),controller.getLayoutGroup().getSelectedToggle(),controller);
		});

		controller.getTreeLayoutGroup().selectedToggleProperty().addListener((InvalidationListener) -> {
			updateTreeLayout(model,controller.getCenterPane().getBoundsInParent().getWidth(),
					controller.getCenterPane().getBoundsInParent().getHeight(),
					controller.getTreeLayoutGroup().getSelectedToggle(),controller,subScene);
		});

		controller.getSelectAllMenuItem().disableProperty().bind(selectedTreesCount.isEqualTo(treesCount)
				.or(treesCount.isEqualTo(0)));
		controller.getSelectAllMenuItem().setOnAction(e -> {
			for (BooleanProperty selectionState : treeSelectionProperties) selectionState.set(true);
		});
		controller.getSelectNoneMenuItem().disableProperty().bind(selectedTreesCount.isEqualTo(0)
				.or(treesCount.isEqualTo(0)));
		controller.getSelectNoneMenuItem().setOnAction(e -> {
			for (BooleanProperty selectionState : treeSelectionProperties) selectionState.set(false);
		});
		controller.getSelectInverseMenuItem().disableProperty().bind(treesCount.isEqualTo(0));
		controller.getSelectInverseMenuItem().setOnAction(e -> {
			for (Node treeSheet : trees.getChildren()) ((TreeSheet)treeSheet).setSelectedProperty();
		});

		controller.getOrderGroup().selectedToggleProperty().addListener((observableValue, oldValue, newValue) -> {
			try {
				changeTreeOrder(model,controller.getOrderGroup(),oldValue,newValue,controller,subScene,geneTreeView.getStage());
			} catch (IOException e) {
				e.printStackTrace();
			}
		});


		// ToolBar (the zoom slider is handled by the layout)
		controller.getSearchGeneComboBox().disableProperty().bind(controller.getSlider().disableProperty());
		controller.getSearchGeneComboBox().setOnAction(e -> {
			String selectedItem = controller.getSearchGeneComboBox().getSelectionModel().getSelectedItem();
			if (selectedItem != null) {
				for (int i=0; i<model.getOrderedGeneNames().size(); i++) {
					if (model.getOrderedGeneNames().get(i).equals(selectedItem)){
						controller.getSlider().setValue(i+1);
						controller.getSearchGeneComboBox().getEditor().setText(selectedItem);
					}
				}
			}
		});


		// Slider and buttons to go through the trees
		controller.getSlider().prefWidthProperty().bind(controller.getCenterPane().widthProperty().subtract(50));
		controller.getSlider().valueProperty().addListener((observableValue, oldValue, newValue) -> {
			currentLayout.updatePosition((double)oldValue,(double)newValue, controller.getCenterPane().getBoundsInParent().getWidth(), treeWidth);
			updateButtonTooltips(newValue.doubleValue(),model.getOrderedGeneNames());
		});
		// For Stack layout: using snapshots whenever the slider is dragged for fluent performance
		controller.getSlider().setOnMouseDragged(e -> {
			if (currentLayout.getType() == LayoutType.Stack) {
				((StackLayout) currentLayout).setSliderDragged(true);
				subScene.setRoot(treeSnapshots);
			}
		});
		controller.getSlider().setOnMouseReleased(e -> {
			if (currentLayout.getType() == LayoutType.Stack) {
				((StackLayout) currentLayout).setSliderDragged(false);
				currentLayout.updatePosition(1,controller.getSlider().getValue(),controller.getCenterPane().getBoundsInParent().getWidth(), treeWidth);
				subScene.setRoot(trees);
			}
		});

		controller.getPreviousButton().setOnAction(e -> focusOnPreviousTree(controller.getSlider()));
		controller.getPreviousButton().setTooltip(previousTreeToolTip);
		controller.getPreviousButton().disableProperty().bind(controller.getSlider().disableProperty());

		controller.getNextButton().setOnAction(e -> focusOnNextTree(controller.getSlider()));
		controller.getNextButton().setTooltip(nextTreeToolTip);
		controller.getNextButton().disableProperty().bind(controller.getSlider().disableProperty());
	}

	private void openFile(Stage stage, GeneTreeViewController controller, Model model, SubScene subScene) {
		final var fileChooser = new FileChooser();
		fileChooser.setTitle("Open trees");

		var file = fileChooser.showOpenDialog(stage);
		if (file != null) {
			Service<Void> loadingTreesService = new Service<>() {
				@Override
				protected Task<Void> createTask() {
					return new LoadTreesTask(file,model);
				}
			};
			controller.getProgressBar().visibleProperty().bind(loadingTreesService.runningProperty());
			controller.getProgressBar().progressProperty().bind(loadingTreesService.progressProperty());
			loadingTreesService.setOnScheduled(v -> {
				controller.getProgressLabel().setText("Loading trees ...");
			});
			loadingTreesService.setOnSucceeded(v -> {
				System.out.println("Loading succeeded");
				controller.getLabel().setText("Taxa: %,d, Trees: %,d".formatted(model.getTaxaBlock().getNtax(),
						model.getTreesBlock().getNTrees()));
				treesCount.set(model.getTreesBlock().getNTrees());
				initializeSlider(model.getTreesBlock(), controller.getSlider());
				initializeColorBar(controller.getvBox(),model.getTreesBlock(), controller.getSlider(),
						model.getTreeOrder());
				treeSelectionProperties = new SimpleBooleanProperty[model.getTreesBlock().getNTrees()];
				initializeTreesLayout(model, controller.getCenterPane().getBoundsInParent().getWidth(),
						controller.getCenterPane().getBoundsInParent().getHeight(), controller, subScene);
				initializeGeneSearch(controller.getSearchGeneComboBox(),model.getOrderedGeneNames());
				initializeTaxaList(controller.getTaxonOrderSubMenu(),controller.getOrderGroup(),model.getTaxaBlock());
			});
			loadingTreesService.setOnFailed(u -> {
				System.out.println("Loading trees failed");
				controller.getProgressLabel().setText("Loading trees failed");
			});
			loadingTreesService.restart();

		}
	}

	private void importGeneNames(Stage stage, GeneTreeViewController controller, Model model) {
		var geneNameParser = new GeneNameParser(stage,model);
		// If new names have been parsed to the model, names in treeSheets and snapshots need to be updated:
		geneNameParser.parsedProperty().addListener((InvalidationListener) -> {
			for (int i = 0; i < model.getOrderedGeneNames().size(); i++) {
				((TreeSheet)trees.getChildren().get(i)).setTreeName(model.getOrderedGeneNames().get(i));
				updateSnapshot(i,controller);
			}
		});
	}

	private void importFeatures(Stage stage, GeneTreeViewController controller, Model model) {
		final var fileChooser = new FileChooser();
		fileChooser.setTitle("Import features");

		var file = fileChooser.showOpenDialog(stage);
		if (file != null) {
			Service<Void> importFeaturesService = new Service<>() {
				@Override
				protected Task<Void> createTask() {
					return new ImportFeaturesTask(file,model);
					// TODO: implement ImportFeaturesTask
				}
			};
			controller.getProgressBar().visibleProperty().bind(importFeaturesService.runningProperty());
			controller.getProgressBar().progressProperty().bind(importFeaturesService.progressProperty());
			importFeaturesService.setOnScheduled(v -> {
				controller.getProgressLabel().setText("Importing features ...");
			});
			importFeaturesService.setOnSucceeded(v -> {
				System.out.println("Import succeeded");

			});
			importFeaturesService.setOnFailed(u -> {
				System.out.println("Importing features failed");
				controller.getProgressLabel().setText("Importing features failed");
			});
			importFeaturesService.restart();
		}
	}

	private void exportTreeSubset(Stage stage, GeneTreeViewController controller, Model model) throws IOException {
		final var fileChooser = new FileChooser();
		fileChooser.setTitle("Save tree subset");

		var file = fileChooser.showSaveDialog(stage);
		if (file != null) {
			Service<Void> exportTreesService = new Service<>() {
				@Override
				protected Task<Void> createTask() {
					return new ExportTreesTask(file,model,treeSelectionProperties);
				}
			};
			controller.getProgressBar().visibleProperty().bind(exportTreesService.runningProperty());
			controller.getProgressBar().progressProperty().bind(exportTreesService.progressProperty());
			exportTreesService.setOnScheduled(v -> {
				controller.getProgressLabel().setText("Saving subset of trees in "+file.getName());
			});
			exportTreesService.setOnSucceeded(v -> {
				System.out.println("Export succeeded");
				controller.getProgressLabel().setText("");
			});
			exportTreesService.setOnFailed(u -> {
				System.out.println("Export failed");
				controller.getProgressLabel().setText("Saving subset of trees failed");
			});
			exportTreesService.restart();
		}
	}

	public void initializeSlider(TreesBlock treesBlock, Slider slider) {
		slider.setMin(1);
		slider.setMax(treesBlock.getNTrees());
		slider.disableProperty().setValue(false);
		//Tooltip.install(slider, currentTreeToolTip);
		if (treesBlock.getNTrees() > 0) {
			currentTreeToolTip.setText(treesBlock.getTree(1).getName());
			previousTreeToolTip.setText(currentTreeToolTip.getText());
		}
		if (treesBlock.getNTrees() > 1)
			nextTreeToolTip.setText(treesBlock.getTree(2).getName());
		slider.setMinorTickCount(1);
		slider.setMajorTickUnit(5);
		slider.setValue(1);
	}

	private void initializeColorBar(VBox parent, TreesBlock treesBlock, Slider slider, ArrayList<Integer> treeOrder) {
		colorBar = new ColorBar(treesBlock,slider,treeOrder);
		parent.getChildren().remove(0);
		parent.getChildren().add(0,colorBar);
	}

	public void initializeTreesLayout(Model model, double paneWidth, double paneHeight,
									  GeneTreeViewController controller, SubScene subScene) {
		trees.getChildren().clear();
		Service<Group> visualizeTreesService = new Service<>() {
			@Override
			protected Task<Group> createTask() {
				return new VisualizeTreesTask(model.getTreesBlock(), model.getTreeOrder(), treeWidth, treeHeight,
						treeDiagramType);
			}
		};
		controller.getProgressBar().visibleProperty().bind(visualizeTreesService.runningProperty());
		controller.getProgressBar().progressProperty().bind(visualizeTreesService.progressProperty());
		visualizeTreesService.setOnScheduled(v -> {
			controller.getProgressLabel().setText("Drawing trees ...");
		});
		visualizeTreesService.setOnSucceeded(v -> {
			System.out.println("Visualizations succeeded");
			controller.getCenterPane().getChildren().clear();
			trees = visualizeTreesService.getValue();
			setupTreeSelectionBindings(colorBar.getChildren(),controller);
			createSnapshots(); // can not be done in a service as JavaFX thread is needed
			updateLayout(paneWidth,paneHeight,controller.getLayoutGroup().getSelectedToggle(),controller);
			subScene.setRoot(trees);
			controller.getCenterPane().getChildren().add(subScene);
			controller.getProgressLabel().setText("");
		});
		visualizeTreesService.setOnFailed(u -> {
			System.out.println("Visualizing trees failed");
			controller.getProgressLabel().setText("Drawing trees failed");
		});
		visualizeTreesService.restart();
	}

	private void initializeGeneSearch(ComboBox<String> searchGeneComboBox, ObservableList<String> geneNames) {
		searchGeneComboBox.setItems(geneNames);
		new ComboBoxListener(searchGeneComboBox);
	}

	private void initializeTaxaList(Menu taxonOrderSubMenu, ToggleGroup orderGroup, TaxaBlock taxaBlock) {
		for (Taxon taxon : taxaBlock.getTaxa()) {
			String taxonName = taxon.getName();
			RadioMenuItem newItem = new RadioMenuItem();
			newItem.setText(taxonName);
			newItem.setToggleGroup(orderGroup);
			taxonOrderSubMenu.getItems().add(newItem);
		}
		taxonOrderSubMenu.setDisable(false);
	}

	private void setupTreeSelectionBindings(ObservableList<Node> colorBarBoxes, GeneTreeViewController controller) {
		// Binding the selection state of colorBarBox-treeSheet pairs
		// A mediatorProperty is needed to avoid circular dependency
		if (treeSelectionProperties == null) return;
		selectedTreesCount.set(0);
		for (int i = 0; i < trees.getChildren().size(); i++) {
			ColorBarBox colorBarBox = (ColorBarBox) colorBarBoxes.get(i + 1); // first box is only for spacing
			TreeSheet treeSheet = (TreeSheet) trees.getChildren().get(i);
			BooleanProperty mediatorProperty;
			if (treeSelectionProperties[i] == null) {
				mediatorProperty = new SimpleBooleanProperty();
				treeSelectionProperties[i] = mediatorProperty;
				int treeIndex = i;
				mediatorProperty.addListener((InvalidationListener) -> {
					Platform.runLater(() -> updateSnapshot(treeIndex, controller));
					if (mediatorProperty.get()) selectedTreesCount.set(selectedTreesCount.get()+1);
					else selectedTreesCount.set(selectedTreesCount.get()-1);
				});
			}
			else mediatorProperty = treeSelectionProperties[i];
			colorBarBox.isSelectedProperty().bindBidirectional(mediatorProperty);
			treeSheet.isSelectedProperty().bindBidirectional(mediatorProperty);
			if (mediatorProperty.get()) selectedTreesCount.set(selectedTreesCount.get()+1);
		}
	}

	private void createSnapshots() {
		treeSnapshots.getChildren().clear();
		for (int treeIndex = 0; treeIndex<trees.getChildren().size(); treeIndex++) {
			Node treeVis = trees.getChildren().get(treeIndex);
			Node snapShot = createSnapshot(treeVis);
			treeSnapshots.getChildren().add(treeIndex,snapShot);
		}
	}

	private Node createSnapshot(Node treeVis) {
		Bounds bounds = treeVis.getLayoutBounds();
		WritableImage writableImage = new WritableImage((int)Math.round(bounds.getWidth()*2),
				(int)Math.round(bounds.getHeight()*2)); // scaling with factor 2 for better resolution
		SnapshotParameters parameters = new SnapshotParameters();
		parameters.setTransform(javafx.scene.transform.Transform.scale(2,2));
		Image image = treeVis.snapshot(parameters,writableImage);
		ImageView snapShot = new ImageView(image);
		snapShot.setFitWidth(bounds.getWidth());
		snapShot.setFitHeight(bounds.getHeight());
		return snapShot;
	}

	private void updateSnapshot(int treeIndex, GeneTreeViewController controller) {
		Node treeVis = trees.getChildren().get(treeIndex);
		currentLayout.resetNode(treeVis); // reset rotation and scaling for the snapshot

		Node snapShot = createSnapshot(treeVis);

		// Transformation of treeVis and snapshot according to the current layout and slider position
		currentLayout.initializeNode(snapShot,treeIndex,controller.getSlider().getValue());
		currentLayout.initializeNode(treeVis,treeIndex,controller.getSlider().getValue());

		treeSnapshots.getChildren().remove(treeIndex);
		treeSnapshots.getChildren().add(treeIndex,snapShot);
	}

	private void updateLayout(double paneWidth, double paneHeight, Toggle selectedLayoutToggle,
							  GeneTreeViewController controller) {
		if (selectedLayoutToggle.equals(controller.getStackMenuItem())) {
			if (!trees.getChildren().isEmpty()) currentLayout = new StackLayout(trees.getChildren(),
					treeSnapshots.getChildren(), treeWidth, treeHeight, camera, paneWidth,
					controller.getSlider(), controller.getZoomSlider());
			layoutType = LayoutType.Stack;
		}
		else if (selectedLayoutToggle.equals(controller.getCarouselMenuItem())) {
			if (!trees.getChildren().isEmpty()) currentLayout = new CarouselLayout(trees.getChildren(), treeWidth,
					treeHeight, camera, paneWidth, controller.getSlider(), controller.getZoomSlider());
			layoutType = LayoutType.Carousel;
		}
		else System.out.println("No layout");
		controller.getSlider().setValue(controller.getSlider().getValue());
	}

	private void updateTreeLayout(Model model, double paneWidth, double paneHeight,
								  Toggle selectedTreeLayoutToggle, GeneTreeViewController controller, SubScene subScene) {
		if (selectedTreeLayoutToggle.equals(controller.getRectangularCladoMenuItem()))
			treeDiagramType = TreeDiagramType.RectangularCladogram;
		else if (selectedTreeLayoutToggle.equals(controller.getRectangularPhyloMenuItem()))
			treeDiagramType = TreeDiagramType.RectangularPhylogram;
		else if (selectedTreeLayoutToggle.equals(controller.getRadialCladoMenuItem()))
			treeDiagramType = TreeDiagramType.RadialCladogram;
		else if (selectedTreeLayoutToggle.equals(controller.getRadialPhyloMenuItem()))
			treeDiagramType = TreeDiagramType.RadialPhylogram;
		else if (selectedTreeLayoutToggle.equals(controller.getCircularCladoMenuItem()))
			treeDiagramType = TreeDiagramType.CircularCladogram;
		else if (selectedTreeLayoutToggle.equals(controller.getCircularPhyloMenuItem()))
			treeDiagramType = TreeDiagramType.CircularPhylogram;
		else if (selectedTreeLayoutToggle.equals(controller.getTriangularCladoMenuItem()))
			treeDiagramType = TreeDiagramType.TriangularCladogram;
		if (model.getTreesBlock().getNTrees()>0) initializeTreesLayout(model,paneWidth,paneHeight, controller,subScene);
	}

	private void changeTreeOrder(Model model, ToggleGroup orderGroup, Toggle oldSelection, Toggle newSelection, GeneTreeViewController controller, SubScene subScene, Stage stage) throws IOException {
		if (newSelection == controller.getDefaultOrderMenuItem()) {
			model.resetTreeOrder();
			initializeTreesLayout(model,controller.getCenterPane().getBoundsInParent().getWidth(),
					controller.getCenterPane().getBoundsInParent().getHeight(),controller,subScene);
			initializeColorBar(controller.getvBox(), model.getTreesBlock(),controller.getSlider(),
					model.getTreeOrder());
		}
		else if (newSelection.equals(controller.getTopologyOrderMenuItem())) {
			String selectedTreeName = currentTreeToolTip.getText();
			Stage topologyOrderDialog = new Stage();
			topologyOrderDialog.initStyle(stage.getStyle());
			topologyOrderDialog.setTitle("Similarity calculation");
			topologyOrderDialog.initModality(Modality.APPLICATION_MODAL);
			topologyOrderDialog.initOwner(stage);

			Button startButton = new Button("Calculate");
			startButton.setOnAction(e -> {
				int index = (int)Math.round(controller.getSlider().getValue());
				PhyloTree selectedTree = model.getTreesBlock().getTree(model.getTreeOrder().get(index-1));
				System.out.println(selectedTree.getName());
				TreeMap<Integer,String> orderedGeneNames = new TreeMap<>();
				// TODO: calculate pairwise similarities with selected tree and put similarity-treeName pairs in the TreeMap
				if (orderedGeneNames.size() == model.getTreesBlock().size()) {
					model.setTreeOrder(orderedGeneNames);
					initializeTreesLayout(model,controller.getCenterPane().getBoundsInParent().getWidth(),
							controller.getCenterPane().getBoundsInParent().getHeight(),controller,subScene);
					initializeColorBar(controller.getvBox(), model.getTreesBlock(),controller.getSlider(),
							model.getTreeOrder());
				}
				else orderGroup.selectToggle(oldSelection);
			});

			Button cancelButton = new Button("Cancel");
			cancelButton.setOnAction(e -> {
				topologyOrderDialog.close();
				orderGroup.selectToggle(oldSelection);
			});

			VBox vBox = new VBox(10);
			vBox.setPadding(new Insets(10));
			HBox hBox = new HBox();
			hBox.getChildren().addAll(startButton, cancelButton);
			hBox.setSpacing(5);
			var label = new Label("Pairwise similarity with gene tree "+selectedTreeName+" will be calculated");
			vBox.getChildren().addAll(label, hBox);

			Scene scene = new Scene(vBox, 400, 100);
			topologyOrderDialog.setScene(scene);
			topologyOrderDialog.show();

		}
		else if (newSelection.equals(controller.getFeatureOrderMenuItem())) {
			// TODO: order trees by feature similarity with the currently selected tree -> numerical features only
			orderGroup.selectToggle(oldSelection);
		}
		else { // remaining option: order as in selected taxon
			int toggleIndex = orderGroup.getToggles().indexOf(newSelection)-3;
			String taxonName = controller.getTaxonOrderSubMenu().getItems().get(toggleIndex).getText().replaceAll(" ","+");
			String[] taxonNames = taxonName.split("_");
			String finalTaxonName = taxonNames[0]+"+"+taxonNames[1];
			System.out.println(finalTaxonName);

			Service<TreeMap<Integer,String>> getGeneOrderService = new Service<>() {
				@Override
				protected Task<TreeMap<Integer,String>> createTask() {
					return new GetGeneOrderTask(model, finalTaxonName);
				}
			};
			controller.getProgressBar().visibleProperty().bind(getGeneOrderService.runningProperty());
			controller.getProgressBar().progressProperty().bind(getGeneOrderService.progressProperty());
			getGeneOrderService.setOnScheduled(v -> {
				controller.getProgressLabel().setText("Reordering trees ...");
			});
			getGeneOrderService.setOnSucceeded(v -> {
				System.out.println("Reordering succeeded");
				TreeMap<Integer,String> orderedGeneNames = getGeneOrderService.getValue();
				if (orderedGeneNames.size() == model.getTreesBlock().size()) {
					model.setTreeOrder(orderedGeneNames);
					initializeTreesLayout(model,controller.getCenterPane().getBoundsInParent().getWidth(),
							controller.getCenterPane().getBoundsInParent().getHeight(),controller,subScene);
					initializeColorBar(controller.getvBox(), model.getTreesBlock(),controller.getSlider(),
							model.getTreeOrder());
				}
				controller.getProgressLabel().setText("");
			});
			getGeneOrderService.setOnFailed(u -> {
				System.out.println("Reordering trees failed");
				controller.getProgressLabel().setText("Reordering trees failed");
				orderGroup.selectToggle(oldSelection);
			});
			getGeneOrderService.restart();
		}
	}

	public void updateButtonTooltips(double sliderValue, ObservableList<String> orderedGeneNames) {
		int index = (int)Math.round(sliderValue);
		currentTreeToolTip.setText(orderedGeneNames.get(index-1));
		if (index < orderedGeneNames.size()) {
			nextTreeToolTip.setText(orderedGeneNames.get(index));
		}
		else nextTreeToolTip.setText(currentTreeToolTip.getText());
		if (index>1) {
			previousTreeToolTip.setText(orderedGeneNames.get(index-2));
		}
		else previousTreeToolTip.setText(currentTreeToolTip.getText());
	}

	private void focusOnPreviousTree(Slider slider) {
		if (slider.getValue() < 2) {
			slider.setValue(1);
			return;
		}
		if (Math.abs(slider.getValue() - Math.round(slider.getValue())) < 0.05) slider.setValue(Math.round(slider.getValue()-1));
		else slider.setValue((int)slider.getValue());
	}

	private void focusOnNextTree(Slider slider) {
		if (slider.getValue() > slider.getMax()-1) {
			slider.setValue(slider.getMax());
			return;
		}
		if (Math.abs(slider.getValue() - Math.round(slider.getValue())) < 0.05) slider.setValue(Math.round(slider.getValue()+1));
		else slider.setValue((int)slider.getValue()+1);
	}
}
