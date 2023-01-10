/*
 * NexusWriter.java Copyright (C) 2023 Daniel H. Huson
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

package splitstree6.io.writers.distances;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import jloda.util.Pair;
import splitstree6.data.DistancesBlock;
import splitstree6.data.DistancesFormat;
import splitstree6.data.TaxaBlock;
import splitstree6.io.nexus.DistancesNexusOutput;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

import static splitstree6.data.DistancesFormat.Triangle.Both;

/**
 * write block in Nexus format
 * Daniel Huson, 11.2021
 */
public class NexusWriter extends DistancesWriterBase {
	private final BooleanProperty optionPrependTaxa = new SimpleBooleanProperty(this, "optionPrependTaxa", false);
	private String title;
	private Pair<String, String> link;
	private boolean asWorkflowOnly;

	private final ObjectProperty<DistancesFormat.Triangle> optionTriangle = new SimpleObjectProperty<>(this, "optionTriangle", Both);
	private final BooleanProperty optionLabels = new SimpleBooleanProperty(this, "optionLabels", true);
	private final BooleanProperty optionDiagonal = new SimpleBooleanProperty(this, "optionDiagonal", true);

	public NexusWriter() {
		setFileExtensions("nxs", "nex", "nexus");
	}

	public List<String> listOptions() {
		return List.of(optionLabels.getName(), optionTriangle.getName(), optionDiagonal.getName(), optionPrependTaxa.getName());
	}

	@Override
	public void write(Writer w, TaxaBlock taxa, DistancesBlock distances) throws IOException {
		var saveFormat = new DistancesFormat(distances.getFormat());
		try {
			distances.getFormat().setOptionDiagonal(isOptionDiagonal());
			distances.getFormat().setOptionLabels(isOptionLabels());
			distances.getFormat().setOptionTriangle(getOptionTriangle());

			if (isOptionPrependTaxa())
				new splitstree6.io.writers.taxa.NexusWriter().write(w, taxa, taxa);
			final var output = new DistancesNexusOutput();
			output.setTitleAndLink(getTitle(), getLink());
			if (asWorkflowOnly) {
				var newBlock = new DistancesBlock();
				newBlock.setFormat(distances.getFormat());
				output.write(w, new TaxaBlock(), newBlock);
			} else
				output.write(w, taxa, distances);
			w.flush();
		} finally {
			distances.setFormat(saveFormat);
		}
	}

	public boolean isOptionPrependTaxa() {
		return optionPrependTaxa.get();
	}

	public BooleanProperty optionPrependTaxaProperty() {
		return optionPrependTaxa;
	}

	public DistancesFormat.Triangle getOptionTriangle() {
		return optionTriangle.get();
	}

	public ObjectProperty<DistancesFormat.Triangle> optionTriangleProperty() {
		return optionTriangle;
	}

	public boolean isOptionLabels() {
		return optionLabels.get();
	}

	public BooleanProperty optionLabelsProperty() {
		return optionLabels;
	}

	public boolean isOptionDiagonal() {
		return optionDiagonal.get();
	}

	public BooleanProperty optionDiagonalProperty() {
		return optionDiagonal;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public Pair<String, String> getLink() {
		return link;
	}

	public void setLink(Pair<String, String> link) {
		this.link = link;
	}

	public boolean isAsWorkflowOnly() {
		return asWorkflowOnly;
	}

	public void setAsWorkflowOnly(boolean asWorkflowOnly) {
		this.asWorkflowOnly = asWorkflowOnly;
	}
}
