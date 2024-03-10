package splitstree6.xtra.mapview;

import java.util.ArrayList;
import java.util.HashMap;

public class GeoTrait {
    private double longtitude, latitude;
    private int nTaxa;
    private ArrayList<String> taxa;
    private HashMap<String, Integer> compostion;

    public GeoTrait(double longtitude, double latitude, int nTaxa, ArrayList<String> taxa, HashMap<String, Integer> compostion) {
        this.longtitude = longtitude;
        this.latitude = latitude;
        this.nTaxa = compostion.size();
        this.taxa = taxa;
        this.compostion = compostion;
    }

    public double getLongtitude() {
        return longtitude;
    }

    public double getLatitude() {
        return latitude;
    }

    public int getnTaxa() {
        return nTaxa;
    }

    public ArrayList<String> getTaxa() {
        return taxa;
    }

    public HashMap<String, Integer> getCompostion() {
        return compostion;
    }

    public void printGeotrait(){
        System.out.println("New Geotrait");
        System.out.println("Lat: " + latitude + " Long: " + longtitude + " numTaxa: " + nTaxa);
        for (var t : compostion.keySet()){
            System.out.println("Taxa: " + t.toString() + " comp: " + compostion.get(t));
        }

    }
}
