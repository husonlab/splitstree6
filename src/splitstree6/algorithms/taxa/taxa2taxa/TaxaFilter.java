/*
 * TaxaFilter.java Copyright (C) 2022 Daniel H. Huson
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

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import jloda.util.StringUtils;
import jloda.util.progress.ProgressListener;
import splitstree6.algorithms.IFilter;
import splitstree6.data.TaxaBlock;
import splitstree6.data.TraitsBlock;

import java.util.*;

public class TaxaFilter extends Taxa2Taxa implements IFilter {
	private final ObjectProperty<String[]> optionDisabledTaxa = new SimpleObjectProperty<>(this, "optionDisabledTaxa", new String[0]);

	public List<String> listOptions() {
		return List.of(optionDisabledTaxa.getName());
	}

	@Override
	public void compute(ProgressListener progress, TaxaBlock ignored, TaxaBlock inputTaxaBlock, TaxaBlock outputTaxaBlock) {
		if (getNumberDisabledTaxa() == 0) {
			outputTaxaBlock.copy(inputTaxaBlock);
			setShortDescription(StringUtils.fromCamelCase(getClass().getSimpleName()));
		} else {
			outputTaxaBlock.clear();

			for (var taxon : inputTaxaBlock.getTaxa()) {
				var name = taxon.getName();
				if (!isDisabled(name)) {
					outputTaxaBlock.add(taxon);
				}
			}
			setShortDescription("using " + outputTaxaBlock.getNtax() + " of " + (inputTaxaBlock.getNtax() + " taxa"));
		}

		final var parentTraits = inputTaxaBlock.getTraitsBlock();
		final TraitsBlock childTraits = outputTaxaBlock.getTraitsBlock();

		if (parentTraits != null && childTraits != null && childTraits.getNode() != null) {
			Platform.runLater(() -> {
				childTraits.getNode().setValid(false);
				childTraits.copySubset(inputTaxaBlock, parentTraits, outputTaxaBlock.getTaxa());
				childTraits.getNode().setValid(true);
			});
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
