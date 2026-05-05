package pt.isec.mei.plsql_ai_cli.model;

import java.util.List;

public record VectorizeResult(int vectorized, List<String> skipped, List<String> failed) {
}
