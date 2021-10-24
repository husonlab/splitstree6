/*
 *  SplitPanePresenter.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.window;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.control.SplitPane;
import javafx.util.Duration;

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

		var openCloseLeftButton = controller.getOpenCloseLeftButton();
		openCloseLeftButton.setOnAction(e -> {
			if (mainSplitPane.getDividerPositions()[0] <= 0.01)
				animateSplitPane(mainSplitPane, 300 / mainSplitPane.getWidth(), () -> openCloseLeftButton.setText("<"), true);
			else
				animateSplitPane(mainSplitPane, 0, () -> openCloseLeftButton.setText(">"), true);
		});

		var openCloseRightButton = controller.getOpenCloseRightButton();
		openCloseRightButton.setOnAction((e) -> {
			ensureTreeViewIsOpen();
			if (leftSplitPane.getDividerPositions()[0] >= 0.99)
				animateSplitPane(leftSplitPane, (leftSplitPane.getHeight() - 300) / leftSplitPane.getHeight(), () -> openCloseRightButton.setText(("<")), true);
			else
				animateSplitPane(leftSplitPane, 1.0, () -> openCloseRightButton.setText((">")), true);
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
				double[] dividerPositions = leftSplitPane.getDividerPositions();
				{
					double oldHeight = (1 - dividerPositions[0]) * o.doubleValue();
					dividerPositions[0] = 1 - oldHeight / n.doubleValue();
				}
				leftSplitPane.setDividerPositions(dividerPositions);
			}
		});

		openCloseLeft(false);
	}

	public void openCloseLeft(boolean animate) {
		var openCloseLeftButton = controller.getOpenCloseLeftButton();
		if (mainSplitPane.getDividerPositions()[0] <= 0.01)
			animateSplitPane(mainSplitPane, 300 / mainSplitPane.getWidth(), () -> openCloseLeftButton.setText("<"), animate);
		else
			animateSplitPane(mainSplitPane, 0, () -> openCloseLeftButton.setText(">"), animate);
	}

	public void ensureTreeViewIsOpen() {
		if (mainSplitPane.getDividerPositions()[0] <= 0.01)
			animateSplitPane(mainSplitPane, 300 / mainSplitPane.getWidth(), () -> controller.getOpenCloseLeftButton().setText("<"), true);
	}

	public void ensureAlgorithmsTabPaneIsOpen() {
		ensureTreeViewIsOpen();
		if (leftSplitPane.getDividerPositions()[0] >= (leftSplitPane.getHeight() - 300) / leftSplitPane.getHeight())
			animateSplitPane(leftSplitPane, (leftSplitPane.getHeight() - 300) / leftSplitPane.getHeight(), () -> controller.getOpenCloseLeftButton().setText("<"), true);
	}

	private void animateSplitPane(SplitPane splitPane, double target, Runnable runnable, boolean animate) {
		KeyValue keyValue = new KeyValue(splitPane.getDividers().get(0).positionProperty(), target);
		Timeline timeline = new Timeline(new KeyFrame(Duration.millis(animate ? 500 : 1), keyValue));
		timeline.play();
		timeline.setOnFinished((x) -> runnable.run());
	}

}
