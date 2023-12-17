/*
 *  FileItem.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.mobileframe.filestab;

import jloda.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;

public class FileItem {
	private String name;

	private String path;
	private FileTime date;
	private long size;
	private String kind;

	public FileItem(String path) {
		setPath(path);
		setName(FileUtils.getFileNameWithoutPathOrSuffix(path));
		updateInfo();
		setKind(FileUtils.getFileSuffix(path));
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public FileTime getDate() {
		return date;
	}

	public void setDate(FileTime date) {
		this.date = date;
	}

	public long getSize() {
		return size;
	}

	public void setSize(long size) {
		this.size = size;
	}

	public String getKind() {
		return kind;
	}

	public void setKind(String kind) {
		this.kind = kind;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public void updateInfo() {
		if (FileUtils.fileExistsAndIsNonEmpty(path)) {
			try {
				var attrs = Files.readAttributes(new File(path).toPath(), BasicFileAttributes.class);
				setDate(attrs.lastModifiedTime());
			} catch (IOException ignored) {
			}
			setSize((new File(path)).length());
		}
	}
}