package pt.isec.mei.plsql_ai_cli.commands;

import lombok.AllArgsConstructor;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import pt.isec.mei.plsql_ai_cli.model.CachedDocumentDTO;
import pt.isec.mei.plsql_ai_cli.model.ProcedureAnalysisResult;
import pt.isec.mei.plsql_ai_cli.model.VectorizeResult;
import pt.isec.mei.plsql_ai_cli.service.CacheLoadService;
import pt.isec.mei.plsql_ai_cli.service.ClusteringService;
import pt.isec.mei.plsql_ai_cli.service.DocumentService;
import pt.isec.mei.plsql_ai_cli.service.EmbeddingService;
import pt.isec.mei.plsql_ai_cli.service.GenerationOrchestratorService;
import pt.isec.mei.plsql_ai_cli.service.OllamaService;
import pt.isec.mei.plsql_ai_cli.service.ReportService;
import pt.isec.mei.plsql_ai_cli.service.UserInteractionService;
import pt.isec.mei.plsql_ai_cli.validator.ProceduresFolderValidator;

import java.util.List;

/**
 * Componente único de comandos Shell para o pipeline PL/SQL AI.
 * Todos os comandos disponíveis estão centralizados aqui.
 */
@ShellComponent
@AllArgsConstructor
public class PlsqlCommand {

    private final OllamaService ollamaService;
    private final DocumentService documentService;
    private final UserInteractionService userInteractionService;
    private final ProceduresFolderValidator proceduresFolderValidator;
    private final GenerationOrchestratorService orchestratorService;
    private final EmbeddingService embeddingService;
    private final CacheLoadService cacheLoadService;
    private final ClusteringService clusteringService;
    private final ReportService reportService;

    // ─── Passo 0: Utilitário ─────────────────────────────────────────────────

    @ShellMethod(key = "list", value = "Lists all available .sql procedures found in the procedures/ folder.")
    public String list() {
        proceduresFolderValidator.validateProceduresFolder();
        return documentService.listSqlFiles();
    }

    // ─── Passo 1: Geração de documentação ────────────────────────────────────

    @ShellMethod(key = "analyze", value = "Interactively generates documentation for a single procedure (prompts for strategy, noise level and file).")
    public String analyze() {
        proceduresFolderValidator.validateProceduresFolder();

        String strategy     = userInteractionService.promptForStrategy();
        String noiseLevel   = userInteractionService.promptForNoiseLevel();
        String procedureName = userInteractionService.promptForProcedure();

        return ollamaService.analyze(noiseLevel, procedureName, strategy);
    }

    @ShellMethod(key = "batch", value = "Generates all 9 documentation variants (3 strategies × 3 noise levels) for every procedure in procedures/.")
    public String batch() {
        proceduresFolderValidator.validateProceduresFolder();

        System.out.println("\n==================================================");
        System.out.println("   BATCH GENERATION MODE (Informix Benchmark)");
        System.out.println("==================================================");
        System.out.println("This process will analyse ALL procedures in the input folder.");
        System.out.println("Each procedure will be submitted to 9 variants:");
        System.out.println("  - Strategies: Zero Shot, Few Shot, Chain of Thought");
        System.out.println("  - Contexts:   Clean, Raw, Dirty");
        System.out.println("\nNote: Duration depends on local hardware and model size.");
        System.out.println("--------------------------------------------------\n");

        orchestratorService.runBatchGeneration();

        return "Batch generation complete. Check results/ for the generated files.";
    }

    // ─── Passo 2: Vetorização ─────────────────────────────────────────────────

    @ShellMethod(key = "vectorize", value = "Vectorizes all Markdown files in results/ and saves embeddings to the local cache.")
    public String vectorize() {
        try {
            long start = System.currentTimeMillis();
            VectorizeResult result = embeddingService.vectorizeAll();
            long duration = System.currentTimeMillis() - start;

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Done! %d file(s) vectorized in %d ms.%n", result.vectorized(), duration));

            if (!result.skipped().isEmpty()) {
                sb.append(String.format("%nSkipped (%d):%n", result.skipped().size()));
                result.skipped().forEach(f -> sb.append("  - ").append(f).append(System.lineSeparator()));
            }

            return sb.toString().trim();

        } catch (Exception e) {
            return "Error during vectorization: " + e.getMessage();
        }
    }

    // ─── Passo 3: Clustering e relatório ──────────────────────────────────────

    @ShellMethod(key = "cluster", value = "Runs semantic clustering on the vector cache and generates stability metrics (MD + CSV) in reports/.")
    public String cluster() {
        List<CachedDocumentDTO> docs = cacheLoadService.loadAllDocuments();

        if (docs.isEmpty()) {
            return "Error: Cache is empty. Run 'vectorize' first.";
        }

        List<ProcedureAnalysisResult> results = clusteringService.analyzeAllProcedures(docs);

        return reportService.generateAndSaveReport(results);
    }
}

