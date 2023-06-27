/*
 *  SaveToSVG.java Copyright (C) 2023 Daniel H. Huson
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

import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.chart.Chart;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.TextInputControl;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.*;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import jloda.fx.control.RichTextLabel;
import jloda.fx.util.BasicFX;
import jloda.fx.window.MainWindowManager;
import jloda.thirdparty.PngEncoderFX;
import jloda.util.Basic;
import org.fxmisc.richtext.TextExt;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.text.DateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;

import static splitstree6.xtra.SaveToPDF.*;

public class SaveToSVG {
	/**
	 * draws given pane to a file in PDF format
	 *
	 * @param pane the pane
	 * @param file the file
	 * @throws IOException
	 */
	public static void apply(Node pane, File file) throws IOException {
		if (file.exists())
			Files.delete(file.toPath());

		var svgBuilder = new StringBuilder();

		{
			var currentDateTime = LocalDateTime.now();
			var formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
			var formattedDateTime = currentDateTime.format(formatter);
			svgBuilder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!-- Creator: %s -->\n<!-- Creation Date: %s -->\n".formatted(System.getProperty("user.name"), formattedDateTime));
		}

		double svgMinX;
		double svgMaxX;
		double svgWidth;

		double svgMinY;
		double svgMaxY;
		double svgHeight;
		{

			var bbox = computeBoundingBox(pane);
			svgBuilder.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"%.1f\" height=\"%.1f\">\n".formatted(bbox.getWidth(), bbox.getHeight()));

			svgMinX = bbox.getMinX();
			svgMaxX = bbox.getMaxX();
			svgWidth = bbox.getWidth();
			svgMinY = bbox.getMinY();
			svgMaxY = bbox.getMaxY();
			svgHeight = bbox.getHeight();
		}

		var paneWidth = pane.getBoundsInLocal().getWidth();
		var paneHeight = pane.getBoundsInLocal().getHeight();

		var factor = Math.min(svgWidth / paneWidth, svgHeight / paneHeight);

		Function<Double, Double> ps = s -> (s * factor);
		Function<Double, Double> px = x -> (x * factor + svgMinX);
		Function<Double, Double> py = y -> (y * factor + svgMinY);

		if (MainWindowManager.isUseDarkTheme()) {
			appendRect(svgBuilder, -5, -5, svgWidth + 10, svgHeight + 10, 0, Collections.EMPTY_LIST, Color.TRANSPARENT, Color.web("rgb(60, 63, 65)"));
		}

		for (var n : BasicFX.getAllRecursively(pane, n -> true)) {
			// System.err.println("n: " + n.getClass().getSimpleName());
			if (isNodeVisible(n)) {
				try {
					if (n instanceof Line line) {
						var x1 = px.apply(pane.sceneToLocal(line.localToScene(line.getStartX(), line.getStartY())).getX());
						var y1 = py.apply(pane.sceneToLocal(line.localToScene(line.getStartX(), line.getStartY())).getY());
						var x2 = px.apply(pane.sceneToLocal(line.localToScene(line.getEndX(), line.getEndY())).getX());
						var y2 = py.apply(pane.sceneToLocal(line.localToScene(line.getEndX(), line.getEndY())).getY());
						appendLine(svgBuilder, x1, y1, x2, y2, line.getStrokeWidth(), line.getStrokeDashArray(), line.getStroke());
					} else if (n instanceof Rectangle rectangle) {
						var bounds = pane.sceneToLocal(rectangle.localToScene(rectangle.getBoundsInLocal()));
						appendRect(svgBuilder, px.apply(bounds.getMinX()), py.apply(bounds.getMinY()), ps.apply(bounds.getWidth()), ps.apply(bounds.getHeight()), rectangle.getStrokeWidth(), rectangle.getStrokeDashArray(), rectangle.getStroke(), rectangle.getFill());
					} else if (n instanceof Circle circle) {
						var bounds = pane.sceneToLocal(circle.localToScene(circle.getBoundsInLocal()));
						var r = ps.apply(0.5 * bounds.getHeight());
						var x = px.apply(bounds.getCenterX());
						var y = py.apply(bounds.getCenterY());
						appendCircle(svgBuilder, x, y, r, circle.getStrokeWidth(), circle.getStrokeDashArray(), circle.getStroke(), circle.getFill());
					} else if (n instanceof Ellipse ellipse) {
						var bounds = pane.sceneToLocal(ellipse.localToScene(ellipse.getBoundsInLocal()));
						var rx = ps.apply(ellipse.getRadiusX());
						var ry = ps.apply(ellipse.getRadiusY());
						var x = px.apply(bounds.getCenterX());
						var y = py.apply(bounds.getCenterY());
						appendEllipse(svgBuilder, x, y, rx, ry, ellipse.getStrokeWidth(), ellipse.getStrokeDashArray(), ellipse.getStroke(), ellipse.getFill());
					} else if (n instanceof QuadCurve curve) {
						var sX = px.apply(pane.sceneToLocal(curve.localToScene(curve.getStartX(), curve.getStartY())).getX());
						var sY = py.apply(pane.sceneToLocal(curve.localToScene(curve.getStartX(), curve.getStartY())).getY());
						var cX = px.apply(pane.sceneToLocal(curve.localToScene(curve.getControlX(), curve.getControlY())).getX());
						var cY = py.apply(pane.sceneToLocal(curve.localToScene(curve.getControlX(), curve.getControlY())).getY());
						var tX = px.apply(pane.sceneToLocal(curve.localToScene(curve.getEndX(), curve.getEndY())).getX());
						var tY = py.apply(pane.sceneToLocal(curve.localToScene(curve.getEndX(), curve.getEndY())).getY());
						appendQuadCurve(svgBuilder, sX, sY, cX, cY, tX, tY, curve.getStrokeWidth(), curve.getStrokeDashArray(), curve.getStroke());
					} else if (n instanceof CubicCurve curve) {
						var sX = px.apply(pane.sceneToLocal(curve.localToScene(curve.getStartX(), curve.getStartY())).getX());
						var sY = py.apply(pane.sceneToLocal(curve.localToScene(curve.getStartX(), curve.getStartY())).getY());
						var c1X = px.apply(pane.sceneToLocal(curve.localToScene(curve.getControlX1(), curve.getControlY1())).getX());
						var c1Y = py.apply(pane.sceneToLocal(curve.localToScene(curve.getControlX1(), curve.getControlY1())).getY());
						var c2X = px.apply(pane.sceneToLocal(curve.localToScene(curve.getControlX2(), curve.getControlY2())).getX());
						var c2Y = py.apply(pane.sceneToLocal(curve.localToScene(curve.getControlX2(), curve.getControlY2())).getY());
						var tX = px.apply(pane.sceneToLocal(curve.localToScene(curve.getEndX(), curve.getEndY())).getX());
						var tY = py.apply(pane.sceneToLocal(curve.localToScene(curve.getEndX(), curve.getEndY())).getY());
						appendCubicCurve(svgBuilder, sX, sY, c1X, c1Y, c2X, c2Y, tX, tY, curve.getStrokeWidth(), curve.getStrokeDashArray(), curve.getStroke());
					} else if (n instanceof Path path) {
						if (containedInText(path))
							continue;
						var local = new Point2D(0, 0);
						svgBuilder.append("<path d=\"");
						try {
							for (var element : path.getElements()) {
								System.err.println("Element: " + element);
								if (element instanceof MoveTo moveTo) {
									local = new Point2D(moveTo.getX(), moveTo.getY());
									var t = pane.sceneToLocal(path.localToScene(local.getX(), local.getY()));
									svgBuilder.append(" M%.2f,%.2f".formatted(px.apply(t.getX()), py.apply(t.getY())));
								} else if (element instanceof LineTo lineTo) {
									local = new Point2D(lineTo.getX(), lineTo.getY());
									var t = pane.sceneToLocal(path.localToScene(local.getX(), local.getY()));
									svgBuilder.append(" L%.2f,%.2f".formatted(px.apply(t.getX()), py.apply(t.getY())));
								} else if (element instanceof HLineTo lineTo) {
									local = new Point2D(lineTo.getX(), local.getY());
									var t = pane.sceneToLocal(path.localToScene(local.getX(), local.getY()));
									svgBuilder.append(" L%.2f,%.2f".formatted(px.apply(t.getX()), py.apply(t.getY())));
								} else if (element instanceof VLineTo lineTo) {
									local = new Point2D(local.getX(), lineTo.getY());
									var t = pane.sceneToLocal(path.localToScene(local.getX(), local.getY()));
									svgBuilder.append(" L%.2f,%.2f".formatted(px.apply(t.getX()), py.apply(t.getY())));
								} else if (element instanceof ArcTo arcTo) {
									local = new Point2D(arcTo.getX(), arcTo.getY());
									var t = pane.sceneToLocal(path.localToScene(local.getX(), local.getY()));
									double radiusX = arcTo.getRadiusX();
									double radiusY = arcTo.getRadiusY();
									double xAxisRotation = arcTo.getXAxisRotation();
									boolean largeArcFlag = arcTo.isLargeArcFlag();
									boolean sweepFlag = arcTo.isSweepFlag();
									svgBuilder.append(" A%.2f,%.2f %.2f %d,%d %.2f,%.2f".formatted(radiusX, radiusY, xAxisRotation, (largeArcFlag ? 1 : 0), (sweepFlag ? 1 : 0), px.apply(t.getX()), py.apply(t.getY())));
								} else if (element instanceof QuadCurveTo curveTo) {
									var c = pane.sceneToLocal(path.localToScene(curveTo.getControlX(), curveTo.getControlY()));
									var t = pane.sceneToLocal(path.localToScene(curveTo.getX(), curveTo.getY()));
									svgBuilder.append(" Q%.2f,%.2f %.2f,%.2f".formatted(px.apply(c.getX()), py.apply(c.getY()), px.apply(t.getX()), py.apply(t.getY())));
								} else if (element instanceof CubicCurveTo curveTo) {
									var c1 = pane.sceneToLocal(path.localToScene(curveTo.getControlX1(), curveTo.getControlY1()));
									var c2 = pane.sceneToLocal(path.localToScene(curveTo.getControlX2(), curveTo.getControlY2()));
									var t = pane.sceneToLocal(path.localToScene(curveTo.getX(), curveTo.getY()));
									svgBuilder.append(" C%.2f,%.2f %.2f,%.2f %.2f,%.2f".formatted(px.apply(c1.getX()), py.apply(c1.getY()), px.apply(c2.getX()), py.apply(c2.getY()), px.apply(t.getX()), py.apply(t.getY())));
								}
							}
						} finally {
							svgBuilder.append("\"");
							if (path.getStroke() instanceof Color color)
								svgBuilder.append(" stroke=\"%s\"".formatted(asSvgColor(color)));
							svgBuilder.append(" stroke-width=\"%.2f\"".formatted(path.getStrokeWidth()));
							svgBuilder.append("/>\n");
						}
					} else if (n instanceof Polygon polygon) {
						var points = new ArrayList<Point2D>();
						for (var i = 0; i < polygon.getPoints().size(); i += 2) {
							var x = px.apply(pane.sceneToLocal(polygon.localToScene(polygon.getPoints().get(i), polygon.getPoints().get(i + 1))).getX());
							var y = py.apply(pane.sceneToLocal(polygon.localToScene(polygon.getPoints().get(i), polygon.getPoints().get(i + 1))).getY());
							points.add(new Point2D(x, y));
						}
						appendPolygon(svgBuilder, points, polygon.getStrokeWidth(), polygon.getStrokeDashArray(), polygon.getStroke(), polygon.getFill());
					} else if (n instanceof Text text) {
						if (!text.getText().isBlank()) {
							double screenAngle = 360 - getAngleOnScreen(text); // because y axis points upward in PDF
							var localBounds = text.getBoundsInLocal();
							var origX = localBounds.getMinX();
							var origY = localBounds.getMinY() + 0.87f * localBounds.getHeight();
							var rotateAnchorX = pane.sceneToLocal(text.localToScene(origX, origY)).getX();
							var rotateAnchorY = pane.sceneToLocal(text.localToScene(origX, origY)).getY();
							if (isMirrored(text)) // todo: this is untested:
								screenAngle = 360 - screenAngle;
							var fontHeight = ps.apply(text.getFont().getSize());
							var altFontHeight = ps.apply(0.87 * text.localToScreen(localBounds).getHeight());
							if (!(text instanceof TextExt) && Math.abs(fontHeight - altFontHeight) > 2)
								fontHeight = altFontHeight;
							appendText(svgBuilder, px.apply(rotateAnchorX), py.apply(rotateAnchorY), screenAngle, text.getText(), text.getFont(), fontHeight, text.getFill());
						}
					} else if (n instanceof ImageView imageView) {
						var bounds = pane.sceneToLocal(imageView.localToScene(imageView.getBoundsInLocal()));
						var x = px.apply(bounds.getMinX());
						var width = ps.apply(bounds.getWidth());
						var y = py.apply(bounds.getMinY());
						var height = ps.apply(bounds.getHeight());
						appendImage(svgBuilder, x, y, width, height, imageView.getImage());
					} else if (n instanceof Shape3D || n instanceof Canvas || n instanceof Chart) {
						var parameters = new SnapshotParameters();
						parameters.setFill(Color.TRANSPARENT);
						var snapShot = n.snapshot(parameters, null);
						var bounds = pane.sceneToLocal(n.localToScene(n.getBoundsInLocal()));
						var x = px.apply(bounds.getMinX());
						var width = ps.apply(bounds.getWidth());
						var y = py.apply(bounds.getMinY());
						var height = ps.apply(bounds.getHeight());
						appendImage(svgBuilder, x, y, width, height, snapShot);
					}
				} catch (Exception ex) {
					Basic.caught(ex);
				}
			}
		}
		svgBuilder.append("</svg>\n");
		try (var writer = new FileWriter(file)) {
			writer.write(svgBuilder.toString());
		}
	}

	public static void appendLine(StringBuilder svgBuilder, double x1, double y1, double x2, double y2, double strokeWidth, List<Double> strokeDashArray, Paint stroke) {
		svgBuilder.append("<line x1=\"%.2f\" y1=\"%.2f\" x2=\"%.2f\" y2=\"%.2f\"".formatted(x1, y1, x2, y2));
		if (stroke instanceof Color color && stroke != Color.TRANSPARENT)
			svgBuilder.append(" stroke=\"%s\"".formatted(asSvgColor(color)));
		else
			svgBuilder.append(" stroke=\"none\"");
		svgBuilder.append(" stroke-width=\"%.2f\"".formatted(strokeWidth));
		svgBuilder.append("/>\n");
	}

	public static void appendRect(StringBuilder svgBuilder, double x, double y, double width, double height, double strokeWidth, List<Double> strokeDashArray, Paint stroke, Paint fill) {
		svgBuilder.append("<rect x=\"%.2f\" y=\"%.2f\" width=\"%.2f\" height=\"%.2f\"".formatted(x, y, width, height));
		if (stroke instanceof Color color && stroke != Color.TRANSPARENT)
			svgBuilder.append(" stroke=\"%s\"".formatted(asSvgColor(color)));
		else
			svgBuilder.append(" stroke=\"none\"");
		svgBuilder.append(" stroke-width=\"%.2f\"".formatted(strokeWidth));
		if (fill instanceof Color color && color != Color.TRANSPARENT)
			svgBuilder.append(" fill=\"%s\"".formatted(asSvgColor(color)));
		else
			svgBuilder.append(" fill=\"none\"");
		svgBuilder.append("/>\n");
	}

	public static void appendCircle(StringBuilder svgBuilder, double x, double y, double radius, double strokeWidth, List<Double> strokeDashArray, Paint stroke, Paint fill) {
		svgBuilder.append("<circle cx=\"%.2f\" cy=\"%.2f\" r=\"%.2f\"".formatted(x, y, radius));
		if (stroke instanceof Color color && stroke != Color.TRANSPARENT)
			svgBuilder.append(" stroke=\"%s\"".formatted(asSvgColor(color)));
		else
			svgBuilder.append(" stroke=\"none\"");
		svgBuilder.append(" stroke-width=\"%.2f\"".formatted(strokeWidth));
		if (fill instanceof Color color && color != Color.TRANSPARENT)
			svgBuilder.append(" fill=\"%s\"".formatted(asSvgColor(color)));
		else
			svgBuilder.append(" fill=\"none\"");
		svgBuilder.append("/>\n");
	}

	public static void appendEllipse(StringBuilder svgBuilder, double x, double y, double rx, double ry, double strokeWidth, List<Double> strokeDashArray, Paint stroke, Paint fill) {
		svgBuilder.append("<ellipse cx=\"%.2f\" cy=\"%.2f\" rx=\"%.2f\" ry=\"%.2f\"".formatted(x, y, rx, ry));
		if (stroke instanceof Color color && stroke != Color.TRANSPARENT)
			svgBuilder.append(" stroke=\"%s\"".formatted(asSvgColor(color)));
		else
			svgBuilder.append(" stroke=\"none\"");
		svgBuilder.append(" stroke-width=\"%.2f\"".formatted(strokeWidth));
		if (fill instanceof Color color && color != Color.TRANSPARENT)
			svgBuilder.append(" fill=\"%s\"".formatted(asSvgColor(color)));
		else
			svgBuilder.append(" fill=\"none\"");
		svgBuilder.append("/>\n");
	}

	public static void appendQuadCurve(StringBuilder svgBuilder, Double sX, Double sY, Double cX, Double cY, Double tX, Double tY, double strokeWidth, List<Double> strokeDashArray, Paint stroke) {
		svgBuilder.append("<path d=\"M%.2f,%.2f Q%.2f,%.2f %.2f,%.2f\"".formatted(sX, sY, cX, cY, tX, tY));
		if (stroke instanceof Color color && stroke != Color.TRANSPARENT)
			svgBuilder.append(" stroke=\"%s\"".formatted(asSvgColor(color)));
		else
			svgBuilder.append(" stroke=\"none\"");
		svgBuilder.append(" stroke-width=\"%.2f\"".formatted(strokeWidth));
		svgBuilder.append("/>\n");
	}

	public static void appendCubicCurve(StringBuilder svgBuilder, Double sX, Double sY, Double c1X, Double c1Y, Double c2X, Double c2Y, Double tX, Double tY, double strokeWidth, List<Double> strokeDashArray, Paint stroke) {
		svgBuilder.append("<path d=\"M%.2f,%.2f C%.2f,%.2f %.2f,%.2f %.2f,%.2f\"".formatted(sX, sY, c1X, c1Y, c2X, c2Y, tX, tY));
		if (stroke instanceof Color color && stroke != Color.TRANSPARENT)
			svgBuilder.append(" stroke=\"%s\"".formatted(asSvgColor(color)));
		else
			svgBuilder.append(" stroke=\"none\"");
		svgBuilder.append(" stroke-width=\"%.2f\"".formatted(strokeWidth));
		svgBuilder.append("/>\n");
	}

	private static void appendPolygon(StringBuilder svgBuilder, ArrayList<Point2D> points, double strokeWidth, List<Double> strokeDashArray, Paint stroke, Paint fill) {
		svgBuilder.append("<polygon points=\"");
		for (var point : points) {
			svgBuilder.append(" %.2f,%.2f".formatted(point.getX(), point.getY()));
		}
		svgBuilder.append("\"");

		if (stroke instanceof Color color && stroke != Color.TRANSPARENT)
			svgBuilder.append(" stroke=\"%s\"".formatted(asSvgColor(color)));
		else
			svgBuilder.append(" stroke=\"none\"");
		svgBuilder.append(" stroke-width=\"%.2f\"".formatted(strokeWidth));
		if (fill instanceof Color color && color != Color.TRANSPARENT)
			svgBuilder.append(" fill=\"%s\"".formatted(asSvgColor(color)));
		else
			svgBuilder.append(" fill=\"none\"");
		svgBuilder.append("/>\n");
	}

	public static void appendText(StringBuilder svgBuilder, Double x, Double y, double angle, String text, Font font, Double fontSize, Paint textFill) {
		svgBuilder.append("<text x=\"%.2f\" y=\"%.2f\"".formatted(x, y));
		svgBuilder.append(" font-family=\"%s\"".formatted(getSVGFontName(font.getFamily())));
		svgBuilder.append(" font-size=\"%.1f\"".formatted(fontSize));
		if (font.getName().contains(" Italic"))
			svgBuilder.append(" font-style=\"italic\"");
		if (font.getName().contains(" Bold"))
			svgBuilder.append(" font-style=\"bold\"");
		if (textFill instanceof Color color && color != Color.TRANSPARENT)
			svgBuilder.append(" fill=\"%s\"".formatted(asSvgColor(color)));
		if (angle != 0) {
			svgBuilder.append(" transform=\"rotate(%.1f) %.2f %.2f\"".formatted(angle, x, y));
		}
		svgBuilder.append(">");
		svgBuilder.append(text.replaceAll("&", " &amp;"));
		svgBuilder.append("</text>\n");
	}

	private static Object getSVGFontName(String fontFamily) {
		fontFamily = fontFamily.toLowerCase();
		if (fontFamily.startsWith("times"))
			return "Times New Roman";
		else if (fontFamily.startsWith("arial"))
			return "Arial";
		else if (fontFamily.startsWith("courier") || fontFamily.startsWith("monospaced"))
			return "Courier";
		else // if(fontFamily.startsWith("arial") || fontFamily.startsWith("helvetica") || fontFamily.startsWith("system"))
			return "Helvetica";
	}

	public static void appendImage(StringBuilder svgBuilder, double x, double y, double width, double height, Image image) {
		var encoder = new PngEncoderFX(image);
		var base64Data = Base64.getEncoder().encodeToString(encoder.pngEncode(true));
		svgBuilder.append("<image href=\"data:image/png;base64,").append(base64Data).append("\"");
		svgBuilder.append(" x=\"%.2f\" y=\"%.2f\" width=\"%.2f\" height=\"%.2f\"/>\n".formatted(x, y, width, height));
	}


	public static Rectangle2D computeBoundingBox(Node pane) {
		double minX = Double.MAX_VALUE;
		double minY = Double.MAX_VALUE;
		double maxX = Double.MIN_VALUE;
		double maxY = Double.MIN_VALUE;

		for (var node : BasicFX.getAllRecursively(pane, n -> true)) {
			if (node instanceof Shape) {
				var bounds = pane.sceneToLocal(node.localToScene(node.getBoundsInLocal()));
				minX = Math.min(minX, bounds.getMinX());
				minY = Math.min(minY, bounds.getMinY());
				maxX = Math.max(maxX, bounds.getMaxX());
				maxY = Math.max(maxY, bounds.getMaxY());
			}
		}
		if (true) { // this restricts to the currently
			minX = Math.max(minX, pane.getBoundsInLocal().getMinX());
			minY = Math.max(minY, pane.getBoundsInLocal().getMinY());

			maxX = Math.min(maxX, (pane.getBoundsInLocal().getMaxX()));
			maxY = Math.min(maxY, (pane.getBoundsInLocal().getMaxY()));
		}

		return new Rectangle2D(minX, minY, maxX - minX, maxY - minY);
	}

	private static String asSvgColor(Color color) {
		var r = (int) (color.getRed() * 255);
		var g = (int) (color.getGreen() * 255);
		var b = (int) (color.getBlue() * 255);
		var alpha = color.getOpacity();

		// Convert RGB values to hexadecimal notation
		var hexColor = String.format("#%02X%02X%02X", r, g, b);

		// Append alpha value if it's different from fully opaque (1.0)
		if (alpha < 1.0) {
			String alphaHex = String.format("%02X", (int) (alpha * 255));
			hexColor += alphaHex;
		}
		return hexColor;
	}
}
