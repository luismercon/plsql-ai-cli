package pt.isec.mei.plsql_ai_cli.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum NoiseLevel {
    CLEAN("clean"),
    RAW("raw"),
    DIRTY("dirty");

    private final String type;
}
