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

import javafx.animation.*;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.SetChangeListener;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import javafx.util.Duration;
import jloda.fx.util.ColorSchemeManager;
import jloda.fx.util.Print;

import jloda.fx.util.RunAfterAWhile;
import jloda.phylo.PhyloTree;
import jloda.util.FileLineIterator;
import jloda.util.StringUtils;
import jloda.util.progress.ProgressPercentage;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TreesBlock;
import splitstree6.data.parts.Taxon;
import splitstree6.io.readers.trees.NewickReader;
import splitstree6.layout.tree.TreeDiagramType;
import splitstree6.utils.*;
import splitstree6.xtra.genetreeview.io.*;
import splitstree6.xtra.genetreeview.layout.*;

import java.io.*;
import java.util.*;

public class GeneTreeViewPresenter {

	private Group trees = new Group();
	private final Group treeSnapshots = new Group();
	private final double treeHeight = 250;
	private final double treeWidth = 220; // including taxa label space ~ 80 // TODO: make label space more flexible
	private MultipleFramesLayout currentLayout = new MultipleFramesLayout() {};
	private TreeDiagramType treeDiagramType = TreeDiagramType.RectangularCladogram;;
	private final Tooltip currentTreeToolTip = new Tooltip("");
	private final Tooltip previousTreeToolTip = new Tooltip("");
	private final Tooltip nextTreeToolTip = new Tooltip("");
	private final PerspectiveCamera camera = new PerspectiveCamera(true);
	private ColorBar colorBar = null;
	private final SelectionModelSet<Integer> treeSelectionModel = new SelectionModelSet<>();
	private final SelectionModelSet<Integer> taxaSelectionModel = new SelectionModelSet<>();
	private final HashMap<Integer,SelectionModelSet<Integer>> edgeSelectionModels = new HashMap<>();
	private final HashMap<Integer,TreeSheet> id2treeSheet = new HashMap<>();
	private final IntegerProperty treesCount = new SimpleIntegerProperty(0);
	private final IntegerProperty taxaCount = new SimpleIntegerProperty(0);
	private ChangeListener<Toggle> orderGroupListener = null;
	private final ChangeListener<Toggle> colorGroupListener;
	private final UndoRedoManager undoRedoManager = new UndoRedoManager();
	private double lastSliderValue = 1;
	private final Stabilizer stabilizer = new Stabilizer();

	public GeneTreeViewPresenter(GeneTreeView geneTreeView) {

		var controller = geneTreeView.getController();
		Model model = geneTreeView.getModel();

		// Setting up a subScene with camera (further camera settings are done by the layout)
		var subScene = new SubScene(trees, controller.getCenterPane().getWidth(), controller.getCenterPane().getHeight(), true, SceneAntialiasing.BALANCED);

		subScene.widthProperty().bind(controller.getCenterPane().widthProperty());
		subScene.heightProperty().bind(controller.getCenterPane().heightProperty());

		subScene.setCamera(camera);
		controller.getCenterPane().getChildren().add(subScene);

		geneTreeView.getStage().maximizedProperty().addListener((observableValue, oldValue, newValue) -> {
			if (!newValue & !trees.getChildren().isEmpty()) {
				Runnable updateLayout = () -> updateLayout(controller.getLayoutGroup().getSelectedToggle(), controller);
				RunAfterAWhile.applyInFXThread(System.currentTimeMillis(), updateLayout);
			}
		});


		// Does not work anymore as trees are loaded in a Task
		/*model.lastUpdateProperty().addListener(a -> {
			controller.getLabel().setText("Taxa: %,d, Trees: %,d".formatted(model.getTaxaBlock().getNtax(),
					model.getTreesBlock().getNTrees()));
		});*/


		// MenuBar
		// File Menu
		controller.getOpenMenuItem().setOnAction(e -> openFile(geneTreeView.getStage(),controller,model,subScene));

		controller.getImportGeneNamesMenuItem().disableProperty().bind(controller.getSlider().disableProperty());
		controller.getImportGeneNamesMenuItem().setOnAction(e ->
				importGeneNames(geneTreeView.getStage(), model, controller));

		controller.getExportSubsetMenuItem().disableProperty().bind(treeSelectionModel.sizeProperty().isEqualTo(0));
		controller.getExportSubsetMenuItem().setOnAction(e ->
		{
			try {
				exportTreeSubset(geneTreeView.getStage(),controller,model);
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		});

		controller.getPrintMenuItem().setOnAction(e -> {
			WritableImage writableImage = new WritableImage((int) controller.getCenterPane().getWidth()*3,
					(int) controller.getCenterPane().getHeight()*3); // factor 3 for better resolution
			SnapshotParameters parameters = new SnapshotParameters();
			parameters.setTransform(javafx.scene.transform.Transform.scale(3,3));
			Image image = controller.getCenterPane().snapshot(parameters, writableImage);
			ImageView imageView = new ImageView(image);
			Print.print(geneTreeView.getStage(),imageView);
		});

		controller.getCloseMenuItem().setOnAction(e -> Platform.exit());

		// Edit Menu
		controller.getUndoMenuItem().setOnAction(e -> undoRedoManager.undo());
		controller.getUndoMenuItem().textProperty().bind(undoRedoManager.undoLabelProperty());
		controller.getUndoMenuItem().disableProperty().bind(undoRedoManager.canUndoProperty().not());
		controller.getRedoMenuItem().setOnAction(e -> undoRedoManager.redo());
		controller.getRedoMenuItem().textProperty().bind(undoRedoManager.redoLabelProperty());
		controller.getRedoMenuItem().disableProperty().bind(undoRedoManager.canRedoProperty().not());

		controller.getCopyTaxaMenuItem().disableProperty().bind(treesCount.isEqualTo(0));
		controller.getCopyTaxaMenuItem().setOnAction(e -> {
			StringBuilder taxa = new StringBuilder();
			if (taxaSelectionModel.size() == 0) {
				for (Taxon taxon : model.getTaxaBlock().getTaxa())
					taxa.append(taxon.getName()).append("\n");
			}
			else {
				for (int taxonId : taxaSelectionModel.getSelectedItems())
					taxa.append(model.getTaxaBlock().get(taxonId).getName()).append("\n");
			}
			ClipboardContent content = new ClipboardContent();
			content.putString(taxa.toString());
			Clipboard.getSystemClipboard().setContent(content);
		});

		controller.getCopyImageMenuItem().setOnAction(e -> {
			WritableImage writableImage = new WritableImage((int) controller.getCenterPane().getWidth()*2,
					(int) controller.getCenterPane().getHeight()*2); // factor 2 for better resolution
			SnapshotParameters parameters = new SnapshotParameters();
			//parameters.setFill(Color.TRANSPARENT); // for black background
			parameters.setTransform(javafx.scene.transform.Transform.scale(2,2));
			Image image = controller.getCenterPane().snapshot(parameters, writableImage);

			ClipboardContent content = new ClipboardContent();
			content.putImage(image);
			Clipboard.getSystemClipboard().setContent(content);
		});

		controller.getCopySelectedNewicksMenuItem().disableProperty().bind(treeSelectionModel.sizeProperty().isEqualTo(0));
		controller.getCopySelectedNewicksMenuItem().setOnAction(e -> {
			StringBuilder newicks = new StringBuilder();
			var temp = new File("temp.txt");
			Service<Void> exportTreesService = new Service<>() {
				@Override
				protected Task<Void> createTask() {
					return new ExportTreesTask(temp,model,treeSelectionModel.getSelectedItems());
				}
			};
			controller.getProgressBar().visibleProperty().bind(exportTreesService.runningProperty());
			controller.getProgressBar().progressProperty().bind(exportTreesService.progressProperty());
			exportTreesService.setOnScheduled(v -> {
				controller.getProgressLabel().setText("Copying selected trees in newick format");
			});
			exportTreesService.setOnSucceeded(v -> {
				try {
					BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(temp)));
					String line;
					while ((line = br.readLine()) != null) {
						newicks.append(line).append("\n");
					}
				} catch (IOException ex) {
					ex.printStackTrace();
				}
				var clipboardContent = new ClipboardContent();
				clipboardContent.putString(newicks.toString());
				Clipboard.getSystemClipboard().setContent(clipboardContent);
				System.out.println("Copying succeeded");
				controller.getProgressLabel().setText("");
			});
			exportTreesService.setOnFailed(u -> {
				System.out.println("Copying failed");
				controller.getProgressLabel().setText("Copying failed");
			});
			exportTreesService.restart();
		});

