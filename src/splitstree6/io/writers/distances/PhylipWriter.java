package splitstree6.io.writers.distances;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import splitstree6.data.DistancesBlock;
import splitstree6.data.TaxaBlock;

import java.io.IOException;
import java.io.Writer;

public class PhylipWriter extends DistancesWriter {
	private final BooleanProperty optionTriangular = new SimpleBooleanProperty(false);

	public PhylipWriter() {
		setFileExtensions("dist", "dst");
	}

	@Override
	public void write(Writer w, TaxaBlock taxa, DistancesBlock distances) throws IOException {
		int ntax = taxa.getNtax();

		int maxLabelLength = taxa.getLabel(1).length();
		for (int i = 2; i <= ntax; i++) {
			if (taxa.getLabel(i).length() > maxLabelLength)
				maxLabelLength = taxa.getLabel(i).length();
		}

		w.write("\t" + ntax + "\n");

		if (!isOptionTriangular()) {
			System.err.println("standard");
			for (int i = 1; i <= distances.getDistances().length; i++) {
				StringBuilder sequence = new StringBuilder("");
				for (int j = 1; j <= distances.getDistances()[i - 1].length; j++) {
					sequence.append(String.format("%.5f ", distances.get(i, j)));
				}
				if (taxa.getLabel(i).length() >= 10)
					w.write(taxa.getLabel(i).substring(0, 10));
				else {
					w.write(taxa.getLabel(i));
					for (int k = 0; k < 10 - taxa.getLabel(i).length(); k++) {
						w.write(" ");
					}
				}
				w.write("\t" + sequence + "\n");
			}
		} else {
			System.err.println("triangular");
			w.write(taxa.getLabel(1) + "\n");
			for (int i = 2; i <= distances.getDistances().length; i++) {
				StringBuilder sequence = new StringBuilder("");
				for (int j = 1; j <= i - 1; j++) {
					sequence.append(String.format("%.5f ", distances.get(i, j)));
				}
				if (taxa.getLabel(i).length() >= 10)
					w.write(taxa.getLabel(i).substring(0, 10));
				else {
					w.write(taxa.getLabel(i));
					for (int k = 0; k < 10 - taxa.getLabel(i).length(); k++) {
						w.write(" ");
					}
				}
				w.write("\t" + sequence + "\n");
			}
		}
	}

	public boolean isOptionTriangular() {
		return optionTriangular.get();
	}

	public BooleanProperty optionTriangularProperty() {
		return optionTriangular;
	}

	public void setOptionTriangular(boolean optionTriangular) {
		this.optionTriangular.set(optionTriangular);
	}
}
