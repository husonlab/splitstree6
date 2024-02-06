/*
 *  SwipeUtils.java Copyright (C) 2024 Daniel H. Huson
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

import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.control.ScrollBar;


/**
 * some swipe gesture utils
 * Daniel Huson,2.2024
 */
public class SwipeUtils {
	public static void setConsumeSwipes(Node node) {
		node.setOnSwipeLeft(Event::consume);
		node.setOnSwipeRight(Event::consume);
		node.setOnSwipeUp(Event::consume);
		node.setOnSwipeDown(Event::consume);
	}

	public static void setConsumeSwipeLeft(Node swipeNode) {
		swipeNode.setOnSwipeLeft(Event::consume);
	}

	public static void setConsumeSwipeRight(Node swipeNode) {
		swipeNode.setOnSwipeRight(Event::consume);
	}

	public static void setConsumeSwipeUp(Node swipeNode) {
		swipeNode.setOnSwipeUp(Event::consume);
	}

	public static void setConsumeSwipeDown(Node swipeNode) {
		swipeNode.setOnSwipeDown(Event::consume);
	}

	public static void setOnSwipeLeft(Node swipeNode, Runnable runnable) {
		swipeNode.setOnSwipeLeft(e -> {
			runnable.run();
			e.consume();
		});
	}

	public static void setOnSwipeRight(Node swipeNode, Runnable runnable) {
		swipeNode.setOnSwipeRight(e -> {
			runnable.run();
			e.consume();
		});
	}

	public static void setOnSwipeUp(Node swipeNode, Runnable runnable) {
		swipeNode.setOnSwipeUp(e -> {
			runnable.run();
			e.consume();
		});
	}

	public static void setOnSwipeDown(Node swipeNode, Runnable runnable) {
		swipeNode.setOnSwipeDown(e -> {
			runnable.run();
			e.consume();
		});
	}

	public static void setOnSwipeLeft(Node swipeNode, ScrollBar bar) {
		swipeNode.setOnSwipeLeft(e -> {
			var delta = bar.getVisibleAmount();
			Platform.runLater(() -> bar.setValue(Math.min(bar.getMax(), bar.getValue() + delta)));
			e.consume();
		});
	}

	public static void setOnSwipeRight(Node swipeNode, ScrollBar bar) {
		swipeNode.setOnSwipeRight(e -> {
			var delta = bar.getVisibleAmount();
			Platform.runLater(() -> bar.setValue(Math.max(bar.getMin(), bar.getValue() - delta)));
			e.consume();
		});
	}

	public static void setOnSwipeUp(Node swipeNode, ScrollBar bar) {
		swipeNode.setOnSwipeUp(e -> {
			var delta = bar.getVisibleAmount();
			Platform.runLater(() -> bar.setValue(Math.min(bar.getMax(), bar.getValue() + delta)));
			e.consume();
		});
	}

	public static void setOnSwipeDown(Node swipeNode, ScrollBar bar) {
		swipeNode.setOnSwipeDown(e -> {
			var delta = bar.getVisibleAmount();
			Platform.runLater(() -> bar.setValue(Math.max(bar.getMin(), bar.getValue() - delta)));
			e.consume();
		});
	}
}
