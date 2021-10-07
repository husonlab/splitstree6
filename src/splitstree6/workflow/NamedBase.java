package splitstree6.workflow;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class NamedBase {
	private StringProperty name;
	private StringProperty shortDescription;

	public String getName() {
		if (name == null)
			return getClass().getSimpleName();
		else
			return name.get();
	}

	public StringProperty nameProperty() {
		if (name == null)
			name = new SimpleStringProperty("");
		return name;
	}

	public void setName(String name) {
		nameProperty().set(name);
	}

	public String getShortDescription() {
		if (shortDescription == null)
			return "";
		else
			return shortDescription.get();
	}

	public StringProperty shortDescriptionProperty() {
		if (shortDescription == null)
			shortDescription = new SimpleStringProperty("");
		return shortDescription;
	}

	public void setShortDescription(String shortDescription) {
		shortDescriptionProperty().set(shortDescription);
	}
}
