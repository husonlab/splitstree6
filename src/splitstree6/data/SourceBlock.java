package splitstree6.data;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import jloda.util.Basic;
import splitstree6.workflow.TopFilter;


public class SourceBlock extends DataBlock {
	private final ObservableList<String> sources = FXCollections.observableArrayList();
	private final ObservableList<String> fileExtensions = FXCollections.observableArrayList();

	@Override
	public int size() {
		return sources.size();
	}

	@Override
	public String getInfo() {
		return "Sources: " + sources.size();
	}

	@Override
	public String getDisplayText() {
		return Basic.toString(sources, "\n");
	}

	public ObservableList<String> getSources() {
		return sources;
	}

	public ObservableList<String> getFileExtensions() {
		return fileExtensions;
	}

	@Override
	public TopFilter<? extends DataBlock, ? extends DataBlock> createTopFilter() {
		return null;
	}
}
