/*
 *  ProgressMover.java Copyright (C) 2024 Daniel H. Huson
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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * move the progress bar along when the progress is unknown
 * Daniel Huson, 2.2024
 */
public class ProgressMover implements AutoCloseable {
	private long waitTime = 1000;

	private final ExecutorService executor;

	public ProgressMover(ProgressListener progress) throws CanceledException {
		progress.setMaximum(20);
		progress.setProgress(0);
		// A daemon thread: animating a progress bar must never be a reason for the JVM to
		// stay alive. Belt and braces alongside the interrupt handling below.
		executor = Executors.newSingleThreadExecutor(runnable -> {
			var thread = new Thread(runnable);
			thread.setDaemon(true);
			return thread;
		});
		executor.execute(() -> {
			while (!Thread.currentThread().isInterrupted()) {
				try {
					Thread.sleep(waitTime);
					progress.incrementProgress();
					waitTime = Math.round(1.5 * waitTime);
				} catch (InterruptedException ex) {
					// close() calls shutdownNow(), which interrupts us: that is the stop signal.
					// Catching this with the broad clause below - as this loop used to - made the
					// thread unstoppable, and being non-daemon it then kept the whole JVM alive.
					// Invisible in the application, which exits via System.exit; fatal in a tool
					// or a language binding, where running Autumn meant the process never ended.
					Thread.currentThread().interrupt();
					return;
				} catch (Exception ignored) {
				}
			}
		});
	}

	@Override
	public void close() {
		executor.shutdownNow();
	}
}
