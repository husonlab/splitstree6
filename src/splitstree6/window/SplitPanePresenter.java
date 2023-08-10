/*
 * SplitPanePresenter.java Copyright (C) 2023 Daniel H. Huson
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
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.control.SplitPane;
import javafx.util.Duration;
import jloda.fx.util.BasicFX;
import jloda.fx.util.ProgramProperties;

/**
 * split pane presenter
 * Daniel Huson, 10.2021
 */
public class SplitPanePresenter {
	private final MainWindowController controller;
	private final SplitPane leftSplitPane;
	private final SplitPane mainSplitPane;

	public SplitPanePresenter(MainWindowController controller) {
		this.controller = controller;
		this.mainSplitPane = controller.getMainSplitPane();
		this.leftSplitPane = controller.getLeftSplitPane();
		var sideBarWidth = new SimpleDoubleProperty(this, "sideBarWidth");
		var bottomBarHeight = new SimpleDoubleProperty(this, "bottomBarHeight");

		ProgramProperties.track(sideBarWidth, 300.0);
		ProgramProperties.track(bottomBarHeight, 250.0);

		var openCloseLeftButton = controller.getOpenCloseLeftButton();
		openCloseLeftButton.setOnAction(e -> {
			if (true) {
				if (mainSplitPane.getDividerPositions()[0] <= 0.01) {
					mainSplitPane.setDividerPositions(sideBarWidth.get() / mainSplitPane.getWidth());
					controller.getOpenCloseLeftButton().setText("<");
				} else {
					var pos = mainSplitPane.getDividerPositions()[0] * mainSplitPane.getWidth();
					if (pos > 0)
						sideBarWidth.set(pos);
					mainSplitPane.setDividerPositions(0.0);
					controller.getOpenCloseLeftButton().setText(">");
				}
			} else {
				if (mainSplitPane.getDividerPositions()[0] <= 0.01)
					animateSplitPane(mainSplitPane, 300 / mainSplitPane.getWidth(), () -> openCloseLeftButton.setText("<"), true);
				else
					animateSplitPane(mainSplitPane, 0, () -> openCloseLeftButton.setText(">"), true);
			}
		});
		Platform.runLater(() -> {
			mainSplitPane.setDividerPositions(sideBarWidth.get() / mainSplitPane.getWidth());
			controller.getOpenCloseLeftButton().setText("<");
		});

		var openCloseRightButton = controller.getOpenCloseRightButton();
		openCloseRightButton.setOnAction(e -> {
			if (false) ensureTreeViewIsOpen(false);
			if (true) {
				if (leftSplitPane.getDividerPositions()[0] >= 0.99) {
					var pos = (leftSplitPane.getHeight() - bottomBarHeight.get()) / leftSplitPane.getHeight();
					leftSplitPane.getDividers().get(0).setPosition(pos);
					openCloseRightButton.setText("<");
				} else {
					bottomBarHeight.set((1.0 - leftSplitPane.getDividerPositions()[0]) * leftSplitPane.getHeight());
					leftSplitPane.getDividers().get(0).setPosition(1.0);
					openCloseRightButton.setText(">");
				}
			} else {
				if (leftSplitPane.getDividerPositions()[0] >= 0.99)
					animateSplitPane(leftSplitPane, (leftSplitPane.getHeight() - 300) / leftSplitPane.getHeight(), () -> openCloseRightButton.setText("<"), true);
				else
					animateSplitPane(leftSplitPane, 1.0, () -> openCloseRightButton.setText(">"), true);
			}
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

	}

	public void openCloseLeft(boolean animate) {
		var openCloseLeftButton = controller.getOpenCloseLeftButton();
		if (mainSplitPane.getDividerPositions()[0] <= 0.01)
			animateSplitPane(mainSplitPane, 300 / mainSplitPane.getWidth(), () -> openCloseLeftButton.setText("<"), animate);
		else
			animateSplitPane(mainSplitPane, 0, () -> openCloseLeftButton.setText(">"), animate);
	}

	public void ensureTreeViewIsOpen(boolean animate) {
		if (mainSplitPane.getDividerPositions()[0] <= 0.01)
			animateSplitPane(mainSplitPane, 300 / mainSplitPane.getWidth(), () -> controller.getOpenCloseLeftButton().setText("<"), animate);
		Platform.runLater(() -> leftSplitPane.setDividerPositions(1.0));
	}


	public void ensureAlgorithmsTabPaneIsOpen(Runnable runAfterward) {
		if (mainSplitPane.getDividerPositions()[0] <= 0.01)
			animateSplitPane(mainSplitPane, 300 / mainSplitPane.getWidth(), () -> {
				controller.getOpenCloseLeftButton().setText("<");
				runAfterward.run();
			}, false);
		else if (leftSplitPane.getDividerPositions()[0] >= (leftSplitPane.getHeight() - 300) / leftSplitPane.getHeight())
			animateSplitPane(leftSplitPane, (leftSplitPane.getHeight() - 300) / leftSplitPane.getHeight(), () -> {
				controller.getOpenCloseLeftButton().setText("<");
				runAfterward.run();
			}, true);
		else
			runAfterward.run();
	}

	private void animateSplitPane(SplitPane splitPane, double target, Runnable runnable, boolean animate) {
		KeyValue keyValue = new KeyValue(splitPane.getDividers().get(0).positionProperty(), target);
		Timeline timeline = new Timeline(new KeyFrame(Duration.millis(animate ? 500 : 1), keyValue));
		timeline.play();
		timeline.setOnFinished(x -> runnable.run());
	}
}
