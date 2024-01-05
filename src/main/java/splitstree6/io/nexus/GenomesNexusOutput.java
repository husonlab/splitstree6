/*
 * GenomesNexusOutput.java Copyright (C) 2024 Daniel H. Huson
 *
 * (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package splitstree6.io.nexus;

import jloda.util.StringUtils;
import splitstree6.data.GenomesBlock;
import splitstree6.data.TaxaBlock;

import java.io.IOException;
import java.io.Writer;

/**
 * genomes nexus output
 * Daniel Huson, 3.2020
 */
public class GenomesNexusOutput extends NexusIOBase implements INexusOutput<GenomesBlock> {
	/**
	 * write a block in nexus format
	 */
	@Override
	public void write(Writer w, TaxaBlock taxaBlock, GenomesBlock genomesBlock) throws IOException {
		var format = genomesBlock.getFormat();

		w.write("\nBEGIN " + GenomesBlock.BLOCK_NAME + ";\n");
		writeTitleAndLink(w);
		w.write("DIMENSIONS ntax=" + genomesBlock.getGenomes().size() + ";\n");
		w.write("FORMAT");
		if (format.isOptionLabels())
			w.write(" labels=yes");
		else
			w.write(" labels=no");
		if (format.isOptionAccessions())
			w.write(" accessions=yes");
		else
			w.write(" accessions=no");
		if (format.isOptionMultiPart())
			w.write(" multiPart=yes");
		else
			w.write(" multiPart=no");

		w.write(" dataType=" + format.getCharactersType().name());

		w.write(";\n");

		// write matrix:
		{
			w.write("MATRIX\n");

			var genomes = genomesBlock.getGenomes();
			for (var g = 0; g < genomes.size(); g++) {
				var genome = genomes.get(g);
				var first = true;
				if (format.isOptionLabels()) {
					w.write(String.format("\t'%s'", genome.getName()));
					first = false;
				}
				if (format.isOptionAccessions()) {
					if (first) {
						first = false;
						w.write("\t");
					} else
						w.write(" ");
					w.write(String.format("%s", genome.getAccession()));
				}
				w.write(first ? "\t" : " ");
				if (format.isOptionMultiPart()) {
					w.write(String.format(" %d %d", genome.getLength(), genome.getNumberOfParts()));

					var parts = genome.getParts();
					for (var part : parts) {
						w.write(" " + part.getLength() + " ");
						if (part.getFile() != null) {
							w.write(String.format("'file://%s' %d", StringUtils.protectBackSlashes(part.getFile()), part.getOffset()));
						} else
							w.write(StringUtils.toString(part.getSequence())); // todo: pretty print sequence...
					}
				} else {
					var part = genome.getParts().get(0);
					w.write(" " + part.getLength() + " ");

					if (part.getFile() != null) {
						w.write(String.format("'file://%s' %d", StringUtils.protectBackSlashes(part.getFile()), part.getOffset()));
					} else
						w.write(StringUtils.toString(part.getSequence())); // todo: pretty print sequence...
				}
				if (g < genomes.size() - 1)
					w.write(",");
				w.write("\n");
			}
			w.write(";\n");
		}

		w.write("END; [" + GenomesBlock.BLOCK_NAME + "]\n");
	}
}
