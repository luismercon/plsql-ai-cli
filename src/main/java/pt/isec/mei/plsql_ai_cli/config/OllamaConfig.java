package pt.isec.mei.plsql_ai_cli.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OllamaConfig {
    @Bean("MODEL_A")
    public ChatModel qwenChatModel(OllamaApi ollamaApi) {
        return OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .defaultOptions(
                        OllamaChatOptions.builder()
                                .model("qwen3-coder:30b")
                                .temperature(0.4)
                                .topP(0.9)
                                .topK(50)
                                .build()
                )
                .build();
    }

    @Bean("MODEL_B")
    public ChatModel llamaChatModel(OllamaApi ollamaApi) {
        return OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .defaultOptions(
                        OllamaChatOptions.builder()
                                .model("deepseek-coder-v2:16b")
                                .temperature(0.4)
                                .topP(0.9)
                                .topK(50)
                                .build()
                )
                .build();
    }
}
