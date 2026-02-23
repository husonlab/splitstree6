/*
 * CanceledException.java Copyright (C) 2026 Daniel H. Huson
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
 *
 */

package razornet.utils;

/**
 * Dummy cancellation exception.
 * <p>
 * Note: Must be unchecked because RazorNet.compute(...) does not declare it.
 */
public class CanceledException extends RuntimeException {
	public CanceledException() {
		super();
	}

	public CanceledException(String message) {
		super(message);
	}

	public CanceledException(String message, Throwable cause) {
		super(message, cause);
	}

	public CanceledException(Throwable cause) {
		super(cause);
	}
}
