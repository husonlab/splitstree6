package splitstree6.xtra.mapview;

import de.westnordost.countryboundaries.CountryBoundaries;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class CountryFinder {

    private static final String csvLoc = "countries_codes_and_coordinates.csv";//"splitstree6/xtra/mapview/countries_codes_and_coordinates.csv";//"countries_codes_and_coordinates.csv";

    public record Country(String name, double latitude, double longitude) {}

    private final Map<String, Country> countryMap;

    // Constructor for CountryFinder
    public CountryFinder() throws IOException {
        this.countryMap = loadCountryMapFromCSV();
    }

    // Method to load and parse the CSV file into a map
    private Map<String, Country> loadCountryMapFromCSV() throws IOException {
        System.out.println("Building map");
        Map<String, Country> map = new HashMap<>();
        try {
            URL resource = CountryFinder.class.getResource(csvLoc);
            File file = new File(resource.toURI());
            FileReader fileReader = new FileReader(file);
            BufferedReader br = new BufferedReader(fileReader);
            String line = br.readLine(); // Skip first line

            line = br.readLine();
            while (line!= null) {
                //System.out.println("Next Line " + map.size());
                String[] parts = line.split(",");
                if (line != null) {
                    String name = parts[0].replaceAll("\"", "").trim();
                    String alpha2Code = parts[1].replaceAll("\"", "").trim();
                    double lat = Double.parseDouble(parts[4].replaceAll("\"", "").trim());
                    double lon = Double.parseDouble(parts[5].replaceAll("\"", "").trim());
                    //System.out.println("alpha " + alpha2Code + " name " + name + " lat " + lat + " lon " + lon);
                    Country country = new Country(name, lat, lon);
                    map.put(alpha2Code, country);
                    line = br.readLine();
                } else {
                    System.err.println("Invalid line in CSV: " + line);
                }
            }
        }catch (Exception e){
            System.out.println(e.toString());
        }

        return map;
    }

    public ArrayList<CountryFinder.Country> getCountriesForAlpha2Codes(double minLat, double maxLat, double minLong, double maxLong) throws IOException, URISyntaxException {
        URL resource = CountryFinder.class.getResource("boundaries360x180.ser");
        File file = new File(resource.toURI());
        byte[] bytes = Files.readAllBytes(file.toPath());
        System.out.println(" Looking for countries " + bytes.length);
        CountryBoundaries boundaries = CountryBoundaries.load(new ByteArrayInputStream(bytes));
        var alpha2Codes = boundaries.getIntersectingIds(minLong,minLat,maxLong,maxLat);
        System.out.println(" Found countries " + alpha2Codes.size());
        ArrayList<Country> countries = new ArrayList<>();
        for (String alpha2Code : alpha2Codes) {
            if(!alpha2Code.contains("-")){
                Country country = countryMap.get(alpha2Code);
                if (country != null) {
                    System.out.println(country.name);
                    countries.add(moveCenter(country, minLat,maxLat,minLong,maxLong));
                }
            }
        }
        return countries;
    }

    // Method to get the country map
    public Map<String, Country> getCountryMap() {
        return countryMap;
    }

    private CountryFinder.Country moveCenter(CountryFinder.Country country, double minLat, double maxLat, double minLong, double maxLong){
        double centerLat = country.latitude;
        double centerLong = country.longitude;

        if(centerLat > maxLat)centerLat = maxLat;
        if(centerLat < minLat)centerLat = minLat;
        if(centerLong > maxLong)centerLong = maxLong;
        if(centerLong < minLong)centerLong = minLong;

        return new CountryFinder.Country(country.name, centerLat, centerLong);
    }
}

