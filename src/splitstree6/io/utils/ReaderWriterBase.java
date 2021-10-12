/*
 *  ReaderWriterBase.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.io.utils;

import java.util.ArrayList;

public class ReaderWriterBase {
	private final ArrayList<String> fileExtensions = new ArrayList<>();

	public ArrayList<String> getFileExtensions() {
		return fileExtensions;
	}

	public void setFileExtensions(String... extensions) {
		for (var ex : extensions) {
			if (!fileExtensions.contains(ex))
				fileExtensions.add(ex);
			if (!fileExtensions.contains(ex + ".gz"))
				fileExtensions.add(ex + ".gz");
		}
	}

	public boolean accepts(String file) {
		if (fileExtensions.size() == 0)
			return true;
		else {
			for (var ex : fileExtensions) {
				if (file.endsWith(ex))
					return true;
			}
			return false;
		}
	}
}
