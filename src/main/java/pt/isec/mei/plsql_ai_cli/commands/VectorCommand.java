package pt.isec.mei.plsql_ai_cli.commands;

import lombok.AllArgsConstructor;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;
import pt.isec.mei.plsql_ai_cli.service.LLMAnalysisService;

@ShellComponent
@AllArgsConstructor
public class VectorCommand {

    private final LLMAnalysisService llmAnalysisService;

    @ShellMethod(key = "vectorize", value = "Gera embeddings para um arquivo e salva no cache JSON.")
    public String vectorizeFile(@ShellOption(help = "Nome do arquivo (sem .sql)") String fileName) {
        try {
            long start = System.currentTimeMillis();

            llmAnalysisService.processAndVectorize(fileName);

            long duration = System.currentTimeMillis() - start;
            return String.format("Sucesso! Arquivo '%s' vetorizado em %d ms.", fileName, duration);
        } catch (Exception e) {
            return "Erro ao vetorizar: " + e.getMessage();
        }
    }

    @ShellMethod(key = "vectorize-all", value = "Vetoriza TODOS os arquivos Markdown encontrados na pasta results/")
    public String vectorizeAll() {
        try {
            long start = System.currentTimeMillis();

            int count = llmAnalysisService.vectorizeAll();

            long duration = System.currentTimeMillis() - start;
            return String.format("Sucesso! %d arquivos vetorizados em %d ms.", count, duration);
        } catch (Exception e) {
            return "Erro no processamento em lote: " + e.getMessage();
        }
    }

}
