package pt.isec.mei.plsql_ai_cli.utils;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;

import java.util.List;

public class VectorUtils {


    /**
     * NOVO: Converte float[] (padrão do Spring AI M6) para RealVector
     */
    public static RealVector toRealVector(float[] embedding) {
        if (embedding == null || embedding.length == 0) {
            throw new IllegalArgumentException("Embedding array cannot be null or empty");
        }
        // Apache Commons Math precisa de double[], então convertemos
        double[] doubleArray = new double[embedding.length];
        for (int i = 0; i < embedding.length; i++) {
            doubleArray[i] = embedding[i];
        }
        return new ArrayRealVector(doubleArray);
    }


    /**
     * Converte a Lista de Doubles (Spring AI) para RealVector (Commons Math).
     */
    public static RealVector toRealVector(List<Double> embedding) {
        if (embedding == null || embedding.isEmpty()) {
            throw new IllegalArgumentException("Embedding list cannot be null or empty");
        }
        // Converte List<Double> para double[]
        double[] array = embedding.stream().mapToDouble(Double::doubleValue).toArray();
        return new ArrayRealVector(array);
    }

    /**
     * Calcula a Similaridade de Cosseno entre dois vetores.
     * Retorno: 0.0 a 1.0 (onde 1.0 é idêntico).
     */
    public static double cosineSimilarity(RealVector v1, RealVector v2) {
        if (v1.getDimension() != v2.getDimension()) {
            throw new IllegalArgumentException("Vectors must have the same dimension");
        }
        // Cosseno = (A . B) / (|A| * |B|)
        return v1.cosine(v2);
    }

    /**
     * Sobrecarga para facilitar o uso direto com Listas
     */
    public static double cosineSimilarity(List<Double> l1, List<Double> l2) {
        return cosineSimilarity(toRealVector(l1), toRealVector(l2));
    }

    /**
     * Calcula o Centróide (Média) de uma lista de vetores.
     * Usado para K-Means e para achar o Medoide.
     */
    public static RealVector calculateCentroid(List<RealVector> vectors) {
        if (vectors == null || vectors.isEmpty()) {
            throw new IllegalArgumentException("Vector list cannot be empty");
        }

        int dim = vectors.get(0).getDimension();
        RealVector sum = new ArrayRealVector(dim); // Vetor de zeros

        for (RealVector v : vectors) {
            sum = sum.add(v); // Soma vetorial
        }

        return sum.mapDivide(vectors.size()); // Divide cada elemento pelo total (média)
    }

}
