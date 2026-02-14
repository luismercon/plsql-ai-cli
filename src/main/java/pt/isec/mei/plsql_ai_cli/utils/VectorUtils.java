package pt.isec.mei.plsql_ai_cli.utils;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;

import java.util.List;

public class VectorUtils {

    /**
     * Converte List<Double> (JSON/Cache) para RealVector.
     */
    public static RealVector toRealVector(List<Double> embedding) {
        if (embedding == null || embedding.isEmpty()) {
            throw new IllegalArgumentException("A lista de embedding não pode ser nula");
        }
        double[] array = embedding.stream().mapToDouble(Double::doubleValue).toArray();
        return new ArrayRealVector(array);
    }

    /**
     * Converte float[] (Spring AI M6) para RealVector.
     */
    public static RealVector fromFloatArray(float[] embedding) {
        if (embedding == null || embedding.length == 0) {
            throw new IllegalArgumentException("O array de float não pode ser nulo");
        }
        double[] doubles = new double[embedding.length];
        for (int i = 0; i < embedding.length; i++) {
            doubles[i] = (double) embedding[i];
        }
        return new ArrayRealVector(doubles);
    }

    public static double cosineSimilarity(RealVector v1, RealVector v2) {
        if (v1 == null || v2 == null) return 0.0;
        return v1.cosine(v2);
    }

    public static RealVector calculateCentroid(List<RealVector> vectors) {
        if (vectors == null || vectors.isEmpty()) return null;
        RealVector sum = new ArrayRealVector(vectors.get(0).getDimension());
        for (RealVector v : vectors) {
            sum = sum.add(v);
        }
        return sum.mapDivide(vectors.size());
    }
}