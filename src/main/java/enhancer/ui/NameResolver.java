package enhancer.ui;

public class NameResolver {


    public static String getDisplayNameForStack(String name) {

        // Handle free stacks
        boolean isFree = name.contains("_FREE");
        if (isFree) {
            name = name.replace("_FREE", "");
        }

        // Parse the numeric values from the names
        String displayValue = switch (name) {
            case "TEN" -> "10";
            case "FIFTEEN" -> "15";
            case "TWENTY" -> "20";
            case "TWENTYFIVE" -> "25";
            case "THIRTY" -> "30";
            case "THIRYFIVE" -> "35";
            case "FOURTY" -> "40";
            case "FOURTYFIVE" -> "45";
            case "FIFTYFIVE" -> "55";
            case "SIXTY" -> "60";
            case "SEVENTY" -> "70";
            case "EIGHTY" -> "80";
            case "NINETY" -> "90";
            case "HUNDRED" -> "100";
            case "HUNDREDTEN" -> "110";
            default -> name;
        };

        // Add (Free) indication if needed
        if (isFree) {
            displayValue += " (Free)";
        }

        return displayValue;
    }
}
