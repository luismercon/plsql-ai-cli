package pt.isec.mei.plsql_ai_cli.commands;

import lombok.AllArgsConstructor;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import pt.isec.mei.plsql_ai_cli.model.CachedDocumentDTO;
import pt.isec.mei.plsql_ai_cli.model.ProcedureAnalysisResult;
import pt.isec.mei.plsql_ai_cli.service.CacheLoadService;
import pt.isec.mei.plsql_ai_cli.service.ClusteringService;
import pt.isec.mei.plsql_ai_cli.service.ReportService;

import java.util.List;

@ShellComponent
@AllArgsConstructor
public class ClusterCommand {

    private final CacheLoadService cacheLoaderService;
    private final ClusteringService clusteringService;
    private final ReportService reportService;

    @ShellMethod(key = "cluster", value = "Executa análise, exibe relatório e salva em arquivo .md")
    public String clustering() {
        // 1. Carregar Dados
        List<CachedDocumentDTO> rawDocs = cacheLoaderService.loadAllDocuments();
        if (rawDocs.isEmpty()) return "Cache vazio. Rode 'vectorize-all'.";

        // 2. Processar (Lógica de Negócio)
        List<ProcedureAnalysisResult> results = clusteringService.analyzeAllProcedures(rawDocs);

        // 3. Gerar Relatório e Salvar (View/Persistence)
        return reportService.generateAndSaveReport(results);
    }
}