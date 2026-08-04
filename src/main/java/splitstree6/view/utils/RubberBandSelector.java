/*
 * RubberBandSelector.java Copyright (C) 2025 Daniel H. Huson
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

package splitstree6.view.utils;

import javafx.animation.PauseTransition;
import javafx.collections.ObservableMap;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import jloda.fx.selection.SelectionModel;
import jloda.fx.util.BasicFX;
import jloda.phylo.PhyloGraph;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.Taxon;
import splitstree6.layout.tree.LabeledNodeShape;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * a rubber-band (box) selector that toggles nodes and/or edges that a dragged rectangle intersects.
 * Activated by a long press followed by a drag (a quick drag pans instead), Shift extends the current selection.
 * The generic constructor is used by the network view (nodes and edges); the {@link #createForTaxa} factory
 * sets up a taxa-only selector used by the tree and splits views.
 * Daniel Huson, 2025
 */
public class RubberBandSelector {

	private final Pane canvas;
	private final Collection<? extends Node> nodeShapes;
	private final Collection<? extends Node> edgeShapes;

	private final Runnable deselectAllNodes;
	private final Runnable deselectAllEdges;
	private final Consumer<Node> toggleNode;
	private final Consumer<Node> toggleEdge;

	private static final double LONG_PRESS_SEC = 0.7;
	private static final double TAP_SLOP = 4.0;

	private final Rectangle rubber = new Rectangle();
	private boolean selecting = false;

	// When a band selection completes with the mouse released inside the canvas, the platform synthesizes a
	// MOUSE_CLICKED right after the MOUSE_RELEASED. Some canvases clear the selection on such a click (click on
	// empty space = deselect all), which would immediately wipe what the band just selected. We set this flag when
	// a band completes and swallow that one following click. It is cleared on the next press so it never lingers.
	private boolean swallowNextClick = false;

	// We keep BOTH coordinate systems:
	private Point2D pressCanvas = null; // for drawing the rect on the canvas
	private Point2D pressScene = null; // for selection math in scene coords

	private PauseTransition longPressTimer;

	public RubberBandSelector(
			Pane canvas,
			Collection<? extends Node> nodeShapes,
			Collection<? extends Node> edgeShapes,
			Runnable deselectAllNodes,
			Runnable deselectAllEdges,
			Consumer<Node> toggleNode,
			Consumer<Node> toggleEdge
	) {
		this.canvas = canvas;
		this.nodeShapes = nodeShapes;
		this.edgeShapes = edgeShapes;
		this.deselectAllNodes = deselectAllNodes;
		this.deselectAllEdges = deselectAllEdges;
		this.toggleNode = toggleNode;
		this.toggleEdge = toggleEdge;

		setupRubber();
		installHandlers();
	}

	/**
	 * creates a rubber-band selector that selects taxa only: any node-shape whose bounds the band intersects
	 * has all of its associated taxa toggled in the taxon selection model. Edges are ignored, and node-shapes
	 * that carry no taxon (internal nodes) are simply skipped. This is used by the tree and splits views, which
	 * (unlike the network view) currently only support taxon selection.
	 *
	 * @param canvas              pane to draw the band on and to listen to for mouse events
	 * @param nodeShapeMap        maps graph nodes to their shapes; the live values view is used, so it always
	 *                            reflects the current drawing
	 * @param taxaBlockSupplier   supplies the current working taxa block, used to resolve taxon ids to taxa;
	 *                            a supplier (rather than a fixed block) so the selector stays correct after a
	 *                            taxa filter replaces the working taxa block
	 * @param taxonSelectionModel the taxon selection model to update
	 * @return the selector; keep a reference to it so it is not garbage collected
	 */
	public static RubberBandSelector createForTaxa(Pane canvas, ObservableMap<jloda.graph.Node, LabeledNodeShape> nodeShapeMap,
												   Supplier<TaxaBlock> taxaBlockSupplier, SelectionModel<Taxon> taxonSelectionModel) {
		return new RubberBandSelector(canvas, nodeShapeMap.values(), List.of(),
				taxonSelectionModel::clearSelection, null,
				shape -> toggleTaxaForShape(shape, nodeShapeMap, taxaBlockSupplier, taxonSelectionModel),
				shape -> {
				});
	}

