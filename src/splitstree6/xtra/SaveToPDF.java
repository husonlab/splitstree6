/*
 *  SaveToPDF.java Copyright (C) 2023 Daniel H. Huson
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

/*
 * SaveToPDF.java Copyright (C) 2023. Daniel H. Huson
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
 *
 */

import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.chart.Axis;
import javafx.scene.chart.Chart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.control.Labeled;
import javafx.scene.control.TextInputControl;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.*;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import jloda.fx.control.RichTextLabel;
import jloda.fx.util.BasicFX;
import jloda.fx.util.GeometryUtilsFX;
import jloda.fx.util.ProgramProperties;
import jloda.fx.window.MainWindowManager;
import jloda.thirdparty.PngEncoderFX;
import jloda.util.Basic;
import jloda.util.StringUtils;
import org.apache.fontbox.util.BoundingBox;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.util.Matrix;
import org.fxmisc.richtext.TextExt;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.function.Function;

/**
 * save pane to PDF, trying to draw as objects
 * This is quite incomplete, for example, doesn't draw effects or borders
 * Daniel Huson, 6.2023
 */
public class SaveToPDF {
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

		var document = new PDDocument();
		var page = new PDPage(computeBoundingBox(pane));
		document.addPage(page);

		{
			// Set the metadata
			var documentInformation = document.getDocumentInformation();
			documentInformation.setAuthor(System.getProperty("user.name"));
			documentInformation.setTitle(file.getName());
			documentInformation.setSubject("PDF image of " + file.getName());
			documentInformation.setCreator(ProgramProperties.getProgramName());
			var calendar = new GregorianCalendar(TimeZone.getDefault());
			documentInformation.setCreationDate(calendar);
			documentInformation.setModificationDate(calendar);
		}

		var pdfMinX = page.getCropBox().getLowerLeftX();
		var pdfMaxX = page.getCropBox().getUpperRightX();
		var pdfWidth = page.getCropBox().getWidth();

		var pdfMinY = page.getCropBox().getLowerLeftY();
		var pdfMaxY = page.getCropBox().getUpperRightY();
		var pdfHeight = page.getCropBox().getHeight();

		var paneWidth = pane.getBoundsInLocal().getWidth();
		var paneHeight = pane.getBoundsInLocal().getHeight();

		var factor = Math.min(pdfWidth / paneWidth, pdfHeight / paneHeight);

		Function<Double, Float> ps = s -> (float) (s * factor);
		Function<Double, Float> px = x -> (float) (x * factor + pdfMinX);
		Function<Double, Float> py = y -> (float) (pdfMaxY - y * factor);

		var xPane = pane.localToScene(pane.getBoundsInLocal()).getMinX();
		var yPane = pane.localToScene(pane.getBoundsInLocal()).getMinY();

		var contentStream = new PDPageContentStream(document, page);

		if (MainWindowManager.isUseDarkTheme()) {
			contentStream.setNonStrokingColor(pdfColor(Color.web("rgb(60, 63, 65)")));
			contentStream.addRect(-5, -5, page.getMediaBox().getWidth() + 10, page.getMediaBox().getHeight() + 10);
			contentStream.fill();
		}

