package splitstree6.view.format.selecttraits;

import javafx.fxml.FXML;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TitledPane;

public class SelectTraitsController {
	@FXML
	private TitledPane titledPane;

	@FXML
	private MenuItem allValuesMenuItem;

	@FXML
	private MenuItem noneValueMenuItem;

	@FXML
	private MenuButton traitMenuButton;

	@FXML
	private MenuButton traitValuesMenuButton;

	@FXML
	private void initialize() {
		titledPane.setDisable(true);
	}

	public TitledPane getTitledPane() {
		return titledPane;
	}

	public MenuItem getAllValuesMenuItem() {
		return allValuesMenuItem;
	}

	public MenuItem getNoneValueMenuItem() {
		return noneValueMenuItem;
	}

	public MenuButton getTraitMenuButton() {
		return traitMenuButton;
	}

	public MenuButton getTraitValuesMenuButton() {
		return traitValuesMenuButton;
	}
}
