/*
 * SplitPanePresenter.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.window;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.control.SplitPane;
import javafx.util.Duration;
import jloda.fx.util.ProgramProperties;
import jloda.fx.util.RunAfterAWhile;

/**
 * split pane presenter
 * Daniel Huson, 10.2021
 */
public class SplitPanePresenter {
	private final static double SIDE_BAR_DEFAULT_WIDTH = 250.0;
	private final static double BOTTOM_BAR_DEFAULT_HEIGHT = 250.0;

	private final MainWindowController controller;
	private final SplitPane leftSplitPane;
	private final SplitPane mainSplitPane;

	private final DoubleProperty sideBarWidth = new SimpleDoubleProperty(this, "sideBarWidth", SIDE_BAR_DEFAULT_WIDTH);


	public SplitPanePresenter(MainWindowController controller) {
		this.controller = controller;
		this.mainSplitPane = controller.getMainSplitPane();
		this.leftSplitPane = controller.getLeftSplitPane();


		if (false) {
			ProgramProperties.track(sideBarWidth, SIDE_BAR_DEFAULT_WIDTH);
			DoubleProperty bottomBarHeight = new SimpleDoubleProperty(this, "bottomBarHeight", BOTTOM_BAR_DEFAULT_HEIGHT);
			ProgramProperties.track(bottomBarHeight, BOTTOM_BAR_DEFAULT_HEIGHT);
		}

		var mainSplitPaneDividerChanging = new SimpleBooleanProperty(false);

		controller.getShowWorkflowTreeCheckButton().selectedProperty().addListener((v, o, n) -> {
			if (!mainSplitPaneDividerChanging.get()) {
				try {
					if (n) {
						mainSplitPaneDividerChanging.set(true);
						mainSplitPane.setDividerPositions(sideBarWidth.get() / mainSplitPane.getWidth());
					} else {
						var pos = mainSplitPane.getDividerPositions()[0] * mainSplitPane.getWidth();
						if (pos > 0)
							sideBarWidth.set(pos);
						mainSplitPane.setDividerPositions(0.0);
					}
				} finally {
					mainSplitPaneDividerChanging.set(false);
				}
			}
		});

		mainSplitPane.getDividers().get(0).positionProperty().addListener((v, o, n) -> {
			RunAfterAWhile.applyInFXThread(mainSplitPaneDividerChanging, () -> {
						if (!mainSplitPaneDividerChanging.get()) {
							try {
								mainSplitPaneDividerChanging.set(true);
								controller.getShowWorkflowTreeCheckButton().setSelected(n.doubleValue() >= 0.01);
							} finally {
								mainSplitPaneDividerChanging.set(false);
							}
						}
					}
			);
		});

		Platform.runLater(() -> {
			mainSplitPane.setDividerPositions(sideBarWidth.get() / mainSplitPane.getWidth());
		});

		mainSplitPane.widthProperty().addListener((c, o, n) -> {
			if (n.doubleValue() > 0) {
				double[] dividerPositions = mainSplitPane.getDividerPositions();
				{
					double oldWidth = dividerPositions[0] * o.doubleValue();
					dividerPositions[0] = oldWidth / n.doubleValue();
				}
				mainSplitPane.setDividerPositions(dividerPositions);
			}
		});

		mainSplitPane.heightProperty().addListener((c, o, n) -> {
			if (n.doubleValue() > 0) {
				var dividerPositions = leftSplitPane.getDividerPositions();
				if (dividerPositions[0] <= 0.05)
					dividerPositions[0] = 0;
				else if (dividerPositions[0] >= 0.95)
					dividerPositions[0] = 1;
				else {
					var oldHeight = (1 - dividerPositions[0]) * o.doubleValue();
					dividerPositions[0] = 1 - oldHeight / n.doubleValue();
				}
				leftSplitPane.setDividerPositions(dividerPositions);
			}
		});

		controller.getAlgorithmTabPane().getTabs().addListener((InvalidationListener) e -> {
			if (controller.getAlgorithmTabPane().getTabs().isEmpty())
				ensureAlgorithmsTabPaneIsClosed();
		});
	}

	public void ensureTreeViewIsOpen(boolean animate) {
		if (mainSplitPane.getDividerPositions()[0] <= 0.01)
			animateSplitPane(mainSplitPane, SIDE_BAR_DEFAULT_WIDTH / mainSplitPane.getWidth(), () -> {
			}, animate);
		Platform.runLater(() -> leftSplitPane.setDividerPositions(1.0));
	}


	public void ensureAlgorithmsTabPaneIsOpen(Runnable runAfterward) {
		if (mainSplitPane.getDividerPositions()[0] <= 0.01)
			animateSplitPane(mainSplitPane, SIDE_BAR_DEFAULT_WIDTH / mainSplitPane.getWidth(), null, false);
		if (leftSplitPane.getDividerPositions()[0] >= (leftSplitPane.getHeight() - BOTTOM_BAR_DEFAULT_HEIGHT) / leftSplitPane.getHeight())
			animateSplitPane(leftSplitPane, (leftSplitPane.getHeight() - BOTTOM_BAR_DEFAULT_HEIGHT) / leftSplitPane.getHeight(), runAfterward, true);
		else
			runAfterward.run();
	}

	public void ensureAlgorithmsTabPaneIsClosed() {
		animateSplitPane(leftSplitPane, 1.0, null, true);
	}

	private void animateSplitPane(SplitPane splitPane, double target, Runnable runnable, boolean animate) {
		var keyValue = new KeyValue(splitPane.getDividers().get(0).positionProperty(), target);
		var timeline = new Timeline(new KeyFrame(Duration.millis(animate ? 500 : 1), keyValue));
		if (runnable != null)
			timeline.setOnFinished(x -> runnable.run());
		timeline.play();
	}
}
