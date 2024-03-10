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
        SCHEME1.put(0, "#A9D5C0");
        SCHEME1.put(1, "#FFA07A");
        SCHEME1.put(2, "#FFD700");
        SCHEME1.put(3, "#00FFFF");
        SCHEME1.put(4, "#9370DB");
        SCHEME1.put(5, "#FF6347");
        SCHEME1.put(6, "#20B2AA");
        SCHEME1.put(7, "#87CEEB");
        SCHEME1.put(8, "#7FFFD4");
        SCHEME1.put(9, "#F08080");
        SCHEME1.put(10, "#32CD32");
        SCHEME1.put(11, "#48D1CC");
        SCHEME1.put(12, "#D3D3D3");
        SCHEME1.put(13, "#8A2BE2");
        SCHEME1.put(14, "#FFFF00");
        SCHEME1.put(15, "#40E0D0");
        SCHEME1.put(16, "#FFB6C1");
        SCHEME1.put(17, "#FA8072");
        SCHEME1.put(18, "#DAA520");
        SCHEME1.put(19, "#FF7F50");
        SCHEME1.put(20, "#66CDAA");
        SCHEME1.put(21, "#6A5ACD");
        SCHEME1.put(22, "#FF4500");
        SCHEME1.put(23, "#8B4513");
        SCHEME1.put(24, "#2E8B57");
        SCHEME1.put(25, "#B22222");
        SCHEME1.put(26, "#BC8F8F");
        SCHEME1.put(27, "#20B2AA");
        SCHEME1.put(28, "#2F4F4F");
        SCHEME1.put(29, "#CD5C5C");
        SCHEME1.put(30, "#F4A460");
        SCHEME1.put(31, "#00FA9A");
        SCHEME1.put(32, "#00BFFF");
        SCHEME1.put(33, "#696969");
        SCHEME1.put(34, "#FFD700");
        SCHEME1.put(35, "#B0E0E6");
        SCHEME1.put(36, "#8B008B");
        SCHEME1.put(37, "#FF4500");
        SCHEME1.put(38, "#32CD32");
        SCHEME1.put(39, "#FFD700");
        SCHEME1.put(40, "#4682B4");
        SCHEME1.put(41, "#9400D3");
        SCHEME1.put(42, "#7CFC00");
        SCHEME1.put(43, "#8B008B");
        SCHEME1.put(44, "#808000");
        SCHEME1.put(45, "#DAA520");
        SCHEME1.put(46, "#6A5ACD");
        SCHEME1.put(47, "#483D8B");
        SCHEME1.put(48, "#228B22");
        SCHEME1.put(49, "#8A2BE2");
    }


    public static final Map<Integer, String> SCHEME2 = new HashMap<>();
    static {
        SCHEME2.put(0, "#FF5733");
        SCHEME2.put(1, "#FFC300");
        SCHEME2.put(2, "#FF3333");
        SCHEME2.put(3, "#33FF33");
        SCHEME2.put(4, "#3333FF");
        SCHEME2.put(5, "#33FFFF");
        SCHEME2.put(6, "#FF33FF");
        SCHEME2.put(7, "#33FFCC");
        SCHEME2.put(8, "#FFFF33");
        SCHEME2.put(9, "#CC33FF");
        SCHEME2.put(10, "#33FF33");
        SCHEME2.put(11, "#FF33CC");
        SCHEME2.put(12, "#CCFF33");
        SCHEME2.put(13, "#33CCFF");
        SCHEME2.put(14, "#FFCC33");
        SCHEME2.put(15, "#FF3366");
        SCHEME2.put(16, "#FF3366");
        SCHEME2.put(17, "#3366FF");
        SCHEME2.put(18, "#3366FF");
        SCHEME2.put(19, "#3366FF");
        SCHEME2.put(20, "#FF6633");
        SCHEME2.put(21, "#33FF66");
        SCHEME2.put(22, "#6633FF");
        SCHEME2.put(23, "#FF66CC");
        SCHEME2.put(24, "#CCFF66");
        SCHEME2.put(25, "#66CCFF");
        SCHEME2.put(26, "#66FFCC");
        SCHEME2.put(27, "#CC66FF");
        SCHEME2.put(28, "#66FF33");
        SCHEME2.put(29, "#33FF99");
        SCHEME2.put(30, "#99FF33");
        SCHEME2.put(31, "#FF9933");
        SCHEME2.put(32, "#33FF99");
        SCHEME2.put(33, "#FF9933");
        SCHEME2.put(34, "#9933FF");
        SCHEME2.put(35, "#FF3399");
        SCHEME2.put(36, "#9933FF");
        SCHEME2.put(37, "#3399FF");
        SCHEME2.put(38, "#3399FF");
        SCHEME2.put(39, "#3399FF");
    }

    public static final Map<Integer, String> SCHEME3 = new HashMap<>();
    static {
        SCHEME3.put(0, "#00FF00");
        SCHEME3.put(1, "#00FFFF");
        SCHEME3.put(2, "#FF00FF");
        SCHEME3.put(3, "#FFFF00");
        SCHEME3.put(4, "#FF0000");
        SCHEME3.put(5, "#0000FF");
        SCHEME3.put(6, "#FFA500");
        SCHEME3.put(7, "#800080");
        SCHEME3.put(8, "#008000");
        SCHEME3.put(9, "#800000");
        SCHEME3.put(10, "#808000");
        SCHEME3.put(11, "#008080");
        SCHEME3.put(12, "#800080");
        SCHEME3.put(13, "#008000");
        SCHEME3.put(14, "#800000");
        SCHEME3.put(15, "#808000");
        SCHEME3.put(16, "#008080");
        SCHEME3.put(17, "#800080");
        SCHEME3.put(18, "#008000");
        SCHEME3.put(19, "#800000");
        SCHEME3.put(20, "#00FF00");
        SCHEME3.put(21, "#00FFFF");
        SCHEME3.put(22, "#FF00FF");
        SCHEME3.put(23, "#FFFF00");
        SCHEME3.put(24, "#FF0000");
        SCHEME3.put(25, "#0000FF");
        SCHEME3.put(26, "#FFA500");
        SCHEME3.put(27, "#800080");
        SCHEME3.put(28, "#008000");
        SCHEME3.put(29, "#800000");
        SCHEME3.put(30, "#808000");
        SCHEME3.put(31, "#008080");
        SCHEME3.put(32, "#800080");
        SCHEME3.put(33, "#008000");
        SCHEME3.put(34, "#800000");
        SCHEME3.put(35, "#808000");
        SCHEME3.put(36, "#008080");
        SCHEME3.put(37, "#800080");
        SCHEME3.put(38, "#008000");
        SCHEME3.put(39, "#800000");
    }

}
