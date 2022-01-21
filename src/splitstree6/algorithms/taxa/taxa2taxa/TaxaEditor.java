/*
 * TaxaEditor.java Copyright (C) 2022 Daniel H. Huson
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

package splitstree6.algorithms.taxa.taxa2taxa;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import jloda.util.StringUtils;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.IFilter;
import splitstree6.data.TaxaBlock;
import splitstree6.data.parts.Taxon;

import java.io.IOException;
import java.util.*;

public class TaxaEditor extends Taxa2Taxa implements IFilter {
	private final ObjectProperty<String[]> optionDisabledTaxa = new SimpleObjectProperty<>(this, "optionDisabledTaxa", new String[0]);

	public List<String> listOptions() {
		return List.of(optionDisabledTaxa.getName());
	}

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
			setShortDescription(StringUtils.fromCamelCase(getClass().getSimpleName()));
		} else {
			for (String name : inputData.getLabels()) {
				if (!isDisabled(name)) {
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

	public boolean isDisabled(String name) {
		return Arrays.asList(getOptionDisabledTaxa()).contains(name);
	}


	public void setDisabled(String name, boolean state) {
		setDisabled(Collections.singleton(name), state);
	}

	public void setDisabled(Collection<? extends String> names, boolean state) {
		if (state) {
			var disabled = new TreeSet<>(Arrays.asList(getOptionDisabledTaxa()));
			disabled.addAll(names);
			setOptionDisabledTaxa(disabled.toArray(new String[0]));
		} else {
			var disabled = new TreeSet<>(Arrays.asList(getOptionDisabledTaxa()));
			disabled.removeAll(names);
			setOptionDisabledTaxa(disabled.toArray(new String[0]));
		}
	}

	public void clear() {
		setOptionDisabledTaxa(new String[0]);
	}

	public String[] getOptionDisabledTaxa() {
		if (optionDisabledTaxa.get() == null)
			return new String[0];
		else
			return optionDisabledTaxa.get();
	}

	public ObjectProperty<String[]> optionDisabledTaxaProperty() {
		return optionDisabledTaxa;
	}

	public void setOptionDisabledTaxa(String[] optionDisabledTaxa) {
		this.optionDisabledTaxa.set(optionDisabledTaxa);
	}

	public int getNumberDisabledTaxa() {
		return getOptionDisabledTaxa().length;
	}

	@Override
	public boolean isActive() {
		return true;
	}

	@Override
	public void reset() {
		optionDisabledTaxa.set(new String[0]);
	}
}
