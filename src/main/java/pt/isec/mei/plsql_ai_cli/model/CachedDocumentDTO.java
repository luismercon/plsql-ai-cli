package pt.isec.mei.plsql_ai_cli.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CachedDocumentDTO {

    private String id;

    // O Spring AI às vezes chama de 'text' ou 'content' no JSON, dependendo da versão.
    // Vamos mapear ambos para garantir.
    @JsonProperty("text")
    private String text;

    @JsonProperty("content")
    private String content; // Fallback

    private Map<String, Object> metadata;

    // O CAMPO QUE NÓS QUEREMOS
    private List<Double> embedding;

    /**
     * Converte este DTO para o objeto Document do Spring AI,
     * mas preservando o vetor num formato que possamos usar.
     * Como a classe Document não aceita vetor, retornamos um par ou usamos o DTO diretamente no clustering.
     */
    public RealVector getRealVector() {
        if (embedding == null || embedding.isEmpty()) {
            return null;
        }
        double[] array = embedding.stream().mapToDouble(Double::doubleValue).toArray();
        return new ArrayRealVector(array);
    }

    public String getBody() {
        return text != null ? text : content;
    }

    public Document toDocument() {
        // Cria um Document padrão para uso futuro, se necessário
        return new Document(getBody(), metadata);
    }
}
