/*
 * Utilities.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.tools.server;


/**
 * server utilities
 * Daniel Huson, 8.2020
 */
public class Utilities {
	private static final byte[] SALT = "7DFjUnE9p2uDeDu0".getBytes();

	public static final String SERVER_ERROR = "401 Error:";

	public static byte[] getBytesLittleEndian(int a) {
		return new byte[]{(byte) a, (byte) (a >> 8), (byte) (a >> 16), (byte) (a >> 24)};
	}

	public static byte[] getBytesLittleEndian(long a) {
		return new byte[]{(byte) a, (byte) (a >> 8), (byte) (a >> 16), (byte) (a >> 24), (byte) (a >> 32), (byte) (a >> 40), (byte) (a >> 48), (byte) (a >> 56)};
	}
}