		controller.getCopySelectedTreesMenuItem().disableProperty().bind(treeSelectionModel.sizeProperty().isEqualTo(0));
		controller.getCopySelectedTreesMenuItem().setOnAction(e -> {
			var clipboardContent = new ClipboardContent();
			var gridPane = new GridPane();
			int minColumnNumber = 2;
			if (treeSelectionModel.size() > 6) {
				minColumnNumber = 3;
				if (treeSelectionModel.size() > 12) {
					minColumnNumber = 4;
					if (treeSelectionModel.size() > 28) minColumnNumber = 5;
				}
			}
			int columnNumber = minColumnNumber;
			for (int i = 5; i >= minColumnNumber; i--) {
				if (treeSelectionModel.size()%i == 0) {
					columnNumber = i;
					break;
				}
			}
			int columnCount = 0;
			int rowCount = 0;
			for (int treeId : treeSelectionModel.getSelectedItems()) {
				int index = trees.getChildren().indexOf(id2treeSheet.get(treeId));
				// TODO: maybe remove the selection-indicating blue frame for the image
				ImageView treeSnap = (ImageView) treeSnapshots.getChildren().get(index);
				Image treeImage = treeSnap.getImage();
				WritableImage treeImageCopy = new WritableImage(treeImage.getPixelReader(),
						(int) treeImage.getWidth(), (int) treeImage.getHeight());
				var finalImage = new ImageView(treeImageCopy);
				gridPane.add(finalImage,columnCount,rowCount);
				columnCount++;
				if (columnCount%columnNumber == 0) {
					rowCount++;
					columnCount = 0;
				}
			}
			Image image = gridPane.snapshot(null,null);
			clipboardContent.putImage(image);
			Clipboard.getSystemClipboard().setContent(clipboardContent);
		});

		// TODO: Debug paste and delete
		controller.getPasteMenuItem().disableProperty().bind(treesCount.isEqualTo(0));
		controller.getPasteMenuItem().setOnAction(event -> {
			var pastedTrees = pasteTrees(model,controller);
			if (pastedTrees != null) {
				Runnable undo = () -> {
					for (int i = pastedTrees.size()-1; i >= 0; i--)
						removeTree(pastedTrees.get(i), model, controller);
				};
				Runnable redo = () -> {
					pasteTrees(pastedTrees, model, controller);
				};
				undoRedoManager.add(new SimpleCommand("paste", undo, redo));
			}
		});

		controller.getDeleteSelectedMenuItem().disableProperty().bind(treeSelectionModel.sizeProperty().isEqualTo(0)
				.or(treesCount.isEqualTo(0)));
		controller.getDeleteSelectedMenuItem().setOnAction(e -> {
			ObservableList<PhyloTree> deletedTrees = FXCollections.observableArrayList();
			LinkedList<Integer> deletedTreeIds = new LinkedList<>();
			for (var treeId : treeSelectionModel.getSelectedItems()) {
				var treeSheet = id2treeSheet.get(treeId);
				var treeIndex = model.getOrderedGeneNames().indexOf(treeSheet.getTreeName());
				var phyloTree = model.getTreesBlock().getTree(model.getTreeOrder().get(treeIndex));
				deletedTrees.add(phyloTree);
				deletedTreeIds.add(treeId);
			}
			for (var phyloTree : deletedTrees) removeTree(phyloTree, model, controller);
			//for (var phyloTreeId : deletedTreeIds) removeTree(phyloTreeId, model, controller);
		});

		// Selection Menu: Tree Selection
		controller.getSelectAllMenuItem().disableProperty().bind(treeSelectionModel.sizeProperty().isEqualTo(treesCount)
				.or(treesCount.isEqualTo(0)));
		controller.getSelectAllMenuItem().setOnAction(e -> treeSelectionModel.selectAll(id2treeSheet.keySet()));

		controller.getSelectNoneMenuItem().disableProperty().bind(treeSelectionModel.sizeProperty().isEqualTo(0)
				.or(treesCount.isEqualTo(0)));
		controller.getSelectNoneMenuItem().setOnAction(e -> treeSelectionModel.clearSelection());

		controller.getSelectInverseMenuItem().disableProperty().bind(treesCount.isEqualTo(0));
		controller.getSelectInverseMenuItem().setOnAction(e -> {
			var formerSelection = treeSelectionModel.getSelectedItems();
			var newSelection = new HashSet<Integer>();
			for (int treeId : id2treeSheet.keySet()) {
				if (formerSelection.contains(treeId)) continue;
				newSelection.add(treeId);
			}
			treeSelectionModel.clearSelection();
			treeSelectionModel.selectAll(newSelection);
		});

		treeSelectionModel.getSelectedItems().addListener((SetChangeListener<? super Integer>) c -> {
			if (c.wasAdded()) {
				int treeId = c.getElementAdded();
				TreeSheet treeSheet = id2treeSheet.get(treeId);
				treeSheet.setSelectedProperty(true);
				ColorBarBox colorBarBox = colorBar.getId2colorBarBox().get(treeId);
				colorBarBox.setSelectedProperty(true);
			} else if (c.wasRemoved()) {
				int treeId = c.getElementRemoved();
				TreeSheet treeSheet = id2treeSheet.get(treeId);
				treeSheet.setSelectedProperty(false);
				ColorBarBox colorBarBox = colorBar.getId2colorBarBox().get(treeId);
				colorBarBox.setSelectedProperty(false);
			}
		});

