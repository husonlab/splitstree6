/*
 * Progress.java Copyright (C) 2025 Daniel H. Huson
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

package razornetaccess;

import jloda.util.progress.ProgressListener;
import razornet.utils.CanceledException;

public class ProgressAdapter extends razornet.utils.Progress {
	private final ProgressListener progressListener;

	public ProgressAdapter(ProgressListener progressListener) {
		this.progressListener = progressListener;
	}

	@Override
	public void setProgress(long progress, long total) throws CanceledException {
		try {
			progressListener.setMaximum(total);
			progressListener.setProgress(progress);
		} catch (Exception e) {
			throw new CanceledException();
		}
	}

	@Override
	public void checkForCanceled() throws CanceledException {
		try {
			progressListener.checkForCancel();
		} catch (Exception e) {
			throw new CanceledException();
		}
	}
}
