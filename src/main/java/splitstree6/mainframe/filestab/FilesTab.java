/*
 *  FilesTab.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.mainframe.filestab;

import javafx.fxml.FXMLLoader;
import javafx.scene.control.Tab;
import javafx.stage.Stage;
import jloda.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;


public class FilesTab extends Tab {
	private final File directory;
	private static boolean removedMissingRecentFiles = false;
	private final FilesTabController controller;

	private final FilesTabPresenter presenter;

	public FilesTab(File directory, Stage stage, Consumer<String> fileOpener, Consumer<String> fileCloser) {
		this.directory = directory;
		if (!FileUtils.isDirectory(directory.getPath()))
			throw new RuntimeException("Directory does not exist: " + directory);

		setText("Files");
		var fxmlLoader = new FXMLLoader();
		try (var ins = Objects.requireNonNull(getClass().getResource("FilesTab.fxml")).openStream()) {
			fxmlLoader.load(ins);
		} catch (
				IOException ex) {
			throw new RuntimeException(ex);
		}
		controller = fxmlLoader.getController();
		setContent(controller.getRootPane());
		presenter = new FilesTabPresenter(this, stage, fileOpener, fileCloser);
	}

	public FilesTabController getController() {
		return controller;
	}

	public FilesTabPresenter getPresenter() {
		return presenter;
	}

	public File getDirectory() {
		return directory;
	}
}
