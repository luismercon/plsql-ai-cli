package pt.isec.mei.plsql_ai_cli.utils;

public class StringUtils {

    public static String normalizeToLower(String input) {
        // Normaliza a string para minúsculas de forma segura contra nulls
        return input != null ? input.toLowerCase() : "";
    }
}