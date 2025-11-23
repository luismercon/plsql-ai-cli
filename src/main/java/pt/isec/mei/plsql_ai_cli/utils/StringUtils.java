package pt.isec.mei.plsql_ai_cli.utils;

public class StringUtils {


    public static String normalizeToLower(String string) {
        return string != null ? string.toLowerCase() : "";
    }

    public static String normalizeToUpper(String string) {
        return string != null ? string.toUpperCase() : "";
    }

}
