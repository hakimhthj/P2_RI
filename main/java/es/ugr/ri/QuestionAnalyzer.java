package es.ugr.ri;

import java.util.Arrays;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.en.PorterStemFilter;
import org.apache.lucene.analysis.miscellaneous.SetKeywordMarkerFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;

public class QuestionAnalyzer extends Analyzer {

    private final CharArraySet stopwords;
    private final CharArraySet stemExclusionSet;

    public QuestionAnalyzer() {
        // 1. Partir del conjunto por defecto de EnglishAnalyzer
        CharArraySet baseStopwords = EnglishAnalyzer.getDefaultStopSet();
        this.stopwords = new CharArraySet(baseStopwords, true);

        // 2. Conservar interrogativas y auxiliares clave (se eliminan de la lista de stopwords)
        List<String> preservedWords = Arrays.asList(
                "what", "why", "where", "when", "how", "who", "which",
                "can", "should", "could", "would", "is", "are", "does", "do", "did"
        );
        this.stopwords.removeAll(preservedWords);

        // 3. Proteger palabras clave contra el stemming (Stem Exclusion Set)
        List<String> protectedFromStemming = Arrays.asList(
                "university", "community", "learning"
        );
        this.stemExclusionSet = new CharArraySet(protectedFromStemming, true);
    }

    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer source = new StandardTokenizer();
        TokenStream filter = new LowerCaseFilter(source);
        filter = new StopFilter(filter, stopwords);
        
        if (!stemExclusionSet.isEmpty()) {
            filter = new SetKeywordMarkerFilter(filter, stemExclusionSet);
        }
        
        filter = new PorterStemFilter(filter);
        return new TokenStreamComponents(source, filter);
    }
}