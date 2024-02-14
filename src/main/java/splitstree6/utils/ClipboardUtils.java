/*
 *  ClipboardUtils.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.utils;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Task;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.util.Duration;
import jloda.fx.util.TriConsumer;
import jloda.util.FileUtils;
import jloda.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.List;

/**
 * some clipboard utilities
 * Daniel Huson, 2.2024
 */
public class ClipboardUtils {
	public static boolean MONITOR_CLIPBOARD = true;

	public static TriConsumer<String, Image, File> copyFunction = (string, image, file) -> {
		var content = new ClipboardContent();
		if (string != null)
			content.putString(string);
		if (image != null)
			content.putImage(image);
		if (file != null)
			content.getFiles().add(file);
		if (!content.isEmpty())
			Clipboard.getSystemClipboard().setContent(content);
	};

	private static ClipboardUtils instance;

	private final ScheduledService<Void> service;
	private final BooleanProperty hasString;
	private final BooleanProperty hasImage;
	private final BooleanProperty hasFiles;

	private static ClipboardUtils getInstance() {
		if (instance == null) {
			instance = new ClipboardUtils();
		}
		return instance;
	}

	private ClipboardUtils() {
		hasString = new SimpleBooleanProperty(false);
		hasImage = new SimpleBooleanProperty(false);
		hasFiles = new SimpleBooleanProperty(false);
		if (MONITOR_CLIPBOARD) {
			service = new ScheduledService<>() {
				@Override
				protected Task<Void> createTask() {
					return new Task<>() {
						@Override
						protected Void call() {
							Platform.runLater(() -> {
								hasString.set(Clipboard.getSystemClipboard().hasString());
								hasImage.set(Clipboard.getSystemClipboard().hasImage());
								hasFiles.set(Clipboard.getSystemClipboard().hasFiles());
							});
							return null;
						}
					};
				}
			};
			service.setPeriod(Duration.millis(500));
			Platform.runLater(service::start);
		} else {
			hasString.set(true);
			hasImage.set(true);
			hasFiles.set(true);
			service = null;
		}
	}

	public static void putString(String string) {
		if (copyFunction != null)
			copyFunction.accept(string, null, null);
	}

	public static void putImage(Image image) {
		if (copyFunction != null)
			copyFunction.accept(null, image, null);
	}

	public static void putFile(File file) {
		if (copyFunction != null)
			copyFunction.accept(null, null, file);
	}

	public static void put(String string, Image image, File file) {
		if (copyFunction != null)
			copyFunction.accept(string, image, file);
	}

	/**
	 * get the function currently used to copy items to the clipbard
	 *
	 * @return copy function
	 */
	public static TriConsumer<String, Image, File> getCopyFunction() {
		return copyFunction;
	}

	/**
	 * set the function to be used to copy items to the clipboard
	 *
	 * @param copyFunction the new copy function
	 */
	public static void setCopyFunction(TriConsumer<String, Image, File> copyFunction) {
		ClipboardUtils.copyFunction = copyFunction;
	}

	public static boolean hasString() {
		return Clipboard.getSystemClipboard().hasContent(DataFormat.PLAIN_TEXT);
	}

	public static String getString() {
		return (String) Clipboard.getSystemClipboard().getContent(DataFormat.PLAIN_TEXT);
	}

	public static boolean hasImage() {
		return Clipboard.getSystemClipboard().hasImage();
	}

	public static Image getImage() {
		return Clipboard.getSystemClipboard().getImage();
	}

	public static boolean hasFiles() {
		return Clipboard.getSystemClipboard().hasFiles();
	}

	public static List<File> getFiles() {
		return Clipboard.getSystemClipboard().getFiles();
	}


	public static ReadOnlyBooleanProperty hasStringProperty() {
		return getInstance().hasString;
	}

	public static ReadOnlyBooleanProperty hasImageProperty() {
		return getInstance().hasImage;
	}

	public static ReadOnlyBooleanProperty hasFilesProperty() {
		return getInstance().hasFiles;
	}

	public static String getTextFilesContentOrString() {
		if (hasFiles()) {
			var files = ClipboardUtils.getFiles();
			var buf = new StringBuilder();
			for (var file : files) {
				if (FileUtils.fileExistsAndIsNonEmpty(file) && isTextFile(file)) {
					try {
						buf.append(StringUtils.toString(FileUtils.getLinesFromFile(file.getPath()), "\n"));
					} catch (IOException ignored) {
					}
				}
			}
			return buf.toString();
		} else if (hasString()) {
			var string = ClipboardUtils.getString().trim();
			if (FileUtils.fileExistsAndIsNonEmpty(string) && isTextFile(new File(string))) {
				try {
					string = StringUtils.toString(FileUtils.getLinesFromFile(string), "\n");
				} catch (IOException ignored) {
				}
			}
			return string;
		} else
			return null;
	}

	public static boolean isTextFile(File file) {
		var path = FileSystems.getDefault().getPath(file.getParent(), file.getName());
		try {
			var mimeType = Files.probeContentType(path);
			return mimeType == null || mimeType.equals("text/plain");
		} catch (IOException e) {
			return false;
		}
	}
}
