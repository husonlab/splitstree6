package splitstree6.xtra.mapview.nodes;

import javafx.event.EventHandler;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import splitstree6.xtra.mapview.MapViewController;

/**
 * DraggableLabel extends JavaFX Label to create draggable labels that can be moved within a view.
 * Labels can be dragged by clicking and dragging the mouse on the label itself.
 * Additionally, the visibility of labels can be toggled by right-clicking on them or by using a checkbox
 * provided by the associated controller.
 * Nikolas Kreisz 1.2024
 */
public class DraggableLabel extends Label {
    private double xOffset = 0;
    private double yOffset = 0;


    /**
     * Constructs a DraggableLabel with the specified label text and associated controller.
     *
     * @param labelText  The text to be displayed on the label.
     * @param controller The controller managing the view containing this label.
     */
    public DraggableLabel(String labelText, MapViewController controller) {
        super(labelText);
        Font font = Font.font("Arial", FontWeight.BOLD, 22);
        setFont(font);
        setTextFill(Color.WHITE);
        setVisible(controller.getShowLabelsBox().isSelected());

        // Toggle label visibility on right-click
        setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.SECONDARY) {
                setVisible(!this.isVisible());
            }
        });
        // Toggle visibility based on changes to the showLabels Checkbox
        controller.getShowLabelsBox().selectedProperty().addListener((observable, oldValue, newValue) -> {
            setVisible(newValue);
        });

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