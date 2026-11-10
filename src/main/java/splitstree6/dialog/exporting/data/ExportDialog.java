/*
 *  ExportDialog.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.dialog.exporting.data;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.event.Event;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.ZoomEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.control.MenuItem;
import javafx.stage.Modality;
import javafx.stage.Stage;
import jloda.fx.util.ExtendedFXMLLoader;
import jloda.fx.util.ProgramProperties;
import splitstree6.main.Version;
import splitstree6.window.MainWindow;
import splitstree6.workflow.DataNode;

/**
 * export dialog
 * Daniel Huson, 11.21
 */
public class ExportDialog {
	/**
	 * show the export dialog
	 *
	 * @param mainWindow
	 * @param dataNode
	 */
	public static void show(MainWindow mainWindow, DataNode dataNode) {
		var loader = new ExtendedFXMLLoader<ExportDialogController>(ExportDialogController.class);
		ExportDialogController controller = loader.getController();

		// in a mobile app, the window's root pane is embedded in the app's frame, so the scene to use is the one
		// that the root pane is showing in, not the scene of the window's own stage
		var rootPane = mainWindow.getController().getRootPane();
		if (!ProgramProperties.isDesktop() && rootPane != null && rootPane.getScene() != null
			&& rootPane.getScene().getRoot() instanceof Pane host) {
			showInWindow(mainWindow, dataNode, controller, loader.getRoot(), host);
			return;
		}

		var scene = new Scene(loader.getRoot());

		var stage = new Stage();
		stage.setTitle("Export data - " + dataNode.getTitle() + " - " + Version.NAME);
		//stage.setAlwaysOnTop(true);
		stage.setScene(scene);
		stage.sizeToScene();
		stage.setX(ProgramProperties.isDesktop() ? mainWindow.getStage().getX() + 100 : 100);
		stage.setY(ProgramProperties.isDesktop() ? mainWindow.getStage().getY() + 100 : 100);

		stage.initModality(Modality.APPLICATION_MODAL);
		stage.initOwner(mainWindow.getStage());

		new ExportDialogPresenter(mainWindow, controller, stage, dataNode);
		stage.show();
	}

	/**
	 * create a menu item for this dialog
	 */
	/**
	 * On a mobile device, a second stage is neither modal nor can it be dismissed by tapping next to it. So the
	 * dialog is shown inside the main window instead, on a backdrop that covers the whole window: the backdrop
	 * keeps all taps away from the user interface below it, and a tap on the backdrop closes the dialog.
	 */
	private static void showInWindow(MainWindow mainWindow, DataNode dataNode, ExportDialogController controller, Parent dialog, Pane host) {
		var backdrop = new StackPane(dialog);
		backdrop.setStyle("-fx-background-color: rgba(0,0,0,0.35);");
		dialog.setStyle("-fx-background-color: -fx-background; -fx-background-radius: 8; -fx-border-color: -fx-box-border;"
						+ " -fx-border-radius: 8; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 12, 0.2, 0, 2);");
		if (dialog instanceof Region region) {
			region.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
			// leave room for the on-screen keyboard: place the dialog in the upper part of the window
			StackPane.setAlignment(region, Pos.TOP_CENTER);
			StackPane.setMargin(region, new Insets(80, 10, 10, 10));
		}

		Runnable close = () -> host.getChildren().remove(backdrop);
		backdrop.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
			if (e.getTarget() == backdrop)
				close.run();
			e.consume();
		});
		// nothing gets through to the window below
		backdrop.addEventHandler(MouseEvent.ANY, Event::consume);
		backdrop.addEventHandler(ScrollEvent.ANY, Event::consume);
		backdrop.addEventHandler(ZoomEvent.ANY, Event::consume);

		new ExportDialogPresenter(mainWindow, controller, close, dataNode);

		if (host instanceof AnchorPane) {
			AnchorPane.setTopAnchor(backdrop, 0.0);
			AnchorPane.setBottomAnchor(backdrop, 0.0);
			AnchorPane.setLeftAnchor(backdrop, 0.0);
			AnchorPane.setRightAnchor(backdrop, 0.0);
		} else if (!(host instanceof StackPane)) {
			backdrop.setManaged(false);
			backdrop.resizeRelocate(0, 0, host.getWidth(), host.getHeight());
			host.widthProperty().addListener((v, o, n) -> backdrop.resizeRelocate(0, 0, host.getWidth(), host.getHeight()));
			host.heightProperty().addListener((v, o, n) -> backdrop.resizeRelocate(0, 0, host.getWidth(), host.getHeight()));
		}
		host.getChildren().add(backdrop);
	}

	public static MenuItem createMenuItem(MainWindow mainWindow, DataNode dataNode, ReadOnlyBooleanProperty empty) {
		var menuItem = new MenuItem("Export data...");
		menuItem.setOnAction(e -> ExportDialog.show(mainWindow, dataNode));
		if (dataNode == null || empty == null)
			menuItem.disableProperty().bind(new SimpleBooleanProperty(true));
		else
			menuItem.disableProperty().bind(empty);
		return menuItem;
	}
}
