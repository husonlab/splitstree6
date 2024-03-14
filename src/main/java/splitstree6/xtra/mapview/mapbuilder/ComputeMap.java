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

package splitstree6.xtra.mapview.mapbuilder;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TraitsBlock;
import splitstree6.xtra.mapview.nodes.GeoTrait;
import splitstree6.xtra.mapview.Model;

import java.util.ArrayList;
import java.util.HashMap;


/**
 * Compute class that processes a model and returns the corresponding GeoTrait objects
 * Nikolas Kreisz, 11.2023
 */
public class ComputeMap {

	/**
	 * Creates a list of GeoTraits based on the given Model.
	 *
	 * @param model The Model object containing taxa and traits information.
	 * @return An ArrayList of GeoTraits representing traits extracted from the model.
	 */
	public static ArrayList<GeoTrait> apply(Model model) {

		// Create Geotraits from the model
		ArrayList<GeoTrait> geoTraits = new ArrayList<>();
		TaxaBlock taxaBlock = model.getTaxaBlock();
		TraitsBlock traitsBlock = model.getTaxaBlock().getTraitsBlock();

		for(int i = 1; i <= traitsBlock.size(); i++){
			ArrayList<String> taxa = new ArrayList<>();
			HashMap<String, Integer> composition = new HashMap<>();

			for(int j = 1; j <= taxaBlock.size(); j++){
				if((int) traitsBlock.getTraitValue(j,i) > 0){
					taxa.add(taxaBlock.get(j).getName());
					composition.put(taxaBlock.get(j).getName(), (int) traitsBlock.getTraitValue(j,i));
				}
			}

			GeoTrait geoTrait = new GeoTrait(
					traitsBlock.getTraitLongitude(i),
					traitsBlock.getTraitLatitude(i),
					traitsBlock.getNTraits(),
					taxa,
					composition
			);
			geoTraits.add(geoTrait);
		}
		return geoTraits;
	}
}
