/*
 *  Platform.java Copyright (C) 2023 Daniel H. Huson
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

/**
 * what platform are we running on?
 * Daniel Huson, 11.2023
 */
public enum Platform {
	Linux, MacOS, Windows, Other;

	private static final Platform platform;

	static {
		var os = System.getProperty("os.name", null).toLowerCase();
		if (os.contains("win")) {
			platform = Windows;
		} else if (os.contains("mac")) {
			platform = MacOS;
		} else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
			platform = Linux;
		} else {
			platform = Other;
		}
		System.err.println("Platform detected: " + platform);
	}

	public static boolean isDesktop() {
		return platform != Other;
	}

	public static Platform getPlatform() {
		return platform;
	}
}
