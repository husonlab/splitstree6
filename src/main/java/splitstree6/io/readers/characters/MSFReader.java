/*
 * MSFReader.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.io.readers.characters;

import jloda.util.FileLineIterator;
import jloda.util.IOExceptionWithLineNumber;
import jloda.util.StringUtils;
import jloda.util.progress.ProgressListener;
import splitstree6.data.CharactersBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.CharactersType;
import splitstree6.data.parts.Taxon;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import static splitstree6.io.readers.characters.FastAReader.checkIfCharactersValid;

public class MSFReader extends CharactersReader {
	private CharactersType dataType = CharactersType.Unknown;

	public MSFReader() {
		setFileExtensions("msf");
	}

	@Override
	public void read(ProgressListener progressListener, String fileName, TaxaBlock taxaBlock, CharactersBlock dataBlock) throws IOException {
		Map<String, String> taxa2seq = new LinkedHashMap<>();
		boolean charStarted = false;

		try (FileLineIterator it = new FileLineIterator(fileName)) {

			progressListener.setMaximum(it.getMaximumProgress());
			progressListener.setProgress(0);
			int linesCounter = 0;

			String firstLine = it.next();
			if (firstLine.startsWith("!!NA"))
				dataType = CharactersType.DNA; //todo estimate dna or rna
			else if (firstLine.startsWith("!!AA"))
				dataType = CharactersType.Protein;
			else
				dataType = CharactersType.Standard;

			while (it.hasNext()) {

				linesCounter += 1;
				final String line = it.next();
				final String line_no_spaces = line.replaceAll("\\s", "");

				if (!charStarted && line.contains("Name:")) {
					StringTokenizer tokens = new StringTokenizer(line);
					tokens.nextToken();
					String taxon = tokens.nextToken();

					if (taxa2seq.containsKey(taxon))
						throw new IOExceptionWithLineNumber("Repeated taxon name", linesCounter);

					taxaBlock.add(new Taxon(taxon));
					taxa2seq.put(taxon, "");
				}

				if (line_no_spaces.equals("//")) {
					charStarted = true;
				}

				if (charStarted) {
					String taxon = cutTaxonFromLine(line, taxa2seq.keySet());
					if (!taxon.equals("")) {
						String chars = line.replaceAll("\\s+", "");
						chars = chars.substring(taxon.length());
						checkIfCharactersValid(chars, linesCounter, "" + getMissing() + getGap());
						taxa2seq.replace(taxon, taxa2seq.get(taxon) + chars);
					}
				}

				progressListener.setProgress(it.getProgress());
			}
		}

		String firstKey = (String) taxa2seq.keySet().toArray()[0];
		int nchars = taxa2seq.get(firstKey).length();
		int ntax = taxa2seq.size();

		setCharacters(taxa2seq, ntax, nchars, dataBlock);

	}

	private void setCharacters(Map<String, String> taxa2seq, int ntax, int nchar, CharactersBlock characters) throws IOException {
		characters.setDimension(ntax, nchar);
		characters.setDataType(this.dataType);
		// todo check valid characters
		characters.setMissingCharacter(getMissing());
		characters.setGapCharacter(getGap());

		int labelsCounter = 1;

		for (String label : taxa2seq.keySet()) {
			if (taxa2seq.get(label).length() != nchar)
				throw new IOException("The sequences in the alignment have different lengths! " +
									  "Length of sequence: " + label + " differ from the length of previous sequences :" + nchar);

			for (int j = 1; j <= nchar; j++) {
				char symbol = Character.toLowerCase(taxa2seq.get(label).charAt(j - 1));
				characters.set(labelsCounter, j, symbol);
			}
			labelsCounter++;
		}
	}

	private String cutTaxonFromLine(String line, Set<String> taxaKeys) {
		for (String t : taxaKeys) {
			if (line.contains(t))
				return t;
		}
		return "";
	}


	@Override
	public boolean accepts(String fileName) {
		if (!super.accepts(fileName))
			return false;
		else {
			return acceptsFile(fileName);
		}
	}

	public boolean acceptsFirstLine(String text) {
		var line = StringUtils.getFirstLine(text);
		return line.equalsIgnoreCase("!!NA_MULTIPLE_ALIGNMENT 1.0")
			   || line.equalsIgnoreCase("!!AA_MULTIPLE_ALIGNMENT 1.0")
			   || line.equalsIgnoreCase("!!??_MULTIPLE_ALIGNMENT 1.0");
	}
}
