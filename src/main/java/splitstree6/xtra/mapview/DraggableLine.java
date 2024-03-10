package splitstree6.xtra.mapview;

import javafx.beans.binding.DoubleBinding;
import javafx.scene.chart.PieChart;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;

class DraggableLine {
    private Line line;
    private DraggablePieChart draggablePieChart;

    public DraggableLine(DraggablePieChart draggablePieChart) {
        this.draggablePieChart = draggablePieChart;

        // Create a Line
        line = new Line();

        // Set the initial position of both the start and the end to the center of the PieChart
        double centerX = draggablePieChart.getPieChart().getLayoutX() + draggablePieChart.getPieChart().getWidth() / 2;
        double centerY = draggablePieChart.getPieChart().getLayoutY() + draggablePieChart.getPieChart().getHeight() / 2;

        System.out.println("pos start " + centerX + " " + centerY);
        line.setStartX(centerX);
        line.setStartY(centerY);
        line.setEndX(centerX);
        line.setEndY(centerY);

        line.setStroke(Color.WHITE);
        line.setStrokeWidth(2);
        line.toBack(); // Send the line to the back so that it's behind the PieChart


        line.endXProperty().bind(draggablePieChart.centerXProperty());
        line.endYProperty().bind(draggablePieChart.centerYProperty());
        System.out.println("pos post bind " + line.getEndX() + " " + line.getEndY());
    }

    public Line getLine() {
        return line;
    }

}
