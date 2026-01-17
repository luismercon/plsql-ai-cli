package pt.isec.mei.plsql_ai_cli.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum PromptStrategy {
    SINGLE_SHOT("ss"),
    FEW_SHOT("fs"),
    CHAIN_OF_THOUGHT("cot");

    private final String type;
}