		for (var n : BasicFX.getAllRecursively(pane, n -> true)) {
			// System.err.println("n: " + n.getClass().getSimpleName());
			if (isNodeVisible(n)) {
				try {
					if (n instanceof Line line) {
						contentStream.setLineWidth(ps.apply(line.getStrokeWidth()));
						contentStream.setLineDashPattern(getLineDashPattern(line), 0);

						var x1 = px.apply(pane.sceneToLocal(line.localToScene(line.getStartX(), line.getStartY())).getX());
						var y1 = py.apply(pane.sceneToLocal(line.localToScene(line.getStartX(), line.getStartY())).getY());
						var x2 = px.apply(pane.sceneToLocal(line.localToScene(line.getEndX(), line.getEndY())).getX());
						var y2 = py.apply(pane.sceneToLocal(line.localToScene(line.getEndX(), line.getEndY())).getY());
						contentStream.moveTo(x1, y1);
						contentStream.lineTo(x2, y2);
						doFillStroke(contentStream, line.getStroke(), line.getFill());
					} else if (n instanceof Rectangle rectangle) {
						// todo: this might break of rectangle has been rotated
						contentStream.setLineWidth(ps.apply(rectangle.getStrokeWidth()));
						contentStream.setLineDashPattern(getLineDashPattern(rectangle), 0);
						var bounds = pane.sceneToLocal(rectangle.localToScene(rectangle.getBoundsInLocal()));
						contentStream.addRect(px.apply(bounds.getMinX()), py.apply(bounds.getMaxY()), ps.apply(bounds.getWidth()), ps.apply(bounds.getHeight()));
						doFillStroke(contentStream, rectangle.getStroke(), rectangle.getFill());
					} else if (n instanceof Ellipse ellipse) {
						contentStream.setLineWidth(ps.apply(ellipse.getStrokeWidth()));
						contentStream.setLineDashPattern(getLineDashPattern(ellipse), 0);
						var bounds = pane.sceneToLocal(ellipse.localToScene(ellipse.getBoundsInLocal()));
						var rx = ps.apply(0.5 * bounds.getHeight());
						var ry = ps.apply(0.5 * bounds.getWidth());
						var x = px.apply(bounds.getCenterX());
						var y = py.apply(bounds.getCenterY());
						addEllipse(contentStream, x, y, rx, ry);
						doFillStroke(contentStream, ellipse.getStroke(), ellipse.getFill());
					} else if (n instanceof Circle circle) {
						contentStream.setLineWidth(ps.apply(circle.getStrokeWidth()));
						contentStream.setLineDashPattern(getLineDashPattern(circle), 0);
						var bounds = pane.sceneToLocal(circle.localToScene(circle.getBoundsInLocal()));
						var r = ps.apply(0.5 * bounds.getHeight());
						var x = px.apply(bounds.getCenterX());
						var y = py.apply(bounds.getCenterY());
						addCircle(contentStream, x, y, r);
						doFillStroke(contentStream, circle.getStroke(), circle.getFill());
					} else if (n instanceof QuadCurve || n instanceof CubicCurve) {
						var curve = (n instanceof QuadCurve ? convertQuadCurveToCubicCurve((QuadCurve) n) : (CubicCurve) n);
						contentStream.setLineWidth(ps.apply(curve.getStrokeWidth()));
						contentStream.setLineDashPattern(getLineDashPattern(curve), 0);
						var sX = px.apply(pane.sceneToLocal(curve.localToScene(curve.getStartX(), curve.getStartY())).getX());
						var sY = py.apply(pane.sceneToLocal(curve.localToScene(curve.getStartX(), curve.getStartY())).getY());
						var c1X = px.apply(pane.sceneToLocal(curve.localToScene(curve.getControlX1(), curve.getControlY1())).getX());
						var c1Y = py.apply(pane.sceneToLocal(curve.localToScene(curve.getControlX1(), curve.getControlY1())).getY());
						var c2X = px.apply(pane.sceneToLocal(curve.localToScene(curve.getControlX2(), curve.getControlY2())).getX());
						var c2Y = py.apply(pane.sceneToLocal(curve.localToScene(curve.getControlX2(), curve.getControlY2())).getY());
						var tX = px.apply(pane.sceneToLocal(curve.localToScene(curve.getEndX(), curve.getEndY())).getX());
						var tY = py.apply(pane.sceneToLocal(curve.localToScene(curve.getEndX(), curve.getEndY())).getY());
						contentStream.moveTo(sX, sY);
						contentStream.curveTo(c1X, c1Y, c2X, c2Y, tX, tY);
						doFillStroke(contentStream, curve.getStroke(), curve.getFill());
					} else if (n instanceof Path path) {
						if (containedInText(path))
							continue; // don't draw caret
						contentStream.setLineWidth(ps.apply(path.getStrokeWidth()));
						contentStream.setLineDashPattern(getLineDashPattern(path), 0);
						var local = new Point2D(0, 0);
						for (var element : path.getElements()) {
							if (element instanceof MoveTo moveTo) {
								local = new Point2D(moveTo.getX(), moveTo.getY());
								var t = pane.sceneToLocal(path.localToScene(local.getX(), local.getY()));
								contentStream.moveTo(px.apply(t.getX()), py.apply(t.getY()));
							} else if (element instanceof LineTo lineTo) {
								local = new Point2D(lineTo.getX(), lineTo.getY());
								var t = pane.sceneToLocal(path.localToScene(local.getX(), local.getY()));
								contentStream.lineTo(px.apply(t.getX()), py.apply(t.getY()));
							} else if (element instanceof HLineTo lineTo) {
								local = new Point2D(lineTo.getX(), local.getY());
								var t = pane.sceneToLocal(path.localToScene(local.getX(), local.getY()));
								contentStream.lineTo(px.apply(t.getX()), py.apply(t.getY()));
							} else if (element instanceof VLineTo lineTo) {
								local = new Point2D(local.getX(), lineTo.getY());
								var t = pane.sceneToLocal(path.localToScene(local.getX(), local.getY()));
								contentStream.lineTo(px.apply(t.getX()), py.apply(t.getY()));
							} else if (element instanceof ArcTo arcTo) {
								local = new Point2D(arcTo.getX(), arcTo.getY());
								System.err.println("arcTo: not implemented");
							} else if (element instanceof QuadCurveTo || element instanceof CubicCurveTo) {
								var curveTo = (element instanceof QuadCurveTo ? convertQuadToCubicCurveTo(local.getX(), local.getY(), (QuadCurveTo) element) : (CubicCurveTo) element);
								var t = pane.sceneToLocal(path.localToScene(curveTo.getX(), curveTo.getY()));
								var c1 = pane.sceneToLocal(path.localToScene(curveTo.getControlX1(), curveTo.getControlY1()));
								var c2 = pane.sceneToLocal(path.localToScene(curveTo.getControlX2(), curveTo.getControlY2()));
								contentStream.curveTo(px.apply(c1.getX()), py.apply(c1.getY()), px.apply(c2.getX()), py.apply(c2.getY()), px.apply(t.getX()), py.apply(t.getY()));
							}
						}
						doFillStroke(contentStream, path.getStroke(), path.getFill());
					} else if (n instanceof Polygon polygon) {
						contentStream.setLineWidth(ps.apply(polygon.getStrokeWidth()));
						contentStream.setLineDashPattern(getLineDashPattern(polygon),0);
						var points = polygon.getPoints();
						if (points.size() > 0) {
							var sX = px.apply(pane.sceneToLocal(polygon.localToScene(polygon.getPoints().get(0), polygon.getPoints().get(1))).getX());
							var sY = py.apply(pane.sceneToLocal(polygon.localToScene(polygon.getPoints().get(0), polygon.getPoints().get(1))).getY());

							contentStream.moveTo(sX, sY);
							for (var i = 2; i < points.size(); i += 2) {
								var x = px.apply(pane.sceneToLocal(polygon.localToScene(polygon.getPoints().get(i), polygon.getPoints().get(i + 1))).getX());
								var y = py.apply(pane.sceneToLocal(polygon.localToScene(polygon.getPoints().get(i), polygon.getPoints().get(i + 1))).getY());
								contentStream.lineTo(x, y);
							}
							contentStream.closePath();
							doFillStroke(contentStream, polygon.getStroke(), polygon.getFill());
						}
					} else if (n instanceof Text text) {
						if (!text.getText().isBlank()) {
							double screenAngle = 360 - getAngleOnScreen(text); // because y axis points upward in PDF
							var localBounds = text.getBoundsInLocal();
							var origX = localBounds.getMinX();
							var origY = localBounds.getMinY() + 0.87f * localBounds.getHeight();
							var rotateAnchorX = pane.sceneToLocal(text.localToScene(origX, origY)).getX();
							var rotateAnchorY = pane.sceneToLocal(text.localToScene(origX, origY)).getY();
							contentStream.beginText();
							if (isMirrored(text)) // todo: this is untested:
								screenAngle = 360 - screenAngle;
							contentStream.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(screenAngle), px.apply(rotateAnchorX), py.apply(rotateAnchorY)));
							contentStream.setNonStrokingColor(pdfColor(text.getFill()));
							var fontHeight = ps.apply(text.getFont().getSize());
							var altFontHeight = ps.apply(0.87 * text.localToScreen(localBounds).getHeight());
							if (!(text instanceof TextExt) && Math.abs(fontHeight - altFontHeight) > 2)
								fontHeight = altFontHeight;
							setFont(contentStream, text, fontHeight);
							contentStream.showText(text.getText());
							contentStream.endText();
						}
					} else if (n instanceof ImageView imageView) {
						var encoder = new PngEncoderFX(imageView.getImage());
						var image = PDImageXObject.createFromByteArray(document, encoder.pngEncode(true), "image/png");
						var bounds = pane.sceneToLocal(imageView.localToScene(imageView.getBoundsInLocal()));
						var x = px.apply(bounds.getMinX());
						var width = ps.apply(bounds.getWidth());
						var y = py.apply(bounds.getMaxY());
						var height = ps.apply(bounds.getHeight());
						contentStream.drawImage(image, x, y, width, height);
					} else if (n instanceof Shape3D || n instanceof Canvas || n instanceof Chart) {
						var parameters = new SnapshotParameters();
						parameters.setFill(Color.TRANSPARENT);
						var snapShot = n.snapshot(parameters, null);
						var encoder = new PngEncoderFX(snapShot);
						var image = PDImageXObject.createFromByteArray(document, encoder.pngEncode(true), "image/png");
						var bounds = pane.sceneToLocal(n.localToScene(n.getBoundsInLocal()));
						var x = px.apply(bounds.getMinX());
						var width = ps.apply(bounds.getWidth());
						var y = py.apply(bounds.getMaxY());
						var height = ps.apply(bounds.getHeight());
						contentStream.drawImage(image, x, y, width, height);
					}
				} catch (IOException ex) {
					Basic.caught(ex);
				} finally {
					contentStream.setLineDashPattern(new float[0],0);
				}
			}
		}
		contentStream.close();
		document.save(file);
		document.close();
	}

	private static void setFont(PDPageContentStream contentStream, Text text, float size) throws IOException {
		//System.err.println(text.getFont().getFamily()+" size: "+size);
		contentStream.setFont(convertToPDFBoxFont(text.getFont()), size);
	}

	public static PDType1Font convertToPDFBoxFont(Font javafxFont) {
		var pdfboxFontFamily = "";
		{
			var fontFamily = javafxFont.getFamily().toLowerCase();
			if (fontFamily.startsWith("times") || fontFamily.startsWith("arial"))
				pdfboxFontFamily = Standard14Fonts.FontName.TIMES_ROMAN.getName();
			else if (fontFamily.startsWith("courier") || fontFamily.startsWith("monospaced"))
				pdfboxFontFamily = Standard14Fonts.FontName.COURIER.getName();
			else if (fontFamily.startsWith("symbol"))
				pdfboxFontFamily = Standard14Fonts.FontName.SYMBOL.getName();
			else if (fontFamily.startsWith("zapf_dingbats"))
				pdfboxFontFamily = Standard14Fonts.FontName.ZAPF_DINGBATS.getName();
			else // if(fontFamily.startsWith("arial") || fontFamily.startsWith("helvetica") || fontFamily.startsWith("system"))
				pdfboxFontFamily = Standard14Fonts.FontName.HELVETICA.getName();
		}

		// Map JavaFX font weight and style to PDFBox font
		var bold = javafxFont.getName().contains(" Bold");
		var italic = javafxFont.getName().contains(" Italic");
		var pdfboxFontStyle = "";
		if (bold) {
			if (italic)
				pdfboxFontStyle = "_BoldItalic";
			else
				pdfboxFontStyle = "_Bold";
		} else if (italic)
			pdfboxFontStyle = "_Italic";
		var pdfboxFontFullName = pdfboxFontFamily + pdfboxFontStyle;
		var font = StringUtils.valueOfIgnoreCase(Standard14Fonts.FontName.class, pdfboxFontFullName);
		if (font == null) {
			pdfboxFontFullName = pdfboxFontFullName.replaceAll("Italic", "Oblique");
			font = StringUtils.valueOfIgnoreCase(Standard14Fonts.FontName.class, pdfboxFontFullName);
		}
		if (font == null) {
			font = StringUtils.valueOfIgnoreCase(Standard14Fonts.FontName.class, pdfboxFontFamily);
		}
		if (font == null)
			font = Standard14Fonts.FontName.HELVETICA;
		return new PDType1Font(font);
	}
	private static void doFillStroke(PDPageContentStream contentStream, Paint stroke, Paint fill) throws IOException {
		var pdfStroke = pdfColor(stroke);
		var pdfFill = pdfColor(fill);
		if (pdfStroke != null && pdfFill != null) {
			contentStream.setStrokingColor(pdfStroke);
			contentStream.setNonStrokingColor(pdfFill);
			contentStream.fillAndStroke();
		} else if (pdfStroke != null) {
			contentStream.setStrokingColor(pdfStroke);
			contentStream.stroke();
		} else if (pdfFill != null) {
			contentStream.setNonStrokingColor(pdfFill);
			contentStream.fill();
		}
	}

	private static void addCircle(PDPageContentStream contentStream, float cx, float cy, float r) throws IOException {
		final float k = 0.552284749831f;
		//System.err.println("Circle at: " + cx + "," + cy);
		contentStream.moveTo(cx - r, cy);
		contentStream.curveTo(cx - r, cy + k * r, cx - k * r, cy + r, cx, cy + r);
		contentStream.curveTo(cx + k * r, cy + r, cx + r, cy + k * r, cx + r, cy);
		contentStream.curveTo(cx + r, cy - k * r, cx + k * r, cy - r, cx, cy - r);
		contentStream.curveTo(cx - k * r, cy - r, cx - r, cy - k * r, cx - r, cy);
	}

	private static void addEllipse(PDPageContentStream contentStream, float cx, float cy, float rx, float ry) throws IOException {
		final float k = 0.552284749831f;
		//System.err.println("Circle at: " + cx + "," + cy);
		contentStream.moveTo(cx - rx, cy);
		contentStream.curveTo(cx - rx, cy + k * ry, cx - k * rx, cy + ry, cx, cy + ry);
		contentStream.curveTo(cx + k * rx, cy + ry, cx + rx, cy + k * ry, cx + rx, cy);
		contentStream.curveTo(cx + rx, cy - k * ry, cx + k * rx, cy - ry, cx, cy - ry);
		contentStream.curveTo(cx - k * rx, cy - ry, cx - rx, cy - k * ry, cx - rx, cy);
	}


	private static PDColor pdfColor(Paint paint) {
		if (paint instanceof Color color && !color.equals(Color.TRANSPARENT))
			return new PDColor(new float[]{(float) color.getRed(), (float) color.getGreen(), (float) color.getBlue()}, PDDeviceRGB.INSTANCE);
		else
			return null;
	}

	/**
	 * gets the angle of a node on screen
	 *
	 * @param node the node
	 * @return angle in degrees
	 */
	public static double getAngleOnScreen(Node node) {
		var localOrig = new Point2D(0, 0);
		var localX1000 = new Point2D(1000, 0);
		var orig = node.localToScreen(localOrig);
		if (orig != null) {
			var x1000 = node.localToScreen(localX1000).subtract(orig);
			if (x1000 != null) {
				return GeometryUtilsFX.computeAngle(x1000);
			}
		}
		return 0.0;
	}

	/**
	 * does this pane appear as a mirrored image on the screen?
	 *
	 * @param node the pane
	 * @return true, if mirror image, false if direct image
	 */
	public static boolean isMirrored(Node node) {
		var orig = node.localToScreen(0, 0);
		if (orig != null) {
			var x1000 = node.localToScreen(1000, 0);
			var y1000 = node.localToScreen(0, 1000);
			var p1 = x1000.subtract(orig);
			var p2 = y1000.subtract(orig);
			var determinant = p1.getX() * p2.getY() - p1.getY() * p2.getX();
			return (determinant < 0);
		} else
			return false;
	}

	private static CubicCurve convertQuadCurveToCubicCurve(QuadCurve quadCurve) {
		var cubicCurve = new CubicCurve();
		cubicCurve.setStartX(quadCurve.getStartX());
		cubicCurve.setStartY(quadCurve.getStartY());
		cubicCurve.setEndX(quadCurve.getEndX());
		cubicCurve.setEndY(quadCurve.getEndY());
		var c1x = (2.0 / 3.0) * quadCurve.getControlX() + (1.0 / 3.0) * quadCurve.getStartX();
		var c1y = (2.0 / 3.0) * quadCurve.getControlY() + (1.0 / 3.0) * quadCurve.getStartY();
		var c2x = (2.0 / 3.0) * quadCurve.getControlX() + (1.0 / 3.0) * quadCurve.getEndX();
		var c2y = (2.0 / 3.0) * quadCurve.getControlY() + (1.0 / 3.0) * quadCurve.getEndY();
		cubicCurve.setControlX1(c1x);
		cubicCurve.setControlY1(c1y);
		cubicCurve.setControlX2(c2x);
		cubicCurve.setControlY2(c2y);
		return cubicCurve;
	}

	private static CubicCurveTo convertQuadToCubicCurveTo(double startX, double startY, QuadCurveTo quadCurveTo) {
		var cubicCurveTo = new CubicCurveTo();
		cubicCurveTo.setX(quadCurveTo.getX());
		cubicCurveTo.setY(quadCurveTo.getY());
		var c1x = (2.0 / 3.0) * quadCurveTo.getControlX() + (1.0 / 3.0) * startX;
		var c1y = (2.0 / 3.0) * quadCurveTo.getControlY() + (1.0 / 3.0) * startY;
		var c2x = (2.0 / 3.0) * quadCurveTo.getControlX() + (1.0 / 3.0) * quadCurveTo.getX();
		var c2y = (2.0 / 3.0) * quadCurveTo.getControlY() + (1.0 / 3.0) * quadCurveTo.getY();
		cubicCurveTo.setControlX1(c1x);
		cubicCurveTo.setControlY1(c1y);
		cubicCurveTo.setControlX2(c2x);
		cubicCurveTo.setControlY2(c2y);
		return cubicCurveTo;
	}

	public static PDRectangle computeBoundingBox(Node pane) {
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

		return new PDRectangle(new BoundingBox((float) minX, (float) minY, (float) maxX, (float) maxY));
	}

	public static boolean isNodeVisible(Node node) {
		if (!node.isVisible()) {
			return false;
		}

		Node parent = node.getParent();
		while (parent != null) {
			if (!parent.isVisible()) {
				return false;
			}
			parent = parent.getParent();
		}

		return true;
	}

	public static boolean containedInText(Node node) {
		while (node != null) {
			if (node instanceof Text || node instanceof RichTextLabel || node instanceof Labeled || node instanceof TextInputControl) {
				return true;
			} else
				node = node.getParent();
		}
		return false;
	}

	public static float[] getLineDashPattern(Shape shape) {
		if (shape.getStrokeDashArray().size() > 0) {
			var array = new float[shape.getStrokeDashArray().size()];
			for (var i = 0; i < shape.getStrokeDashArray().size(); i++) {
				array[i] = shape.getStrokeDashArray().get(i).floatValue();
			}
			return array;
		} else
			return new float[0];
	}

}
