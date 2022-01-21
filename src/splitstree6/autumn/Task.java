/*
 *  Task.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.autumn;

/**
 * tasks to be run in parallel on a pool of threads.
 * Use this when you want to run one of the tasks submitted to a ScheduledThreadPoolExecutor
 * in a different thread.
 * Simply call the run method. It will only start running the given runnable if it is not already running.
 * Daniel Huson, 7.2011
 */
public class Task implements Runnable {
	public enum Status {
		PENDING, RUNNING, DONE
	}

	private Status status = Status.PENDING;
	private Runnable runnable;

	/**
	 * construct a new task
	 */
	public Task() {
	}

	/**
	 * construct a new task
	 */
	public Task(Runnable runnable) {
		setRunnable(runnable);
	}

	/**
	 * set the runnable
	 *
	 * @param runnable
	 */
	public void setRunnable(Runnable runnable) {
		this.runnable = runnable;
	}

	/**
	 * try to run the task. It will only be executed, if it has status pending.
	 * If run, once completed, status is set to done.
	 */
	public void run() {
		if (setStatusRun() && runnable != null) {
			try {
				runnable.run();
			} finally {
				setStatusDone();
			}
		}
	}

	/**
	 * returns true, if this task has already been completed
	 *
	 * @return true, if done
	 */
	public boolean isDone() {
		return status == Status.DONE;
	}


	/**
	 * try to set the status to run
	 *
	 * @return true, if status was pending, else false
	 */
	private boolean setStatusRun() {
		synchronized (this) {
			if (status != Status.PENDING)
				return false;
			status = Status.RUNNING;
			return true;
		}
	}

	/**
	 * try to set the status to done
	 *
	 * @return true, if status was running
	 */
	private boolean setStatusDone() {
		synchronized (this) {
			if (status != Status.RUNNING)
				return false;
			status = Status.DONE;
			return true;
		}
	}
}
