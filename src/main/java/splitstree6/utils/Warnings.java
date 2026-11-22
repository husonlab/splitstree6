/*
 *  Warnings.java Copyright (C) 2026 Daniel H. Huson
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

import javafx.stage.Stage;
import jloda.fx.window.NotificationManager;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * warnings from computations and from reading files, shown in the window that they belong to.
 * <p>
 * A warning is written to the message window and the console, as before, and is also shown as a notification in a
 * window. Which window: the one for which the current thread is working. The workflow says so before it runs an
 * algorithm, and the code that opens a file says so before it reads, by {@link #setWindow}; code that warns need not
 * know. A thread for which nobody has said so, such as one of a pool that an algorithm uses, gets the window that was
 * last in front, which is what all warnings got until 11.2026.
 * <p>
 * The same warning is shown as a notification at most once a minute, and at most five warnings are shown within ten
 * seconds; every warning is still written to the message window.
 * Daniel Huson, 11.2026
 */
public class Warnings {
	private static final ThreadLocal<Stage> window = new ThreadLocal<>();

	private static final long SAME_MESSAGE_MILLIS = 60000;
	private static final long BURST_MILLIS = 10000;
	private static final int BURST_SIZE = 5;
	private static final Map<String, Long> lastShown = new HashMap<>();
	private static long burstStart = 0;
	private static int burstCount = 0;

	/**
	 * how a warning is shown; replaced in tests
	 */
	static BiConsumer<Stage, String> notifier = NotificationManager::showWarning;
	static java.util.function.LongSupplier clock = System::currentTimeMillis;

	/**
	 * the current thread works for this window, until {@link #clearWindow} is called. Call both around the work, in a
	 * try and finally
	 *
	 * @param stage the window, or null if there is none (no user interface)
	 */
	public static void setWindow(Stage stage) {
		if (stage != null)
			window.set(stage);
		else
			window.remove();
	}

	public static void clearWindow() {
		window.remove();
	}

	/**
	 * the window that the current thread works for, or null
	 */
	public static Stage getWindow() {
		return window.get();
	}

	/**
	 * show a warning
	 *
	 * @param message the text, without a leading "Warning:"
	 */
	public static void show(String message) {
		if (mayNotify(message))
			notifier.accept(window.get(), message); // writes to the console as well
		else
			System.err.println("Warning: " + message);
	}

	private static synchronized boolean mayNotify(String message) {
		var now = clock.getAsLong();
		var last = lastShown.get(message);
		if (last != null && now - last < SAME_MESSAGE_MILLIS)
			return false;
		if (now - burstStart > BURST_MILLIS) {
			burstStart = now;
			burstCount = 0;
		}
		if (++burstCount > BURST_SIZE)
			return false;
		if (lastShown.size() > 1000)
			lastShown.clear();
		lastShown.put(message, now);
		return true;
	}

	/**
	 * forget what has been shown; for tests
	 */
	static synchronized void reset() {
		lastShown.clear();
		burstStart = 0;
		burstCount = 0;
	}
}
