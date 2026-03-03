package pt.isec.mei.plsql_ai_cli.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import pt.isec.mei.plsql_ai_cli.enums.NoiseLevel;
import pt.isec.mei.plsql_ai_cli.enums.PromptStrategy;
import pt.isec.mei.plsql_ai_cli.model.AnalysisResult;
import pt.isec.mei.plsql_ai_cli.model.ProcedureDocumentation;
import pt.isec.mei.plsql_ai_cli.model.TokensData;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

    @Value("classpath:prompts/zero-shot-template.st")
    protected Resource zeroShotPrompt;

    @Value("classpath:prompts/few-shot-template.st")
    protected Resource fewShotPrompt;

    @Value("classpath:prompts/chain-of-thougth-template.st")
    protected Resource chainOfThoughtPrompt;

    @Value("classpath:prompts/system-template.st")
    protected Resource systemPrompt;

    @Value("${spring.ai.ollama.chat.options.model}")
    private String modelName;

    @Autowired
    public OllamaService(ChatModel chatModel, DocumentService documentService) {
        this.chatModel = chatModel;
        this.documentService = documentService;
    }

    public String analyze(String noiseLevel, String procedureName, String strategy) {
        long startTime = System.currentTimeMillis();

        // Determina qual versão do código carregar (Clean, Raw ou Dirty)
        String code = getCodeByNoiseLevel(noiseLevel, procedureName);

        log.info("Starting individual analysis. Model: {}. Strategy: {}. Noise: {}", modelName, strategy, noiseLevel);

        try {
            ProcedureDocumentation documentation = executeChat(code, strategy);
            TokensData tokensData = new TokensData(0, 0, 0);

            long processingTimeMs = System.currentTimeMillis() - startTime;

            String fileName = documentService.saveDocumentationToMarkdown(
                    documentation, processingTimeMs, tokensData, modelName, procedureName, noiseLevel, strategy
            );
            return "Análise concluída e guardada em: " + fileName;
        } catch (Exception e) {
            log.error("Erro na análise individual", e);
            return "Falha na análise: " + e.getMessage();
        }
    }

    public AnalysisResult analyzeForBatch(File sqlFile, PromptStrategy strategy, NoiseLevel noise) {
        String procedureName = sqlFile.getName().replace(".sql", "");
        String code = getCodeByNoiseLevel(noise.getLevel(), procedureName);

        try {
            ProcedureDocumentation doc = executeChat(code, strategy.getStrategy());
            return new AnalysisResult(doc, new TokensData(0, 0, 0));
        } catch (Exception e) {
            throw new RuntimeException("Falha na análise batch: " + sqlFile.getName(), e);
        }
    }

    private ProcedureDocumentation executeChat(String code, String strategy) throws IOException {
        BeanOutputConverter<ProcedureDocumentation> outputConverter = new BeanOutputConverter<>(ProcedureDocumentation.class);

        String systemPromptContent = new String(systemPrompt.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        Resource selectedPrompt = choosePromptTemplate(strategy);
        String promptContent = new String(selectedPrompt.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        SystemMessage systemMessage = new SystemMessage(systemPromptContent);
        PromptTemplate promptTemplate = new PromptTemplate(promptContent);

        Map<String, Object> map = new HashMap<>();
        map.put("code", code);
        map.put("format", outputConverter.getFormat());

        OllamaOptions options = OllamaOptions.builder()
                .temperature(0.1)
                .format("json")
                .build();

        Prompt finalPrompt = new Prompt(List.of(systemMessage, promptTemplate.create(map).getInstructions().get(0)), options);
        ChatResponse response = chatModel.call(finalPrompt);

        String cleanedJson = extractJson(response.getResult().getOutput().getText());
        return outputConverter.convert(cleanedJson);
    }

    private String getCodeByNoiseLevel(String noiseLevel, String procedureName) {
        return switch (noiseLevel) {
            case "raw" -> documentService.readRawProcedure(procedureName);
            case "dirty" -> documentService.readDirtyProcedure(procedureName);
            default -> documentService.cleanCommentsService(procedureName);
        };
    }

    private Resource choosePromptTemplate(String strategy) {
        return switch (strategy) {
            case "few-shot" -> fewShotPrompt;
            case "chain-of-thought" -> chainOfThoughtPrompt;
            default -> zeroShotPrompt;
        };
    }

    private String extractJson(String rawResponse) {
        if (rawResponse == null || rawResponse.trim().isEmpty()) return "";

        Pattern pattern = Pattern.compile("```(?:json)?\\s*\\n?(.+?)```", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(rawResponse);
        if (matcher.find()) return matcher.group(1).trim();

        Pattern jsonPattern = Pattern.compile("(\\{.+})", Pattern.DOTALL);
        Matcher jsonMatcher = jsonPattern.matcher(rawResponse);
        if (jsonMatcher.find()) return jsonMatcher.group(1).trim();

        return rawResponse.trim();
    }
}