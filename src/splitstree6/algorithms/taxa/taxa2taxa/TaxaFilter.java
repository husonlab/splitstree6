package splitstree6.algorithms.taxa.taxa2taxa;

import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import jloda.util.Basic;
import jloda.util.ProgressListener;
import splitstree6.algorithms.IFilter;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.Taxon;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class TaxaFilter extends Taxa2Taxa implements IFilter {
	private final ObservableSet<String> optionDisabledTaxa = FXCollections.observableSet();

	@Override
	public void compute(ProgressListener progress, TaxaBlock ignored, TaxaBlock inputData, TaxaBlock outputData) throws IOException {
		final Map<String, String> name2displayLabel = new HashMap<>();
		for (int t = 1; t <= inputData.getNtax(); t++) {
			final Taxon taxon = inputData.get(t);
			name2displayLabel.put(taxon.getName(), taxon.getDisplayLabel());
		}
		outputData.getTaxa().clear();

		if (getNumberDisabledTaxa() == 0) {
			outputData.getTaxa().addAll(inputData.getTaxa());
			setShortDescription(Basic.fromCamelCase(getClass().getSimpleName()));
		} else {
			for (String name : inputData.getLabels()) {
				if (!getOptionDisabledTaxa().contains(name)) {
					outputData.addTaxaByNames(Collections.singleton(name));
					if (inputData.get(name).getDisplayLabel() != null)
						outputData.get(name).setDisplayLabel(inputData.get(name).getDisplayLabel());
					else
						outputData.get(name).setDisplayLabel(name2displayLabel.get(name));
				}
			}
			setShortDescription("using " + outputData.getNtax() + " of " + (inputData.getNtax() + " taxa"));
		}
	}

	public void clear() {
		getOptionDisabledTaxa().clear();
	}

	public ObservableSet<String> getOptionDisabledTaxa() {
		return optionDisabledTaxa;
	}

	public int getNumberDisabledTaxa() {
		return optionDisabledTaxa.size();
	}

	@Override
	public boolean isActive() {
		return true;
	}
}
