/*
 *  RunningJobs.java Copyright (C) 2026 Daniel H. Huson
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

import javafx.concurrent.Service;

import java.util.HashSet;
import java.util.Set;

/**
 * registry of long-running jobs that a view starts on its own account
 * <p>
 * A view's heavy computations - the tanglegram layout optimization, say - are started only after the view's
 * algorithm node has already succeeded, so "the workflow is valid" does not mean "the views have finished".
 * Anything that waits for a complete result, above all a headless run, must wait for these jobs as well or it
 * will write its output while a view is still working.
 * <p>
 * Use {@link #track(Service)} rather than add/remove by hand: a job that is registered but never unregistered
 * hangs the wait forever, and one that is never registered is silently raced, which is the failure this class
 * exists to prevent.
 * <p>
 * Daniel Huson, 8.2026
 */
public class RunningJobs {
	private static final Set<Object> jobs = new HashSet<>();

	/**
	 * registers a service for exactly as long as it runs
	 */
	public static void track(Service<?> service) {
		service.runningProperty().addListener((v, o, n) -> {
			if (n)
				add(service);
			else
				remove(service);
		});
	}

	public static void add(Object job) {
		synchronized (jobs) {
			jobs.add(job);
			jobs.notifyAll();
		}
	}

	public static void remove(Object job) {
		synchronized (jobs) {
			jobs.remove(job);
			jobs.notifyAll();
		}
	}

	public static int size() {
		synchronized (jobs) {
			return jobs.size();
		}
	}

	public static boolean isEmpty() {
		return size() == 0;
	}

	/**
	 * waits until no tracked job is running
	 * <p>
	 * Callers should give jobs a moment to start before calling this - a view job is launched from a listener
	 * that has not necessarily run yet at the instant the workflow reports itself valid - which is what the
	 * graceMillis argument is for.
	 *
	 * @param graceMillis   time to wait before first looking, allowing jobs to start
	 * @param timeoutMillis maximum total time to wait, or 0 for no limit
	 * @return true if all jobs finished, false on timeout
	 */
	public static boolean awaitAll(long graceMillis, long timeoutMillis) throws InterruptedException {
		if (graceMillis > 0)
			Thread.sleep(graceMillis);
		var end = System.currentTimeMillis() + timeoutMillis;
		synchronized (jobs) {
			while (!jobs.isEmpty()) {
				if (timeoutMillis <= 0)
					jobs.wait();
				else {
					var remaining = end - System.currentTimeMillis();
					if (remaining <= 0)
						return false;
					jobs.wait(remaining);
				}
			}
		}
		return true;
	}
}
