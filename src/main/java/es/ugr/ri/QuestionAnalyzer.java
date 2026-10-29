package es.ugr.ri;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.en.EnglishPossessiveFilter;
import org.apache.lucene.analysis.en.PorterStemFilter;
import org.apache.lucene.analysis.miscellaneous.SetKeywordMarkerFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;

/** Una sola cadena de filtros, con un perfil de referencia y otro para el experimento. */
public final class QuestionAnalyzer extends Analyzer {
    public enum Profile { INITIAL, MODIFIED }

    // Son stopwords reales del conjunto inglés. Interrogativos como what ya se conservan.
    static final Set<String> PRESERVED_STOPWORDS = Set.of("is", "are", "was", "not", "no");
    // Las entidades se conservan literalmente; learning se reduce para mantener learning/learned/learn.
    // are y was se protegen también de Porter, que produciría ar y wa.
    static final List<String> STEM_EXCLUSIONS = List.of("university", "community", "are", "was");

    private final CharArraySet stopwords;
    private final CharArraySet stemExclusions;

    public QuestionAnalyzer() {
        this(Profile.MODIFIED);
    }

    public QuestionAnalyzer(Profile profile) {
        Objects.requireNonNull(profile);
        CharArraySet base = EnglishAnalyzer.getDefaultStopSet();
        if (profile == Profile.INITIAL) {
            // Comportamiento REAL del Suggestion original: remove(String) no eliminaba nada
            // de CharArraySet y no había exclusiones de stemming. No se corrige la referencia.
            stopwords = CharArraySet.unmodifiableSet(CharArraySet.copy(base));
            stemExclusions = CharArraySet.EMPTY_SET;
        } else {
            // CharArraySet no admite remove: construir la diferencia sin modificar el conjunto base.
            CharArraySet filtered = new CharArraySet(base.size(), false);
            for (Object entry : base) {
                String term = new String((char[]) entry);
                if (!PRESERVED_STOPWORDS.contains(term)) filtered.add(term);
            }
            stopwords = CharArraySet.unmodifiableSet(filtered);
            stemExclusions = CharArraySet.unmodifiableSet(new CharArraySet(STEM_EXCLUSIONS, false));
        }
    }

    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        StandardTokenizer tokenizer = new StandardTokenizer();
        TokenStream stream = new EnglishPossessiveFilter(tokenizer);
        stream = new LowerCaseFilter(stream);
        stream = new StopFilter(stream, stopwords);
        if (!stemExclusions.isEmpty()) stream = new SetKeywordMarkerFilter(stream, stemExclusions);
        stream = new PorterStemFilter(stream);
        return new TokenStreamComponents(tokenizer, stream);
    }
}
