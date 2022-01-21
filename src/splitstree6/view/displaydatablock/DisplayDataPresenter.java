/*
 *  DisplayDataPresenter.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.view.displaydatablock;

import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Orientation;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import jloda.fx.window.NotificationManager;
import jloda.util.StringUtils;
import splitstree6.io.utils.DataBlockWriter;
import splitstree6.io.writers.ExportManager;
import splitstree6.options.Option;
import splitstree6.options.OptionControlCreator;
import splitstree6.window.MainWindow;
import splitstree6.workflow.DataNode;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * display data presenter
 * Daniel Huson, 11.2021
 */
public class DisplayDataPresenter {
	private final List<ChangeListener> changeListeners = new ArrayList<>();

	private final InvalidationListener dataBlockChangeListener;

	public DisplayDataPresenter(MainWindow mainWindow, DisplayData displayData, DisplayDataController controller, DataNode dataNode) {
		{
			// add format titled pane to tool bar:
			var displayTextController = displayData.getController();
			displayTextController.getToolBar().getItems().add(new Separator(Orientation.VERTICAL));
			var titledPane = controller.getTitledPane();
			AnchorPane.setLeftAnchor(titledPane, 100.0);
			AnchorPane.setTopAnchor(titledPane, 4.0);
			displayTextController.getAnchorPane().getChildren().add(titledPane);

			titledPane.setAnimated(true);
			titledPane.setExpanded(false);
			titledPane.setText("Format: Nexus");
		}

		var workflow = mainWindow.getWorkflow();
		var taxaBlock = workflow.getWorkingTaxaBlock();

		var exporter = new SimpleObjectProperty<DataBlockWriter>();

		exporter.addListener((v, o, n) -> {
			setupOptionControls(controller, n);
		});

		controller.getFormatCBox().valueProperty().addListener((v, o, n) -> {
			controller.getMainPane().getChildren().clear();
			if (n != null) {
				exporter.set(ExportManager.getInstance().getExporterByName(dataNode.getDataBlock().getClass(), n));
			}
		});

		dataBlockChangeListener = e -> {
			controller.getFormatCBox().getItems().setAll(ExportManager.getInstance().getExporterNames(dataNode.getDataBlock()));
			for (var name : controller.getFormatCBox().getItems()) {
				if (name.startsWith("Nexus")) {
					controller.getFormatCBox().setValue(name);
					return;
				}
			}
			if (controller.getFormatCBox().getItems().size() > 0)
				controller.getFormatCBox().setValue(controller.getFormatCBox().getItems().get(0));
		};
		dataNode.dataBlockProperty().addListener(new WeakInvalidationListener(dataBlockChangeListener));

		dataBlockChangeListener.invalidated(null);

		controller.getTitledPane().setAnimated(true);
		//controller.getTitledPane().setExpanded(true);

		controller.getApplyButton().setOnAction(e -> {
			if (exporter.get() != null) {
				try (var w = new StringWriter()) {
					exporter.get().write(w, taxaBlock, dataNode.getDataBlock());
					displayData.replaceText(w.toString());
					controller.getTitledPane().setText("Format: " + exporter.get().getName());
					controller.getTitledPane().setExpanded(false);
				} catch (IOException ex) {
					NotificationManager.showError("Export data failed: " + ex);
					controller.getTitledPane().setText("Format");
				}
			}
		});
		controller.getApplyButton().disableProperty().bind(displayData.emptyProperty().or(exporter.isNull()));
	}

	public void setupOptionControls(DisplayDataController controller, DataBlockWriter exporter) {
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