		// Selection Menu: Taxa Selection
		controller.getSelectAllTaxaMenuItem().disableProperty().bind(taxaSelectionModel.sizeProperty().isEqualTo(taxaCount)
				.or(taxaCount.isEqualTo(0)));
		controller.getSelectAllTaxaMenuItem().setOnAction(e -> {
			for (var taxon : model.getTaxaBlock().getTaxa()) {
				taxaSelectionModel.select(model.getTaxaBlock().indexOf(taxon));
			}
		});

		controller.getSelectNoTaxaMenuItem().disableProperty().bind(taxaSelectionModel.sizeProperty().isEqualTo(0)
				.or(taxaCount.isEqualTo(0)));
		controller.getSelectNoTaxaMenuItem().setOnAction(e -> taxaSelectionModel.clearSelection());

		controller.getSelectInverseTaxaMenuItem().disableProperty().bind(taxaCount.isEqualTo(0));
		controller.getSelectInverseTaxaMenuItem().setOnAction(e -> {
			var formerSelection = taxaSelectionModel.getSelectedItems();
			var newSelection = new HashSet<Integer>();
			for (var taxon : model.getTaxaBlock().getTaxa()) {
				int taxonId = model.getTaxaBlock().indexOf(taxon);
				if (formerSelection.contains(taxonId)) continue;
				newSelection.add(taxonId);
			}
			taxaSelectionModel.clearSelection();
			taxaSelectionModel.selectAll(newSelection);
		});

		taxaSelectionModel.getSelectedItems().addListener((SetChangeListener<? super Integer>) c -> {
			if (c.wasAdded()) {
				int taxonId = c.getElementAdded();
				String taxonName = model.getTaxaBlock().getLabel(taxonId);
				for (int index = 0; index < trees.getChildren().size(); index++) {
					TreeSheet treeSheet = (TreeSheet)trees.getChildren().get(index);
					if (treeSheet.selectTaxon(taxonName, true)) {
						Runnable updateEdgeSelection = treeSheet::updateEdgeSelection;
						RunAfterAWhile.applyInFXThread(treeSheet.getTreeId(), updateEdgeSelection);
						if (controller.getColoringGroup().getSelectedToggle() == controller.getMonophyleticColoringMenuItem())
							updateColoring(controller.getColoringGroup(), controller, model);
					}
				}
			} else if (c.wasRemoved()) {
				int taxonId = c.getElementRemoved();
				String taxonName = model.getTaxaBlock().getLabel(taxonId);
				for (int index = 0; index < trees.getChildren().size(); index++) {
					TreeSheet treeSheet = (TreeSheet)trees.getChildren().get(index);
					if (treeSheet.selectTaxon(taxonName, false)) {
						Runnable updateEdgeSelection = treeSheet::updateEdgeSelection;
						RunAfterAWhile.applyInFXThread(treeSheet.getTreeId(), updateEdgeSelection);
						if (controller.getColoringGroup().getSelectedToggle() == controller.getMonophyleticColoringMenuItem())
							updateColoring(controller.getColoringGroup(), controller, model);
					}
				}
			}
		});

		// Layout Menu
		controller.getLayoutGroup().selectedToggleProperty().addListener((observableValue, oldValue, newValue) -> {
			updateLayout(controller.getLayoutGroup().getSelectedToggle(),controller);
			Runnable undo = () -> controller.getLayoutGroup().selectToggle(oldValue);
			Runnable redo = () -> controller.getLayoutGroup().selectToggle(newValue);
			undoRedoManager.add(new SimpleCommand("layout", undo, redo));
		});

		orderGroupListener = (observableValue, oldValue, newValue) -> {
			try {
				var oldTreeOrder = model.getTreeOrder();
				changeTreeOrder(model, controller.getOrderGroup(), oldValue, newValue, controller, subScene,
						geneTreeView.getStage());
				var newTreeOrder = model.getTreeOrder();
				Runnable undo = () -> {
					controller.getOrderGroup().selectedToggleProperty().removeListener(orderGroupListener);
					controller.getOrderGroup().selectToggle(oldValue);
					model.setTreeOrder(oldTreeOrder);
					initializeTreesLayout(model,controller.getCenterPane().getBoundsInParent().getWidth(),
							controller.getCenterPane().getBoundsInParent().getHeight(),controller,subScene);
					colorBar.reorder(oldTreeOrder);
					initializeTreeLists(controller.getSimilarityColoringSubMenu(),controller.getColoringGroup(),
							controller.getSimilarityOrderSubMenu(), controller.getOrderGroup(), model.getOrderedGeneNames());
					controller.getOrderGroup().selectedToggleProperty().addListener(orderGroupListener);
				};
				Runnable redo = () -> {
					controller.getOrderGroup().selectedToggleProperty().removeListener(orderGroupListener);
					controller.getOrderGroup().selectToggle(newValue);
					model.setTreeOrder(newTreeOrder);
					initializeTreesLayout(model,controller.getCenterPane().getBoundsInParent().getWidth(),
							controller.getCenterPane().getBoundsInParent().getHeight(),controller,subScene);
					colorBar.reorder(newTreeOrder);
					initializeTreeLists(controller.getSimilarityColoringSubMenu(),controller.getColoringGroup(),
							controller.getSimilarityOrderSubMenu(), controller.getOrderGroup(), model.getOrderedGeneNames());
					controller.getOrderGroup().selectedToggleProperty().addListener(orderGroupListener);
				};
				undoRedoManager.add(new SimpleCommand("order", undo, redo));
			} catch (IOException e) {
				e.printStackTrace();
			}
		};
		controller.getOrderGroup().selectedToggleProperty().addListener(orderGroupListener);

		// View Menu
		/*controller.getRectangularCladoMenuItem().setGraphic(TreeDiagramType.RectangularCladogram.createNode());
		controller.getRectangularPhyloMenuItem().setGraphic(TreeDiagramType.RectangularPhylogram.createNode());
		controller.getTriangularCladoMenuItem().setGraphic(TreeDiagramType.TriangularCladogram.createNode());
		controller.getRadialCladoMenuItem().setGraphic(TreeDiagramType.RadialCladogram.createNode());
		controller.getRadialPhyloMenuItem().setGraphic(TreeDiagramType.RadialPhylogram.createNode());
		controller.getCircularCladoMenuItem().setGraphic(TreeDiagramType.CircularCladogram.createNode());
		controller.getCircularPhyloMenuItem().setGraphic(TreeDiagramType.CircularPhylogram.createNode());*/

