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
import javafx.beans.property.*;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.stage.Stage;
import jloda.fx.selection.SelectionModel;
import jloda.fx.selection.SetSelectionModel;
import jloda.fx.util.ExtendedFXMLLoader;
import jloda.fx.util.MemoryUsage;
import jloda.fx.window.IMainWindow;
import jloda.fx.window.MainWindowManager;
import jloda.util.FileUtils;
import jloda.util.ProgramProperties;
import splitstree6.data.parts.Taxon;
import splitstree6.methods.ExtractMethodsText;
import splitstree6.tabs.IDisplayTab;
import splitstree6.tabs.algorithms.taxa.TaxaFilterTab;
import splitstree6.tabs.textdisplay.TextDisplayTab;
import splitstree6.tabs.workflow.WorkflowTab;
import splitstree6.window.presenter.MainWindowPresenter;
import splitstree6.workflow.Workflow;

public class MainWindow implements IMainWindow {
	private final Parent root;
	private MainWindowPresenter presenter;
	private final MainWindowController controller;
	private final Workflow workflow = new Workflow();
	private final SelectionModel<Taxon> selectionModel = new SetSelectionModel<>();
	private final BooleanProperty dirty = new SimpleBooleanProperty(false);
	private final BooleanProperty empty = new SimpleBooleanProperty(true);
	private final StringProperty name = new SimpleStringProperty("");

	private final WorkflowTab workflowTab;
	private final TextDisplayTab methodsTab;

	private Stage stage;

	public MainWindow() {
		Platform.setImplicitExit(false);

		{
			final ExtendedFXMLLoader<MainWindowController> extendedFXMLLoader = new ExtendedFXMLLoader<>(this.getClass());
			root = extendedFXMLLoader.getRoot();
			controller = extendedFXMLLoader.getController();
		}

		final MemoryUsage memoryUsage = MemoryUsage.getInstance();
		controller.getMemoryLabel().textProperty().bind(memoryUsage.memoryUsageStringProperty());

		/*
		FileOpenManager.setExtensions(Arrays.asList(new FileChooser.ExtensionFilter("GFA", "*.gfa", "*.fga.gz"),
				new FileChooser.ExtensionFilter("Alora", "*.alora"),
				new FileChooser.ExtensionFilter("All", "*.*")));

		FileOpenManager.setFileOpener(FileLoader.fileOpener());
		*/

		workflowTab = new WorkflowTab(this);
		methodsTab = new TextDisplayTab(this, "Methods", false, false);
		workflow.validProperty().addListener((v, o, n) -> methodsTab.replaceText(n ? "" : ExtractMethodsText.getInstance().apply(workflow)));
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

		getController().getAlgorithmTabPane().getTabs().add(new TaxaFilterTab(this));

		getController().getMainTabPane().getTabs().addAll(workflowTab, methodsTab);

		Platform.runLater(() -> getController().getMainTabPane().getSelectionModel().select(0));

		name.addListener(c -> stage.setTitle(FileUtils.getFileNameWithoutPath(getName()) + (isDirty() ? "*" : "") + " - " + ProgramProperties.getProgramName()));

		dirtyProperty().addListener(c -> stage.setTitle(FileUtils.getFileNameWithoutPath(getName()) + (isDirty() ? "*" : "") + " - " + ProgramProperties.getProgramName()));

		setName("Empty");

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

	public void setName(String name) {
		this.name.set(FileUtils.replaceFileSuffix(FileUtils.getFileNameWithoutPath(name), ""));
	}

	public Tab getTabByClass(Class clazz) {
		for (var tab : controller.getMainTabPane().getTabs()) {
			if (tab.getClass().isAssignableFrom(clazz))
				return tab;
		}
		return null;
	}

	public IDisplayTab getBy(String name) {
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

	public SelectionModel<Taxon> getSelectionModel() {
		return selectionModel;
	}

	public BooleanProperty emptyProperty() {
		return empty;
	}

	public void setEmpty(boolean empty) {
		this.empty.set(empty);
	}

	public Parent getRoot() {
		return root;
	}

	public void addTabToMainTabPane(Tab tab) {
		controller.getMainTabPane().getTabs().add(tab);

	}
}
