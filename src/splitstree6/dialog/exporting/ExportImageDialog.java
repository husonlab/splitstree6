/*
 * ExportImageDialog.java Copyright (C) 2023 Daniel H. Huson
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
 *
 */

package splitstree6.dialog.exporting;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import jloda.fx.util.PrintUtils;
import jloda.fx.util.ProgramProperties;
import jloda.fx.window.NotificationManager;
import jloda.util.FileUtils;
import jloda.util.StringUtils;
import splitstree6.xtra.SaveToPDF;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * save an image to a file
 * Daniel Huson, 4.2023
 */
public class ExportImageDialog {

	public static void show(String file, Stage stage, Node mainNode) {
		final var fileChooser = new FileChooser();
		fileChooser.setTitle("Export Image");

		var previousFormat = ProgramProperties.get("SaveImageFormat", "gif");
		var previousDir = new File(ProgramProperties.get("SaveImageDir", ""));
		if (previousDir.isDirectory()) {
			fileChooser.setInitialDirectory(previousDir);
		} else
			fileChooser.setInitialDirectory((new File(file).getParentFile()));
		fileChooser.setInitialFileName(FileUtils.getFileNameWithoutPathOrSuffix(file) + "." + previousFormat);


		var supported = new String[]{"gif", "png", "tif", "pdf"}; // ImageIO.getWriterFileSuffixes(); // not all work
		var formats = Arrays.stream(supported).map(f -> "*." + f).toArray(String[]::new);
		fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter(String.format("Image Files (%s)", StringUtils.toString(supported, ", ")), formats));

		try {
			var selectedFile = fileChooser.showSaveDialog(stage);
			if (selectedFile != null) {
				var suffix = FileUtils.getFileSuffix(selectedFile.getName()).replaceAll("^.", "");
				var format = Arrays.stream(supported).filter(s -> s.equalsIgnoreCase(suffix)).findAny().orElse(null);
				if (format != null) {
					saveNodeAsImage(mainNode, format, selectedFile);
					ProgramProperties.put("SaveImageFormat", format);
					ProgramProperties.put("SaveImageDir", selectedFile.getParent());
				} else
					throw new IOException("Unknown image format: " + suffix);
			}
		} catch (IOException ex) {
			NotificationManager.showError("Save image failed: " + ex.getMessage());
		}
	}

	public static void saveNodeAsImage(Node node, String formatName, File file) throws IOException {
		if (formatName.equalsIgnoreCase("pdf")) {
			SaveToPDF.apply(node, file);
		} else {
			if (node instanceof Pane pane) {
				var scrollPane = pane.getChildren().stream().filter(c -> c instanceof ScrollPane).map(c -> (ScrollPane) c).findAny().orElse(null);
				node = PrintUtils.createImage(pane, scrollPane);
			}
			var parameters = new SnapshotParameters();
			parameters.setFill(Color.TRANSPARENT);
			var snapshot = node.snapshot(parameters, null);
			if (!ImageIO.write(SwingFXUtils.fromFXImage(snapshot, null), formatName, file)) {
				throw new IOException("Write failed: format not supported: " + formatName);
			}
		}
	}
}