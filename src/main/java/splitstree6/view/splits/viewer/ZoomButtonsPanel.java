/*
 *  ZoomButtonsPanel.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.view.splits.viewer;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.scene.control.Button;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import jloda.fx.icons.MaterialIcons;
import jloda.fx.util.AService;
import jloda.util.Single;


public class ZoomButtonsPanel extends Pane {
	private static AService<Boolean> service;

	private static final Single<Runnable> pending = new Single<>();

	public ZoomButtonsPanel(Button expandHorizontal, Button contractHorizontal,
							Button expandVertical, Button contractVertical,
							BooleanProperty disableHorizontal, BooleanProperty disableVertical) {
		if (service == null) {
			service = new AService<>();

			service.setCallable(() -> {
				Thread.sleep(4000);
				if (pending.isNotNull())
					Platform.runLater(pending.get());
				return true;
			});
		}

		var expandHorzonticallyButton = new Button();
		if (disableHorizontal != null)
			expandHorzonticallyButton.disableProperty().bind(disableHorizontal);
		var contractHorizontallyButton = new Button();
		if (disableHorizontal != null)
			contractHorizontallyButton.disableProperty().bind(disableHorizontal);

		MaterialIcons.setIcon(expandHorzonticallyButton, "unfold_more", "-fx-rotate: 90;", true);
		MaterialIcons.setIcon(contractHorizontallyButton, "unfold_less", "-fx-rotate: 90;", true);

		Button expandVerticallyButton = (expandVertical != null ? new Button() : null);
		if (expandVerticallyButton != null) {
			if (disableVertical != null)
				expandVerticallyButton.disableProperty().bind(disableVertical);
			MaterialIcons.setIcon(expandVerticallyButton, "unfold_more");
		}
		var contractVerticallyButton = (contractVertical != null ? new Button() : null);
		if (contractVerticallyButton != null) {
			if (disableVertical != null)
				contractVerticallyButton.disableProperty().bind(disableVertical);
			MaterialIcons.setIcon(contractVerticallyButton, "unfold_less");
		}

		var pane = this;
		if (expandVerticallyButton != null && contractVerticallyButton != null)
			pane.getChildren().add(new VBox(new HBox(expandHorzonticallyButton, contractHorizontallyButton),
					new HBox(expandVerticallyButton, contractVerticallyButton)));
		else
			pane.getChildren().add(new HBox(expandHorzonticallyButton, contractHorizontallyButton));


		addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
			show();
		});

		expandHorzonticallyButton.setOnAction(e -> {
			expandHorizontal.getOnAction().handle(e);
			service.restart();
		});

		contractHorizontallyButton.setOnAction(e -> {
			contractHorizontal.getOnAction().handle(e);
			service.restart();
		});

		if (expandVerticallyButton != null)
			expandVerticallyButton.setOnAction(e -> {
				expandVertical.getOnAction().handle(e);
				service.restart();
			});

		if (contractVerticallyButton != null)
			contractVerticallyButton.setOnAction(e -> {
				contractVertical.getOnAction().handle(e);
				service.restart();
			});

		show();
	}

	public void show() {
		setVisible(true);
		updatePending(() -> setVisible(false));
	}

	private void updatePending(Runnable task) {
		if (pending.isNotNull()) {
			service.cancel();
		}
		pending.set(task);
		service.restart();
	}
}
