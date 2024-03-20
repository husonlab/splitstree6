/*
 *  FastAReader.java Copyright (C) 2024 Daniel H. Huson
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

package splitstree6.io.readers.characters;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import jloda.util.FileLineIterator;
import jloda.util.FileUtils;
import jloda.util.IOExceptionWithLineNumber;
import jloda.util.StringUtils;
import jloda.util.progress.ProgressListener;
import splitstree6.data.CharactersBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.CharactersType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Import Characters in FastA format.
 * Daria Evseeva, 07.2017
 * Daniel Huson, 10.2021
 */
public class FastAReader extends CharactersReader {
	private static final String[] possibleIDs =
			{"gb", "emb", "ena", "dbj", "pir", "prf", "sp", "pdb", "pat", "bbs", "gnl", "ref", "lcl"};

	private final BooleanProperty optionFullLabels = new SimpleBooleanProperty(this, "optionFullLabels", false);
	private final BooleanProperty optionPIRFormat = new SimpleBooleanProperty(this, "optionPIRFormat", false);

	public FastAReader() {
		setFileExtensions("fasta", "fas", "fa", "seq", "fsa", "fna", "dna");
	}

	/**
	 * parse a file
	 */
	public void read(ProgressListener progressListener, String inputFile, TaxaBlock taxa, CharactersBlock characters) throws IOException {
		final ArrayList<String> taxonNamesFound = new ArrayList<>();
		final ArrayList<String> matrix = new ArrayList<>();
		int ntax = 0;
		int nchar = 0;
		int counter = 0;

		try (var it = new FileLineIterator(inputFile)) {
			progressListener.setMaximum(it.getMaximumProgress());
			progressListener.setProgress(0);
			int currentSequenceLength = 0;
			var currentSequence = new StringBuilder();
			var ignoreNext = false;

			while (it.hasNext()) {
				final String line = it.next();

				counter++;
				if (line.startsWith(";") || line.isEmpty())
					continue;
				if (line.equals(">"))
					throw new IOExceptionWithLineNumber("No taxa label given", counter);

				if (line.startsWith(">")) {
					if (isOptionPIRFormat()) ignoreNext = true;

					if (isOptionFullLabels())
						addTaxaName(line, taxonNamesFound, counter);
					else
						addTaxaName(cutLabel(line), taxonNamesFound, counter);

					ntax++;

					if (ntax > 1 && currentSequence.toString().isEmpty())
						throw new IOExceptionWithLineNumber("No sequence", counter);

					if (nchar != 0 && nchar != currentSequenceLength)
						throw new IOExceptionWithLineNumber("Sequences must be the same length. " +
															"Wrong number of chars, Length " + nchar + " expected", counter - 1);

					if (!currentSequence.toString().equals("")) matrix.add(currentSequence.toString());
					nchar = currentSequenceLength;
					currentSequenceLength = 0;
					currentSequence = new StringBuilder();
				} else {
					if (ignoreNext) {
						ignoreNext = false;
						continue;
					}
					String tmpLine;
					if (isOptionPIRFormat() && line.charAt(line.length() - 1) == '*')
						tmpLine = line.substring(0, line.length() - 1); // cut the last symbol
					else
						tmpLine = line;
					var allowedChars = "" + getMissing() + getGap();
					checkIfCharactersValid(tmpLine, counter, allowedChars);
					var add = tmpLine.replaceAll("\\s+", "");
					currentSequenceLength += add.length();
					currentSequence.append(add);
				}
				progressListener.setProgress(it.getProgress());
			}

			if (currentSequence.length() == 0)
				throw new IOExceptionWithLineNumber("SequenceType " + ntax + " is zero", counter);
			matrix.add(currentSequence.toString());
			if (nchar != currentSequenceLength)
				throw new IOExceptionWithLineNumber("Wrong number of chars. Length " + nchar + " expected", counter);
		}

		taxa.addTaxaByNames(taxonNamesFound);
		characters.setDimension(ntax, nchar);
		characters.setGapCharacter(getGap());
		characters.setMissingCharacter(getMissing());
		readMatrix(matrix, characters);
	}

