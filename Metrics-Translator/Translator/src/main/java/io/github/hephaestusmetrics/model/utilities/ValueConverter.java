package io.github.hephaestusmetrics.model.utilities;

/**
 * Value converter
 */
public final class ValueConverter {

    private ValueConverter() {
    }

    /**
     * Convert double [ ].
     *
     * @param valueString the value string
     * @return the double [ ]
     */
    public static double[] convert(String[] valueString) {
        double timestamp;
        double value;
        try {
            timestamp = Double.parseDouble(valueString[0]);
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            timestamp = Double.NaN;
        }
        try {
            value = Double.parseDouble(valueString[1]);
        } catch (NumberFormatException e) {
            if (valueString[1].equalsIgnoreCase("inf")) {
                value = Double.POSITIVE_INFINITY;
            } else if (valueString[1].equalsIgnoreCase("-inf")) {
                value = Double.NEGATIVE_INFINITY;
            } else {
                value = Double.NaN;
            }
        } catch (IndexOutOfBoundsException e){
            value = Double.NaN;
        }
        return new double[]{timestamp, value};
    }
}
