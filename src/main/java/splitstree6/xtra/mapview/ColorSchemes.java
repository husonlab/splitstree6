package splitstree6.xtra.mapview;
import java.util.HashMap;
import java.util.Map;

public class ColorSchemes {

    public static Map<Integer, String> getScheme(String label){
        switch (label){
            case "Scheme-1":
                return SCHEME1;
            case "Scheme-2":
                return SCHEME2;
            case "Scheme-3":
                return SCHEME3;
            default: return SCHEME1;
        }
    }

    public static final Map<Integer, String> SCHEME1 = new HashMap<>();
    static {
        SCHEME1.put(1, "#99D9EA");
        SCHEME1.put(2, "#FB8072");
        SCHEME1.put(3, "#FFFFB2");
        SCHEME1.put(4, "#CCEBC5");
        SCHEME1.put(5, "#DECBE4");
        SCHEME1.put(6, "#FEB24C");
        SCHEME1.put(7, "#8DD3C7");
        SCHEME1.put(8, "#FFED6F");
        SCHEME1.put(9, "#BEBADA");
        SCHEME1.put(10, "#80B1D3");
        SCHEME1.put(11, "#FDB462");
        SCHEME1.put(12, "#B3DE69");
        SCHEME1.put(13, "#D9D9D9");
        SCHEME1.put(14, "#BC80BD");
        SCHEME1.put(15, "#CCEBC5");
        SCHEME1.put(16, "#FFED6F");
        SCHEME1.put(17, "#9467BD");
        SCHEME1.put(18, "#C49496");
        SCHEME1.put(19, "#8C564B");
        SCHEME1.put(20, "#8C564B");
    }

    public static final Map<Integer, String> SCHEME2 = new HashMap<>();
    static {
        SCHEME2.put(1, "#FF5733");
        SCHEME2.put(2, "#FFC300");
        SCHEME2.put(3, "#FF3333");
        SCHEME2.put(4, "#33FF33");
        SCHEME2.put(5, "#3333FF");
        SCHEME2.put(6, "#33FFFF");
        SCHEME2.put(7, "#FF33FF");
        SCHEME2.put(8, "#33FFCC");
        SCHEME2.put(9, "#FFFF33");
        SCHEME2.put(10, "#CC33FF");
        SCHEME2.put(11, "#33FF33");
        SCHEME2.put(12, "#FF33CC");
        SCHEME2.put(13, "#CCFF33");
        SCHEME2.put(14, "#33CCFF");
        SCHEME2.put(15, "#FFCC33");
        SCHEME2.put(16, "#FF3366");
        SCHEME2.put(17, "#FF3366");
        SCHEME2.put(18, "#3366FF");
        SCHEME2.put(19, "#3366FF");
        SCHEME2.put(20, "#3366FF");
    }
    public static final Map<Integer, String> SCHEME3 = new HashMap<>();
    static {
        SCHEME3.put(1, "#00FF00");
        SCHEME3.put(2, "#00FFFF");
        SCHEME3.put(3, "#FF00FF");
        SCHEME3.put(4, "#FFFF00");
        SCHEME3.put(5, "#FF0000");
        SCHEME3.put(6, "#0000FF");
        SCHEME3.put(7, "#FFA500");
        SCHEME3.put(8, "#800080");
        SCHEME3.put(9, "#008000");
        SCHEME3.put(10, "#800000");
        SCHEME3.put(11, "#808000");
        SCHEME3.put(12, "#008080");
        SCHEME3.put(13, "#800080");
        SCHEME3.put(14, "#008000");
        SCHEME3.put(15, "#800000");
        SCHEME3.put(16, "#808000");
        SCHEME3.put(17, "#008080");
        SCHEME3.put(18, "#800080");
        SCHEME3.put(19, "#008000");
        SCHEME3.put(20, "#800000");
    }
}
