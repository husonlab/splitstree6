/*
 * TaxaNexusInput.java Copyright (C) 2024 Daniel H. Huson
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

import jloda.util.IOExceptionWithLineNumber;
import jloda.util.parse.NexusStreamParser;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.Taxon;

import java.io.IOException;

/**
 * taxa nexus input
 * Daniel Huson, 2.2018
 */
public class TaxaNexusInput extends NexusIOBase {
	public static final String SYNTAX = """
			BEGIN TAXA;
				[TITLE title;]
				DIMENSIONS NTAX=number-of-taxa;
				[TAXLABELS
					list-of-labels
				;]
				[TAXINFO
					list-of-info-items (use 'null' for missing item)
				;]
				[DISPLAYLABELS
					list-of-html-strings (use 'null' for missing item)
				;]
			END;
			""";

	public static final String DESCRIPTION = """
			This block maintains the list of all taxa in the analysis.
			There is a fixed number (nTax) of taxa and each has an id 1..nTax and label associated with it.
			Optionally, an info string can be provided for each taxon.
			Optionally, a display label can be provided for each taxon. This many include certain HTML tags
			that are used to render the label.
			""";

	public String getSyntax() {
		return SYNTAX;
	}

	/**
	 * parse a taxa block
	 */
	public void parse(NexusStreamParser np, TaxaBlock taxaBlock) throws IOException {
		taxaBlock.clear();
		if (np.peekMatchIgnoreCase("#nexus"))
			np.matchIgnoreCase("#nexus"); // skip header line if it is the first line

		np.matchBeginBlock("TAXA");
		parseTitleAndLink(np);

		np.matchIgnoreCase("DIMENSIONS ntax=");
		final var ntax = np.getInt();
		taxaBlock.setNtax(ntax);
		np.matchIgnoreCase(";");

		var labelsDetected = false;

		if (np.peekMatchIgnoreCase("taxLabels")) // grab labels now
		{
			np.matchIgnoreCase("taxLabels");
			if (np.peekMatchIgnoreCase("_detect_")) // for compatibility with SplitsTree3:
			{
				np.matchIgnoreCase("_detect_");
			} else {
				for (var t = 1; t <= ntax; t++) {
					final var taxonName = np.getLabelRespectCase();
					if (taxonName.equals(";"))
						throw new IOExceptionWithLineNumber("expected " + ntax + " taxon names, found: " + (t - 1), np.lineno());
					final Taxon taxon = new Taxon(taxonName);

					if (taxaBlock.indexOf(taxon) != -1) {
						throw new IOExceptionWithLineNumber("taxon name '" + taxonName + "' appears multiple times, at " + taxaBlock.indexOf(taxon) + " and " + t, np.lineno());
					}
					taxaBlock.add(taxon);
				}
				labelsDetected = true;
			}
			if (!np.peekMatchIgnoreCase(";")) {
				int count = ntax;
				while (!np.peekMatchIgnoreCase(";")) {
					np.getWordRespectCase();
					count++;
				}
				throw new IOExceptionWithLineNumber(np.lineno(), "expected " + ntax + " taxon names, found: " + count);

			}
			np.matchIgnoreCase(";");
		}

		if (labelsDetected && np.peekMatchIgnoreCase("displayLabels")) // get display labels
		{
			np.matchIgnoreCase("displayLabels");

			for (var t = 1; t <= ntax; t++) {
				final var displayLabel = np.getLabelRespectCase();
				if (!displayLabel.equals("null")) //  explicitly the word "null"
					taxaBlock.get(t).setDisplayLabel(displayLabel);
			}
			np.matchIgnoreCase(";");
		}

		if (labelsDetected && np.peekMatchIgnoreCase("taxInfo")) // get info for labels
		{
			np.matchIgnoreCase("taxInfo");

			for (var t = 1; t <= ntax; t++) {
				final String info = np.getLabelRespectCase();
				if (!info.equals("null")) //  explicitly the word "null"
					taxaBlock.get(t).setInfo(info);
			}
			np.matchIgnoreCase(";");
		}

		np.matchEndBlock();
	}

	public static void captureComments(NexusStreamParser np, TaxaBlock taxaBlock) {
		var comments = np.popComments();
		if (comments != null) {
			if (taxaBlock.getComments() == null)
				taxaBlock.setComments(comments);
			else
				taxaBlock.setComments(taxaBlock.getComments() + "\n" + comments);
		}
	}
}
