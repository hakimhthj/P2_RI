package es.ugr.ri;

import java.io.IOException;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.core.SimpleAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.es.SpanishAnalyzer;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;

public class AnalisisDemo {

    public static void analizarYResumir(String nombre, Analyzer analyzer, String texto) throws IOException {
        int totalTokens = 0;
        java.util.Set<String> terminosUnicos = new java.util.HashSet<>();

        System.out.println("\n==================================================");
        System.out.println("ANALIZADOR: " + nombre);
        System.out.println("==================================================");

        try (TokenStream ts = analyzer.tokenStream("contenido", texto)) {
            CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
            OffsetAttribute offset = ts.addAttribute(OffsetAttribute.class);
            PositionIncrementAttribute inc = ts.addAttribute(PositionIncrementAttribute.class);
            TypeAttribute tipo = ts.addAttribute(TypeAttribute.class);

            int pos = -1;
            ts.reset();
            while (ts.incrementToken()) {
                totalTokens++;
                terminosUnicos.add(term.toString());
                pos += inc.getPositionIncrement();

                // Imprimir los primeros 15 tokens para inspección en el informe
                if (totalTokens <= 15) {
                    System.out.printf("%-22s pos %-3d offset=[%3d, %3d] tipo=%s%n",
                            term.toString(), pos, offset.startOffset(), offset.endOffset(), tipo.type());
                }
            }
            ts.end();
        }
        System.out.printf("%n[Métricas] Total Tokens: %d | Términos Únicos (Vocabulario): %d%n",
                totalTokens, terminosUnicos.size());
    }

    public static void main(String[] args) throws Exception {
        // Texto de 100-200 palabras con tildes, puntuación, números, nombres propios y términos compuestos
        String texto = "El pasado 15 de marzo de 2026, la Universidad de Granada (UGR) organizó un simposio internacional "
                     + "sobre la recuperación de información y el aprendizaje automático en España. El profesor Juan F. Huete "
                     + "presentó un sistema web-based capaz de procesar rápidamente 50.000 consultas complejas. "
                     + "Los investigadores analizaron la eficacia de las herramientas open-source como Apache Lucene 10.5.2, "
                     + "evaluando la precisión, la cobertura semántica y la eliminación de términos irrelevantes. "
                     + "A pesar de las dificultades morfológicas, la solución técnica demostró una mejora del 18.5% en "
                     + "los resultados de búsqueda textual.";

        // Analizadores básicos
        try (Analyzer ws = new WhitespaceAnalyzer();
             Analyzer std = new StandardAnalyzer();
             Analyzer es = new SpanishAnalyzer();

             // Tratamiento adicional 1: Normalización de acentos + Stopwords personalizadas
             Analyzer customAcentos = CustomAnalyzer.builder()
                     .withTokenizer("standard")
                     .addTokenFilter("lowercase")
                     .addTokenFilter("asciiFolding") // Normaliza tildes (á -> a, ñ -> n)
                     .addTokenFilter("spanishLightStem")
                     .build();

             // Tratamiento adicional 2: Shingles (n-gramas de tokens / bi-gramas)
             Analyzer customShingles = CustomAnalyzer.builder()
                     .withTokenizer("standard")
                     .addTokenFilter("lowercase")
                     .addTokenFilter("shingle", "minShingleSize", "2", "maxShingleSize", "2", "outputUnigrams", "true")
                     .build()) {

            analizarYResumir("WhitespaceAnalyzer", ws, texto);
            analizarYResumir("StandardAnalyzer", std, texto);
            analizarYResumir("SpanishAnalyzer", es, texto);
            analizarYResumir("Custom (ASCIIFolding + Stemming)", customAcentos, texto);
            analizarYResumir("Custom (Shingles / Bi-gramas)", customShingles, texto);
        }
    }
}
