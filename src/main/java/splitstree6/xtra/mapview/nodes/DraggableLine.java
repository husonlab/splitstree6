package splitstree6.xtra.mapview.nodes;

import javafx.scene.paint.Color;
import javafx.scene.shape.Line;

/**
 * DraggableLine represents a line that connects to the center of a DraggablePieChart and a fixed point.
 * This class allows the line to dynamically adjust its position based on the center coordinates
 * Nikolas Kreisz 1.2024
 */
public class DraggableLine {
    private Line line;
    private DraggablePieChart draggablePieChart;

    /**
     * Constructs a DraggableLine with the specified DraggablePieChart.
     *
     * @param draggablePieChart The DraggablePieChart to which the line connects.
     */
    public DraggableLine(DraggablePieChart draggablePieChart) {
        this.draggablePieChart = draggablePieChart;

        line = new Line();

        // Set the initial position of both the start and the end to the center of the PieChart
        double centerX = draggablePieChart.getPieChart().getLayoutX() + draggablePieChart.getPieChart().getWidth() / 2;
        double centerY = draggablePieChart.getPieChart().getLayoutY() + draggablePieChart.getPieChart().getHeight() / 2;
        line.setStartX(centerX);
        line.setStartY(centerY);
        line.setEndX(centerX);
        line.setEndY(centerY);

        line.setStroke(Color.WHITE);
        line.setStrokeWidth(2);
        line.toBack(); // Send the line to the back so that it's behind the PieChart


        line.endXProperty().bind(draggablePieChart.centerXProperty());
        line.endYProperty().bind(draggablePieChart.centerYProperty());
    }

    /**
     * Retrieves the line representing the connection.
     *
     * @return The Line object representing the connection.
     */
    public Line getLine() {
        return line;
    }

}
