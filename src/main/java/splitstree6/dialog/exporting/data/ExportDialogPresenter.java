/*
 *  ExportDialogPresenter.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.dialog.exporting.data;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import jloda.fx.util.ProgramProperties;
import jloda.fx.window.MainWindowManager;
import jloda.fx.window.NotificationManager;
import jloda.util.FileUtils;
import jloda.util.StringUtils;
import splitstree6.io.utils.DataBlockWriter;
import splitstree6.io.writers.ExportManager;
import splitstree6.main.SplitsTree6;
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

	// these two static properties are used to customize the mobile version
	public static StringProperty fileName = new SimpleStringProperty("");
	public static StringProperty mobileDirectory = new SimpleStringProperty("");

	public ExportDialogPresenter(MainWindow mainWindow, ExportDialogController controller, Stage stage, DataNode dataNode) {
		MainWindowManager.getInstance().addAuxiliaryWindow(mainWindow, stage);

		fileName.set("");

		var exporter = new SimpleObjectProperty<DataBlockWriter>();

		if (!SplitsTree6.isDesktop()) {
			if (controller.getBrowseButton().getParent() instanceof Pane pane) {
				pane.getChildren().remove(controller.getBrowseButton());
			}
			exporter.addListener((v, o, n) -> {
				var suffix = (n == null ? "" : "." + n.getFileExtensions().get(0));
				controller.getFileTextField().setText(FileUtils.getFileNameWithoutPath(FileUtils.replaceFileSuffix(mainWindow.getFileName(), "-" + StringUtils.toLowerCaseWithUnderScores(dataNode.getTitle()) + suffix)));
			});
			controller.getFileTextField().setText(FileUtils.getFileNameWithoutPathOrSuffix(mainWindow.getFileName()));
		}

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
		if (controller.getFormatCBox().getValue() == null && !controller.getFormatCBox().getItems().isEmpty())
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

		dataNode.titleProperty().addListener(e -> controller.getTitleLabel().setText("Export '%s' of type '%s'"
				.formatted(dataNode.getTitle(), dataNode.getClass().getSimpleName())));
		controller.getTitleLabel().setText("Export '%s' of type '%s'".formatted(dataNode.getTitle(),
				dataNode.getDataBlock().getClass().getSimpleName().replaceAll("Block$", "")));
		controller.getApplyButton().setOnAction(e -> {
			try {
				var fileName = controller.getFileTextField().getText();
				if (!SplitsTree6.isDesktop()) {
					fileName = mobileDirectory.get() + File.separator + fileName + ".txt";
				}
				FileUtils.checkFileWritable(fileName, true);
				exporter.get().write(fileName, mainWindow.getWorkflow().getWorkingTaxaBlock(), dataNode.getDataBlock());
				MainWindowManager.getInstance().removeAuxiliaryWindow(mainWindow, stage);
				stage.hide();
				System.err.println("Exported " + dataNode.getTitle() + " to file: " + FileUtils.getFileNameWithoutPath(fileName));
				ExportDialogPresenter.fileName.set(fileName);
			} catch (Exception ex) {
				NotificationManager.showError("Export failed: " + ex);
			}
		});
		controller.getApplyButton().disableProperty().bind(controller.getFileTextField().textProperty().isEmpty());
	}

	/**
	 * show export dialog
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
		fileChooser.setInitialFileName(FileUtils.getFileNameWithoutPath(FileUtils.replaceFileSuffix(mainWindow.getFileName(), "-" + StringUtils.toLowerCaseWithUnderScores(dataNode.getTitle()))));

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
