/*
 *  LatLongRect.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.xtra.mapview;

import javafx.geometry.Point2D;

import java.util.Collection;

/**
 * a rectangle describing a range of latitude and longitude coordinates
 *
 * @param minLatitude
 * @param minLongitude
 * @param rangeLatitude
 * @param rangeLongitude Daniel Huson, 12.2023
 */
public record LatLongRect(double minLatitude, double minLongitude, double rangeLatitude, double rangeLongitude) {
	public static final double leftLongitude = -180.0;
	public static final double rightLongitude = 180.0;

	public static final double topLatitude = 90.0;

	public static final double bottomLatitude = -90.0;

	public double maxLatitude() {
		return minLatitude + rangeLatitude;
	}

	public double maxLongitude() {
		return minLongitude + rangeLongitude;
	}

	public static LatLongRect create(Collection<Point2D> latLongPoints, double proportionMargin) {
		var minLat = latLongPoints.stream().mapToDouble(Point2D::getX).min().orElse(0);
		var maxLat = latLongPoints.stream().mapToDouble(Point2D::getX).max().orElse(0);
		var minLong = latLongPoints.stream().mapToDouble(Point2D::getY).min().orElse(0);
		var maxLong = latLongPoints.stream().mapToDouble(Point2D::getY).max().orElse(0);
		if (proportionMargin != 0) { // add a margin
			var rangeLong = (maxLong - minLong);
			minLong = Math.max(leftLongitude, minLong - proportionMargin * rangeLong);
			maxLong = Math.min(rightLongitude, maxLong + proportionMargin * rangeLong);
			var rangeLat = (maxLat - minLat);
			minLat = Math.max(bottomLatitude, minLat - proportionMargin * rangeLat);
			maxLat = Math.min(topLatitude, maxLat + proportionMargin * rangeLat);
		}
		return new LatLongRect(minLat, minLong, maxLat - minLat, maxLong - minLong);
	}
}
