/*
 *  ImportDialogPresenter.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.dialog.importdialog;

import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.stage.FileChooser;
import jloda.fx.util.ProgramProperties;
import jloda.fx.window.NotificationManager;
import splitstree6.data.CharactersBlock;
import splitstree6.data.DistancesBlock;
import splitstree6.data.SplitsBlock;
import splitstree6.data.TreesBlock;
import splitstree6.io.nexus.workflow.WorkflowNexusInput;
import splitstree6.io.readers.ImportManager;
import splitstree6.io.utils.SimilaritiesToDistances;
import splitstree6.window.MainWindow;
import splitstree6.workflow.WorkflowSetup;

import java.io.File;
import java.util.List;
import java.util.function.Consumer;

public class ImportDialogPresenter {

	public ImportDialogPresenter(ImportDialog importDialog) {
		var mainWindow = importDialog.getMainWindow();
		var controller = importDialog.getController();

		var fileName = new SimpleStringProperty(this, "fileName", "");
		fileName.bindBidirectional(controller.getFileTextField().textProperty());
		controller.getFileTextField().disableProperty().bind(mainWindow.getWorkflow().runningProperty());

		controller.getDataTypeComboBox().getItems().setAll(List.of(CharactersBlock.class, DistancesBlock.class, SplitsBlock.class, TreesBlock.class));
		controller.getDataTypeComboBox().disableProperty().bind(mainWindow.getWorkflow().runningProperty());

		controller.getSimilarityToDistanceMethod().disableProperty().bind(controller.getSimilarityValues().selectedProperty().not());

		controller.getDataTypeComboBox().getSelectionModel().selectedItemProperty().addListener((v, o, n) -> {
			if (n == CharactersBlock.class)
				controller.getCharactersTab().getTabPane().getSelectionModel().select(controller.getCharactersTab());
			else if (n == DistancesBlock.class)
				controller.getDistancesTab().getTabPane().getSelectionModel().select(controller.getDistancesTab());
			else if (n == SplitsBlock.class)
				controller.getSplitsTab().getTabPane().getSelectionModel().select(controller.getSplitsTab());
			else if (n == TreesBlock.class)
				controller.getTreesTab().getTabPane().getSelectionModel().select(controller.getTreesTab());

			controller.getCharactersTab().getTabPane().setVisible(n != null);
		});

		controller.getFileFormatComboBox().getItems().addAll(ImportManager.getInstance().getAllFileFormats());
		controller.getFileFormatComboBox().disableProperty().bind(mainWindow.getWorkflow().runningProperty());

		final var selectedExtensionFilter = new SimpleObjectProperty<FileChooser.ExtensionFilter>();
		controller.getBrowseButton().setOnAction((e) -> {
			var previousDir = new File(ProgramProperties.get("ImportDir", ""));
			var fileChooser = new FileChooser();
			if (previousDir.isDirectory())
				fileChooser.setInitialDirectory(previousDir);
			fileChooser.setTitle("Open Import File");
			fileChooser.getExtensionFilters().addAll(ImportManager.getInstance().getExtensionFilters());
			if (selectedExtensionFilter.get() != null)
				fileChooser.setSelectedExtensionFilter(selectedExtensionFilter.get());
			// show file browser
			var selectedFile = fileChooser.showOpenDialog(mainWindow.getStage());
			if (selectedFile != null) {
				if (selectedFile.getParentFile().isDirectory())
					ProgramProperties.put("ImportDir", selectedFile.getParent());
				controller.getFileTextField().setText(selectedFile.getPath());
				selectedExtensionFilter.set(fileChooser.getSelectedExtensionFilter());

			}
		});
		controller.getBrowseButton().disableProperty().bind(mainWindow.getWorkflow().runningProperty());

		fileName.addListener((c, o, n) -> {
			controller.getDataTypeComboBox().getItems().setAll(ImportManager.getInstance().getAllDataTypes(n));
			var dataType = ImportManager.getInstance().getDataType(n);
			controller.getDataTypeComboBox().setValue(dataType);
			controller.getFileFormatComboBox().getItems().setAll(ImportManager.getInstance().getAllFileFormats(n));
			controller.getFileFormatComboBox().setValue(ImportManager.getInstance().getFileFormat(n));
		});

		controller.getCloseButton().setOnAction((e) -> {
			if (mainWindow.getWorkflow().isRunning())
				mainWindow.getWorkflow().cancel();
			importDialog.getStage().hide();
		});

		controller.getImportButton().setOnAction((e) -> {
			final var importer = ImportManager.getInstance().getImporterByDataTypeAndFileFormat(controller.getDataTypeComboBox().getSelectionModel().getSelectedItem(),
					controller.getFileFormatComboBox().getSelectionModel().getSelectedItem());
			if (importer == null)
				NotificationManager.showWarning("Can't import selected data type and file format");
			else {
				parseAndLoad(mainWindow, fileName.get(), controller);
				importDialog.getStage().hide();
			}
		});
		controller.getImportButton().disableProperty().bind(mainWindow.getWorkflow().runningProperty().or(
				Bindings.isNull(controller.getDataTypeComboBox().getSelectionModel().selectedItemProperty()).or(Bindings.equal(controller.getDataTypeComboBox().getSelectionModel().selectedItemProperty(), "Unknown"))
						.or(Bindings.isNull(controller.getFileFormatComboBox().getSelectionModel().selectedItemProperty())).or(Bindings.equal(controller.getFileFormatComboBox().getSelectionModel().selectedItemProperty(), "Unknown"))));

	}

	private static void parseAndLoad(MainWindow mainWindow, String fileName, ImportDialogController controller) {
		var dataType = controller.getDataTypeComboBox().getValue();
		try {
			final Consumer<Throwable> failedHandler = ex -> {
				mainWindow.getWorkflow().clear();
				NotificationManager.showError("Import failed: " + ex.getMessage());
			};
			final Runnable runOnSuccess = () -> {
				if (dataType.equals(DistancesBlock.class)) {
					if (controller.getSimilarityValues().isSelected()) {
						var method = controller.getSimilarityToDistanceMethod().getValue();
						if (method != null) {
							if (mainWindow.getWorkflow().getInputDataBlock() instanceof DistancesBlock distancesBlock) {
								SimilaritiesToDistances.apply(method, distancesBlock);
							}
							if (mainWindow.getWorkflow().getWorkingDataBlock() instanceof DistancesBlock distancesBlock) {
								SimilaritiesToDistances.apply(method, distancesBlock);
							}
						}
					}
				}
				mainWindow.getPresenter().getSplitPanePresenter().ensureTreeViewIsOpen(false);
				mainWindow.setFileName(fileName);
				mainWindow.setDirty(true);
			};
			if (WorkflowNexusInput.isApplicable(fileName)) {
				WorkflowNexusInput.open(mainWindow, fileName, failedHandler, runOnSuccess);
			} else
				WorkflowSetup.apply(fileName, mainWindow.getWorkflow(), failedHandler, runOnSuccess, dataType);

		} catch (Exception ex) {

			NotificationManager.showError("Import failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
		}
	}

}
