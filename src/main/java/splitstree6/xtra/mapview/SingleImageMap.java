/*
 *  SingleImageMap.java Copyright (C) 2024 Daniel H. Huson
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
import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/**
 * this provides a map rectangle from a single image map file
 * Daniel Huson, 1.2024
 */
public class SingleImageMap {
	private static Image image;

	static {
		//setMap("Equal-Earth-Map-0.jpg");
		setMap("2_no_clouds_16k.jpg");
	}

	/**
	 * set the image to use
	 *
	 * @param mapImageResourceName the name of a map resource file
	 */
	public static void setMap(String mapImageResourceName) {
		try (var ins = SingleImageMap.class.getResourceAsStream(mapImageResourceName)) {
			image = new Image(ins);
		} catch (IOException ex) {
			System.err.println("Failed to load map: " + ex.getMessage());
			System.exit(1);
		}
	}

	/**
	 * creates a map pane that contains the given lat/long coordinates and fits the given target
	 *
	 * @param latLongPoints list of points to cover
	 * @param targetWidth   target width for map
	 * @param targetHeight  target height for map
	 * @return map pane
	 */
	public static MapPane createMapPane(Collection<Point2D> latLongPoints, double targetWidth, double targetHeight) {
		var aspectRatio = targetWidth/targetHeight;
		return createMapPane(LatLongRect.create(latLongPoints, 0.10, aspectRatio), targetWidth, targetHeight);
	}

	/**
	 * creates a map pane that contains the given rectangle of lat/long coordinates and fits the given target
	 *
	 * @param rectangle
	 * @param targetWidth
	 * @param targetHeight
	 * @return map pane
	 */
	public static MapPane createMapPane(LatLongRect rectangle, double targetWidth, double targetHeight) {

		var targetSize = Math.max(targetWidth, targetHeight);

		var xOffset = ((rectangle.minLongitude() - LatLongRect.leftLongitude) / (LatLongRect.rightLongitude - LatLongRect.leftLongitude)) * image.getWidth();
		var yOffset = ((LatLongRect.topLatitude - rectangle.maxLatitude()) / (LatLongRect.topLatitude - LatLongRect.bottomLatitude)) * image.getHeight();

		var width = (rectangle.rangeLongitude() / (LatLongRect.rightLongitude - LatLongRect.leftLongitude)) * image.getWidth();
		var height = (rectangle.rangeLatitude() / (LatLongRect.topLatitude - LatLongRect.bottomLatitude)) * image.getHeight();
		/*
		double xAddOffset = 0;
		double yAddOffset = 0;

		System.out.println("Width: " + width +" Height: " + height + " xOffset: " + xOffset +" yOffset: " + yOffset);

		if(width/height > targetWidth/targetHeight){
			var modifiedHeight = width / (targetWidth/targetHeight);
			yAddOffset = 0.5 * (modifiedHeight-height);
			height = modifiedHeight;
		}else if(width/height < targetWidth/targetHeight){
			var modifiedWidth = height / (targetWidth/targetHeight);
			xAddOffset = 0.5 * (modifiedWidth-width);
			width = modifiedWidth;
		}

		final double xTotalOffset = xAddOffset + xOffset;
		final double yTotalOffset = yAddOffset + yOffset;
		xOffset += xAddOffset;
		yOffset += yAddOffset;
		*/
		System.out.println("Width: " + width +" Height: " + height + " xOffset: " + xOffset +" yOffset: " + yOffset);

		var factor = Math.min(targetSize / width, targetSize / height);

		Function<Double, Double> latitudeYFunction = latitude ->
				factor * (((LatLongRect.topLatitude - latitude) / (LatLongRect.topLatitude - LatLongRect.bottomLatitude)) * image.getHeight() - yOffset);

		Function<Double, Double> longtitudeXFunction = longitude ->
				factor * (((longitude - LatLongRect.leftLongitude) / (LatLongRect.rightLongitude - LatLongRect.leftLongitude)) * image.getWidth() - xOffset);



		var croppedImage = new WritableImage((int) width, (int) height);


		var pixelReader = image.getPixelReader();
		for (var i = 0; i < (int) width; i++) {
			for (var j = 0; j < (int) height; j++) {
				int pixelColor = pixelReader.getArgb(i + (int) xOffset, j + (int) yOffset);
				croppedImage.getPixelWriter().setArgb(i, j, pixelColor);
			}
		}
		var imageView = new ImageView(croppedImage);
		imageView.setPreserveRatio(true);
		imageView.setFitWidth(targetWidth);
		imageView.setFitHeight(targetHeight);

		var mapPane = new MapPane(new Rectangle2D(0, 0, targetWidth, targetHeight), List.of(imageView), latitudeYFunction, longtitudeXFunction);
		mapPane.setBounds(rectangle);
		return mapPane;

	}
}
