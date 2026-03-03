package pt.isec.mei.plsql_ai_cli.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum PromptStrategy {
    ZERO_SHOT("zero-shot"),
    FEW_SHOT("few-shot"),
    CHAIN_OF_THOUGHT("chain-of-thought");

    private final String strategy;
}
