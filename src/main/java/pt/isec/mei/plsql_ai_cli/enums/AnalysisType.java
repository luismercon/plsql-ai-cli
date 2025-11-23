package pt.isec.mei.plsql_ai_cli.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum AnalysisType {
    CLEAN("clean"),
    RAW("raw"),
    DIRTY("dirty");

    private final String type;
}
