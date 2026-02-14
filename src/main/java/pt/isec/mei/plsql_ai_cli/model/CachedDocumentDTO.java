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

    @JsonProperty("text")
    private String text;

    @JsonProperty("content")
    private String content;

    private Map<String, Object> metadata;

    private List<Double> embedding;

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
        return new Document(getBody(), metadata);
    }
}