package io.github.hephaestusmetrics.model;

/**
 * The enum Result type.
 */
public enum ResultType {
    /**
     * Vector result type.
     */
    VECTOR,
    /**
     * Matrix result type.
     */
    MATRIX,
    /**
     * String result type.
     */
    STRING,
    /**
     * Scalar result type.
     */
    SCALAR;

    /**
     * From string result type.
     *
     * @param s the s
     * @return the result type
     */
    public static ResultType fromString(String s) {
        if (s.equalsIgnoreCase("string")) {
            return STRING;
        } else if (s.equalsIgnoreCase("scalar")) {
            return SCALAR;
        } else if (s.equalsIgnoreCase("vector")) {
            return VECTOR;
        } else if (s.equalsIgnoreCase("matrix")) {
            return MATRIX;
        } else {
            throw new RuntimeException("Result type " + s + " not recognized");
        }
    }

}
