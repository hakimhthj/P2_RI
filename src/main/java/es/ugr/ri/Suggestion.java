package es.ugr.ri;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.search.suggest.InputIterator;
import org.apache.lucene.search.suggest.Lookup;
import org.apache.lucene.search.suggest.analyzing.AnalyzingInfixSuggester;
import org.apache.lucene.search.suggest.analyzing.AnalyzingSuggester;
import org.apache.lucene.search.suggest.analyzing.FreeTextSuggester;
import org.apache.lucene.search.suggest.analyzing.FuzzySuggester;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.BytesRef;

/**
 * Práctica RI 2026/27. Entrada TSV con cabecera: qid<TAB>question<TAB>frequency.
 * Cada fila representa una pregunta distinta; frequency cuenta apariciones
 * en pares de Quora, NO el número de búsquedas de usuarios.
 *
 * Ejecutar: java ... es.ugr.ri.Suggestion ruta.tsv [standard|english|question|question-initial|whitespace]
 *                                     [uniform|frequency] [prefix|fuzzy|infix|next]
 * Si no se especifica la estrategia se ofrece un menú interactivo.
 */
public class Suggestion {

    record Question(String qid, String text, long frequency) {}

    static List<Question> loadTsv(Path path) throws IOException {
        List<Question> rows = new ArrayList<>();
        Set<String> seenIds = new java.util.HashSet<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            if (header == null) throw new IOException("TSV vacío: " + path);
            String[] columns = header.replace("\uFEFF", "").split("\t", -1);
            int qid = column(columns, "qid");
            int question = column(columns, "question");
            int frequency = column(columns, "frequency");
            int max = Math.max(qid, Math.max(question, frequency));
            String line;
            int lineNo = 1;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                String[] fields = line.split("\t", -1);
                if (fields.length <= max) throw new IOException("Fila TSV incompleta: " + lineNo);
                String id = fields[qid].trim();
                String text = fields[question].trim();
                if (id.isEmpty() || text.isEmpty()) continue;
                if (!seenIds.add(id)) throw new IOException("qid repetido en línea " + lineNo + ": " + id);
                long f;
                try { f = Long.parseLong(fields[frequency].trim()); }
                catch (NumberFormatException ex) { throw new IOException("Frecuencia inválida en línea " + lineNo, ex); }
                if (f < 1) throw new IOException("Frecuencia debe ser >= 1 en línea " + lineNo);
                rows.add(new Question(id, text, f));
            }
        }
        if (rows.isEmpty()) throw new IOException("No hay consultas válidas en el TSV");
        return rows;
    }

    static int column(String[] columns, String name) throws IOException {
        for (int i = 0; i < columns.length; i++)
            if (columns[i].trim().equalsIgnoreCase(name)) return i;
        throw new IOException("Falta la columna obligatoria: " + name);
    }

    /** InputIterator con pesos explícitos y orden de entrada reproducible. */
    static final class QuestionsIterator implements InputIterator {
        private final Iterator<Question> iterator;
        private final boolean uniform;
        private Question current;
        QuestionsIterator(List<Question> questions, boolean uniform) {
            this.iterator = questions.iterator();
            this.uniform = uniform;
        }
        @Override public BytesRef next() {
            if (!iterator.hasNext()) { current = null; return null; }
            current = iterator.next();
            return new BytesRef(current.text());
        }
        @Override public long weight() { return uniform ? 1L : current.frequency(); }
        @Override public BytesRef payload() { return null; }
        @Override public boolean hasPayloads() { return false; }
        @Override public Set<BytesRef> contexts() { return null; }
        @Override public boolean hasContexts() { return false; }
    }

    static Analyzer analyzer(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "standard" -> new StandardAnalyzer();
            case "english" -> new EnglishAnalyzer();
            case "question" -> new QuestionAnalyzer();
            case "question-initial" -> new QuestionAnalyzer(QuestionAnalyzer.Profile.INITIAL);
            case "whitespace" -> new WhitespaceAnalyzer();
            default -> throw new IllegalArgumentException(
                    "Analyzer: standard, english, question, question-initial o whitespace");
        };
    }

    record TokenData(String term, int position, int increment, int start, int end) {}

    static List<TokenData> tokens(Analyzer analyzer, String input) throws IOException {
        List<TokenData> result = new ArrayList<>();
        try (TokenStream ts = analyzer.tokenStream("question", input)) {
            CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
            OffsetAttribute offset = ts.addAttribute(OffsetAttribute.class);
            PositionIncrementAttribute increment = ts.addAttribute(PositionIncrementAttribute.class);
            ts.reset();
            int pos = -1;
            while (ts.incrementToken()) {
                pos += increment.getPositionIncrement();
                result.add(new TokenData(term.toString(), pos, increment.getPositionIncrement(),
                        offset.startOffset(), offset.endOffset()));
            }
            ts.end();
        }
        return result;
    }

    static void showTokens(Analyzer analyzer, String input) throws IOException {
        for (TokenData token : tokens(analyzer, input))
            System.out.printf("  %-24s pos=%-3d offsets=[%d,%d)%n",
                    token.term(), token.position(), token.start(), token.end());
    }

    static void closeLookup(Lookup lookup) throws IOException {
        if (lookup instanceof AutoCloseable closeable) {
            try { closeable.close(); }
            catch (Exception ex) { throw new IOException("Error al cerrar el suggester", ex); }
        }
    }

    static Lookup build(String strategy, Analyzer analyzer, List<Question> questions,
                        boolean uniform, Directory directory) throws IOException {
        Lookup lookup;
        switch (strategy) {
            case "prefix" -> lookup = new AnalyzingSuggester(directory, "prefix", analyzer);
            case "fuzzy" -> lookup = new FuzzySuggester(directory, "fuzzy", analyzer);
            case "infix" -> lookup = new AnalyzingInfixSuggester(directory, analyzer);
            case "next" -> lookup = new FreeTextSuggester(analyzer);
            default -> throw new IllegalArgumentException("Estrategia: prefix, fuzzy, infix o next");
        }
        lookup.build(new QuestionsIterator(questions, uniform));
        return lookup;
    }

    static List<Lookup.LookupResult> suggestions(Lookup lookup, String strategy, String input, int k)
            throws IOException {
        if (strategy.equals("infix"))
            return ((AnalyzingInfixSuggester) lookup).lookup(input, k, false, false);
        try {
            return lookup.lookup(input, false, k);
        } catch (NullPointerException ex) {
            // Incidencia reproducida con los huecos de StopFilter en Lucene 10.5.2.
            // Solo se captura este fallo conocido; otros errores siguen propagándose.
            StackTraceElement frame = ex.getStackTrace()[0];
            if (strategy.equals("next") && frame.getClassName().equals(FreeTextSuggester.class.getName())
                    && frame.getMethodName().equals("decodeWeight"))
                throw new NextTermException(ex);
            throw ex;
        }
    }

    static final class NextTermException extends IOException {
        NextTermException(Throwable cause) {
            super("FreeTextSuggester 10.5.2: frecuencia de contexto ausente (hueco de posición)", cause);
        }
    }

    static void run(String strategy, Analyzer analyzer, List<Question> questions,
                    boolean uniform, Scanner scanner) throws IOException {
        System.out.printf("Construyendo %s con %,d preguntas (%s)...%n",
                strategy, questions.size(), uniform ? "peso uniforme" : "frecuencia");
        try (Directory directory = new ByteBuffersDirectory()) {
            Lookup lookup = build(strategy, analyzer, questions, uniform, directory);
            try {
                System.out.println("Escribe una consulta; FIN para volver al menú.");
                while (true) {
                    System.out.print("consulta> ");
                    if (!scanner.hasNextLine()) return;
                    // El espacio final distingue completar un prefijo de predecir la siguiente palabra.
                    String input = scanner.nextLine();
                    if (input.trim().equalsIgnoreCase("FIN")) return;
                    if (input.isBlank()) continue;
                    List<Lookup.LookupResult> found;
                    try { found = suggestions(lookup, strategy, input, 5); }
                    catch (NextTermException ex) {
                        System.out.println("  ERROR_LUCENE: " + ex.getMessage());
                        continue;
                    }
                    if (found.isEmpty()) System.out.println("  (sin sugerencias)");
                    int rank = 0;
                    for (Lookup.LookupResult r : found)
                        System.out.printf("  %d. %s  [valor=%d]%n", ++rank,
                                r.key.toString().replace('\u001e', ' '), r.value);
                    if (strategy.equals("next"))
                        System.out.println("  Nota: 'valor' es una puntuación del modelo de lenguaje, no frequency.");
                }
            } finally {
                closeLookup(lookup);
            }
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1 || args.length > 4) {
            System.err.println("Uso: Suggestion <quora_queries_50000.tsv> "
                + "[standard|english|question|question-initial|whitespace] [uniform|frequency] "
                + "[prefix|fuzzy|infix|next]");
            System.exit(2);
        }
        Path input = Path.of(args[0]);
        String analyzerName = args.length >= 2 ? args[1] : "standard";
        String weights = args.length >= 3 ? args[2] : "frequency";
        if (!weights.equals("uniform") && !weights.equals("frequency"))
            throw new IllegalArgumentException("Pesos: uniform o frequency");
        List<Question> questions = loadTsv(input);
        questions.sort(Comparator.comparing(Question::text).thenComparing(Question::qid));
        System.out.printf("Cargadas %,d preguntas únicas por qid.%n", questions.size());
        try (Analyzer analyzer = analyzer(analyzerName); Scanner scanner = new Scanner(System.in)) {
            if (args.length == 4) {
                run(args[3], analyzer, questions, weights.equals("uniform"), scanner);
                return;
            }
            while (true) {
                System.out.println("\n1. Inspeccionar tokens  2. Prefix  3. Fuzzy "
                        + " 4. Infix  5. Next-term  6. Salir");
                System.out.print("opción> ");
                if (!scanner.hasNextLine()) break;
                String option = scanner.nextLine().trim();
                switch (option) {
                    case "1" -> {
                        System.out.print("texto> ");
                        if (scanner.hasNextLine()) showTokens(analyzer, scanner.nextLine());
                    }
                    case "2" -> run("prefix", analyzer, questions, weights.equals("uniform"), scanner);
                    case "3" -> run("fuzzy", analyzer, questions, weights.equals("uniform"), scanner);
                    case "4" -> run("infix", analyzer, questions, weights.equals("uniform"), scanner);
                    case "5" -> run("next", analyzer, questions, weights.equals("uniform"), scanner);
                    case "6" -> { return; }
                    default -> System.out.println("Opción no válida (1-6).");
                }
            }
        }
    }
}
