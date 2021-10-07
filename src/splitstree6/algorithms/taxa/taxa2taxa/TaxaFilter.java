package splitstree6.algorithms.taxa.taxa2taxa;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import jloda.util.Basic;
import jloda.util.ProgressListener;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.Taxon;

import java.io.IOException;
import java.util.*;

public class TaxaFilter extends Taxa2Taxa {
	private final ObjectProperty<String[]> optionEnabledTaxa = new SimpleObjectProperty<>(new String[0]);
	private final ObjectProperty<String[]> optionDisabledTaxa = new SimpleObjectProperty<>(new String[0]);

	@Override
	public void compute(ProgressListener progress, TaxaBlock ignored, TaxaBlock inputData, TaxaBlock outputData) throws IOException {
		final Map<String, String> name2displayLabel = new HashMap<>();
		for (int t = 1; t <= outputData.getNtax(); t++) {
			final Taxon taxon = outputData.get(t);
			name2displayLabel.put(taxon.getName(), taxon.getDisplayLabel());
		}
		outputData.getTaxa().clear();

		if (numberEnabledTaxa() == 0 && numberDisabledTaxa() == 0) // nothing has been explicitly set, copy everything
		{
			outputData.getTaxa().clear();
			outputData.getTaxa().addAll(inputData.getTaxa());
		} else {
			final Set<String> disabled = new HashSet<>(Arrays.asList(getOptionDisabledTaxa()));
			for (String name : getOptionEnabledTaxa()) {
				if (!disabled.contains(name)) {
					outputData.addTaxaByNames(Collections.singleton(name));
					if (inputData.get(name).getDisplayLabel() != null)
						outputData.get(name).setDisplayLabel(inputData.get(name).getDisplayLabel());
					else
						outputData.get(name).setDisplayLabel(name2displayLabel.get(name));
				}
			}
		}

		if (numberEnabledTaxa() == 0 && numberDisabledTaxa() == 0)
			setShortDescription(Basic.fromCamelCase(Basic.getShortName(this.getClass())));
		else if (numberDisabledTaxa() == 0)
			setShortDescription("using all " + numberEnabledTaxa() + " taxa");
		else
			setShortDescription("using " + numberEnabledTaxa() + " of " + (inputData.getNtax() + " taxa"));
	}

	public void clear() {
		setOptionEnabledTaxa(new String[0]);
		setOptionDisabledTaxa(new String[0]);
	}

	public int numberEnabledTaxa() {
		return getOptionEnabledTaxa().length;
	}

	public int numberDisabledTaxa() {
		return getOptionDisabledTaxa().length;
	}

	public String[] getOptionEnabledTaxa() {
		return optionEnabledTaxa.get();
	}

	public ObjectProperty<String[]> optionEnabledTaxaProperty() {
		return optionEnabledTaxa;
	}

	public void setOptionEnabledTaxa(String[] optionEnabledTaxa) {
		this.optionEnabledTaxa.set(optionEnabledTaxa);
	}

	public String[] getOptionDisabledTaxa() {
		return optionDisabledTaxa.get();
	}

	public ObjectProperty<String[]> optionDisabledTaxaProperty() {
		return optionDisabledTaxa;
	}

	public void setOptionDisabledTaxa(String[] optionDisabledTaxa) {
		this.optionDisabledTaxa.set(optionDisabledTaxa);
	}
}
