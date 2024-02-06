/*
 *  QRViewUtils.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.qr;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.qrcode.QRCodeWriter;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;
import jloda.fx.util.BasicFX;
import jloda.fx.util.DraggableLabel;
import jloda.fx.util.ProgramExecutorService;
import jloda.util.Basic;
import jloda.util.StringUtils;
import splitstree6.main.SplitsTree6;

import java.util.function.Function;

/**
 * utilities for setting up a QR view
 * Daniel Huson, 2.2024
 */
public class QRViewUtils {

	/**
	 * setup QR code view
	 *
	 * @param anchorPane          container
	 * @param source              the source property, usually either phylotree or block of splits
	 * @param stringFunction      creates the string for the given source
	 * @param qrImageViewProperty this will contain the qr image view
	 * @param show                show or hide the image
	 * @param <T>                 source type
	 */
	public static <T> void setup(AnchorPane anchorPane, ReadOnlyObjectProperty<T> source, Function<T, String> stringFunction, ObjectProperty<ImageView> qrImageViewProperty, ReadOnlyBooleanProperty show) {
		var qrImageView = new ImageView();
		qrImageViewProperty.set(qrImageView);

		qrImageView.setId("qr");
		qrImageView.setPreserveRatio(true);
		qrImageView.setFitHeight(256);

		var copyTextMenuItem = new MenuItem("Copy Text");
		var copyImageMenuItem = new MenuItem("Copy Image");
		var contextMenu = new ContextMenu(copyTextMenuItem, copyImageMenuItem);

		qrImageView.setOnContextMenuRequested(e -> {
			var sourceValue = source.get();
			if (sourceValue != null) {
				var stringValue = stringFunction.apply(sourceValue);
				if (stringValue != null) {
					copyTextMenuItem.setOnAction(a -> {
						var content = new ClipboardContent();
						content.putString(stringValue);
						Clipboard.getSystemClipboard().setContent(content);
					});
					copyImageMenuItem.setOnAction(x -> {
						var content = new ClipboardContent();
						content.putImage(qrImageView.getImage());
						Clipboard.getSystemClipboard().setContent(content);
					});
					contextMenu.show(qrImageView, e.getScreenX(), e.getScreenY());
					ProgramExecutorService.submit(3000, () -> Platform.runLater(contextMenu::hide));
				}
			}
		});
		if (SplitsTree6.isDesktop()) {
			qrImageView.setOnScroll(e -> {
				if (e.getDeltaY() > 0) {
					if (qrImageView.getScaleX() < 5) {
						qrImageView.setScaleX(1.1 * qrImageView.getScaleX());
						qrImageView.setScaleY(1.1 * qrImageView.getScaleY());
					}
				} else if (e.getDeltaY() < 0) {
					if (qrImageView.getScaleX() > 0.2) {
						qrImageView.setScaleX(1 / 1.1 * qrImageView.getScaleX());
						qrImageView.setScaleY(1 / 1.1 * qrImageView.getScaleY());
					}
				}
				e.consume();
			});
		} else {
			qrImageView.setOnZoom(e -> {
				if ((e.getZoomFactor() > 1 && e.getZoomFactor() * qrImageView.getScaleX() < 5)
					|| (e.getZoomFactor() < 1 && e.getZoomFactor() * qrImageView.getScaleX() > 0.2)) {
					qrImageView.setScaleX(e.getZoomFactor() * qrImageView.getScaleX());
					qrImageView.setScaleY(e.getZoomFactor() * qrImageView.getScaleY());
				}
				e.consume();
			});
		}
		AnchorPane.setBottomAnchor(qrImageView, 20.0);
		AnchorPane.setLeftAnchor(qrImageView, 20.0);
		DraggableLabel.makeDraggable(qrImageView);

		show.addListener((v, o, n) -> {
			anchorPane.getChildren().removeAll(BasicFX.findRecursively(anchorPane, a -> a.getId() != null && a.getId().equals("qr")));
			if (n) {
				var string = stringFunction.apply(source.get());
				if (string != null)
					Tooltip.install(qrImageView, new Tooltip(StringUtils.abbreviateDotDotDot(string, 100)));
				else Tooltip.install(qrImageView, null);
				qrImageView.setImage(createImage(string, 1024, 1024));
				anchorPane.getChildren().add(1, qrImageView);
			}
		});

		source.addListener((v, o, n) -> {
			if (n == null)
				qrImageView.setImage(null);
			else if (anchorPane.getChildren().contains(qrImageView)) {
				var string = stringFunction.apply(n);
				if (string != null)
					Tooltip.install(qrImageView, new Tooltip(StringUtils.abbreviateDotDotDot(string, 100)));
				else Tooltip.install(qrImageView, null);
				qrImageView.setImage(createImage(string, 1024, 1024));
			}
		});
	}

	public static Image createImage(String text, int width, int height) {
		try {
			var image = new WritableImage(width, height);
			if (text != null && !text.isBlank()) {
				var qrCodeWriter = new QRCodeWriter();
				var bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, width, height);
				var pixelWriter = image.getPixelWriter();

				for (int y = 0; y < height; y++) {
					for (int x = 0; x < width; x++) {
						pixelWriter.setColor(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
					}
				}
			}
			return image;
		} catch (Exception e) {
			Basic.caught(e);
		}
		return null;
	}

}
