package splitstree6.view.format.marks;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.VBox;

public class MarksController {

	@FXML
	private Button clearColorButton;

	@FXML
	private ColorPicker fillColorPicker;

	@FXML
	private TitledPane titledPane;

	@FXML
	private VBox vBox;

	public Button getClearColorButton() {
		return clearColorButton;
	}

	public ColorPicker getFillColorPicker() {
		return fillColorPicker;
	}

	public TitledPane getTitledPane() {
		return titledPane;
	}

	public VBox getvBox() {
		return vBox;
	}
}
