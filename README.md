# Análisis de texto y autocompletado con Apache Lucene

Proyecto académico de Recuperación de Información de la Universidad de Granada. Comparamos analizadores de texto, personalizamos el tratamiento de preguntas en inglés y estudiamos cuatro estrategias de sugerencia sobre una colección de 50.000 preguntas de Quora.

**Tecnologías:** Java · Apache Lucene · Maven.

**Autores:** Hakim Tahri Hassani Jotei y Rafael Estévez Marrero.

## Qué hemos trabajado

- Comparación de analizadores y de sus tokens, posiciones y offsets.
- Diseño de un analizador que conserva negaciones y protege palabras del stemming.
- Pruebas de Prefix, Fuzzy, Infix y Next-term, comparando analizadores y pesos.

## Un ejemplo

Con la consulta `How do I start learming`, con una errata, StandardAnalyzer y pesos por frecuencia:

```text
Prefix: sin sugerencias
Fuzzy:
  1. How do I start learning c language?
  2. How do I start learning and practicing programming?
```

Fuzzy recupera sugerencias aunque haya un error al escribir. Esto no garantiza que todas sean relevantes: también hemos observado coincidencias entre palabras de distinto significado, como `learn` y `earn`.

## Qué hemos aprendido

- Eliminar stopwords puede hacer que se pierda información importante, como una negación.
- El stemming permite relacionar variantes de una palabra, pero puede producir raíces poco naturales.
- Los pesos cambian el orden de las sugerencias; una pregunta frecuente no siempre es la más adecuada.

## Archivos del proyecto

El código se encuentra en `src/main/java/es/ugr/ri/` y está dividido en tres clases:

- `AnalisisDemo.java`: analiza el texto de `texto_naruto.txt` y muestra los tokens y un resumen de los resultados de la actividad 1.
- `QuestionAnalyzer.java`: contiene nuestro analizador de preguntas, con los cambios en las stopwords y las palabras que hemos protegido del stemming.
- `Suggestion.java`: permite ver los tokens de una consulta y probar los cuatro métodos de sugerencia.

En la raíz también se encuentran `pom.xml`, con la configuración de Maven y las dependencias, y los archivos de texto necesarios para las pruebas.

## Requisitos

Para ejecutar el proyecto necesitamos Java 21 o superior y Maven. Hemos utilizado Java 25.0.4.1 de Microsoft, Maven 3.10.0 y Apache Lucene 10.5.2.

Antes de empezar, debemos tener `texto_naruto.txt` y `quora_queries_50000.tsv` en la misma carpeta que `pom.xml`. Si el corpus no está incluido, se puede descargar de PRADO y colocarlo en esa carpeta.

El corpus utiliza codificación UTF-8 y contiene las columnas `qid`, `question` y `frequency`, separadas por tabuladores. El campo `frequency` indica cuántas veces aparece la pregunta en los pares originales de Quora, no cuántas veces la han buscado los usuarios.

## Cómo ejecutarlo

Abrimos PowerShell en la carpeta del proyecto, donde está `pom.xml`, y compilamos:

```powershell
mvn clean compile
```

Para ejecutar la actividad 1:

```powershell
mvn exec:java "-Dexec.mainClass=es.ugr.ri.AnalisisDemo"
```

Para probar el analizador de preguntas y los métodos de sugerencia:

```powershell
mvn exec:java "-Dexec.mainClass=es.ugr.ri.Suggestion" "-Dexec.args=quora_queries_50000.tsv question frequency"
```

Este comando abre un menú que permite inspeccionar los tokens o elegir entre Prefix, Fuzzy, Infix y Next-term. Después de elegir un método, podemos escribir las consultas que queramos probar. Escribimos `FIN` para volver al menú y elegimos la opción `6` para salir del programa.

## Cambiar la configuración

Los argumentos de `Suggestion` siguen este orden:

```text
corpus.tsv analizador pesos estrategia
```

- **Analizador:** `standard`, `english`, `question`, `question-initial` o `whitespace`. La opción `question` utiliza nuestro analizador modificado y `question-initial` permite compararlo con el inicial.
- **Pesos:** `uniform` asigna el mismo peso a todas las preguntas y `frequency` utiliza su frecuencia en la colección.
- **Estrategia:** `prefix`, `fuzzy`, `infix` o `next`. Este último argumento es opcional; si no lo ponemos, aparece el menú.

Por ejemplo, para probar directamente Fuzzy con StandardAnalyzer y pesos uniformes:

```powershell
mvn exec:java "-Dexec.mainClass=es.ugr.ri.Suggestion" "-Dexec.args=quora_queries_50000.tsv standard uniform fuzzy"
```

## Consultas de prueba

En `consultas_prueba.tsv` hemos recogido las consultas y comparaciones de la memoria, con sus analizadores, pesos, estrategias y apartados. `no_aplica` indica que un campo no interviene en esa prueba.

El TSV sirve de guía y no se carga automáticamente. Usamos los argumentos de cada fila e introducimos la consulta en `Suggestion`. Para `tokens`, indicamos solo el corpus y el analizador y elegimos la opción 1 del menú; los pesos no afectan a los tokens. Si `espacio_final` es `si`, añadimos un espacio al final.

Las configuraciones incluidas se han comprobado con el código del proyecto. `How can I learn`, sin espacio final, permite comparar pesos. `hubris` permite comparar Prefix e Infix, y `machine `, con espacio final, permite comparar los analizadores y pesos con Next-term. También se incluyen seis preguntas completas para inspeccionar sus tokens.

El programa pide hasta cinco sugerencias (`k=5`) y usa los constructores predeterminados de Lucene 10.5.2. Fuzzy admite una edición (`maxEdits=1`); Infix consulta con `allTermsRequired=false` y `doHighlight=false`; Next-term usa bigramas (`grams=2`) y `alpha=0.4`. Estos valores vienen del código y de Lucene, no se pasan como argumentos al ejecutar.

## Algunas cosas a tener en cuenta

En Next-term, el espacio al final de la consulta importa: escribir `machine` no es lo mismo que escribir `machine `, con un espacio después. Este método utiliza las frecuencias de los n-gramas para generar continuaciones, por lo que cambiar entre `uniform` y `frequency` no modifica sus resultados.

Algunas consultas con stopwords pueden provocar un error de Lucene 10.5.2 en Next-term relacionado con los huecos de posición. En ese caso, el programa muestra `ERROR_LUCENE` y permite seguir probando otras consultas. Este error se distingue de una consulta que simplemente no devuelve sugerencias.

## Asistencia de IA

Hemos utilizado Gemini y ChatGPT como apoyo para resolver dudas, entender el código y detectar errores.
