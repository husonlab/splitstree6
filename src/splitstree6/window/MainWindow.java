/*
 * MainWindow.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.window;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.stage.Stage;
import jloda.fx.selection.SelectionModel;
import jloda.fx.selection.SetSelectionModel;
import jloda.fx.util.ExtendedFXMLLoader;
import jloda.fx.util.MemoryUsage;
import jloda.fx.util.ProgramProperties;
import jloda.fx.util.ResourceManagerFX;
import jloda.fx.window.IMainWindow;
import jloda.fx.window.MainWindowManager;
import jloda.util.Basic;
import jloda.util.FileUtils;
import jloda.util.Single;
import splitstree6.data.TaxaBlock;
import splitstree6.data.ViewBlock;
import splitstree6.data.parts.Taxon;
import splitstree6.methods.ExtractMethodsText;
import splitstree6.tabs.IDisplayTab;
import splitstree6.tabs.displaytext.DisplayTextTab;
import splitstree6.tabs.workflow.WorkflowTab;
import splitstree6.view.alignment.AlignmentView;
import splitstree6.workflow.Workflow;
import splitstree6.workflowtree.WorkflowTreeView;

public class MainWindow implements IMainWindow {
	private final Parent root;
	private MainWindowPresenter presenter;
	private final MainWindowController controller;
	private final Workflow workflow = new Workflow(this);

	private final TextTabsManager textTabsManager;
	private final AlgorithmTabsManager algorithmTabsManager;

	private final ObservableList<Taxon> activeTaxa = FXCollections.observableArrayList();
	private final SelectionModel<Taxon> taxonSelectionModel = new SetSelectionModel<>();

	private final BooleanProperty dirty = new SimpleBooleanProperty(this, "dirty", false);
	private final BooleanProperty empty = new SimpleBooleanProperty(this, "empty", true);
	private final StringProperty name = new SimpleStringProperty(this, "name", "");

	private final ObjectProperty<TaxaBlock> workingTaxa = new SimpleObjectProperty<>();

	private final WorkflowTab workflowTab;
	private final DisplayTextTab methodsTab;
	private final WorkflowTreeView workflowTreeView;

	private final StringProperty fileName = new SimpleStringProperty(this, "fileName", "Untitled");
	private final BooleanProperty hasSplitsTree6File = new SimpleBooleanProperty(this, "hasSplitsTree6File", false);

	private Stage stage;

	public MainWindow() {
		Platform.setImplicitExit(false);

		final ExtendedFXMLLoader<MainWindowController> loader = new ExtendedFXMLLoader<>(this.getClass());
		root = loader.getRoot();
		controller = loader.getController();

		workflow.setServiceConfigurator(s -> s.setProgressParentPane(controller.getBottomFlowPane()));

		empty.bind(Bindings.isEmpty(workflow.nodes()));

		final MemoryUsage memoryUsage = MemoryUsage.getInstance();
		controller.getMemoryLabel().textProperty().bind(memoryUsage.memoryUsageStringProperty());

		methodsTab = new DisplayTextTab(this, "Methods", false);
		methodsTab.setGraphic(ResourceManagerFX.getIconAsImageView("sun/History24.gif", 16));

		workflow.validProperty().addListener(e -> updateMethodsTab());
		workflow.numberOfNodesProperty().addListener(e -> Platform.runLater(this::updateMethodsTab));

		workflowTab = new WorkflowTab(this);
		workflowTab.setGraphic(ResourceManagerFX.getIconAsImageView("sun/Preferences24.gif", 16));
		workflow.validProperty().addListener((v, o, n) -> {
			if (workflow.getWorkingTaxaBlock() == null) {
				activeTaxa.clear();
			} else {
				if (!Basic.equal(activeTaxa, workflow.getWorkingTaxaBlock().getTaxa()))
					activeTaxa.setAll(workflow.getWorkingTaxaBlock().getTaxa());
			}
			workingTaxa.set(workflow.getWorkingTaxaBlock());
		});

		//BasicFX.reportChanges("running",workflow.runningProperty());
		var first = new Single<>(true);
		workflow.runningProperty().addListener((v, o, n) -> {
			if (n) {
				if (first.get())
					first.set(false);
				else
					setDirty(true); // after initial run of the workflow, any further run makes document "dirty"
			}
		});

		textTabsManager = new TextTabsManager(this);
		algorithmTabsManager = new AlgorithmTabsManager(this);

		workflowTreeView = new WorkflowTreeView(this);

		fileName.addListener((v, o, n) -> name.set(n == null || n.isBlank() ? "Untitled" : FileUtils.replaceFileSuffix(FileUtils.getFileNameWithoutPath(n), "")));
		name.set("Untitled");

		presenter = new MainWindowPresenter(this);
	}

	@Override
	public Stage getStage() {
		return stage;
	}

	@Override
	public IMainWindow createNew() {
		return new MainWindow();
	}


	@Override
	public void show(Stage stage, double screenX, double screenY, double width, double height) {
		this.stage = stage;
		
		stage.getIcons().addAll(ProgramProperties.getProgramIconsFX());

		var scene = new Scene(root);

		stage.setScene(scene);
		scene.getStylesheets().add("jloda/resources/css/white_pane.css");

		stage.titleProperty().addListener(e -> MainWindowManager.getInstance().fireChanged());

		presenter.setStage(stage);

		getController().getMainTabPane().getTabs().addAll(workflowTab);

		Platform.runLater(() -> getController().getMainTabPane().getSelectionModel().select(0));

		InvalidationListener invalidationListener = e -> stage.setTitle(getName() + (isDirty() ? "*" : "") + " - " + ProgramProperties.getProgramName());
		name.addListener(invalidationListener);
		dirty.addListener(invalidationListener);
		invalidationListener.invalidated(null);
		stage.show();
		Platform.runLater(() -> {
			stage.setWidth(stage.getWidth() - 1);
			stage.setWidth(stage.getWidth() + 1);
		});// this hack ensures that bottom flowpane is shown
	}

	@Override
	public boolean isEmpty() {
		return workflow.size() == 0;
	}

	@Override
	public void close() {
		stage.hide();
	}


	public MainWindowController getController() {
		return controller;
	}

	public MainWindowPresenter getPresenter() {
		return presenter;
	}

	public Workflow getWorkflow() {
		return workflow;
	}

	public boolean isDirty() {
		return dirty.get();
	}

	public BooleanProperty dirtyProperty() {
		return dirty;
	}

	public void setDirty(boolean dirty) {
		this.dirty.set(dirty);
	}

	public String getName() {
		return name.get();
	}

	public ReadOnlyStringProperty nameProperty() {
		return name;
	}

	public Tab getTabByClass(Class clazz) {
		for (var tab : controller.getMainTabPane().getTabs()) {
			if (tab.getClass() == clazz)
				return tab;
		}
		return null;
	}

	public IDisplayTab getByName(String name) {
		for (var tab : controller.getMainTabPane().getTabs()) {
			if (tab instanceof IDisplayTab displayTab)
				return displayTab;
		}
		for (var tab : controller.getAlgorithmTabPane().getTabs()) {
			if (tab instanceof IDisplayTab displayTab)
				return displayTab;
		}
		return null;
	}

	public SelectionModel<Taxon> getTaxonSelectionModel() {
		return taxonSelectionModel;
	}

	public ReadOnlyBooleanProperty emptyProperty() {
		return empty;
	}

	public Parent getRoot() {
		return root;
	}

	public void addTabToMainTabPane(Tab tab) {
		try {
			if (tab != null && !controller.getMainTabPane().getTabs().contains(tab))
				controller.getMainTabPane().getTabs().add(tab);
		} catch (Exception ex) {
			Basic.caught(ex);
		}
	}

	public void removeTabFromMainTabPane(Tab tab) {
		controller.getMainTabPane().getTabs().remove(tab);
	}

	public TextTabsManager getTextTabsManager() {
		return textTabsManager;
	}

	public AlgorithmTabsManager getAlgorithmTabsManager() {
		return algorithmTabsManager;
	}

	public WorkflowTreeView getWorkflowTreeView() {
		return workflowTreeView;
	}

	public String getFileName() {
		return fileName.get();
	}

	public StringProperty fileNameProperty() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName.set(fileName);
	}

	public boolean isHasSplitsTree6File() {
		return hasSplitsTree6File.get();
	}

	public BooleanProperty hasSplitsTree6FileProperty() {
		return hasSplitsTree6File;
	}

	public void setHasSplitsTree6File(boolean hasSplitsTree6File) {
		this.hasSplitsTree6File.set(hasSplitsTree6File);
	}

	public ObservableList<Taxon> getActiveTaxa() {
		return activeTaxa;
	}

	public TaxaBlock getWorkingTaxa() {
		return workingTaxa.get();
	}

	public ReadOnlyObjectProperty<TaxaBlock> workingTaxaProperty() {
		return workingTaxa;
	}

	public void updateMethodsTab() {
		methodsTab.replaceText(workflow.isValid() ? ExtractMethodsText.getInstance().apply(workflow) : "");
	}

	public WorkflowTab getWorkflowTab() {
		return workflowTab;
	}

	public AlignmentView getAlignmentViewer() {
		var alignmentViewNode = getWorkflow().getAlignmentViewNode();
		if (alignmentViewNode != null && alignmentViewNode.getDataBlock() instanceof ViewBlock viewBlock) {
			return (AlignmentView) viewBlock.getView();
		} else
			return null;
	}

	public void setPresenter(MainWindowPresenter presenter) {
		this.presenter = presenter;
	}
}
