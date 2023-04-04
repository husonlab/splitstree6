package splitstree6.xtra.genetreeview;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class GeneTreeViewController {

	@FXML
	private FlowPane bottomPane;

	@FXML
	private StackPane centerPane;

	@FXML
	private MenuItem closeMenuItem;

	@FXML
	private MenuBar menuBar;

	@FXML
	private MenuItem openMenuItem;

	@FXML
	private ToolBar toolBar;

	@FXML
	private VBox topPane;

	@FXML
	private Label label;

	@FXML
	private void initialize() {
		label.setText("");
	}

	public FlowPane getBottomPane() {
		return bottomPane;
	}

	public StackPane getCenterPane() {
		return centerPane;
	}

	public MenuItem getCloseMenuItem() {
		return closeMenuItem;
	}

	public MenuBar getMenuBar() {
		return menuBar;
	}

	public MenuItem getOpenMenuItem() {
		return openMenuItem;
	}

	public ToolBar getToolBar() {
		return toolBar;
	}

	public VBox getTopPane() {
		return topPane;
	}

	public Label getLabel() {
		return label;
	}
}
