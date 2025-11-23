package pt.isec.mei.plsql_ai_cli.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum Approach {
    NOISE("noise"),
    TECHNIQUE("technique");

    private final String value;
}
