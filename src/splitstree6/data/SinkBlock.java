package splitstree6.data;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import splitstree6.workflow.TopFilter;


public class SinkBlock extends DataBlock {
	private final StringProperty sink = new SimpleStringProperty();
	private final ObservableList<String> fileExtensions = FXCollections.observableArrayList();

	@Override
	public int size() {
		return isSinkSet() ? 1 : 0;
	}

	@Override
	public String getInfo() {
		return (isSinkSet() ? getSink() : " not set");
	}

	@Override
	public String getDisplayText() {
		return getSink();
	}

	public String getSink() {
		return sink.get();
	}

	public StringProperty sinkProperty() {
		return sink;
	}

	public void setSink(String sink) {
		this.sink.set(sink);
	}

	public boolean isSinkSet() {
		return getSink() != null && !getSink().isBlank();
	}

	public ObservableList<String> getFileExtensions() {
		return fileExtensions;
	}

	@Override
	public TopFilter<? extends DataBlock, ? extends DataBlock> createTopFilter() {
		return null;
	}
}
