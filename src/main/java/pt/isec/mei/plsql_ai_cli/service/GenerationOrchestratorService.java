package pt.isec.mei.plsql_ai_cli.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;
import me.tongfei.progressbar.ProgressBarStyle;
import org.springframework.stereotype.Service;
import pt.isec.mei.plsql_ai_cli.enums.NoiseLevel;
import pt.isec.mei.plsql_ai_cli.enums.PromptStrategy;
import pt.isec.mei.plsql_ai_cli.model.AnalysisResult;


import java.io.File;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class GenerationOrchestratorService {

    private final DocumentService documentService;
    private final OllamaService ollamaService;

    public void runBatchGeneration() {
        log.info("Starting batch generation process...");

        // 1. Busca os arquivos físicos para o loop
        List<File> files = documentService.getAllSqlFiles();
        if (files.isEmpty()) {
            log.warn("No SQL files found in 'procedures' folder to process.");
            return;
        }

        // 2. Calcula o total de passos para a barra de progresso
        int totalSteps = files.size() * PromptStrategy.values().length * NoiseLevel.values().length;

        System.out.println("Initializing Batch Process: " + totalSteps + " operations queued.\n");

        // 3. Inicia a Barra de Progresso (Try-with-resources garante que ela feche no final)
        try (ProgressBar pb = new ProgressBarBuilder()
                .setTaskName("Batch Gen")
                .setInitialMax(totalSteps)
                .setStyle(ProgressBarStyle.ASCII)
                .build()) {

            for (File file : files) {
                for (PromptStrategy strategy : PromptStrategy.values()) {
                    for (NoiseLevel noise : NoiseLevel.values()) {

                        // Atualiza a mensagem visual: "proc_vendas.sql [FEW_SHOT - DIRTY]"
                        String taskName = String.format("%s [%s - %s]", file.getName(), strategy.getType(), noise.getType());
                        pb.setExtraMessage(taskName);

                        try {
                            long startTime = System.currentTimeMillis();

                            // CHAMADA CRÍTICA: Recebe Documento + Tokens
                            AnalysisResult result = ollamaService.analyzeForBatch(file, strategy, noise);

                            long duration = System.currentTimeMillis() - startTime;

                            // SALVAMENTO: Passa os tokens reais para o CSV/Markdown
                            documentService.saveBatchResult(
                                    result.doc(),
                                    file,
                                    strategy,
                                    noise,
                                    duration,
                                    result.tokens()
                            );

                        } catch (Exception e) {
                            // Loga o erro mas NÃO para o loop. O experimento continua.
                            log.error("Error processing {}", taskName, e);
                        } finally {
                            // Avança a barra independente de sucesso ou erro
                            pb.step();
                        }
                    }
                }
            }
        }
        log.info("Batch generation completed successfully.");
    }
}