/*
 *  InputOutput.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.xtra.draw;

import javafx.geometry.Point2D;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import jloda.fx.control.RichTextLabel;
import jloda.fx.util.ColorUtilsFX;
import jloda.fx.window.MainWindowManager;
import jloda.graph.io.GraphGML;
import jloda.util.Basic;
import jloda.util.NumberUtils;
import jloda.util.StringUtils;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.List;

import static splitstree6.xtra.draw.DrawPane.INTERPOLATE_STEP;

/**
 * input and output of created network
 * Daniel Huson, 9.2024
 */
public class InputOutput {
	/**
	 * save the phylo sketch in GML format
	 *
	 * @param w        writer
	 * @param drawPane the draw pane
	 * @throws IOException something went wrong
	 */
	public static void save(Writer w, DrawPane drawPane) throws IOException {
		var network = drawPane.getNetwork();

		var nodeKeyNames = List.of("taxon", "shape", "size", "fill", "x", "y", "label", "label_color", "label_dx", "label_dy");
		var edgeKeyNames = List.of("path", "stroke", "stroke_width");
		var comment = "Created by PhyloSketch App on %s".formatted(Basic.getDateString("yyyy-MM-dd HH:mm:ss"));
		GraphGML.writeGML(network, comment, network.getName(), true, 1, w,
				nodeKeyNames, (key, v) -> {
					var shape = (Shape) v.getData();
					var label = (RichTextLabel) v.getInfo();
					var value = String.valueOf(switch (key) {
						case "taxon" -> network.hasTaxa(v) ? network.getTaxon(v) : "";
						case "shape" -> shape.getClass().getSimpleName().toLowerCase();
						case "size" -> {
							var buf = new StringBuilder();
							if (shape instanceof Rectangle rectangle) {
								buf.append(StringUtils.removeTrailingZerosAfterDot("%.1f", rectangle.getWidth()));
								buf.append(",");
								buf.append(StringUtils.removeTrailingZerosAfterDot("%.1f", rectangle.getHeight()));
							} else if (shape instanceof Circle circle) {
								buf.append(StringUtils.removeTrailingZerosAfterDot("%.1f", circle.getRadius()));
							} else {
								buf.append(StringUtils.removeTrailingZerosAfterDot("%.1f", shape.getBoundsInLocal().getWidth()));
								buf.append(",");
								buf.append(StringUtils.removeTrailingZerosAfterDot("%.1f", shape.getBoundsInLocal().getHeight()));
							}
							yield buf.toString();
						}
						case "fill" -> (shape.getFill() != null
										&& !(!MainWindowManager.isUseDarkTheme() && shape.getFill() == Color.BLACK)
										&& !(MainWindowManager.isUseDarkTheme() && shape.getFill() == Color.WHITE)) ? shape.getFill() : "";
						case "x" -> StringUtils.removeTrailingZerosAfterDot("%.1f", shape.getTranslateX());
						case "y" -> StringUtils.removeTrailingZerosAfterDot("%.1f", shape.getTranslateY());
						case "label" -> network.getLabel(v) != null ? network.getLabel(v) : "";
						case "label_stroke" -> (label != null && label.getTextFill() != null
												&& !(!MainWindowManager.isUseDarkTheme() && label.getTextFill() == Color.BLACK)
												&& !(MainWindowManager.isUseDarkTheme() && label.getTextFill() == Color.WHITE)) ? label.getTextFill() : "";
						case "label_dx" ->
								label != null ? StringUtils.removeTrailingZerosAfterDot("%.1f", label.getLayoutX()) : "";
						case "label_dy" ->
								label != null ? StringUtils.removeTrailingZerosAfterDot("%.1f", label.getLayoutY()) : "";
						default -> "";
					});
					return (value.isBlank() ? null : value);
				}, edgeKeyNames, (key, e) -> {
					var path = (Path) e.getData();
					var value = String.valueOf(switch (key) {
						case "path" -> pathToString(path);
						case "stroke" -> (path.getStroke() != null
										  && !(!MainWindowManager.isUseDarkTheme() && path.getStroke() == Color.BLACK)
										  && !(MainWindowManager.isUseDarkTheme() && path.getStroke() == Color.WHITE)) ? path.getStroke() : "";
						case "stroke_width" -> path.getStrokeWidth() != 1 ? path.getStrokeWidth() : "";
						default -> "";
					});
					return (value.isBlank() ? null : value);
				});
	}

