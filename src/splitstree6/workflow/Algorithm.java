package splitstree6.workflow;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import jloda.util.ProgressListener;
import splitstree6.data.DataBlock;
import splitstree6.data.TaxaBlock;
import splitstree6.workflow.interfaces.HasFromClass;
import splitstree6.workflow.interfaces.HasToClass;

import java.io.IOException;

public abstract class Algorithm<S extends DataBlock, T extends DataBlock> implements HasFromClass<S>, HasToClass<T> {
	private final Class<S> fromClass;
	private final Class<T> toClass;
	private final StringProperty shortDescription = new SimpleStringProperty(getClass().getSimpleName());

	protected Algorithm(Class<S> fromClass, Class<T> toClass) {
		this.fromClass = fromClass;
		this.toClass = toClass;
	}

	public abstract void compute(ProgressListener progress, TaxaBlock taxaBlock, S inputData, T outputData) throws IOException;

	public String getCitation() {
		return null;
	}

	public Class<S> getFromClass() {
		return fromClass;
	}

	public Class<T> getToClass() {
		return toClass;
	}

	public boolean isApplicable(TaxaBlock taxa, S datablock) {
		return taxa.size() > 0 && datablock.size() > 0;
	}

	public String getShortDescription() {
		return shortDescription.get();
	}

	public StringProperty shortDescriptionProperty() {
		return shortDescription;
	}

	public void setShortDescription(String shortDescription) {
		this.shortDescription.set(shortDescription);
	}
}
