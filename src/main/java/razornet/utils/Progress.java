/*
 * Progress.java Copyright (C) 2026 Daniel H. Huson
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
 * Dummy progress abstraction used by the public stub of RazorNet.
 * <p>
 * The real implementation in the private RazorNet package
 * likely provides richer functionality. This version only
 * defines the methods required by ProgressAdapter.
 */
public abstract class Progress {

	/**
	 * Set current progress and total work.
	 *
	 * @param progress current progress
	 * @param total    total work units
	 * @throws CanceledException if computation was canceled
	 */
	public abstract void setProgress(long progress, long total) throws CanceledException;

	/**
	 * Check whether computation has been canceled.
	 *
	 * @throws CanceledException if canceled
	 */
	public abstract void checkForCanceled() throws CanceledException;

	/**
	 * Set a subtask description.
	 *
	 * @param name subtask name
	 */
	public abstract void setSubTask(String name);
}