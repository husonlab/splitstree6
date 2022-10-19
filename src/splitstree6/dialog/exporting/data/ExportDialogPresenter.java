/*
 * ExportDialogPresenter.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.dialog.exporting.data;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import jloda.fx.util.ProgramProperties;
import jloda.fx.window.MainWindowManager;
import jloda.fx.window.NotificationManager;
import jloda.util.FileUtils;
import jloda.util.StringUtils;
import splitstree6.io.utils.DataBlockWriter;
import splitstree6.io.writers.ExportManager;
import splitstree6.options.Option;
import splitstree6.options.OptionControlCreator;
import splitstree6.window.MainWindow;
import splitstree6.workflow.DataNode;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * export dialog presenter
 * Daniel Huson, 11.2021
 */
public class ExportDialogPresenter {
	private final List<ChangeListener> changeListeners = new ArrayList<>();

	public ExportDialogPresenter(MainWindow mainWindow, ExportDialogController controller, Stage stage, DataNode dataNode) {
		MainWindowManager.getInstance().addAuxiliaryWindow(mainWindow, stage);

		var exporter = new SimpleObjectProperty<DataBlockWriter>();

		exporter.addListener((v, o, n) -> setupOptionControls(controller, n));

		controller.getFormatCBox().valueProperty().addListener((v, o, n) -> {
			controller.getMainPane().getChildren().clear();
			if (n != null) {
				exporter.set(ExportManager.getInstance().getExporterByName(dataNode.getDataBlock().getClass(), n));
			}
		});

		controller.getFormatCBox().getItems().setAll(ExportManager.getInstance().getExporterNames(dataNode.getDataBlock()));
		for (var name : controller.getFormatCBox().getItems()) {
			if (name.startsWith("Nexus")) {
				controller.getFormatCBox().setValue(name);
			}
		}
		if (controller.getFormatCBox().getValue() == null && controller.getFormatCBox().getItems().size() > 0)
			controller.getFormatCBox().setValue(controller.getFormatCBox().getItems().get(0));

		controller.getBrowseButton().setOnAction(e -> {
			var file = showExportDialog(mainWindow, dataNode, exporter.get());
			if (file != null)
				controller.getFileTextField().setText(file.getPath());
		});

		controller.getCancelButton().setOnAction(e -> {
			stage.hide();
			MainWindowManager.getInstance().removeAuxiliaryWindow(mainWindow, stage);
		});

		controller.getApplyButton().setOnAction(e -> {
			try {
				var fileName = controller.getFileTextField().getText();
				FileUtils.checkFileWritable(fileName, true);
				exporter.get().write(fileName, mainWindow.getWorkflow().getWorkingTaxaBlock(), dataNode.getDataBlock());
				stage.hide();
				MainWindowManager.getInstance().removeAuxiliaryWindow(mainWindow, stage);
			} catch (Exception ex) {
				NotificationManager.showError("Export failed: " + ex);
			}
		});
		controller.getApplyButton().disableProperty().bind(controller.getFileTextField().textProperty().isEmpty());
	}

	/**
	 * save dialog
	 *
	 * @param mainWindow the main window
	 * @return true if saved
	 */
	public static File showExportDialog(MainWindow mainWindow, DataNode dataNode, DataBlockWriter dataBlockWriter) {
		final var fileChooser = new FileChooser();
		fileChooser.setTitle("Export SplitsTree6 data");

		final var previousDir = new File(ProgramProperties.get("ExportDir", ""));
		if (previousDir.isDirectory()) {
			fileChooser.setInitialDirectory(previousDir);
		} else
			fileChooser.setInitialDirectory((new File(mainWindow.getFileName()).getParentFile()));

		fileChooser.getExtensionFilters().addAll(dataBlockWriter.getExtensionFilter());
		fileChooser.setInitialFileName(FileUtils.getFileNameWithoutPath(FileUtils.replaceFileSuffix(mainWindow.getFileName(), "-" + StringUtils.toLowerCaseWithUnderScores(dataNode.getTitle()) + "." + dataBlockWriter.getFileExtensions().get(0))));

		return fileChooser.showSaveDialog(mainWindow.getStage());
	}

	public void setupOptionControls(ExportDialogController controller, DataBlockWriter exporter) {
		controller.getMainPane().getChildren().clear();
		changeListeners.clear();
		for (var option : Option.getAllOptions(exporter)) {
			var control = OptionControlCreator.apply(option, changeListeners);
			if (control != null) {
				var label = new Label(StringUtils.fromCamelCase(option.getName()));
				label.setPrefWidth(120);
				var hbox = new HBox(label, control);
				hbox.setPrefWidth(HBox.USE_COMPUTED_SIZE);
				controller.getMainPane().getChildren().add(hbox);
			}
		}
	}
}
