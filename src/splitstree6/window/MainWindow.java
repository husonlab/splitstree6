/*
 *  MainWindow.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.window;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import jloda.fx.selection.SelectionModel;
import jloda.fx.selection.SetSelectionModel;
import jloda.fx.util.ExtendedFXMLLoader;
import jloda.fx.util.MemoryUsage;
import jloda.fx.window.IMainWindow;
import jloda.fx.window.MainWindowManager;
import jloda.util.Basic;
import jloda.util.FileUtils;
import jloda.util.ProgramProperties;
import splitstree6.data.parts.Taxon;
import splitstree6.methods.ExtractMethodsText;
import splitstree6.tabs.IDisplayTab;
import splitstree6.tabs.textdisplay.TextDisplayTab;
import splitstree6.tabs.workflow.WorkflowTab;
import splitstree6.workflow.Workflow;
import splitstree6.workflowtree.WorkflowTreeView;

public class MainWindow implements IMainWindow {
	private final Parent root;
	private MainWindowPresenter presenter;
	private final MainWindowController controller;
	private final Workflow workflow = new Workflow(this);

	private final TextTabsManager textTabsManager;
	private final AlgorithmTabsManager algorithmTabsManager;

	private final SelectionModel<Taxon> taxonSelectionModel = new SetSelectionModel<>();
	private final BooleanProperty dirty = new SimpleBooleanProperty(false);
	private final BooleanProperty empty = new SimpleBooleanProperty(true);
	private final StringProperty name = new SimpleStringProperty("");

	private final WorkflowTab workflowTab;
	private final TextDisplayTab methodsTab;
	private final WorkflowTreeView workflowTreeView;

	private final StringProperty fileName = new SimpleStringProperty("");
	private final BooleanProperty hasSplitsTree6File = new SimpleBooleanProperty(false);

	private final ObjectProperty<Font> displayFont = new SimpleObjectProperty<>(new Font("Arial", 12));

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

		workflowTab = new WorkflowTab(this);
		methodsTab = new TextDisplayTab(this, "Methods", false, false);
		workflow.validProperty().addListener((v, o, n) -> methodsTab.replaceText(n ? ExtractMethodsText.getInstance().apply(workflow) : ""));

		textTabsManager = new TextTabsManager(this);
		algorithmTabsManager = new AlgorithmTabsManager(this);

		fileName.addListener((v, o, n) -> name.set(n == null || n.isBlank() ? "Empty" : FileUtils.replaceFileSuffix(FileUtils.getFileNameWithoutPath(n), "")));

		workflowTreeView = new WorkflowTreeView(this);
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
	public void show(Stage stage0, double screenX, double screenY, double width, double height) {
		if (stage0 == null)
			stage0 = new Stage();
		this.stage = stage0;
		stage.getIcons().addAll(ProgramProperties.getProgramIconsFX());

		var scene = new Scene(root, width, height);

		stage.setScene(scene);
		stage.sizeToScene();
		stage.setX(screenX);
		stage.setY(screenY);

		scene.getStylesheets().add("jloda/resources/css/white_pane.css");

		stage.titleProperty().addListener((e) -> MainWindowManager.getInstance().fireChanged());

		presenter = new MainWindowPresenter(this);

		getController().getMainTabPane().getTabs().addAll(workflowTab, methodsTab);

		Platform.runLater(() -> getController().getMainTabPane().getSelectionModel().select(0));

		name.addListener(c -> stage.setTitle(FileUtils.getFileNameWithoutPath(getName()) + (isDirty() ? "*" : "") + " - " + ProgramProperties.getProgramName()));

		dirtyProperty().addListener(c -> stage.setTitle(FileUtils.getFileNameWithoutPath(getName()) + (isDirty() ? "*" : "") + " - " + ProgramProperties.getProgramName()));
		stage.show();
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

	public Font getDisplayFont() {
		return displayFont.get();
	}

	public ObjectProperty<Font> displayFontProperty() {
		return displayFont;
	}

	public void setDisplayFont(Font displayFont) {
		this.displayFont.set(displayFont);
	}
}