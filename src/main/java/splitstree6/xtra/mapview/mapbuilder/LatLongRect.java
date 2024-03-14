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

package splitstree6.xtra.mapview.mapbuilder;

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

	/**
	 * Calculates and returns the maximum latitude value within the geographic rectangle.
	 *
	 * @return The maximum latitude value within the rectangle.
	 */
	public double maxLatitude() {
		return minLatitude + rangeLatitude;
	}

	/**
	 * Calculates and returns the maximum longitude value within the geographic rectangle.
	 *
	 * @return The maximum longitude value within the rectangle.
	 */
	public double maxLongitude() {
		return minLongitude + rangeLongitude;
	}

	/**
	 * Creates a rectangular geographic region (LatLongRect) that encloses a collection of latitude/longitude points,
	 * considering the windows aspect ratio, a proportionmarigin and minimum/maximum values for longitude/latitude.
	 *
	 * @param latLongPoints   A collection of latitude/longitude points to be enclosed by the rectangle.
	 * @param proportionMargin  The proportion margin to be added to the latitude and longitude ranges.
	 * @param aspectRatio  The desired aspect ratio of the resulting rectangle.
	 * @return  A LatLongRect object representing the computed rectangular geographic region.
	 */
	public static LatLongRect create(Collection<Point2D> latLongPoints, double proportionMargin, double aspectRatio) {
		var minLat = latLongPoints.stream().mapToDouble(Point2D::getX).min().orElse(0);
		var maxLat = latLongPoints.stream().mapToDouble(Point2D::getX).max().orElse(0);
		var minLong = latLongPoints.stream().mapToDouble(Point2D::getY).min().orElse(0);
		var maxLong = latLongPoints.stream().mapToDouble(Point2D::getY).max().orElse(0);


		var rangeLat = Math.abs(minLat - maxLat);
		var rangeLong = Math.abs(minLong - maxLong);

		// Add proportion margin to the longtitude/latitude range
		minLong -= proportionMargin * rangeLong;
		maxLong += proportionMargin * rangeLong;
		minLat -= proportionMargin * rangeLat;
		maxLat += proportionMargin * rangeLat;



		// Modify range latitude to minimum size
		rangeLat = Math.abs(minLat - maxLat);
		rangeLong = Math.abs(minLong - maxLong);
		if(rangeLat < 10){
			var addLat = 10 - rangeLat;
			minLat -= addLat/2;
			maxLat += addLat/2;
			rangeLat = 10;
		}

		// Modify range Longtitude to minimum size
		if(rangeLong < 15){
			var addLong = 15 - rangeLong;
			minLong -= addLong/2;
			maxLong += addLong/2;
			rangeLong = 15;
		}



		// Fit the computed rectangle to the required aspect ratio
		if(rangeLong/rangeLat < aspectRatio){
			var addLong = (rangeLat*aspectRatio) - rangeLong;
			minLong -= addLong/2;
			maxLong += addLong/2;
		}else if(rangeLong/rangeLat > aspectRatio){
			var addLat = (rangeLong/aspectRatio) - rangeLat;
			minLat -= addLat/2;
			maxLat += addLat/2;
		}

		// Distribute surplus Latitude
		if(minLat < bottomLatitude){
			maxLat += (bottomLatitude - minLat);
			if(maxLat > topLatitude) maxLat = topLatitude;
			minLat = bottomLatitude;
		}
		if(maxLat > topLatitude){
			minLat -= (maxLat - topLatitude);
			if(minLat < bottomLatitude)minLat = bottomLatitude;
			maxLat = topLatitude;
		}

		// Distribute surplus Longtitude
		if(minLong < leftLongitude){
			maxLong += (leftLongitude - minLong);
			if(maxLong > rightLongitude)maxLong = rightLongitude;
			minLong = leftLongitude;
		}
		if(maxLong > rightLongitude){
			minLong -= (maxLong - rightLongitude);
			if(minLong < leftLongitude)minLong = leftLongitude;
			maxLong = rightLongitude;
		}


		rangeLong = Math.abs(minLong - maxLong);
		rangeLat = Math.abs(minLat - maxLat);
		return new LatLongRect(minLat, minLong, rangeLat, rangeLong);
	}
}
