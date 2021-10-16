/*
 *  TryPrinting.java Copyright (C) 2021 Daniel H. Huson
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

package splitstree6.xtra;

import javafx.application.Application;
import javafx.print.PageLayout;
import javafx.print.PrinterJob;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;

public class TryPrinting extends Application {
	private static PageLayout pageLayoutSelected;

	@Override
	public void start(Stage stage) throws Exception {


		var textArea = new TextArea("Please implement multi-page printing\n".repeat(100));
		textArea.setWrapText(true);

		var pageLayoutButton = new Button("Page Layout");
		pageLayoutButton.setOnAction(e -> {
			final PrinterJob job = PrinterJob.createPrinterJob();
			if (job.showPageSetupDialog(stage)) {
				pageLayoutSelected = (job.getJobSettings().getPageLayout());
			}
		});
		var printButton = new Button("Print");
		printButton.setOnAction(e -> printText(stage, textArea.getText()));
		var quitButton = new Button("Quit");
		quitButton.setOnAction(e -> System.exit(0));

		var borderPane = new BorderPane(textArea);
		borderPane.setBottom(new HBox(new Separator(), pageLayoutButton, new Separator(), printButton, new Separator(), quitButton));

		stage.setScene(new Scene(borderPane, 800, 800));
		stage.sizeToScene();
		stage.show();
	}

	public static void printText(Stage owner, String text) {
		final PrinterJob printerJob = PrinterJob.createPrinterJob();
		if (printerJob != null && printerJob.showPrintDialog(owner)) {

			var printArea = new TextFlow(new Text(text));
			printArea.setStyle("-fx-font-family: 'Courier New'; -fx-font-size: 10");

			PageLayout pageLayout = pageLayoutSelected != null ? pageLayoutSelected : printerJob.getJobSettings().getPageLayout();
			printArea.setMaxWidth(pageLayout.getPrintableWidth());

			if (printerJob.printPage(printArea)) {
				printerJob.endJob();
				// done printing
			} else {
				System.err.println("Print failed");
			}
		}
	}
}
