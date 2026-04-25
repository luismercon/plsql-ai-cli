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
        List<File> files = documentService.getAllSqlFiles();
        if (files.isEmpty()) {
            log.warn("No SQL files found in the 'procedures' folder.");
            return;
        }

        int totalSteps = files.size() * PromptStrategy.values().length * NoiseLevel.values().length;
        System.out.println("Starting batch processing: " + totalSteps + " operations queued.\n");

        try (ProgressBar pb = new ProgressBarBuilder()
                .setTaskName("Batch Gen")
                .setInitialMax(totalSteps)
                .setStyle(ProgressBarStyle.ASCII)
                .build()) {

            for (File file : files) {
                for (PromptStrategy strategy : PromptStrategy.values()) {
                    for (NoiseLevel noise : NoiseLevel.values()) {

                        String taskName = String.format("%s [%s - %s]", file.getName(), strategy.getStrategy(), noise.getLevel());
                        pb.setExtraMessage(taskName);

                        try {
                            long startTime = System.currentTimeMillis();

                            // Executa a análise via Ollama (Codestral)
                            AnalysisResult result = ollamaService.analyzeForBatch(file, strategy, noise);

                            long duration = System.currentTimeMillis() - startTime;

                            // Persiste o resultado em Markdown/Metadata
                            documentService.saveBatchResult(
                                    result.doc(),
                                    file,
                                    strategy,
                                    noise,
                                    duration,
                                    result.tokens()
                            );

                        } catch (Exception e) {
                            log.error("Error processing {}: {}", taskName, e.getMessage());
                        } finally {
                            pb.step();
                        }
                    }
                }
            }
        }
        log.info("Batch generation complete.");
    }
}