		/*for (var menuitem : controller.getTreeLayoutGroup().getToggles()) {
			String toggleName = menuitem.toString();
			System.out.println(toggleName);
			for (var treeType : TreeDiagramType.values()) {
				System.out.println(treeType.toString());
				if (treeType.toString().equals(toggleName)) {
					((MenuItem)menuitem).setGraphic(treeType.createNode());
				}
			}
		}*/
		controller.getTreeLayoutGroup().selectedToggleProperty().addListener((observableValue,oldValue,newValue) -> {
			updateTreeLayout(model,controller.getCenterPane().getBoundsInParent().getWidth(),
					controller.getCenterPane().getBoundsInParent().getHeight(),
					controller.getTreeLayoutGroup().getSelectedToggle(),controller,subScene);
			Runnable undo = () -> controller.getTreeLayoutGroup().selectToggle(oldValue);
			Runnable redo = () -> controller.getTreeLayoutGroup().selectToggle(newValue);
			undoRedoManager.add(new SimpleCommand("view", undo, redo));
		});

		colorGroupListener = (observableValue, oldValue, newValue) -> {
			updateColoring(controller.getColoringGroup(), controller, model);
			Runnable undo = () -> controller.getColoringGroup().selectToggle(oldValue);
			Runnable redo = () -> controller.getColoringGroup().selectToggle(newValue);
			undoRedoManager.add(new SimpleCommand("coloring", undo, redo));
		};
		controller.getColoringGroup().selectedToggleProperty().addListener(colorGroupListener);

		// Help Menu
		controller.getAboutMenuItem().setOnAction(e -> {
		// TODO: show some kind of information
		});

		// ToolBar (the zoom slider is handled by the layout)
		controller.getSearchGeneComboBox().disableProperty().bind(controller.getSlider().disableProperty());
		controller.getSearchGeneComboBox().setOnAction(e -> {
			String selectedItem = controller.getSearchGeneComboBox().getSelectionModel().getSelectedItem();
			if (selectedItem != null) {
				for (int i=0; i<model.getOrderedGeneNames().size(); i++) {
					if (model.getOrderedGeneNames().get(i).equals(selectedItem)){
						double initialSliderValue = controller.getSlider().getValue();
						double targetValue = i+1;
						double distance = Math.abs(targetValue-initialSliderValue);
						double duration = Math.min(distance*300,3000);
						var keyValue = new KeyValue(controller.getSlider().valueProperty(), targetValue);
						var keyFrame = new KeyFrame(Duration.millis(duration),keyValue);
						var timeLine = new Timeline(keyFrame);
						timeLine.play();
						controller.getSearchGeneComboBox().getEditor().setText(selectedItem);
					}
				}
			}
		});


		// Navigation: Slider and buttons to go through the trees
		controller.getSlider().prefWidthProperty().bind(controller.getCenterPane().widthProperty().subtract(50)); // space 50 for buttons
		controller.getSlider().valueProperty().addListener((observableValue, oldValue, newValue) -> {
			currentLayout.updatePosition((double)oldValue, (double)newValue);
			updateButtonTooltips(controller.getSlider().getValue(), model.getOrderedGeneNames());
		});
		// For Stack layout: using snapshots whenever the slider is dragged for fluent performance
		controller.getSlider().setOnDragDetected((e -> {
			lastSliderValue = controller.getSlider().getValue(); // remember value for undo/redo
			if (currentLayout.getType() == LayoutType.Stack) {
				((StackLayout) currentLayout).setSliderDragged(true);
				subScene.setRoot(treeSnapshots);
			}
		}));
		controller.getSlider().setOnMouseReleased(e -> {
			if (currentLayout.getType() == LayoutType.Stack) {
				((StackLayout) currentLayout).setSliderDragged(false);
				currentLayout.updatePosition(1, controller.getSlider().getValue());
				subScene.setRoot(trees);
			}
			undoRedoManager.add(new PropertyCommand<>("navigation",
					controller.getSlider().valueProperty(), lastSliderValue, controller.getSlider().getValue()));
		});

		controller.getPreviousButton().setOnAction(e -> {
			double oldValue = controller.getSlider().getValue();
			focusOnPreviousTree(controller.getSlider());
			double newValue = controller.getSlider().getValue();
			Runnable undo = () -> controller.getSlider().setValue(oldValue);
			Runnable redo = () -> controller.getSlider().setValue(newValue);
			undoRedoManager.add(new SimpleCommand("navigation", undo, redo));
		});
		controller.getPreviousButton().setTooltip(previousTreeToolTip);
		controller.getPreviousButton().disableProperty().bind(controller.getSlider().disableProperty());

