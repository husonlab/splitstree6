package splitstree6.xtra.mapview;

import javafx.event.EventHandler;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;

public class DraggableLabel extends Label {
    private double xOffset = 0;
    private double yOffset = 0;

    public DraggableLabel(String labelText, MapViewController controller) {
        super(labelText);
        setTextFill(Color.WHITE);
        setVisible(false);


        setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.SECONDARY) {
                // If right-clicked, toggle visibility of the label
                setVisible(!this.isVisible());
            }
        });

        controller.getShowLabelsBox().selectedProperty().addListener((observable, oldValue, newValue) -> {
            setVisible(newValue);
        });
        // Set up event handlers for dragging
        setOnMousePressed(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                xOffset = event.getSceneX() - getLayoutX();
                yOffset = event.getSceneY() - getLayoutY();
            }
        });

        setOnMouseDragged(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                setLayoutX(event.getSceneX() - xOffset);
                setLayoutY(event.getSceneY() - yOffset);
            }
        });
    }
}