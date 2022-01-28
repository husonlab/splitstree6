/*
 * ClustalImporter.java Copyright (C) 2022 Daniel H. Huson
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
import jloda.util.FileUtils;
import jloda.util.IOExceptionWithLineNumber;
import jloda.util.progress.ProgressListener;
import splitstree6.data.CharactersBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.CharactersType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

import static splitstree6.io.readers.characters.FastAReader.checkIfCharactersValid;

/**
 * The sequence alignment outputs from CLUSTAL software often are given the default extension .aln.
 * CLUSTAL is an interleaved format. In a page-wide arrangement the
 * sequence name is in the first column and a part of the sequence’s data is right justified.
 * <p>
 * http://www.clustal.org/download/clustalw_help.txt
 * Daria Evseeva,05.08.2017, Daniel Huson, 3.2020
 */

public class ClustalImporter extends CharactersReader {

	public ClustalImporter() {
		setFileExtensions("aln", "clustal");
	}

	@Override
	public void read(ProgressListener progressListener, String fileName, TaxaBlock taxa, CharactersBlock characters) throws IOException {
		final Map<String, String> taxa2seq = new LinkedHashMap<>();

		int ntax;
		int nchar;
		var labels = new ArrayList<String>();
		var labelSet = new HashSet<String>();

		try (FileLineIterator it = new FileLineIterator(fileName)) {
			int sequenceInLineLength = 0;
			int counter = 0;
			progressListener.setMaximum(it.getMaximumProgress());
			progressListener.setProgress(0);

			for (var line : it.lines()) {
				counter++;
				if (line.toUpperCase().startsWith("CLUSTAL"))
					continue;
				if (!line.isBlank() && hasAlphabeticalSymbols(line)) {
					String tmpLine = line;

					// cut numbers from the end
					int lastSeqIndex = tmpLine.length();
					while (Character.isDigit(tmpLine.charAt(lastSeqIndex - 1)))
						lastSeqIndex--;
					tmpLine = tmpLine.substring(0, lastSeqIndex);

					String label;
					int labelIndex = tmpLine.indexOf(' ');
					if (labelIndex == -1 || labelIndex == 0)
						throw new IOExceptionWithLineNumber("No taxa label given", counter);
					else
						label = tmpLine.substring(0, labelIndex);
					if (!labelSet.contains(label)) {
						labels.add(label);
						labelSet.add(label);
					}

					tmpLine = tmpLine.replaceAll("\\s+", "");

					if (sequenceInLineLength == 0) sequenceInLineLength = tmpLine.substring(labelIndex).length();

					String allowedChars = "" + getMissing() + getGap();
					checkIfCharactersValid(tmpLine.substring(labelIndex), counter, allowedChars);
					if (!taxa2seq.containsKey(label)) {
						taxa2seq.put(label, tmpLine.substring(labelIndex));
					} else {
						taxa2seq.put(label, taxa2seq.get(label) + tmpLine.substring(labelIndex));
					}
				}
				progressListener.setProgress(it.getProgress());
			}
			if (taxa2seq.isEmpty())
				throw new IOException("No sequences found");
		}

		ntax = taxa2seq.size();
		nchar = taxa2seq.get(taxa2seq.keySet().iterator().next()).length();
		for (String s : taxa2seq.keySet()) {
			if (nchar != taxa2seq.get(s).length())
				throw new IOException("Sequence has wrong length: " + s);
			else
				nchar = taxa2seq.get(s).length();
		}

        /*System.err.println("ntax: " + ntax + " nchar: " + nchar);
        for (String s : taxa2seq.keySet()) {
            System.err.println(s);
            System.err.println(taxa2seq.get(s));
        }*/

		taxa.addTaxaByNames(labels);

		characters.setDimension(ntax, nchar);
		characters.setDataType(CharactersType.guessType(CharactersType.union(taxa2seq.values().toArray(new String[0]))));
		characters.setGapCharacter(getGap());
		characters.setMissingCharacter(getMissing());

		for (int i = 0; i < labels.size(); i++) {
			var seq = taxa2seq.get(labels.get(i));
			for (int j = 0; j < seq.length(); j++)
				characters.set(i + 1, j + 1, seq.charAt(j));
		}
	}

	private static boolean hasAlphabeticalSymbols(String line) {
		for (char c : line.toCharArray()) {
			if (Character.isLetter(c)) return true;
		}
		return false;
	}

	@Override
	public boolean accepts(String fileName) {
		if (!super.accepts(fileName))
			return false;
		else {
			var line = FileUtils.getFirstLineFromFile(new File(fileName));
			return line != null && line.toUpperCase().startsWith("CLUSTAL");
		}
	}
}
