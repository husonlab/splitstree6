/*
 *  CharactersFilterTest.java Copyright (C) 2026 Daniel H. Huson
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

package splitstree6.algorithms.characters.characters2characters;

import jloda.util.progress.ProgressSilent;
import org.junit.jupiter.api.Test;
import splitstree6.data.CharactersBlock;
import splitstree6.data.parts.CharactersType;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static splitstree6.testing.TestUtils.*;

/**
 * the filters on characters: which sites and which rows remain
 */
public class CharactersFilterTest {
	/**
	 * the sites, as columns, of five sequences:
	 * 1 constant, 2 one sequence differs, 3 two states twice each, 4 three states of which two occur once,
	 * 5 constant with a gap, 6 two states twice each with a gap, 7 constant, 8 three states, two of them twice,
	 * 9 constant with a missing state
	 */
	private static final String[] ROWS = {
			"aaaaaaaaa",
			"aaaaaaaaa",
			"aacaacaca",
			"aaccacaca",
			"acgg--ag?"};

	private static CharactersBlock input() {
		return characters(CharactersType.DNA, "acgt", ROWS);
	}

	private static List<String> columns(CharactersBlock chars) {
		var result = new ArrayList<String>();
		for (var c = 1; c <= chars.getNchar(); c++) {
			var buf = new StringBuilder();
			for (var t = 1; t <= chars.getNtax(); t++) buf.append(chars.get(t, c));
			result.add(buf.toString());
		}
		return result;
	}

	private static List<String> columns(int... sites) {
		var all = columns(input());
		var result = new ArrayList<String>();
		for (var site : sites) result.add(all.get(site - 1));
		return result;
	}

	private static CharactersBlock run(CharactersFilter filter) {
		var child = new CharactersBlock();
		filter.compute(new ProgressSilent(), taxa(ROWS.length), input(), child);
		return child;
	}

	@Test
	void withoutOptionsAllSitesAreKept() {
		var filter = new CharactersFilter();
		var child = run(filter);
		assertEquals(columns(input()), columns(child));
		assertFalse(filter.isActive());
		assertEquals(CharactersType.DNA, child.getDataType());
		assertEquals(input().getSymbols(), child.getSymbols());
		assertEquals('-', child.getGapCharacter());
		assertEquals('?', child.getMissingCharacter());
	}

	/**
	 * the option to exclude sites with a gap once had an effect only together with one of the options for constant or
	 * uninformative sites
	 */
	@Test
	void sitesWithAGapAreExcluded() {
		var filter = new CharactersFilter();
		filter.setOptionExcludeGapSites(true);
		assertEquals(columns(1, 2, 3, 4, 7, 8, 9), columns(run(filter)));
		assertTrue(filter.isActive());
	}

	/**
	 * a site is constant if all sequences that have a state there have the same one; gaps and missing states do not
	 * make a site variable
	 */
	@Test
	void constantSitesAreExcluded() {
		var filter = new CharactersFilter();
		filter.setOptionExcludeConstantSites(true);
		assertEquals(columns(2, 3, 4, 6, 8), columns(run(filter)));
	}

	/**
	 * a site is parsimony informative if at least two states occur in at least two sequences each. Sites with three
	 * states of which two occur once, and constant sites, were once kept
	 */
	@Test
	void parsimonyUninformativeSitesAreExcluded() {
		var filter = new CharactersFilter();
		filter.setOptionExcludeParsimonyUninformativeSites(true);
		assertEquals(columns(3, 6, 8), columns(run(filter)));
	}

	@Test
	void codonPositionsAreExcluded() {
		var filter = new CharactersFilter();
		filter.setOptionExcludeFirstCodonPosition(true);
		assertEquals(columns(2, 3, 5, 6, 8, 9), columns(run(filter)));
		filter = new CharactersFilter();
		filter.setOptionExcludeSecondCodonPosition(true);
		filter.setOptionExcludeThirdCodonPosition(true);
		assertEquals(columns(1, 4, 7), columns(run(filter)));
	}

	@Test
	void optionsCombine() {
		var filter = new CharactersFilter();
		filter.setOptionExcludeGapSites(true);
		filter.setOptionExcludeConstantSites(true);
		filter.setOptionExcludeThirdCodonPosition(true);
		assertEquals(columns(2, 4, 8), columns(run(filter)));
	}

	/**
	 * whether the sequences contain ambiguity codes must survive the filter, because the distance methods ask for it
	 */
	@Test
	void ambiguityCodesAreStillKnownAfterFiltering() {
		var parent = characters(CharactersType.DNA, "acgt", "aay", "acc", "agc");
		parent.setHasAmbiguityCodes(true);
		var filter = new CharactersFilter();
		filter.setOptionExcludeConstantSites(true);
		var child = new CharactersBlock();
		filter.compute(new ProgressSilent(), taxa(3), parent, child);
		assertEquals(2, child.getNchar());
		assertTrue(child.isHasAmbiguityCodes());
	}

	/**
	 * the taxa filter keeps the rows of the remaining taxa, in the order of the new taxa block
	 */
	@Test
	void taxaFilterKeepsTheRowsOfTheRemainingTaxa() throws Exception {
		var original = taxa(5);
		var modified = new splitstree6.data.TaxaBlock();
		modified.addTaxaByNames(List.of("t4", "t1", "t5"));
		var child = new CharactersBlock();
		new CharactersTaxaFilter().filter(new ProgressSilent(), original, modified, input(), child);
		assertEquals(3, child.getNtax());
		assertEquals(9, child.getNchar());
		assertEquals(ROWS[3], new String(child.getRow0(0)));
		assertEquals(ROWS[0], new String(child.getRow0(1)));
		assertEquals(ROWS[4], new String(child.getRow0(2)));
		assertEquals(CharactersType.DNA, child.getDataType());
	}

	@Test
	void taxaFilterWithDisabledCharacters() throws Exception {
		var filter = new CharactersTaxaFilter();
		filter.setOptionDisabledCharacters(new int[]{1, 5, 9});
		var child = new CharactersBlock();
		filter.filter(new ProgressSilent(), taxa(5), taxa(5), input(), child);
		assertEquals(columns(2, 3, 4, 6, 7, 8), columns(child));
	}
}
