package splitstree6.xtra.mapview;

import javafx.event.EventHandler;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;

public class DraggableLabel extends Label {
    private double xOffset = 0;
    private double yOffset = 0;

    public DraggableLabel(String labelText) {
        super(labelText);
        //this.setStyle("-fx-font-size: 16px;");
        setTextFill(Color.WHITE);

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