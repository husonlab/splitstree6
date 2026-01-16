/*
 * InputDebug.java Copyright (C) 2026 Daniel H. Huson
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

package splitstree6.view.splits.viewer;

import javafx.event.Event;
import javafx.event.EventType;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.*;

import java.util.Set;

public final class InputDebug {

	private static final Set<EventType<?>> INTERESTING = Set.of(
			// Scroll events (most likely cause)
			ScrollEvent.SCROLL_STARTED,
			ScrollEvent.SCROLL,
			ScrollEvent.SCROLL_FINISHED,

			// Touch
			TouchEvent.TOUCH_PRESSED,
			TouchEvent.TOUCH_MOVED,
			TouchEvent.TOUCH_RELEASED,
			TouchEvent.TOUCH_STATIONARY,

			// Mouse (touch emulation)
			MouseEvent.MOUSE_PRESSED,
			MouseEvent.MOUSE_DRAGGED,
			MouseEvent.MOUSE_RELEASED,

			// Other gestures
			ZoomEvent.ZOOM_STARTED,
			ZoomEvent.ZOOM,
			ZoomEvent.ZOOM_FINISHED,
			RotateEvent.ROTATION_STARTED,
			RotateEvent.ROTATE,
			RotateEvent.ROTATION_FINISHED,
			SwipeEvent.SWIPE_LEFT,
			SwipeEvent.SWIPE_RIGHT,
			SwipeEvent.SWIPE_UP,
			SwipeEvent.SWIPE_DOWN
	);

	public static void attach(Scene scene, ScrollPane sp) {
		// Scene-level: see *everything*
		scene.addEventFilter(Event.ANY, e -> log("SCENE", e));

		// ScrollPane-level: see what arrives at the scroll pane
		sp.addEventFilter(Event.ANY, e -> log("SP-FILTER", e));
		sp.addEventHandler(Event.ANY, e -> log("SP-HANDLER", e));

		// Content-level: many iOS scroll events target the content
		Node content = sp.getContent();
		if (content != null) {
			content.addEventFilter(Event.ANY, e -> log("CONTENT-FILTER", e));
			content.addEventHandler(Event.ANY, e -> log("CONTENT-HANDLER", e));
		}
	}

	private static void log(String where, Event e) {
		if (!INTERESTING.contains(e.getEventType())) return;

		String type = e.getEventType().toString();
		String target = (e.getTarget() == null) ? "null" : e.getTarget().getClass().getSimpleName();
		String source = (e.getSource() == null) ? "null" : e.getSource().getClass().getSimpleName();

		String extra = "";
		if (e instanceof ScrollEvent se) {
			extra = String.format(" direct=%s inertia=%s touchCount=%d dX=%.2f dY=%.2f totalX=%.2f totalY=%.2f",
					se.isDirect(), se.isInertia(), se.getTouchCount(),
					se.getDeltaX(), se.getDeltaY(),
					se.getTotalDeltaX(), se.getTotalDeltaY());
		} else if (e instanceof TouchEvent te) {
			extra = String.format(" touchCount=%d pointId=%d state=%s x=%.2f y=%.2f",
					te.getTouchCount(),
					te.getTouchPoint().getId(),
					te.getTouchPoint().getState(),
					te.getTouchPoint().getX(),
					te.getTouchPoint().getY());
		} else if (e instanceof MouseEvent me) {
			extra = String.format(" synthesized=%s button=%s x=%.2f y=%.2f",
					me.isSynthesized(), me.getButton(), me.getX(), me.getY());
		} else if (e instanceof ZoomEvent ze) {
			extra = String.format(" factor=%.4f totalFactor=%.4f", ze.getZoomFactor(), ze.getTotalZoomFactor());
		} else if (e instanceof RotateEvent re) {
			extra = String.format(" angle=%.4f totalAngle=%.4f", re.getAngle(), re.getTotalAngle());
		} else if (e instanceof SwipeEvent) {
			extra = " (swipe)";
		}

		System.out.printf("[%s] %s target=%s source=%s consumed=%s%s%n",
				where, type, target, source, e.isConsumed(), extra);
	}
}