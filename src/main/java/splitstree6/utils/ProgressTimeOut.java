/*
 *  ProgressTimeOut.java Copyright (C) 2024 Daniel H. Huson
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

import jloda.util.CanceledException;
import jloda.util.progress.ProgressListener;

/**
 * progress listener that throws a user canceled expection after a given amount of time
 */
public class ProgressTimeOut implements ProgressListener {
	private final long endTime;

	/**
	 * constructor
	 *
	 * @param timeInMilliseconds time until canceled exception in thrown
	 */
	public ProgressTimeOut(long timeInMilliseconds) {
		endTime = System.currentTimeMillis() + timeInMilliseconds;
	}

	@Override
	public void setMaximum(long l) {

	}

	@Override
	public void setProgress(long l) throws CanceledException {
		checkForCancel();
	}

	@Override
	public void setProgressIgnoreCancel(long l) {

	}

	@Override
	public long getProgress() {
		return 0;
	}

	@Override
	public void checkForCancel() throws CanceledException {
		if (System.currentTimeMillis() > endTime)
			throw new CanceledException();
	}

	@Override
	public void setTasks(String s, String s1) {

	}

	@Override
	public void setSubtask(String s) {

	}

	@Override
	public void setCancelable(boolean b) {

	}

	@Override
	public boolean isUserCancelled() {
		return System.currentTimeMillis() > endTime;

	}

	@Override
	public void setUserCancelled(boolean b) {

	}

	@Override
	public void incrementProgress() throws CanceledException {
		checkForCancel();
	}

	@Override
	public void incrementProgressIgnoreCancel() {

	}

	@Override
	public void close() {

	}

	@Override
	public boolean isCancelable() {
		return false;
	}

	@Override
	public void reportTaskCompleted() {

	}

	@Override
	public void setDebug(boolean b) {

	}

	@Override
	public void setPause(boolean b) {

	}

	@Override
	public boolean getPause() {
		return false;
	}
}
