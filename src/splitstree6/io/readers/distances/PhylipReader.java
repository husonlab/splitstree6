package splitstree6.io.readers.distances;

import jloda.util.Basic;
import jloda.util.FileLineIterator;
import jloda.util.IOExceptionWithLineNumber;
import jloda.util.ProgressListener;
import splitstree6.data.DistancesBlock;
import splitstree6.data.TaxaBlock;

import java.io.IOException;
import java.util.Collections;

;

public class PhylipReader extends DistancesReader {
	public enum Triangle {Both, Lower, Upper}

	public PhylipReader() {
		setFileExtensions("dist", "dst");
	}

	@Override
	public void read(ProgressListener progress, String fileName, TaxaBlock taxaBlock, DistancesBlock distancesBlock) throws IOException {
		Triangle triangle = null;
		int row = 0;
		int numberOfTaxa = 0;

		try (var it = new FileLineIterator(fileName, progress)) {
			while (it.hasNext()) {
				var line = it.next().trim();
				if (line.startsWith("#") || line.length() == 0)
					continue;
				if (row == 0) {
					numberOfTaxa = Integer.parseInt(line);
					distancesBlock.setNtax(numberOfTaxa);
				} else {
					final String[] tokens = line.split("\\s+");
					if (row == 1) {
						if (tokens.length == 1)
							triangle = Triangle.Lower;
						else if (tokens.length == numberOfTaxa)
							triangle = Triangle.Upper;
						else if (tokens.length == numberOfTaxa + 1)
							triangle = Triangle.Both;
						else
							throw new IOExceptionWithLineNumber(it.getLineNumber(), "Matrix has wrong shape");
					}

					if (row > numberOfTaxa)
						throw new IOExceptionWithLineNumber(it.getLineNumber(), "Matrix has wrong shape");

					if (triangle == Triangle.Both) {
						if (tokens.length != numberOfTaxa + 1)
							throw new IOExceptionWithLineNumber(it.getLineNumber(), "Matrix has wrong shape");
						taxaBlock.addTaxaByNames(Collections.singleton(tokens[0]));
						for (int col = 1; col < tokens.length; col++) {
							final double value = Basic.parseDouble(tokens[col]);
							distancesBlock.set(row, col, value);
						}
					} else if (triangle == Triangle.Upper) {
						if (tokens.length != numberOfTaxa + 1 - row)
							throw new IOExceptionWithLineNumber(it.getLineNumber(), "Matrix has wrong shape");
						taxaBlock.addTaxaByNames(Collections.singleton(tokens[0]));
						for (int i = 1; i < tokens.length; i++) {
							final int col = row + i;
							final double value = Basic.parseDouble(tokens[i]);
							distancesBlock.set(row, col, value);
							distancesBlock.set(col, row, value);
						}
					} else if (triangle == Triangle.Lower) {
						if (tokens.length != row)
							throw new IOExceptionWithLineNumber(it.getLineNumber(), "Matrix has wrong shape");
						taxaBlock.addTaxaByNames(Collections.singleton(tokens[0]));
						for (int col = 1; col < tokens.length; col++) {
							final double value = Basic.parseDouble(tokens[col]);
							distancesBlock.set(row, col, value);
							distancesBlock.set(col, row, value);
						}
					}
				}
				row++;
			}
		}
	}
}