	/**
	 * creates a rubber-band selector that selects taxa only, given the taxon "shapes" (e.g. taxon labels) directly
	 * together with a function that maps a shape to its taxon. This is used by the DensiTree view, where the consensus
	 * tree and its taxon labels are drawn as nodes on top of a canvas and there is no node-shape map to reverse-look-up.
	 *
	 * @param canvas              pane to draw the band on and to listen to for mouse events
	 * @param taxonShapes         the shapes that represent taxa (a live collection is fine, it is iterated on release)
	 * @param shapeToTaxon        maps a taxon shape to its taxon; may return null to skip a shape
	 * @param taxonSelectionModel the taxon selection model to update
	 * @return the selector; keep a reference to it so it is not garbage collected
	 */
	public static RubberBandSelector createForTaxonShapes(Pane canvas, Collection<? extends Node> taxonShapes,
														  Function<Node, Taxon> shapeToTaxon, SelectionModel<Taxon> taxonSelectionModel) {
		return new RubberBandSelector(canvas, taxonShapes, List.of(),
				taxonSelectionModel::clearSelection, null,
				shape -> {
					var taxon = shapeToTaxon.apply(shape);
					if (taxon != null)
						taxonSelectionModel.toggleSelection(taxon);
				},
				shape -> {
				});
	}

	/**
	 * toggles the taxa associated with the graph node whose shape equals the given shape
	 */
	private static void toggleTaxaForShape(Node shape, ObservableMap<jloda.graph.Node, LabeledNodeShape> nodeShapeMap,
										   Supplier<TaxaBlock> taxaBlockSupplier, SelectionModel<Taxon> taxonSelectionModel) {
		for (var v : nodeShapeMap.keySet()) {
			if (nodeShapeMap.get(v) == shape) {
				if (v.getOwner() instanceof PhyloGraph graph) {
					var taxaBlock = taxaBlockSupplier.get();
					if (taxaBlock != null) {
						for (var t : graph.getTaxa(v)) {
							var taxon = taxaBlock.get(t);
							if (taxon != null)
								taxonSelectionModel.toggleSelection(taxon);
						}
					}
				}
				return;
			}
		}
	}

	private void setupRubber() {
		rubber.setManaged(false);
		rubber.setVisible(false);
		rubber.setStroke(Color.web("#2979ff"));
		rubber.setStrokeWidth(1.5);
		rubber.getStrokeDashArray().setAll(6.0, 6.0);
		rubber.setFill(Color.web("#2979ff", 0.15));
		canvas.getChildren().add(rubber);
	}