		controller.getNextButton().setOnAction(e -> {
			double oldValue = controller.getSlider().getValue();
			focusOnNextTree(controller.getSlider());
			double newValue = controller.getSlider().getValue();
			Runnable undo = () -> controller.getSlider().setValue(oldValue);
			Runnable redo = () -> controller.getSlider().setValue(newValue);
			undoRedoManager.add(new SimpleCommand("navigation", undo, redo));
		});
		controller.getNextButton().setTooltip(nextTreeToolTip);
		controller.getNextButton().disableProperty().bind(controller.getSlider().disableProperty());
	}

	private void openFile(Stage stage, GeneTreeViewController controller, Model model, SubScene subScene) {
		final var fileChooser = new FileChooser();
		fileChooser.setTitle("Open trees");
		fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Newick Trees (tre,tree,trees,new,nwk,treefile)","*.tre","*.tree",".trees", ".new", ".nwk", ".treefile"));

		var file = fileChooser.showOpenDialog(stage);
		if (file != null) {
			Service<Void> loadingTreesService = new Service<>() {
				@Override
				protected Task<Void> createTask() {
					return new LoadTreesTask(file,model,stabilizer);
				}
			};
			controller.getProgressBar().visibleProperty().bind(loadingTreesService.runningProperty());
			controller.getProgressBar().progressProperty().bind(loadingTreesService.progressProperty());
			loadingTreesService.setOnScheduled(v -> {
				controller.getProgressLabel().setText("Loading trees ...");
			});
			loadingTreesService.setOnSucceeded(v -> {
				System.out.println("Loading succeeded");
				updateInfoLabel(controller.getLabel(), model.getTaxaBlock().getNtax(), model.getTreesBlock().getNTrees());
				treesCount.set(model.getTreesBlock().getNTrees());
				taxaCount.set(model.getTaxaBlock().getNtax());
				trees.getChildren().clear();
				treeSnapshots.getChildren().clear();
				treeSelectionModel.clearSelection();
				taxaSelectionModel.clearSelection();
				edgeSelectionModels.clear();
				colorBar = null;
				initializeSlider(model.getTreesBlock(), controller.getSlider());
				initializeColorBar(controller.getvBox(),model.getTreesBlock(), controller.getSlider(),
						model, controller);
				initializeTreesLayout(model, controller.getCenterPane().getBoundsInParent().getWidth(),
						controller.getCenterPane().getBoundsInParent().getHeight(), controller, subScene);
				initializeGeneSearch(controller.getSearchGeneComboBox(),model.getOrderedGeneNames());
				controller.getOrderGroup().selectedToggleProperty().removeListener(orderGroupListener);
				controller.getDefaultOrderMenuItem().setSelected(true);
				controller.getOrderGroup().selectedToggleProperty().addListener(orderGroupListener);
				controller.getColoringGroup().selectedToggleProperty().removeListener(colorGroupListener);
				controller.getNoColoringMenuItem().setSelected(true);
				controller.getColoringGroup().selectedToggleProperty().addListener(colorGroupListener);
				initializeTreeLists(controller.getSimilarityColoringSubMenu(), controller.getColoringGroup(),
						controller.getSimilarityOrderSubMenu(), controller.getOrderGroup(),model.getOrderedGeneNames());
				initializeTaxaList(controller.getTaxonOrderSubMenu(),controller.getOrderGroup(),model.getTaxaBlock());
			});
			loadingTreesService.setOnFailed(u -> {
				System.out.println("Loading trees failed");
				controller.getProgressLabel().setText("Loading trees failed");
			});
			loadingTreesService.restart();

		}
	}

	private void importGeneNames(Stage stage, Model model, GeneTreeViewController controller) {
		var geneNameParser = new GeneNameParser(stage,model);
		// If new names have been parsed to the model, names in treeSheets and snapshots need to be updated:
		geneNameParser.parsedProperty().addListener((InvalidationListener) -> {
			for (int i = 0; i < model.getOrderedGeneNames().size(); i++) {
				((TreeSheet)trees.getChildren().get(i)).setTreeName(model.getOrderedGeneNames().get(i));
			}
		});
		initializeTreeLists(controller.getSimilarityColoringSubMenu(), controller.getColoringGroup(),
				controller.getSimilarityOrderSubMenu(), controller.getOrderGroup(),model.getOrderedGeneNames());
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
		fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Newick Trees (tre,tree,trees,new,nwk,treefile)","*.tre","*.tree",".trees", ".new", ".nwk", ".treefile"));

		var file = fileChooser.showSaveDialog(stage);
		if (file != null) {
			Service<Void> exportTreesService = new Service<>() {
				@Override
				protected Task<Void> createTask() {
					return new ExportTreesTask(file,model,treeSelectionModel.getSelectedItems());
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
		slider.maxProperty().bind(treesCount);
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

	private void initializeColorBar(VBox parent, TreesBlock treesBlock, Slider slider, Model model,
									GeneTreeViewController controller) {
		colorBar = new ColorBar(treesBlock,slider,model.getTreeOrder());
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
						treeDiagramType, taxaSelectionModel, edgeSelectionModels);
			}
		};
		controller.getProgressBar().visibleProperty().bind(visualizeTreesService.runningProperty());
		controller.getProgressBar().progressProperty().bind(visualizeTreesService.progressProperty());
		visualizeTreesService.setOnScheduled(v -> {
			controller.getProgressLabel().setText("Drawing trees ...");
		});
		visualizeTreesService.setOnSucceeded(v -> {
			controller.getCenterPane().getChildren().clear();
			trees = visualizeTreesService.getValue();
			treeSnapshots.getChildren().clear();
			for (var ignored : trees.getChildren()) treeSnapshots.getChildren().add(new Rectangle());
			for (Node node : trees.getChildren()) {
				TreeSheet treeSheet = (TreeSheet) node;
				int id = treeSheet.getTreeId();
				id2treeSheet.put(id,treeSheet);
				if (treeSelectionModel.getSelectedItems().contains(id)) {
					treeSheet.setSelectedProperty(true);
					colorBar.getId2colorBarBox().get(id).setSelectedProperty(true);
				}
				setupTreeSelectionAndSnapshots(controller, treeSheet, id);
			}
			subScene.setRoot(trees);
			controller.getCenterPane().getChildren().add(subScene);
			updateLayout(controller.getLayoutGroup().getSelectedToggle(),controller);
			System.out.println("Visualizations succeeded");
			controller.getSlider().setDisable(false);
			controller.getProgressLabel().setText("");
		});
		visualizeTreesService.setOnFailed(u -> {
			System.out.println("Visualizing trees failed");
			controller.getProgressLabel().setText("Drawing trees failed");
		});
		visualizeTreesService.restart();
	}

	private void setupTreeSelectionAndSnapshots(GeneTreeViewController controller, TreeSheet treeSheet, int id) {
		treeSheet.layoutBoundsProperty().addListener((observable,oldValue,newValue) -> {
			if (newValue.getWidth()>0 & newValue.getHeight()>0) {
				Runnable updateSnapshot = () -> updateSnapshot(trees.getChildren().indexOf(treeSheet), controller);
				RunAfterAWhile.applyInFXThread(treeSheet, updateSnapshot);
			}
		});
		treeSheet.lastUpdateProperty().addListener(((observableValue, oldValue, newValue) -> {
			if (trees.getChildren().contains(treeSheet)) {
				Runnable updateSnapshot = () -> updateSnapshot(trees.getChildren().indexOf(treeSheet), controller);
				RunAfterAWhile.applyInFXThreadOrClearIfAlreadyWaiting(treeSheet, updateSnapshot);
			}
		}));
		makeTreeSelectable(id);
	}

	private void makeTreeSelectable(int treeId) {
		id2treeSheet.get(treeId).getSelectionRectangle().setOnMouseClicked(e -> {
			boolean selectedBefore = treeSelectionModel.getSelectedItems().contains(treeId);
			if (!e.isShiftDown()) {
				treeSelectionModel.clearSelection();
				if (!selectedBefore)
					treeSelectionModel.select(treeId);
			} else
				treeSelectionModel.setSelected(treeId, !selectedBefore);
		});
		colorBar.getId2colorBarBox().get(treeId).setOnMouseClicked(e -> {
			boolean selectedBefore = treeSelectionModel.getSelectedItems().contains(treeId);
			if (!e.isShiftDown()) {
				treeSelectionModel.clearSelection();
				if (!selectedBefore)
					treeSelectionModel.select(treeId);
			} else
				treeSelectionModel.setSelected(treeId, !selectedBefore);
		});
	}

	private void initializeGeneSearch(ComboBox<String> searchGeneComboBox, ObservableList<String> geneNames) {
		searchGeneComboBox.setItems(geneNames);
		new ComboBoxListener(searchGeneComboBox);
	}

	private void initializeTreeLists(Menu similarityColoringSubMenu, ToggleGroup coloringGroup,
									 Menu similarityOrderSubMenu, ToggleGroup orderGroup, ObservableList<String> geneNames) {
		similarityColoringSubMenu.getItems().clear();
		similarityOrderSubMenu.getItems().clear();
		for (var geneName : geneNames) {
			RadioMenuItem newColoringItem = new RadioMenuItem();
			newColoringItem.setText(geneName);
			newColoringItem.setToggleGroup(coloringGroup);
			similarityColoringSubMenu.getItems().add(newColoringItem);

			RadioMenuItem newOrderMenuItem = new RadioMenuItem();
			newOrderMenuItem.setText(geneName);
			newOrderMenuItem.setToggleGroup(orderGroup);
			similarityOrderSubMenu.getItems().add(newOrderMenuItem);
		}
		similarityColoringSubMenu.setDisable(false);
		similarityOrderSubMenu.setDisable(false);
	}

	private void initializeTaxaList(Menu taxonOrderSubMenu, ToggleGroup orderGroup, TaxaBlock taxaBlock) {
		taxonOrderSubMenu.getItems().clear();
		for (Taxon taxon : taxaBlock.getTaxa()) {
			String taxonName = taxon.getName();
			RadioMenuItem newItem = new RadioMenuItem();
			newItem.setText(taxonName);
			newItem.setToggleGroup(orderGroup);
			newItem.setUserData(taxonName);
			taxonOrderSubMenu.getItems().add(newItem);
		}
		taxonOrderSubMenu.setDisable(false);
	}

	private ObservableList<PhyloTree> pasteTrees(Model model, GeneTreeViewController controller) {
		Clipboard clipboard = Clipboard.getSystemClipboard();
		try {
			String content;
			File file;
			var newickReader = new NewickReader();
			var newTreeBlock = new TreesBlock();
			if (clipboard.hasString()) {
				content = clipboard.getString();
				file = new File("temp.txt");
				FileWriter fw = new FileWriter(file);
				fw.write(content);
				fw.close();
				var iterator = new FileLineIterator(file);
				newickReader.read(new ProgressPercentage(),iterator,model.getTaxaBlock(),newTreeBlock);

			} else if (clipboard.hasFiles()) {
				file = clipboard.getFiles().get(0);
				newickReader.read(new ProgressPercentage(), file.getPath(), model.getTaxaBlock(), newTreeBlock);
			}
			else return null;

			// TODO: debug taxon ids of pasted trees !

			for (PhyloTree phyloTree : newTreeBlock.getTrees()) {
				String treeName = phyloTree.getName();
				if (Objects.equals(model.getTreesBlock().getTree(model.getTreesBlock().getNTrees()).getName(),
						"tree-" + model.getTreesBlock().getNTrees()) & treeName.startsWith("tree-"))
					treeName = "tree-"+(model.getTreesBlock().getNTrees()+1);
				int suffix = 2;
				while (model.getOrderedGeneNames().contains(treeName)) {
					treeName = phyloTree.getName()+"-"+suffix;
					suffix+=1;
				}
				phyloTree.setName(treeName);
				stabilizer.apply(phyloTree);
			}
			pasteTrees(newTreeBlock.getTrees(), model, controller);
			return newTreeBlock.getTrees();
		} catch (IOException ex) {
			ex.printStackTrace();
			return null;
		}
	}

	private void pasteTrees(ObservableList<PhyloTree> phyloTrees, Model model, GeneTreeViewController controller) {
		for (PhyloTree phyloTree : phyloTrees) {
			model.addTree(phyloTree);
			System.out.println(phyloTree.getName()+" has been added to the model");
			int treeId = model.getTreesBlock().getTrees().indexOf(phyloTree)+1;

			if (colorBar == null)
				initializeColorBar(controller.getvBox(), model.getTreesBlock(), controller.getSlider(), model, controller);
			else {
				colorBar.addColorBox(phyloTree.getName(), treeId);
			}

			edgeSelectionModels.put(treeId, new SelectionModelSet<>());
			TreeSheet treeSheet = new TreeSheet(phyloTree, treeId, treeWidth, treeHeight, treeDiagramType,
					taxaSelectionModel, edgeSelectionModels.get(treeId));
			int index = trees.getChildren().size();
			trees.getChildren().add(index, treeSheet);
			currentLayout.initializeNode(treeSheet, index);
			id2treeSheet.put(treeId, treeSheet);
			treeSnapshots.getChildren().add(index, new Rectangle());
			setupTreeSelectionAndSnapshots(controller, treeSheet, treeId);
		}
		treesCount.set(model.getTreesBlock().getNTrees());
		taxaCount.set(model.getTaxaBlock().getNtax());
		initializeTaxaList(controller.getTaxonOrderSubMenu(), controller.getOrderGroup(), model.getTaxaBlock());
		initializeTreeLists(controller.getSimilarityColoringSubMenu(), controller.getColoringGroup(),
				controller.getSimilarityOrderSubMenu(), controller.getOrderGroup(),model.getOrderedGeneNames());
		updateInfoLabel(controller.getLabel(), model.getTaxaBlock().getNtax(), model.getTreesBlock().getNTrees());
	}

/*	private void removeTree(int removedId, Model model, GeneTreeViewController controller) {
		if (id2treeSheet.containsKey(removedId)) {
			//int removedId = model.getTreesBlock().getTrees().indexOf(phyloTree) + 1;
			int removedIndex = model.getOrderedGeneNames().indexOf(phyloTree.getName());
			TreeSheet removedTreeSheet = (TreeSheet) trees.getChildren().remove(removedIndex);
			//int removedId = removedTreeSheet.getTreeId();
			treeSelectionModel.setSelected(removedId,false);
			edgeSelectionModels.remove(removedId);
			System.out.println("Removed id: "+removedId);
			TreeSheet removed = id2treeSheet.get(removedId);
			//int removedPosition = trees.getChildren().indexOf(removed);
			System.out.println("Removed position: "+removedIndex);
			//System.out.println(trees.getChildren().remove(removed));
			treeSnapshots.getChildren().remove(removedIndex);
			model.remove(removedId);
			treesCount.set(model.getTreesBlock().getNTrees());
			// TODO: If taxa are unique in the removed tree, they should be removed from the TaxaBlock
			colorBar.removeColorBox(removedId);
			id2treeSheet.remove(removedId);
			initializeTreeLists(controller.getSimilarityColoringSubMenu(), controller.getColoringGroup(),
					controller.getSimilarityOrderSubMenu(), controller.getOrderGroup(),model.getOrderedGeneNames());
			updateInfoLabel(controller.getLabel(), model.getTaxaBlock().getNtax(), model.getTreesBlock().getNTrees());
			if (currentLayout.getType().equals(LayoutType.Carousel) & (treesCount.get() >= 50 | removedIndex != treesCount.get())) {
				((CarouselLayout)currentLayout).initializeLayout();
			}
			else if (currentLayout.getType().equals(LayoutType.Stack) & removedIndex != treesCount.get())
				currentLayout.updatePosition(controller.getSlider().getValue(), controller.getSlider().getValue());
		}
	}*/

	private void removeTree(PhyloTree phyloTree, Model model, GeneTreeViewController controller) {
		if (model.getTreesBlock().getTrees().contains(phyloTree)) {
			//int removedId = model.getTreesBlock().getTrees().indexOf(phyloTree) + 1;
			int removedIndex = model.getOrderedGeneNames().indexOf(phyloTree.getName());
			TreeSheet removedTreeSheet = (TreeSheet) trees.getChildren().remove(removedIndex);
			int removedId = removedTreeSheet.getTreeId();
			treeSelectionModel.setSelected(removedId,false);
			edgeSelectionModels.remove(removedId);
			System.out.println("Removed id: "+removedId);
			TreeSheet removed = id2treeSheet.get(removedId);
			//int removedPosition = trees.getChildren().indexOf(removed);
			System.out.println("Removed position: "+removedIndex);
			//System.out.println(trees.getChildren().remove(removed));
			treeSnapshots.getChildren().remove(removedIndex);
			model.remove(removedId);
			treesCount.set(model.getTreesBlock().getNTrees());
			// TODO: If taxa are unique in the removed tree, they should be removed from the TaxaBlock
			colorBar.removeColorBox(removedId);
			id2treeSheet.remove(removedId);
			initializeTreeLists(controller.getSimilarityColoringSubMenu(), controller.getColoringGroup(),
					controller.getSimilarityOrderSubMenu(), controller.getOrderGroup(),model.getOrderedGeneNames());
			updateInfoLabel(controller.getLabel(), model.getTaxaBlock().getNtax(), model.getTreesBlock().getNTrees());
			if (currentLayout.getType().equals(LayoutType.Carousel) & (treesCount.get() >= 50 | removedIndex != treesCount.get())) {
				((CarouselLayout)currentLayout).initializeLayout();
			}
			else if (currentLayout.getType().equals(LayoutType.Stack) & removedIndex != treesCount.get())
				currentLayout.updatePosition(controller.getSlider().getValue(), controller.getSlider().getValue());
		}
	}

	private Node createSnapshot(Node treeVis) {
		Bounds bounds = treeVis.getLayoutBounds();
		WritableImage writableImage = new WritableImage((int)Math.ceil(bounds.getWidth()*2),
				(int)Math.ceil(bounds.getHeight()*2)); // scaling with factor 2 for better resolution
		SnapshotParameters parameters = new SnapshotParameters();
		parameters.setFill(Color.TRANSPARENT);
		parameters.setTransform(javafx.scene.transform.Transform.scale(2,2));
		Image image = treeVis.snapshot(parameters,writableImage);
		ImageView snapShot = new ImageView(image);
		snapShot.setFitWidth(bounds.getWidth());
		snapShot.setFitHeight(bounds.getHeight());
		return snapShot;
	}

	private void updateSnapshot(int treeIndex, GeneTreeViewController controller) {
		if (treeIndex == -1) return;
		Node treeSheet = trees.getChildren().get(treeIndex);
		currentLayout.resetNode(treeSheet); // reset rotation, transformations and scaling for the snapshot

		Node snapShot = createSnapshot(treeSheet);

		// Transformation of treeSheet and snapshot according to the current layout and slider position
		currentLayout.initializeNode(snapShot, treeIndex);
		currentLayout.initializeNode(treeSheet, treeIndex);

		// Add / Replace snapshot in Group treeSnapshots
		if (treeSnapshots.getChildren().size()==trees.getChildren().size()) treeSnapshots.getChildren().remove(treeIndex);
		treeSnapshots.getChildren().add(treeIndex,snapShot);
	}

	private void updateLayout(Toggle selectedLayoutToggle,
							  GeneTreeViewController controller) {
		if (selectedLayoutToggle.equals(controller.getStackMenuItem())) {
			if (!trees.getChildren().isEmpty()) currentLayout = new StackLayout(trees.getChildren(),
					treeSnapshots.getChildren(), treeWidth, treeHeight, controller.getCenterPane().widthProperty(),
					controller.getCenterPane().heightProperty(), camera, controller.getSlider(), controller.getZoomSlider());
		}
		else if (selectedLayoutToggle.equals(controller.getCarouselMenuItem())) {
			if (!trees.getChildren().isEmpty()) currentLayout = new CarouselLayout(trees.getChildren(), treeWidth,
					treeHeight, controller.getCenterPane().widthProperty(), controller.getCenterPane().heightProperty(),
					camera, controller.getSlider(), controller.getZoomSlider());
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

	private void updateColoring(ToggleGroup coloringGroup, GeneTreeViewController controller, Model model) {
		if (colorBar == null) return;
		if (coloringGroup.getSelectedToggle() == controller.getNoColoringMenuItem()) {
			colorBar.resetColoring();
		}
		else if (coloringGroup.getSelectedToggle() == controller.getMonophyleticColoringMenuItem()) {
			colorBar.resetColoring();
			for (int treeId : model.getTreeOrder()) {
				TreeSheet treeSheet = id2treeSheet.get(treeId);
				boolean monophyletic = treeSheet.monophyleticSelection();
				if (monophyletic) colorBar.setColor(treeId, Color.KHAKI);
			}
		}
		else { // remaining option: color by similarity with selected tree
			String selectedTreeName = ((MenuItem) coloringGroup.getSelectedToggle()).getText();
			int treeIndex = model.getOrderedGeneNames().indexOf(selectedTreeName);
			PhyloTree selectedTree = model.getTreesBlock().getTree(model.getTreeOrder().get(treeIndex));

			Service<ArrayList<Integer>> getTreeSimilaritiesService = new Service<>() {
				@Override
				protected Task<ArrayList<Integer>> createTask() {
					return new SimilarityCalculationTask(model.getTreesBlock(), selectedTree);
				}
			};
			controller.getProgressBar().visibleProperty().bind(getTreeSimilaritiesService.runningProperty());
			controller.getProgressBar().progressProperty().bind(getTreeSimilaritiesService.progressProperty());
			getTreeSimilaritiesService.setOnScheduled(v -> {
				controller.getProgressLabel().setText("Calculating tree similarities ...");
			});
			getTreeSimilaritiesService.setOnSucceeded(v -> {
				System.out.println("Similarity calculation succeeded");
				ArrayList<Integer> similarities = getTreeSimilaritiesService.getValue();
				if (similarities.size() == model.getTreesBlock().size()) {
					colorBar.resetColoring();
					var colors = ColorSchemeManager.getInstance().getColorScheme("White-Green");
					//for (String colorSchemeName : ColorSchemeManager.getInstance().getNames()) System.out.println(colorSchemeName);
					TreeSet<Integer> sorted = new TreeSet<>(similarities);
					int max = sorted.last();
					int min = sorted.first();
					int range = max - min;
					if (range == 0) range = 1;
					for (int i = 0; i < model.getTreeOrder().size(); i++) {
						int treeId = model.getTreeOrder().get(i);
						double relativeSimilarity = (similarities.get(treeId-1) - min) / (double)range;
						Color color = colors.get((int) ((colors.size()-1)*relativeSimilarity));
						colorBar.setColor(treeId, color);
					}
				}
				controller.getProgressLabel().setText("");
			});
			getTreeSimilaritiesService.setOnFailed(u -> {
				System.out.println("Tree similarity calculation failed");
				controller.getProgressLabel().setText("Tree similarity calculation failed");
			});
			getTreeSimilaritiesService.restart();
		}
	}

	private void changeTreeOrder(Model model, ToggleGroup orderGroup, Toggle oldSelection, Toggle newSelection,
								 GeneTreeViewController controller, SubScene subScene, Stage stage) throws IOException {
		if (newSelection == controller.getDefaultOrderMenuItem()) {
			model.resetTreeOrder();
			initializeTreesLayout(model,controller.getCenterPane().getBoundsInParent().getWidth(),
					controller.getCenterPane().getBoundsInParent().getHeight(),controller,subScene);
			colorBar.reorder(model.getTreeOrder());
			initializeTreeLists(controller.getSimilarityColoringSubMenu(), controller.getColoringGroup(),
					controller.getSimilarityOrderSubMenu(), controller.getOrderGroup(),model.getOrderedGeneNames());
		}
		else if (controller.getSimilarityOrderSubMenu().getItems().contains((MenuItem) newSelection)) {
			String selectedTreeName = ((MenuItem) newSelection).getText();
			Stage topologyOrderDialog = new Stage();
			topologyOrderDialog.initStyle(stage.getStyle());
			topologyOrderDialog.setTitle("Similarity calculation");
			topologyOrderDialog.initModality(Modality.APPLICATION_MODAL);
			topologyOrderDialog.initOwner(stage);

			Button startButton = new Button("Calculate");
			startButton.setOnAction(e -> {
				topologyOrderDialog.close();

				int treeIndex = model.getOrderedGeneNames().indexOf(selectedTreeName);
				PhyloTree selectedTree = model.getTreesBlock().getTree(model.getTreeOrder().get(treeIndex));

				Service<ArrayList<Integer>> getTreeSimilaritiesService = new Service<>() {
					@Override
					protected Task<ArrayList<Integer>> createTask() {
						return new SimilarityCalculationTask(model.getTreesBlock(), selectedTree);
					}
				};
				controller.getProgressBar().visibleProperty().bind(getTreeSimilaritiesService.runningProperty());
				controller.getProgressBar().progressProperty().bind(getTreeSimilaritiesService.progressProperty());
				getTreeSimilaritiesService.setOnScheduled(v -> {
					controller.getProgressLabel().setText("Calculating tree similarities ...");
				});
				getTreeSimilaritiesService.setOnSucceeded(v -> {
					System.out.println("Similarity calculation succeeded");
					ArrayList<Integer> similarities = getTreeSimilaritiesService.getValue();
					TreeMap<Double,String> orderedGeneNames = new TreeMap<>(Collections.reverseOrder());
					for (int i = 0; i < similarities.size(); i++) {
						double similarity = (double) similarities.get(i);
						while (orderedGeneNames.containsKey(similarity)) similarity -= 0.000001;
						orderedGeneNames.put(similarity, model.getTreesBlock().getTree(i+1).getName());
					}
					System.out.println(orderedGeneNames.size());
					if (orderedGeneNames.size() == model.getTreesBlock().size()) {
						System.out.println("Starting reordering");
						model.setTreeOrder(orderedGeneNames);
						System.out.println("New tree order is set");
						initializeTreesLayout(model,controller.getCenterPane().getBoundsInParent().getWidth(),
								controller.getCenterPane().getBoundsInParent().getHeight(),controller,subScene);
						System.out.println("Layout is initialized");
						colorBar.reorder(model.getTreeOrder());
						initializeTreeLists(controller.getSimilarityColoringSubMenu(), controller.getColoringGroup(),
								controller.getSimilarityOrderSubMenu(), controller.getOrderGroup(),model.getOrderedGeneNames());
					}
					controller.getProgressLabel().setText("");
				});
				getTreeSimilaritiesService.setOnFailed(u -> {
					System.out.println("Tree similarity calculation failed");
					controller.getProgressLabel().setText("Tree similarity calculation failed");
					orderGroup.selectedToggleProperty().removeListener(orderGroupListener);
					orderGroup.selectToggle(oldSelection);
					orderGroup.selectedToggleProperty().addListener(orderGroupListener);
				});
				getTreeSimilaritiesService.restart();
			});

			Button cancelButton = new Button("Cancel");
			cancelButton.setOnAction(e -> {
				topologyOrderDialog.close();
				orderGroup.selectedToggleProperty().removeListener(orderGroupListener);
				orderGroup.selectToggle(oldSelection);
				orderGroup.selectedToggleProperty().addListener(orderGroupListener);
			});

			VBox vBox = new VBox(10);
			vBox.setPadding(new Insets(10));
			HBox hBox = new HBox();
			hBox.getChildren().addAll(startButton, cancelButton);
			hBox.setSpacing(5);
			var label = new Label("Pairwise similarity with gene tree "+selectedTreeName+" will be calculated \n" +
					"based on Robinson-Foulds Distances");
			vBox.getChildren().addAll(label, hBox);

			Scene scene = new Scene(vBox, 350, 100);
			topologyOrderDialog.setScene(scene);
			topologyOrderDialog.show();

		}
		/*else if (newSelection.equals(controller.getFeatureOrderMenuItem())) {
			// TODO: order trees by feature similarity with the currently selected tree -> numerical features only
			orderGroup.selectedToggleProperty().removeListener(orderGroupListener);
			orderGroup.selectToggle(oldSelection);
			orderGroup.selectedToggleProperty().addListener(orderGroupListener);
		}*/
		else { // remaining option: order as in selected taxon's genome
			String taxonName = ((String) orderGroup.getSelectedToggle().getUserData()).replace(" ","+");
			if (taxonName.contains("_")) {
				String[] taxonNames = taxonName.split("_");
				taxonName = taxonNames[0]+"+"+taxonNames[1];
			}
			System.out.println(taxonName);
			var geneOrderDialog = new GeneOrderDialog(stage,taxonName);
			geneOrderDialog.doneProperty().addListener((InvalidationListener) -> {
				String finalTaxonName = geneOrderDialog.getFinalTaxonName();
				if (finalTaxonName == null) {
					orderGroup.selectedToggleProperty().removeListener(orderGroupListener);
					orderGroup.selectToggle(oldSelection);
					orderGroup.selectedToggleProperty().addListener(orderGroupListener);
					return;
				}
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
						colorBar.reorder(model.getTreeOrder());
						initializeTreeLists(controller.getSimilarityColoringSubMenu(), controller.getColoringGroup(),
								controller.getSimilarityOrderSubMenu(), controller.getOrderGroup(),model.getOrderedGeneNames());
					}
					controller.getProgressLabel().setText("");
				});
				getGeneOrderService.setOnFailed(u -> {
					System.out.println("Reordering trees failed");
					controller.getProgressLabel().setText("Reordering trees failed");
					orderGroup.selectedToggleProperty().removeListener(orderGroupListener);
					orderGroup.selectToggle(oldSelection);
					orderGroup.selectedToggleProperty().addListener(orderGroupListener);
				});
				getGeneOrderService.restart();
			});
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

	private void updateInfoLabel(Label label, int nTaxa, int nTrees) {
		label.setText("Trees: %,d, Taxa: %,d".formatted(nTrees, nTaxa));
	}
}
