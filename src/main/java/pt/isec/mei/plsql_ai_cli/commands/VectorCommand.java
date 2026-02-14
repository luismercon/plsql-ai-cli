package pt.isec.mei.plsql_ai_cli.commands;

import lombok.AllArgsConstructor;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import pt.isec.mei.plsql_ai_cli.service.LLMAnalysisService;

@ShellComponent
@AllArgsConstructor
public class VectorCommand {

    private final LLMAnalysisService llmAnalysisService;

    @ShellMethod(key = "vectorize-all", value = "Vetoriza TODOS os ficheiros Markdown encontrados na pasta results/")
    public String vectorizeAll() {
        try {
            long start = System.currentTimeMillis();

            int count = llmAnalysisService.vectorizeAll();

            long duration = System.currentTimeMillis() - start;
            return String.format("Sucesso! %d ficheiros vetorizados em %d ms.", count, duration);
        } catch (Exception e) {
            return "Erro no processamento em lote: " + e.getMessage();
        }
    }
}