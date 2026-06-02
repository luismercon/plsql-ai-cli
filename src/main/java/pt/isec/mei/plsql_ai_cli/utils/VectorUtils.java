package pt.isec.mei.plsql_ai_cli.utils;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;
import org.springframework.ai.document.Document;

import java.util.List;

public class VectorUtils {

    /**
     * Converte List<Double> (JSON/Cache) para RealVector.
     */
    public static RealVector toRealVector(List<Double> embedding) {
        if (embedding == null || embedding.isEmpty()) {
            throw new IllegalArgumentException("Embedding list cannot be null or empty");
        }
        double[] array = embedding.stream().mapToDouble(Double::doubleValue).toArray();
        return new ArrayRealVector(array);
    }

    /**
     * Safely extracts the embedding vector from document metadata.
     * @param doc The document containing the embedding in metadata
     * @return The embedding as a List of Doubles
     * @throws IllegalStateException if the embedding is missing or has incorrect type
     */
    @SuppressWarnings("unchecked")
    public static List<Double> getEmbeddingFromMetadata(Document doc) {
        Object embeddingObj = doc.getMetadata().get("custom_embedding");
        if (embeddingObj instanceof List<?> list) {
            // Validate that the list contains Doubles (check first element if not empty)
            if (list.isEmpty() || list.get(0) instanceof Double) {
                return (List<Double>) list;
            }
        }
        throw new IllegalStateException("custom_embedding must be a List<Double> but was: " +
                (embeddingObj != null ? embeddingObj.getClass().getName() : "null"));
    }

    public static double cosineSimilarity(RealVector v1, RealVector v2) {
        if (v1 == null || v2 == null) return 0.0;
        return v1.cosine(v2);
    }

}