/*
 *  WarningsTest.java Copyright (C) 2026 Daniel H. Huson
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * which warnings are shown as notifications, and for which thread
 */
public class WarningsTest {
	private final List<String> shown = new ArrayList<>();
	private final AtomicLong time = new AtomicLong(1_000_000);

	@BeforeEach
	void setup() {
		Warnings.reset();
		Warnings.notifier = (stage, message) -> shown.add(message);
		Warnings.clock = time::get;
	}

	@AfterEach
	void restore() {
		Warnings.notifier = jloda.fx.window.NotificationManager::showWarning;
		Warnings.clock = System::currentTimeMillis;
		Warnings.reset();
		Warnings.clearWindow();
	}

	@Test
	void theSameWarningIsShownOnceAMinute() {
		Warnings.show("a");
		Warnings.show("a");
		time.addAndGet(59_000);
		Warnings.show("a");
		assertEquals(List.of("a"), shown);
		time.addAndGet(2_000);
		Warnings.show("a");
		assertEquals(List.of("a", "a"), shown);
	}

	@Test
	void atMostFiveWarningsInTenSeconds() {
		for (var i = 1; i <= 8; i++) Warnings.show("warning " + i);
		assertEquals(5, shown.size());
		time.addAndGet(11_000);
		Warnings.show("warning 9");
		assertEquals(6, shown.size());
		assertEquals("warning 9", shown.get(5));
	}

	/**
	 * the window is that of the thread that warns: another thread does not see it, and it is gone after clearing
	 */
	@Test
	void theWindowBelongsToTheThread() throws Exception {
		assertNull(Warnings.getWindow());
		Warnings.setWindow(null);
		assertNull(Warnings.getWindow());
		var seen = new Stage[1];
		var other = new Thread(() -> seen[0] = Warnings.getWindow());
		other.start();
		other.join();
		assertNull(seen[0]);
		Warnings.clearWindow();
		assertNull(Warnings.getWindow());
	}
}
