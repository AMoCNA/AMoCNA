package com.kubiki.daedalus.core;

import lombok.experimental.UtilityClass;

import java.util.regex.Pattern;

@UtilityClass
public class DaedalusConstants {
    // Delimiters
    public static final String DEFAULT_VALUE_SEPARATOR = ":-";

    // Formatting
    public static final String IRI_BEGIN = "<";
    public static final String IRI_END = ">";
    public static final String QUOTE = "\"";
    public static final String COLLECTION_SEPARATOR = ", ";
    public static final String EMPTY_STRING = "";

    // Regex
    public static final String VAR_PATTERN_STR = "\\$\\{([^}]+)}";
    public static final Pattern VAR_PATTERN = Pattern.compile(VAR_PATTERN_STR);

    // Proxy Methods
    public static final String EQUALS_METHOD = "equals";
    public static final String HASH_CODE_METHOD = "hashCode";
    public static final String TO_STRING_METHOD = "toString";
    public static final String PROXY_PREFIX = "DaedalusProxy[";
    public static final String PROXY_SUFFIX = "]";

    // Spring
    public static final String DEFAULT_BASE_PACKAGE = "com.kubiki";
}
