/*
 * AuxiliaryThreadScheduler.java Copyright (C) 2022 Daniel H. Huson
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
package splitstree6.autumn;

import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * manage auxiliary compute threads
 * Daniel Huson, 6.2011
 * todo: this is completely untested
 */
public class AuxiliaryThreadScheduler {
	final private ScheduledThreadPoolExecutor executor;
    final static private boolean verbose = true;

    final public static byte PENDING = 0;
    final public static byte RUNNING = 1;
    final public static byte DONE = 2;
    final public static byte DE_SCHEDULED = 2;

    final private SortedMap<Long, Runnable> scheduledTasks = new TreeMap<>();
    final private Map<Long, Future> id2future = new HashMap<>();

    final private Set<Long> pending = new HashSet<>();
    final private Set<Long> running = new HashSet<>();

    /**
	 * create a scheduler
	 */
    public AuxiliaryThreadScheduler(int maxNumberOfThreads) {
        executor = new ScheduledThreadPoolExecutor(maxNumberOfThreads);
    }

	/**
	 * schedule a task to be run on the next available thread
	 *
	 * @return task id
	 */
	public long schedule(Runnable runnable) {
		synchronized (scheduledTasks) {
			long taskId = System.currentTimeMillis();
			scheduledTasks.put(taskId, runnable);

			if (launch(taskId))
				running.add(taskId);
			else
				pending.add(taskId);
			return taskId;
		}
	}

	/**
	 * determine status of a given task
	 *
	 * @return PENDING, RUNNING or DONE
	 */
	public byte getStatus(long taskId) {
		synchronized (scheduledTasks) {
			if (pending.contains(taskId))
				return PENDING;
			else if (running.contains(taskId))
				return RUNNING;
			else return DONE;
		}
	}

	/**
	 * de-schedule a task. Use this if we want to run the task ourselves.
	 *
	 * @return status of task, de-scheduling only successful if DE_SCHEDULED returned
	 */
	public byte deSchedule(long taskId) {
		synchronized (scheduledTasks) {
			if (pending.contains(taskId)) {
				pending.remove(taskId);
				scheduledTasks.remove(taskId);
				return DE_SCHEDULED;
			} else if (running.contains(taskId))
				return RUNNING;
			else return DONE;
		}
	}

	/**
	 * wait for the task to complete.
	 *
	 */
	public void waitFor(long taskId) {
		var future = id2future.get(taskId);
		try {
			future.wait();
		} catch (InterruptedException ignored) {
		}
		id2future.remove(taskId);
	}

	/**
	 * wait for a set of tasks to complete
	 *
	 */
	public void waitFor(HashSet<Long> taskIds) {
		for (var taskId : taskIds) {
			waitFor(taskId);
		}
	}

	/**
	 * attempt to launch a task in one of the worker threads
	 *
	 * @return true, if launched, false if not
	 */
	private boolean launch(final long taskId) {
		if (executor.getActiveCount() < executor.getMaximumPoolSize()) {

			final var task = scheduledTasks.get(taskId);

			pending.remove(taskId);
			running.add(taskId);

			var future = executor.submit(() -> {
				if (verbose)
					System.err.println("Task " + taskId + " started");
				task.run();
				if (verbose)
					System.err.println("Task " + taskId + " finished");
				launchNext();
			});
			id2future.put(taskId, future);
			return true;
		} else
			return false;
	}

	private void launchNext() {
		synchronized (scheduledTasks) {
			for (var taskId : scheduledTasks.keySet()) {
				if (pending.contains(taskId)) {
					if (launch(taskId))
						return;
				}
			}
		}
	}

	/**
	 * stops all threads. Hard stop.
	 */
	public void stopAllThreads() {
		executor.shutdownNow();
	}
}
