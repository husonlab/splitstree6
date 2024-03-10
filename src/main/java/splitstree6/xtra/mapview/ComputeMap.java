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
import splitstree6.data.TaxaBlock;
import splitstree6.data.TraitsBlock;
import java.util.ArrayList;
import java.util.HashMap;


/**
 * compute a map visualization for the given model
 * Niko Kreisz, 11.2023
 */
public class ComputeMap {
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
			geoTrait.printGeotrait();
			geoTraits.add(geoTrait);
		}


		return geoTraits;
	}
}

/*
		System.out.println("Taxablock: " + taxaBlock.getNtax());
		for (var taxa : taxaBlock.getTaxa()){
			System.out.println("Taxa-name: " + taxa.getName());
			//System.out.println("Taxa-Info: " + taxa.getInfo());
		}

		System.out.println("Traitsblock size: " + traitsBlock.size());
		System.out.println("Labels " + traitsBlock.getTraitLabels().toString());
		System.out.println("Has lontitude: " + traitsBlock.isSetLatitudeLongitude());
		System.out.println("val " + traitsBlock.getTraitValue(5, 3));
		System.out.println("Latitude " + traitsBlock.getTraitLatitude(2));
		System.out.println("Latitude " + traitsBlock.getTraitLatitude(3));
		System.out.println("Latitude " + traitsBlock.getTraitLatitude(4));
		System.out.println("Latitude " + traitsBlock.getTraitLatitude(5));

		for(int i = 1; i <= traitsBlock.size(); i++){
			System.out.println(traitsBlock.getTraitLongitude(i));
			System.out.println(traitsBlock.getTraitLatitude(i));
			for(int j = 1; j <= taxaBlock.size(); j++){
				System.out.print(taxaBlock.get(j).getName());
				System.out.println(" " + traitsBlock.getTraitValue(j,i));
			}
		}
*/