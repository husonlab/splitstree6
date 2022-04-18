package splitstree6.view.format.taxmark;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.VBox;
import jloda.fx.shapes.NodeShape;

public class TaxonMarkController {

	@FXML
	private Button clearColorButton;

	@FXML
	private ColorPicker fillColorPicker;

	@FXML
	private TitledPane titledPane;

	@FXML
	private VBox vBox;

	@FXML
	private Button addButton;

	@FXML
	private ChoiceBox<NodeShape> shapeCBox;

	@FXML
	private void initialize() {
		shapeCBox.getItems().addAll(NodeShape.values());
	}

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

	public ChoiceBox<NodeShape> getShapeCBox() {
		return shapeCBox;
	}

	public Button getAddButton() {
		return addButton;
	}
}