	/**
	 * load a a phylo-sketch, previously saved in GML format
	 *
	 * @param r        reader
	 * @param drawPane the draw pane
	 * @throws IOException something went wrong
	 */
	public static void load(Reader r, DrawPane drawPane) throws IOException {
		drawPane.clear();
		var network = drawPane.getNetwork();

		var gmlInfo = GraphGML.readGML(r, network, (key, v, value) -> {
			switch (key) {
				case "tax_id" -> {
					network.addTaxon(v, Integer.parseInt(value));
				}
				case "shape" -> {
					var shape = value.equals("square") ? new Rectangle(3, 3) : new Circle(3);
					drawPane.createNode(shape);
				}
				case "size" -> {
					if (v.getData() instanceof Shape shape) {
						var tokens = StringUtils.split(value, ',');
						if (shape instanceof Rectangle rectangle) {
							if (tokens.length == 1 && NumberUtils.isDouble(tokens[0])) {
								var w = NumberUtils.parseDouble(tokens[0]);
								rectangle.setWidth(w);
								rectangle.setHeight(w);
							} else if (tokens.length >= 2 && NumberUtils.isDouble(tokens[0]) && NumberUtils.isDouble(tokens[1])) {
								var w = NumberUtils.parseDouble(tokens[0]);
								var h = NumberUtils.parseDouble(tokens[1]);
								rectangle.setWidth(w);
								rectangle.setHeight(h);
							}
						} else if (shape instanceof Circle circle) {
							if (tokens.length >= 1 && NumberUtils.isDouble(tokens[0])) {
								var w = NumberUtils.parseDouble(tokens[0]);
								circle.setRadius(w);
							}
						}
					}
				}
				case "fill" -> {
					if (v.getData() instanceof Shape shape && ColorUtilsFX.isColor(value)) {
						shape.setFill(ColorUtilsFX.parseColor(value));
					}
				}
				case "x" -> {
					if (v.getData() instanceof Shape shape && NumberUtils.isDouble(value)) {
						shape.setTranslateX(NumberUtils.parseDouble(value));
					}
				}
				case "y" -> {
					if (v.getData() instanceof Shape shape && NumberUtils.isDouble(value)) {
						shape.setTranslateY(NumberUtils.parseDouble(value));
					}
				}
				case "label" -> {
					network.setLabel(v, value);
					drawPane.createLabel(v, value);
				}
				case "label_stroke" -> {
					if (v.getInfo() instanceof RichTextLabel label && ColorUtilsFX.isColor(value)) {
						label.setTextFill(ColorUtilsFX.parseColor(value));
					}
				}
				case "label_dx" -> {
					if (v.getInfo() instanceof RichTextLabel label && NumberUtils.isDouble(value)) {
						label.setLayoutX(NumberUtils.parseDouble(value));
					}
				}
				case "label_dy" -> {
					if (v.getInfo() instanceof RichTextLabel label && NumberUtils.isDouble(value)) {
						label.setLayoutY(NumberUtils.parseDouble(value));
					}
				}
			}
		}, (key, e, value) -> {
			switch (key) {
				case "path" -> {
					var path = stringToPath(value);
					drawPane.addPath(e, path);
				}
				case "stroke" -> {
					if (e.getData() instanceof Path path && ColorUtilsFX.isColor(value)) {
						path.setStroke(ColorUtilsFX.parseColor(value));
					}
				}
				case "stroke_width" -> {
					if (e.getData() instanceof Path path && NumberUtils.isDouble(value)) {
						path.setStrokeWidth(NumberUtils.parseDouble(value));
					}
				}
			}
		});
		// create shapes for any nodes for which shape not given
		for (var v : network.nodes()) {
			if (!(v.getData() instanceof Shape)) {
				drawPane.createNode(new Circle(3));
			}
		}
		// create paths for any edges for which path not given
		for (var e : network.edges()) {
			if (!(e.getData() instanceof Path)) {
				var a = new Point2D(((Shape) e.getSource().getData()).getTranslateX(), ((Shape) e.getSource().getData()).getTranslateY());
				var b = new Point2D(((Shape) e.getTarget().getData()).getTranslateX(), ((Shape) e.getTarget().getData()).getTranslateY());
				var path = DrawUtils.createPath(a, b, INTERPOLATE_STEP);
				drawPane.addPath(e, path);
			}
		}
		if (gmlInfo.comment() != null) {
			System.err.println(gmlInfo.comment());
		}
		network.setName(gmlInfo.label());
	}


	private static String pathToString(Path path) {
		var buf = new StringBuilder();
		for (var item : path.getElements()) {
			var point = DrawUtils.getCoordinates(item);
			if (!buf.isEmpty())
				buf.append(",");
			buf.append(StringUtils.removeTrailingZerosAfterDot("%.1f", point.getX()));
			buf.append(",");
			buf.append(StringUtils.removeTrailingZerosAfterDot("%.1f", point.getY()));
		}
		return buf.toString();
	}

	private static Path stringToPath(String text) {
		var tokens = text.split(",");

		var path = new Path();
		for (var i = 0; i + 1 < tokens.length; i += 2) {
			var x = Double.parseDouble(tokens[i]);
			var y = Double.parseDouble(tokens[i + 1]);
			if (i == 0) {
				path.getElements().add(new MoveTo(x, y));
			} else {
				path.getElements().add(new LineTo(x, y));
			}
		}
		return path;
	}
}

