package splitstree6.xtra.mapview;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.ObservableList;
import javafx.scene.chart.PieChart;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import static splitstree6.xtra.mapview.ColorSchemes.SCHEME1;

public class DraggablePieChart {
    private PieChart pieChart;
    private ArrayList<Integer> colorIndexes = new ArrayList<>();
    private double offsetX, offsetY;

    private final DoubleProperty centerXProperty = new SimpleDoubleProperty();
    private final DoubleProperty centerYProperty = new SimpleDoubleProperty();


    public DraggablePieChart(ObservableList data) {
        // Create a PieChart
        pieChart = new PieChart();


        pieChart.setData(data);



        // Set up event handlers for dragging
        pieChart.setOnMousePressed(event -> {
            System.out.println("pressed");
            offsetX = event.getSceneX() - pieChart.getLayoutX();
            offsetY = event.getSceneY() - pieChart.getLayoutY();
        });

        pieChart.setOnMouseDragged(event -> {

            pieChart.setLayoutX(event.getSceneX() - offsetX);
            pieChart.setLayoutY(event.getSceneY() - offsetY);

            centerXProperty.set(pieChart.getLayoutX() + pieChart.getBoundsInLocal().getWidth() / 2);
            centerYProperty.set(pieChart.getLayoutY() + pieChart.getBoundsInLocal().getHeight() / 2);

        });

    }

    public void saveColorIDs(ArrayList<String> traits){
        for(var trait : traits){
            System.out.print(" " + trait.toString());
        }
        ArrayList<Integer> indexes = new ArrayList<>();
        var data = pieChart.getData();
        for(PieChart.Data label : data){
            System.out.println(" trait " + label.getName() + " color " + traits.indexOf(label.getName()));
            indexes.add(traits.indexOf(label.getName()));
        }
        colorIndexes = indexes;
        System.out.println("ind length" + colorIndexes.size());
    }

    public void updateColors(String scheme){
        Map<Integer, String> colors = ColorSchemes.getScheme(scheme);

        for(int i = 0; i < pieChart.getData().size() ; i++){
            System.out.println(" Chart color" + colorIndexes.get(i));
            String style = "-fx-pie-color: " + colors.get(colorIndexes.get(i)) + ";";
            pieChart.getData().get(i).getNode().setStyle(style);
        }
    }

    public void updateCenter(){
        centerXProperty.set(pieChart.getLayoutX() + pieChart.getBoundsInLocal().getWidth() / 2);
        centerYProperty.set(pieChart.getLayoutY() + pieChart.getBoundsInLocal().getHeight() / 2);
    }

    public PieChart getPieChart() {
        return pieChart;
    }

    // Provide access to centerX and centerY properties
    public DoubleProperty centerXProperty() {
        return centerXProperty;
    }

    public DoubleProperty centerYProperty() {
        return centerYProperty;
    }
}
