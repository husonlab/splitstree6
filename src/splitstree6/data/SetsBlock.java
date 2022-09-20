/*
 *  SetsBlock.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.data;


import jloda.util.parse.NexusStreamParser;
import splitstree6.io.nexus.SetsNexusInput;
import splitstree6.io.nexus.SetsNexusOutput;
import splitstree6.workflow.DataBlock;
import splitstree6.workflow.DataTaxaFilter;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;

/**
 * sets block
 * Daniel Huson, 9.2022
 */
public class SetsBlock extends DataBlock implements IAdditionalDataBlock {
	public static final String BLOCK_NAME = "SETS";

	private final ArrayList<TaxSet> taxSets = new ArrayList<>();
	private final ArrayList<CharSet> charSets = new ArrayList<>();

	public SetsBlock() {
	}

	public SetsBlock(TaxaBlock srcTaxa, SetsBlock src) {
		this();
		setInducedSets(srcTaxa, src, srcTaxa);
	}

	public void clear() {
		taxSets.clear();
		charSets.clear();
	}

	public int size() {
		return taxSets.size() + charSets.size();
	}

	public ArrayList<TaxSet> getTaxSets() {
		return taxSets;
	}

	public ArrayList<CharSet> getCharSets() {
		return charSets;
	}

	@Override
	public void updateShortDescription() {
		setShortDescription("%,d taxsets and %,d charsets".formatted(taxSets.size(), charSets.size()));

	}

	@Override
	public DataTaxaFilter<? extends DataBlock, ? extends DataBlock> createTaxaDataFilter() {
		return null;
	}

	@Override
	public String getBlockName() {
		return BLOCK_NAME;
	}

	@Override
	public SetsBlock newInstance() {
		return (SetsBlock) super.newInstance();
	}

	public void setInducedSets(TaxaBlock srcTaxa, SetsBlock srcSets, TaxaBlock targetTaxa) {
		clear();
		var seen = new HashSet<BitSet>();
		for (var taxSet : srcSets.getTaxSets()) {
			var set = new BitSet();
			for (var tarId = 1; tarId <= targetTaxa.getNtax(); tarId++) {
				var taxon = targetTaxa.get(tarId);
				var srcId = srcTaxa.indexOf(taxon);
				if (taxSet.get(srcId)) {
					set.set(tarId);
				}
			}
			if (set.cardinality() > 0 && !seen.contains(set)) {
				getTaxSets().add(new TaxSet(taxSet.getName(), set));
				seen.add(set);
			}
		}
		for (var charSet : srcSets.getCharSets()) {
			getCharSets().add(new CharSet(charSet.getName(), charSet));
		}
	}

	public static class TaxSet extends BitSet {
		private String name;

		public TaxSet(String name, BitSet bits) {
			this.name = name;
			or(bits);
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}

	public static class CharSet extends BitSet {
		private String name;

		public CharSet(String name, BitSet bits) {
			this.name = name;
			or(bits);
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}

	public static void main(String[] args) throws IOException {
		var taxaBlock = new TaxaBlock();
		taxaBlock.addTaxonByName("first");
		taxaBlock.addTaxonByName("second");
		taxaBlock.addTaxonByName("third");
		taxaBlock.addTaxonByName("four");

		var input = """
				begin sets;
				taxset all=first second third four;
				taxset first=1;
				taxset last=4;
				charset pos1=1-100\\3;
				charset pos2=2-100\\3;
				charset pos3=3-100\\3;
				charset prefix=1-100;
				charset coding=1-21\\3 40-70\\3;
				charset some=1 3 5 66 99;
				end;
				""";

		var setsBlock = new SetsBlock();
		try (var np = new NexusStreamParser(new StringReader(input))) {
			(new SetsNexusInput()).parse(np, taxaBlock, setsBlock);
			var w = new StringWriter();
			(new SetsNexusOutput()).write(w, taxaBlock, setsBlock);
			System.err.println(w);
		}
	}
}
