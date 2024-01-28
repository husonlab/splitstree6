/*
 *  ComputeMap.java Copyright (C) 2024 Daniel H. Huson
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

import javafx.scene.Node;

import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TraitsBlock;
import splitstree6.data.parts.Taxon;

import java.util.ArrayList;
import java.util.HashMap;


/**
 * compute a map visualization for the given model
 * Niko Kreisz, 11.2023
 */
public class ComputeMap {
	public static ArrayList<Node> apply(Model model, double targetWidth, double targetHeight) {

		ArrayList<Node> diagrams = new ArrayList<>();
		int numOfTraits = model.getTaxaBlock().getTraitsBlock().getNTraits();
		ArrayList<GeoTrait> geoTraits = new ArrayList<>();
		TaxaBlock taxaBlock = model.getTaxaBlock();
		TraitsBlock traitsBlock = model.getTaxaBlock().getTraitsBlock();

		System.out.println(numOfTraits);
		System.out.println(traitsBlock.getTraitLabel(1));
		for(var taxa : taxaBlock.getTaxa()){
			System.out.println(taxa.getName());
			traitsBlock.getTraitId(taxa.getName());
		}


		for(int i = 0; i < numOfTraits; i++){
			ArrayList<String> taxaLabel = new ArrayList<>();
			HashMap<String, Integer> composition = new HashMap<>();



			for(Taxon taxon : model.getTaxaBlock().getTaxa()){
				taxaLabel.add(taxon.getName());
				composition.put(taxon.getName(), 4);
			}

			geoTraits.add(new GeoTrait(
					traitsBlock.getTraitLongitude(i),
					traitsBlock.getTraitLatitude(i),
					4,//model.getTaxaBlock().getNtax(),
					new ArrayList<>(),//taxaLabel,
					new HashMap<>() //composition
			));

			System.out.println("Number of traits" + geoTraits.size());
		}

		System.out.println(geoTraits.size());
		for(GeoTrait geoTrait : geoTraits){
			System.out.println("adding" + geoTrait.getLongtitude() + geoTrait.getLatitude());
			Circle circle = new Circle(5, Color.RED);
			circle.setTranslateX(geoTrait.getLatitude());
			circle.setTranslateY(geoTrait.getLongtitude());
			diagrams.add(circle);
		}

		diagrams = new ArrayList<>();



		return diagrams;
	}
}
