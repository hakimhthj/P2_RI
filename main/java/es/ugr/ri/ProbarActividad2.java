package es.ugr.ri;

import java.io.IOException;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;

public class ProbarActividad2 {

    public static void compararPregunta(String pregunta, Analyzer std, Analyzer eng, Analyzer qa) throws IOException {
        System.out.println("\n==========================================================================================");
        System.out.println("PREGUNTA: \"" + pregunta + "\"");
        System.out.println("==========================================================================================");

        imprimirTokens("StandardAnalyzer", std, pregunta);
        imprimirTokens("EnglishAnalyzer ", eng, pregunta);
        imprimirTokens("QuestionAnalyzer", qa, pregunta);
    }

    private static void imprimirTokens(String nombre, Analyzer analyzer, String texto) throws IOException {
        System.out.printf("%-18s -> ", nombre);
        try (TokenStream ts = analyzer.tokenStream("question", texto)) {
            CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
            PositionIncrementAttribute inc = ts.addAttribute(PositionIncrementAttribute.class);
            
            ts.reset();
            StringBuilder sb = new StringBuilder("[ ");
            int pos = -1;
            while (ts.incrementToken()) {
                pos += inc.getPositionIncrement();
                sb.append(term.toString()).append(" (p:").append(pos).append(")  ");
            }
            sb.append("]");
            System.out.println(sb.toString());
            ts.end();
        }
    }

    public static void main(String[] args) throws IOException {
        // 6 Preguntas seleccionadas con variaciones de interrogativas, auxiliares y morfología
        String[] preguntas = {
            "How can I learn Python programming effectively?",
            "Why is machine learning so popular in computer science?",
            "Where should I study at university to become a software engineer?",
            "What are the best communities for learning web development?",
            "Which programming language is better for machine learning?",
            "Does community college provide good educational opportunities?"
        };

        try (Analyzer std = new StandardAnalyzer();
             Analyzer eng = new EnglishAnalyzer();
             Analyzer qa = new QuestionAnalyzer()) {

            for (String preg : preguntas) {
                compararPregunta(preg, std, eng, qa);
            }
        }
    }
}