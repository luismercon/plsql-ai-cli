package pt.isec.mei.plsql_ai_cli.commands;

import lombok.AllArgsConstructor;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import pt.isec.mei.plsql_ai_cli.model.VectorizeResult;
import pt.isec.mei.plsql_ai_cli.service.LLMAnalysisService;

@ShellComponent
@AllArgsConstructor
public class VectorCommand {

    private final LLMAnalysisService llmAnalysisService;

    @ShellMethod(key = "vectorize-all", value = "Vetoriza TODOS os ficheiros Markdown encontrados na pasta results/")
    public String vectorizeAll() {
        try {
            long start = System.currentTimeMillis();

            VectorizeResult result = llmAnalysisService.vectorizeAll();

            long duration = System.currentTimeMillis() - start;

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Sucesso! %d ficheiros vetorizados em %d ms.%n", result.vectorized(), duration));

            if (!result.skipped().isEmpty()) {
                sb.append(String.format("%nIgnorados (%d):%n", result.skipped().size()));
                result.skipped().forEach(f -> sb.append("  - ").append(f).append(System.lineSeparator()));
            }

            return sb.toString().trim();
        } catch (Exception e) {
            return "Erro no processamento em lote: " + e.getMessage();
        }
    }
}

