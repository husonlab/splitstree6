/*
 *  CollapseIdenticalHyplotypes.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.utils;

import jloda.fx.window.NotificationManager;
import jloda.util.StringUtils;
import jloda.util.Triplet;
import splitstree6.data.CharactersBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TraitsBlock;
import splitstree6.data.parts.Taxon;

import java.util.*;

public class CollapseIdenticalHyplotypes {
	public static Triplet<TaxaBlock, TraitsBlock, CharactersBlock> apply(TaxaBlock inputTaxa, CharactersBlock inputCharacters) {
		var taxLabelMap = new TreeMap<Integer, List<String>>();
		for (var t = 1; t <= inputTaxa.getNtax(); t++) {
			taxLabelMap.computeIfAbsent(t, k -> new ArrayList<>()).add(inputTaxa.getLabel(t));
		}

		var inputMatrix = inputCharacters.getMatrix();
		var countCollapsed = 0;
		for (var s = 1; s <= inputTaxa.getNtax(); s++) {
			if (!taxLabelMap.get(s).isEmpty()) {
				for (var t = s + 1; t <= inputTaxa.getNtax(); t++) {
					if (identical(inputMatrix[s - 1], inputMatrix[t - 1])) {
						taxLabelMap.get(s).addAll(taxLabelMap.get(t));
						taxLabelMap.put(t, new ArrayList<>());
						countCollapsed++;
					}
				}
			}
		}
		var inputTraits = inputTaxa.getTraitsBlock();

		var outputNTax = (int) taxLabelMap.entrySet().stream().filter(e -> !e.getValue().isEmpty()).count();
		var nchar = inputCharacters.getNchar();
		var outputTaxa = new TaxaBlock();

		var outputCharacters = new CharactersBlock();
		outputCharacters.setDimension(outputNTax, nchar);
		outputCharacters.setFormat(inputCharacters.getFormat());
		outputCharacters.setStateLabeler(inputCharacters.getStateLabeler());
		outputCharacters.setDataType(inputCharacters.getDataType());
		outputCharacters.setDiploid(inputCharacters.isDiploid());
		outputCharacters.setGapCharacter(inputCharacters.getGapCharacter());
		outputCharacters.setMissingCharacter(inputCharacters.getMissingCharacter());
		outputCharacters.setSymbols(inputCharacters.getSymbols());
		outputCharacters.setRespectCase(inputCharacters.isRespectCase());
		outputCharacters.setUseCharacterWeights(inputCharacters.isUseCharacterWeights());
		outputCharacters.setUseCharacterLabels(inputCharacters.isUseCharacterLabels());

		var outputMatrix = outputCharacters.getMatrix();

		var outputTraits = new TraitsBlock();


		if (inputTraits == null || inputTraits.getNTraits() == 0)
			outputTraits.setDimensions(outputNTax, outputNTax);
		else
			outputTraits.setDimensions(outputNTax, 0);


		var outInMap = new HashMap<Integer, int[]>();

		{
			var which = 0;
			var outId = 0;
			for (var e : taxLabelMap.entrySet().stream().filter(e -> !e.getValue().isEmpty()).toList()) {
				var inId = e.getKey();
				var members = e.getValue();
				outId++;

				var name = (members.size() == 1 ? inputTaxa.getLabel(inId) : "TYPE" + (++which));

				if (inputTraits == null) {
					outputTraits.setTraitLabel(outId, name);
					for (var i = 1; i <= outputNTax; i++) {
						outputTraits.setTraitValue(outId, i, i == outId ? members.size() : 0);
					}
				}

				var taxon = new Taxon(name);
				taxon.setInfo(StringUtils.toString(members, ","));
				outputTaxa.add(taxon);
				outInMap.put(outputTaxa.indexOf(taxon), members.stream().mapToInt(inputTaxa::indexOf).toArray());
				System.arraycopy(inputMatrix[inId - 1], 0, outputMatrix[outId - 1], 0, inputMatrix[inId - 1].length);
			}
		}
		if (countCollapsed == 0) {
			NotificationManager.showInformation("All haplotypes unique");
			return null;
		} else {

			if (inputTraits != null) {
				for (var inTraitId = 1; inTraitId <= inputTraits.getNTraits(); inTraitId++) {
					if (inputTraits.isNumerical(inTraitId)) {
						var traitLabel = inputTraits.getTraitLabel(inTraitId);
						var outTraitId = outputTraits.addTrait(traitLabel);
						if (inputTraits.isSetLatitudeLongitude()) {
							outputTraits.setTraitLatitude(outTraitId, inputTraits.getTraitLatitude(inTraitId));
							outputTraits.setTraitLongitude(outTraitId, inputTraits.getTraitLongitude(inTraitId));
						}
						var traitId = inTraitId;
						for (var outTax = 1; outTax <= outputTaxa.getNtax(); outTax++) {
							var sum = Arrays.stream(outInMap.get(outTax)).mapToDouble(t -> inputTraits.getTraitValue(t, traitId)).sum();
							outputTraits.setTraitValue(outTax, outTraitId, (float) sum);
						}
					}
				}
			}
			NotificationManager.showInformation("Unique haplotypes: " + outputTaxa.getNtax());
			return new Triplet<>(outputTaxa, outputTraits, outputCharacters);
		}
	}

	private static boolean identical(char[] a, char[] b) {
		for (var i = 0; i < a.length; i++) {
			if (a[i] != b[i])
				return false;
		}
		return true;
	}
}
