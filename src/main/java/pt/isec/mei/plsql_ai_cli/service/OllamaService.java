package pt.isec.mei.plsql_ai_cli.service;

import lombok.extern.slf4j.Slf4j;
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;
import me.tongfei.progressbar.ProgressBarStyle;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import pt.isec.mei.plsql_ai_cli.enums.NoiseLevel;
import pt.isec.mei.plsql_ai_cli.enums.PromptStrategy;
import pt.isec.mei.plsql_ai_cli.model.ProcedureDocumentation;
import pt.isec.mei.plsql_ai_cli.model.TokensData;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class OllamaService {

    private final ChatModel chatModel;
    private final DocumentService documentService;

    @Value("classpath:prompts/single-shot-template.st")
    protected Resource singleShotPrompt;

    @Value("classpath:prompts/few-shot-template.st")
    protected Resource fewShotPrompt;

    @Value("classpath:prompts/chain-of-thougth-template.st")
    protected Resource chainOfThoughtPrompt;

    @Value("classpath:prompts/system-template.st")
    protected Resource systemPrompt;


    @Autowired
    public OllamaService(ChatModel chatModel, DocumentService documentService) {
        this.chatModel = chatModel;
        this.documentService = documentService;
    }

    public String analyze(String type, String procedureName, String approach, String promptType) {

        long startTime = System.currentTimeMillis();

        String code = getCodeBasedOnApproach(approach, type, procedureName);

        log.info("Starting to analyze in Ollama service. Type:{}. Model: mistral:7b. Approach: {}. Prompt Type: {}", type, approach, promptType);

        BeanOutputConverter<ProcedureDocumentation> outputConverter = new BeanOutputConverter<>(ProcedureDocumentation.class);

        try {
            String systemPromptContent = new String(systemPrompt.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            Resource selectedPrompt = choosePromptTemplate(promptType);
            String prompt = new String(selectedPrompt.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            SystemMessage systemMessage = new SystemMessage(systemPromptContent);
            PromptTemplate promptTemplate = new PromptTemplate(prompt);

            Prompt userPrompt = stuffCodeInUserPrompt(code, outputConverter.getFormat(), promptTemplate);

            Prompt finalMountedPrompt = new Prompt(List.of(systemMessage, userPrompt.getInstructions().get(0)));

            log.info("Sending prompt to Ollama model ...");
            ChatResponse response = chatModel.call(finalMountedPrompt);

            // Extract and clean JSON from response
            String rawResponse = response.getResult().getOutput().getText();
            String cleanedJson = extractJson(rawResponse);

            TokensData tokensData = extractTokensData(response);

            long endTime = System.currentTimeMillis();
            long processingTimeMs = endTime - startTime;

            log.info("Ollama response time: {} ms", processingTimeMs);

            ProcedureDocumentation documentation = outputConverter
                    .convert(cleanedJson);

            try {
                String fileName = documentService.saveDocumentationToMarkdown(documentation, processingTimeMs, tokensData, approach, procedureName, type, promptType);
                return "Analysis completed and saved to: " + fileName;
            } catch (IOException e) {
                log.error("Error saving analysis to file", e);
                return "Analysis completed but failed to save to file: " + e.getMessage();
            }
        } catch (IOException e) {
            log.error("Error reading prompt templates", e);
            return "Failed to read prompt templates: " + e.getMessage();
        }

    }

    /**
     * Generate batch analysis for all procedures with all combinations of PromptStrategy and NoiseLevel
     * @return Summary of the batch generation
     */
    public String generateBatch() {
        log.info("Starting batch generation for all procedures");

        // Get all SQL files from procedures folder
        File proceduresDir = new File("procedures");
        if (!proceduresDir.exists() || !proceduresDir.isDirectory()) {
            return "Procedures directory not found";
        }

        File[] files = proceduresDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".sql"));
        if (files == null || files.length == 0) {
            return "No SQL files found in procedures directory";
        }

        // Build list of all combinations
        List<BatchAnalysisTask> tasks = new ArrayList<>();
        for (File file : files) {
            String procedureName = file.getName().replaceAll("\\.sql$", "");

            for (PromptStrategy strategy : PromptStrategy.values()) {
                for (NoiseLevel noiseLevel : NoiseLevel.values()) {
                    tasks.add(new BatchAnalysisTask(
                        procedureName,
                        strategy.getType(),
                        noiseLevel.getType()
                    ));
                }
            }
        }

        int totalTasks = tasks.size();
        int successCount = 0;
        int failureCount = 0;

        log.info("Total tasks to process: {} (Files: {}, Combinations: 3x3=9 per file)", totalTasks, files.length);

        // Create progress bar with speed display
        try (ProgressBar pb = new ProgressBarBuilder()
                .setTaskName("Batch Analysis")
                .setInitialMax(totalTasks)
                .setStyle(ProgressBarStyle.ASCII)
                .setUpdateIntervalMillis(100)
                .showSpeed()  // Show processing speed (tasks/sec)
                .build()) {

            int taskNumber = 0;

            for (BatchAnalysisTask task : tasks) {
                taskNumber++;

                // Show what's starting BEFORE the long operation
                String taskInfo = String.format("%s [%s/%s]",
                    task.procedureName,
                    task.promptType,
                    task.noiseLevel);

                pb.setExtraMessage(taskInfo);

                log.info("▶ Starting task {}/{}: {}", taskNumber, totalTasks, taskInfo);

                try {
                    // Perform analysis (this blocks for 10-30+ seconds)
                    analyze(task.noiseLevel, task.procedureName, "noise", task.promptType);
                    successCount++;
                    log.info("✓ Task {}/{} completed successfully", taskNumber, totalTasks);

                } catch (Exception e) {
                    log.error("✗ Task {}/{} failed: {}",
                        taskNumber,
                        totalTasks,
                        e.getMessage());
                    failureCount++;
                }

                pb.step();
            }
        }

        String summary = String.format(
            "\nBatch generation completed!\n" +
            "Total procedures: %d\n" +
            "Total analyses: %d\n" +
            "Successful: %d\n" +
            "Failed: %d\n" +
            "Results saved in: results/{procedure_name}/",
            files.length,
            totalTasks,
            successCount,
            failureCount
        );

        log.info(summary);
        return summary;
    }

    /**
     * Inner class to represent a batch analysis task
     */
    private static class BatchAnalysisTask {
        final String procedureName;
        final String promptType;
        final String noiseLevel;

        BatchAnalysisTask(String procedureName, String promptType, String noiseLevel) {
            this.procedureName = procedureName;
            this.promptType = promptType;
            this.noiseLevel = noiseLevel;
        }
    }

    private String getCodeBasedOnApproach(String approach, String type, String procedureName) {
        return switch (approach) {
            case "technique" -> documentService.cleanCommentsService(procedureName);
            case "noise" -> codeRouter(type, procedureName);
            default -> codeRouter(type, procedureName);
        };
    }

    private String codeRouter(String type, String procedureName) {
        return switch (type) {
            case "clean" -> documentService.cleanCommentsService(procedureName);
            case "raw" -> documentService.readRawProcedure(procedureName);
            case "dirty" -> documentService.readDirtyProcedure(procedureName);
            default -> documentService.cleanCommentsService(procedureName);
        };
    }


    private TokensData extractTokensData(ChatResponse response) {
        int promptTokens = response.getMetadata().getUsage().getPromptTokens();
        int completionTokens = response.getMetadata().getUsage().getCompletionTokens();
        int totalTokens = response.getMetadata().getUsage().getTotalTokens();

        log.info("Prompt Tokens: {}, completion tokens: {}, total: {}", promptTokens, completionTokens, totalTokens);

        return new TokensData(promptTokens, completionTokens, totalTokens);
    }

    private Prompt stuffCodeInUserPrompt(String code, String format, PromptTemplate promptTemplate) {
        Map<String, Object> map = new HashMap<>();
        map.put("code", code);
        map.put("format", format);
        return promptTemplate.create(map);
    }

    private Resource choosePromptTemplate(String promptType) {
        log.info("Choosing prompt template for promptType: {}", promptType);
        return switch (promptType) {
            case "ss" -> singleShotPrompt;
            case "fs" -> fewShotPrompt;
            case "cot" -> chainOfThoughtPrompt;
            default -> singleShotPrompt;
        };
    }

    /**
     * Extracts JSON from a response that may contain markdown code fences or explanatory text.
     * Handles multiple scenarios:
     * 1. Response wrapped in ```json ... ```
     * 2. Response with explanatory text before/after JSON
     * 3. Pure JSON response
     */
    private String extractJson(String rawResponse) {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            log.warn("Raw response is null or empty");
            return rawResponse;
        }

        String trimmed = rawResponse.trim();

        // Case 1: Remove markdown code fences (```json ... ``` or ``` ... ```)
        Pattern markdownPattern = Pattern.compile("```(?:json)?\\s*\\n?(.+?)```", Pattern.DOTALL);
        Matcher markdownMatcher = markdownPattern.matcher(trimmed);
        if (markdownMatcher.find()) {
            String extracted = markdownMatcher.group(1).trim();
            log.debug("Extracted JSON from markdown code fence");
            return extracted;
        }

        // Case 2: Find JSON object in the response (starts with { and ends with })
        Pattern jsonPattern = Pattern.compile("(\\{.+})", Pattern.DOTALL);
        Matcher jsonMatcher = jsonPattern.matcher(trimmed);
        if (jsonMatcher.find()) {
            String extracted = jsonMatcher.group(1).trim();
            log.debug("Extracted JSON object from mixed text response");
            return extracted;
        }

        // Case 3: Already clean JSON (starts with { or [)
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            log.debug("Response is already clean JSON");
            return trimmed;
        }

        // Case 4: No JSON found - return original and let parser fail with clear error
        log.warn("No JSON pattern found in response. Returning original text.");
        return rawResponse;
    }

}
