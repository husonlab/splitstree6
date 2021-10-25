package splitstree6.densitree;

import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Pane;

public class DensiTreeMainController {

	@FXML
	private Pane mainPane;

	@FXML
	private Canvas canvas;

	@FXML
	private MenuItem openMenuItem;

	@FXML
	private MenuItem quitMenuItem;

	@FXML
	private MenuItem copyMenuItem;

	@FXML
	private ToolBar toolBar;

	@FXML
	private Button drawButton;

	@FXML
	private FlowPane bottomFlowPane;

	@FXML
	private Label messageLabel;

	public Pane getMainPane() {
		return mainPane;
	}

	public Canvas getCanvas() {
		return canvas;
	}

	public MenuItem getOpenMenuItem() {
		return openMenuItem;
	}

	public MenuItem getQuitMenuItem() {
		return quitMenuItem;
	}

	public MenuItem getCopyMenuItem() {
		return copyMenuItem;
	}

	public ToolBar getToolBar() {
		return toolBar;
	}

	public Button getDrawButton() {
		return drawButton;
	}

	public FlowPane getBottomFlowPane() {
		return bottomFlowPane;
	}

	public Label getMessageLabel() {
		return messageLabel;
	}
}