	private void installHandlers() {
		canvas.addEventHandler(MouseEvent.MOUSE_PRESSED, this::onPressed);
		canvas.addEventHandler(MouseEvent.MOUSE_DRAGGED, this::onDragged);
		canvas.addEventHandler(MouseEvent.MOUSE_RELEASED, this::onReleased);
		// Swallow the click that the platform synthesizes right after a band selection released inside the canvas,
		// so a "click on empty space clears selection" handler on the canvas cannot wipe the fresh band selection.
		// A filter (capturing phase) runs before the canvas's own bubbling onMouseClicked handler on the same node.
		canvas.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> {
			if (swallowNextClick) {
				swallowNextClick = false;
				e.consume();
			}
		});
		canvas.addEventHandler(MouseEvent.MOUSE_EXITED, e -> {
			// Do NOT cancel an active band when the cursor leaves the drawing: during a press-drag gesture,
			// drag and release events keep being delivered to this canvas, so the band stays alive (clamped
			// to the drawing, see onDragged) until the mouse is released. This lets the user select items right
			// at the edge of the drawing. Only clean up a still-pending (not-yet-begun) selection.
			if (!selecting) {
				if (longPressTimer != null) {
					longPressTimer.stop();
					longPressTimer = null;
				}
				cancelSelection();
			}
		});
	}

	private void onPressed(MouseEvent e) {
		if (!e.isPrimaryButtonDown()) return;

		swallowNextClick = false; // start of a new gesture: never carry a stale click-swallow over

		// Record both canvas-space and scene-space positions
		pressCanvas = new Point2D(e.getX(), e.getY());
		pressScene = canvas.localToScene(pressCanvas);

		if (longPressTimer != null) longPressTimer.stop();
		longPressTimer = new PauseTransition(Duration.seconds(LONG_PRESS_SEC));
		longPressTimer.setOnFinished(ae -> {
			if (pressCanvas != null) beginRubber(pressCanvas);
		});
		longPressTimer.playFromStart();
	}

	private void onDragged(MouseEvent e) {
		if (pressCanvas == null) return;

		if (!selecting) {
			if (pressCanvas.distance(e.getX(), e.getY()) > TAP_SLOP && longPressTimer != null) {
				longPressTimer.stop(); // treat as normal drag (no band)
				longPressTimer = null;
			}
			return;
		}

		// Update on-canvas rectangle for visuals, clamping the moving corner to the drawing so the band
		// stays visible but never extends outside of it (drag/release events still arrive when the cursor
		// is outside the canvas during a press-drag gesture).
		updateRubber(pressCanvas.getX(), pressCanvas.getY(), clamp(e.getX(), canvas.getWidth()), clamp(e.getY(), canvas.getHeight()));
		e.consume();
	}

	private void onReleased(MouseEvent e) {
		if (longPressTimer != null) {
			longPressTimer.stop();
			longPressTimer = null;
		}
		if (!selecting) {
			pressCanvas = null;
			pressScene = null;
			return;
		}

		// A band is completing: swallow the click the platform will synthesize if the release is inside the canvas,
		// so it cannot clear the selection we are about to make.
		swallowNextClick = true;

		// Build the band in SCENE coordinates from pressScene to current point (converted), clamping the
		// moving corner to the drawing so the selected region matches the (clamped) visible rectangle.
		Point2D releaseScene = canvas.localToScene(new Point2D(clamp(e.getX(), canvas.getWidth()), clamp(e.getY(), canvas.getHeight())));
		Rectangle2D bandScene = rectFromPoints(pressScene, releaseScene);

		if (!e.isShiftDown()) {
			if (deselectAllNodes != null) deselectAllNodes.run();
			if (deselectAllEdges != null) deselectAllEdges.run();
		}

		// Toggle nodes that intersect band (all in scene coords)
		for (Node n : nodeShapes) {
			if (!n.isVisible()) continue;
			var nb = n.localToScene(n.getBoundsInLocal());      // <-- SCENE bounds
			if (intersects(nb, bandScene)) {
				toggleNode.accept(n);
			}
		}

		// Toggle edges whose segment intersects the band (in scene coords)
		for (var n : edgeShapes) {
			if (!n.isVisible()) continue;
			if (n instanceof Group group && !BasicFX.findRecursively(group, a -> a instanceof Line).isEmpty()) {
				var line = (Line) BasicFX.findRecursively(group, a -> a instanceof Line).get(0);
				var s = line.localToScene(line.getStartX(), line.getStartY()); // <-- SCENE points
				var t = line.localToScene(line.getEndX(), line.getEndY());
				if (lineIntersectsRect(s, t, bandScene)) {
					toggleEdge.accept(n);
				}
			} else {
				var nb = n.localToScene(n.getBoundsInLocal());      // <-- SCENE bounds
				if (intersects(nb, bandScene)) {
					toggleEdge.accept(n);
				}
			}
		}

		cancelSelection();
		pressCanvas = null;
		pressScene = null;
		e.consume();
	}

	private void beginRubber(Point2D originCanvas) {
		selecting = true;
		rubber.setVisible(true);
		rubber.setX(originCanvas.getX());
		rubber.setY(originCanvas.getY());
		rubber.setWidth(0);
		rubber.setHeight(0);
		canvas.setCursor(Cursor.CROSSHAIR);
	}

	private void updateRubber(double x0, double y0, double x1, double y1) {
		double x = Math.min(x0, x1);
		double y = Math.min(y0, y1);
		double w = Math.abs(x1 - x0);
		double h = Math.abs(y1 - y0);

		rubber.setX(x);
		rubber.setY(y);
		rubber.setWidth(w);
		rubber.setHeight(h);
	}

	private void cancelSelection() {
		selecting = false;
		rubber.setVisible(false);
		rubber.setWidth(0);
		rubber.setHeight(0);
		canvas.setCursor(Cursor.DEFAULT);
	}

	// ---------- geometry helpers (scene-space) ----------

	/**
	 * clamps a value to the range [0, max], keeping the rubber band inside the drawing
	 */
	private static double clamp(double value, double max) {
		if (value < 0)
			return 0;
		return Math.min(value, max);
	}

	private static Rectangle2D rectFromPoints(Point2D a, Point2D b) {
		double x = Math.min(a.getX(), b.getX());
		double y = Math.min(a.getY(), b.getY());
		double w = Math.abs(a.getX() - b.getX());
		double h = Math.abs(a.getY() - b.getY());
		return new Rectangle2D(x, y, w, h);
	}

	private static boolean intersects(Bounds b, Rectangle2D r) {
		return b.getMaxX() >= r.getMinX() && b.getMinX() <= r.getMaxX()
			   && b.getMaxY() >= r.getMinY() && b.getMinY() <= r.getMaxY();
	}

	private static boolean contains(Rectangle2D r, Point2D p) {
		return p.getX() >= r.getMinX() && p.getX() <= r.getMaxX()
			   && p.getY() >= r.getMinY() && p.getY() <= r.getMaxY();
	}

	private static boolean lineIntersectsRect(Point2D a, Point2D b, Rectangle2D r) {
		// quick bbox reject
		double minX = Math.min(a.getX(), b.getX());
		double maxX = Math.max(a.getX(), b.getX());
		double minY = Math.min(a.getY(), b.getY());
		double maxY = Math.max(a.getY(), b.getY());
		if (maxX < r.getMinX() || minX > r.getMaxX() || maxY < r.getMinY() || minY > r.getMaxY()) {
			// still allow endpoints inside
			return contains(r, a) || contains(r, b);
		}
		if (contains(r, a) || contains(r, b)) return true;

		// test against four edges of the rectangle
		Point2D r00 = new Point2D(r.getMinX(), r.getMinY());
		Point2D r10 = new Point2D(r.getMaxX(), r.getMinY());
		Point2D r11 = new Point2D(r.getMaxX(), r.getMaxY());
		Point2D r01 = new Point2D(r.getMinX(), r.getMaxY());
		return segmentsIntersect(a, b, r00, r10)
			   || segmentsIntersect(a, b, r10, r11)
			   || segmentsIntersect(a, b, r11, r01)
			   || segmentsIntersect(a, b, r01, r00);
	}

	private static boolean segmentsIntersect(Point2D a1, Point2D a2, Point2D b1, Point2D b2) {
		double d1 = cross(b1, b2, a1);
		double d2 = cross(b1, b2, a2);
		double d3 = cross(a1, a2, b1);
		double d4 = cross(a1, a2, b2);

		if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) &&
			((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) return true;

		// colinear cases
		if (d1 == 0 && onSegment(b1, b2, a1)) return true;
		if (d2 == 0 && onSegment(b1, b2, a2)) return true;
		if (d3 == 0 && onSegment(a1, a2, b1)) return true;
		if (d4 == 0 && onSegment(a1, a2, b2)) return true;

		return false;
	}

	private static double cross(Point2D a, Point2D b, Point2D c) {
		return (b.getX() - a.getX()) * (c.getY() - a.getY())
			   - (b.getY() - a.getY()) * (c.getX() - a.getX());
	}

	private static boolean onSegment(Point2D a, Point2D b, Point2D p) {
		return p.getX() >= Math.min(a.getX(), b.getX()) && p.getX() <= Math.max(a.getX(), b.getX())
			   && p.getY() >= Math.min(a.getY(), b.getY()) && p.getY() <= Math.max(a.getY(), b.getY());
	}
}