	private void readMatrix(ArrayList<String> matrix, CharactersBlock characters) {
		StringBuilder foundSymbols = new StringBuilder();
		for (int i = 1; i <= characters.getNtax(); i++) {
			for (int j = 1; j <= characters.getNchar(); j++) {
				char symbol = Character.toLowerCase(matrix.get(i - 1).charAt(j - 1));
				if (foundSymbols.toString().indexOf(symbol) == -1) {
					foundSymbols.append(symbol);
				}
				characters.set(i, j, symbol);
			}
		}
		characters.setDataType(CharactersType.guessType(CharactersType.union(foundSymbols.toString())));
	}

	private static String cutLabel(String infoLine) {

		if (infoLine.contains("[organism=")) {
			int index1 = infoLine.indexOf("[organism=") + 10;
			int index2 = infoLine.indexOf(']');
			return ">" + infoLine.substring(index1, index2);
		}

		// check if the info line contains any of databases IDs
		infoLine = infoLine;
		String foundID = "";
		for (String id : possibleIDs) {
			if (infoLine.contains(">" + id + "|") || infoLine.contains("|" + id + "|")) {
				foundID = id;
				break;
			}
		}

		if (!foundID.equals("")) {
			String afterID = infoLine.substring(infoLine.indexOf(foundID) + foundID.length());
			int index1;
			int index2;
			if (foundID.equals("pir") || foundID.equals("prf") || foundID.equals("pat") || foundID.equals("gnl")) {
				if (foundID.equals("pir") || foundID.equals("prf"))
					index1 = afterID.indexOf('|') + 2;
				else
					index1 = afterID.indexOf('|') + 1;
				return ">" + afterID.substring(index1);
			} else {
				index1 = afterID.indexOf('|') + 1;
				index2 = afterID.substring(index1 + 1).indexOf('|') + 2;
				return ">" + afterID.substring(index1, index2);
			}
		}
		return ">" + infoLine.substring(1);
	}

	public boolean isOptionFullLabels() {
		return optionFullLabels.get();
	}

	public BooleanProperty optionFullLabelsProperty() {
		return optionFullLabels;
	}

	public void setOptionFullLabels(boolean optionFullLabels) {
		this.optionFullLabels.set(optionFullLabels);
	}

	public boolean isOptionPIRFormat() {
		return optionPIRFormat.get();
	}

	public BooleanProperty optionPIRFormatProperty() {
		return optionPIRFormat;
	}

	public void setOptionPIRFormat(boolean optionPIRFormat) {
		this.optionPIRFormat.set(optionPIRFormat);
	}

	protected static void checkIfCharactersValid(String line, int counter, String allowedChars) throws IOException {
		if (line.isEmpty())
			throw new IOExceptionWithLineNumber("No characters sequence is given", counter);

		String regex = "[^a-z0-9 \t" + allowedChars + "]";
		Pattern p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
		Matcher m = p.matcher(line);
		boolean found = m.find();
		if (found) {
			throw new IOExceptionWithLineNumber("Unexpected character: " + m.group(), counter);
		}
	}

	/**
	 * add new taxa taxon to a given list of taxa labels
	 * if repeating taxa label is found, convert to "label + number" form
	 */
	static void addTaxaName(String line, ArrayList<String> taxonNames, int linesCounter) {
		var sameNamesCounter = 0;
		if (taxonNames.contains(line.substring(1))) {
			System.err.println("Warning: Repeated taxon name " + line.substring(1) + ". Line: " + linesCounter);
			sameNamesCounter++;
		}
		while (taxonNames.contains(line.substring(1) + "(" + sameNamesCounter + ")")) {
			sameNamesCounter++;
		}

		if (sameNamesCounter == 0)
			taxonNames.add(line.substring(1));
		else
			taxonNames.add(line.substring(1) + "(" + sameNamesCounter + ")");
	}


	@Override
	public boolean accepts(String fileName) {
		if (!super.accepts(fileName))
			return false;
		else {
			var line = FileUtils.getFirstLineFromFileIgnoreEmptyLines(new File(fileName), ";", 20);
			return line != null && acceptsFirstLine(line);
		}
	}

	public boolean acceptsFirstLine(String line) {
		return StringUtils.getFirstLine(line).startsWith(">");
	}
}
