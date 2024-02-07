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

import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import jloda.fx.util.TriConsumer;

import java.io.File;

/**
 * some clipboard utilities
 * Daniel Huson, 2.2024
 */
public class ClipboardUtils {
	public static TriConsumer<String, Image, File> copyFunction;


	static {
		copyFunction = (string, image, file) -> {
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
}
