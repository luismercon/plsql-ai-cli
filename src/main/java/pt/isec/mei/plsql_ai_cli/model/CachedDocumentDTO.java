package pt.isec.mei.plsql_ai_cli.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

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


    public String getBody() {
        return text != null ? text : content;
    }


}