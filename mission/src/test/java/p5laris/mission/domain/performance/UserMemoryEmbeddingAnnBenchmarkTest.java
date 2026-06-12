package p5laris.mission.domain.performance;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Manual benchmark for mission RAG vector search.
 *
 * <p>This test rewrites the configured benchmark database. It is skipped during
 * normal test runs and runs only when RAG_BENCHMARK_ENABLED=true is provided.</p>
 */
class UserMemoryEmbeddingAnnBenchmarkTest {

    private static final int DIMENSION = 768;
    private static final int TOP_K = 5;
    private static final int CANDIDATE_LIMIT = Math.max(100, TOP_K * 20);
    private static final double SIMILARITY_THRESHOLD = 0.72d;
    private static final double MAX_DISTANCE = 1.0d - SIMILARITY_THRESHOLD;
    private static final String MODEL = "gemini-embedding-001";
    private static final long BASE_USER_ID = 990_000_000L;
    private static final int[] SCALES = {100, 1_000, 5_000, 10_000};
    private static final String RAG_FILTER_INDEX = "idx_user_memory_embeddings_rag_filter";
    private static final String HNSW_INDEX = "idx_user_memory_embeddings_embedding_hnsw";

    private static final String SEARCH_SQL = """
            SELECT
                user_memory_id,
                memory_type,
                source_type,
                content,
                metadata_json,
                importance,
                created_at,
                distance
            FROM (
                SELECT
                    m.id AS user_memory_id,
                    m.memory_type,
                    m.source_type,
                    m.content,
                    m.metadata_json::text AS metadata_json,
                    m.importance,
                    m.created_at,
                    e.embedding <=> CAST(? AS vector) AS distance
                FROM user_memory_embeddings e
                JOIN user_memories m ON m.id = e.user_memory_id
                WHERE e.user_id = ?
                  AND e.status = 'COMPLETED'
                  AND e.embedding_model = ?
                  AND e.embedding_dimension = ?
                  AND e.embedding IS NOT NULL
                ORDER BY e.embedding <=> CAST(? AS vector) ASC
                LIMIT ?
            ) candidates
            WHERE distance <= ?
            ORDER BY distance ASC,
                     importance DESC,
                     created_at DESC
            LIMIT ?
            """;

    @Test
    void benchmarkBtreeOnlyAndHnswSearch() throws Exception {
        Assumptions.assumeTrue(isEnabled(), "Set RAG_BENCHMARK_ENABLED=true to run this benchmark.");

        BenchmarkConfig config = BenchmarkConfig.fromSystemProperties();
        ensureDatabaseExists(config);
        migrateCleanBenchmarkDatabase(config);

        try (Connection connection = DriverManager.getConnection(config.benchmarkUrl(), config.username(), config.password())) {
            SyntheticDataset dataset = seedSyntheticDataset(connection);

            dropAnnIndexes(connection);
            analyze(connection);
            List<BenchmarkResult> baselineResults = runMode(connection, "AS_IS_EXACT_SCAN", dataset, config);

            createAnnIndexes(connection);
            analyze(connection);
            List<BenchmarkResult> hnswResults = runMode(connection, "TO_BE_HNSW", dataset, config);

            List<BenchmarkResult> results = new ArrayList<>();
            results.addAll(baselineResults);
            results.addAll(hnswResults);

            Path reportDir = reportDirectory();
            Files.createDirectories(reportDir);
            writeCsv(reportDir.resolve("rag-ann-benchmark-results.csv"), results);
            writeMarkdown(reportDir.resolve("rag-ann-benchmark-results.md"), results, config);

            for (BenchmarkResult result : results) {
                Files.writeString(
                        reportDir.resolve("%s-%d-plan.txt".formatted(result.mode().toLowerCase(Locale.ROOT), result.memoryCount())),
                        result.explainPlan()
                );
            }

            assertFalse(results.isEmpty());
            System.out.println("RAG ANN benchmark report: " + reportDir.toAbsolutePath());
        }
    }

    private static boolean isEnabled() {
        return Boolean.parseBoolean(System.getenv().getOrDefault("RAG_BENCHMARK_ENABLED", "false"))
                || Boolean.parseBoolean(System.getProperty("rag.benchmark.enabled", "false"));
    }

    private static void ensureDatabaseExists(BenchmarkConfig config) throws SQLException {
        try (Connection connection = DriverManager.getConnection(config.adminUrl(), config.username(), config.password());
             PreparedStatement exists = connection.prepareStatement("SELECT 1 FROM pg_database WHERE datname = ?")) {
            exists.setString(1, config.database());
            try (ResultSet rs = exists.executeQuery()) {
                if (rs.next()) {
                    return;
                }
            }

            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE DATABASE " + quoteIdentifier(config.database()));
            }
        }
    }

    private static void migrateCleanBenchmarkDatabase(BenchmarkConfig config) {
        Flyway flyway = Flyway.configure()
                .dataSource(config.benchmarkUrl(), config.username(), config.password())
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();
        flyway.clean();
        flyway.migrate();
    }

    private static SyntheticDataset seedSyntheticDataset(Connection connection) throws SQLException {
        connection.setAutoCommit(false);

        Map<Integer, String> queryVectorByScale = new HashMap<>();
        Map<Long, VectorPayload> vectorBySourceId = new HashMap<>();
        Instant now = Instant.now();

        try (PreparedStatement memoryInsert = connection.prepareStatement("""
                INSERT INTO user_memories (
                    user_id,
                    source_type,
                    source_id,
                    memory_type,
                    content,
                    metadata_json,
                    importance,
                    created_at,
                    updated_at
                )
                VALUES (?, 'MISSION_COMPLETION_ANSWER', ?, ?, ?, CAST(? AS jsonb), ?, ?, ?)
                """)) {

            for (int scale : SCALES) {
                long userId = userId(scale);
                Random random = new Random(20260612L + scale);
                float[] queryVector = unitVector(random);
                queryVectorByScale.put(scale, toVectorLiteral(queryVector));
                int closeRows = Math.min(100, scale);

                for (int index = 0; index < scale; index++) {
                    long sourceId = sourceId(userId, index);
                    boolean closeToQuery = index < closeRows;
                    float[] vector = closeToQuery
                            ? noisyUnitVector(queryVector, random, 0.015f)
                            : unitVector(random);
                    vectorBySourceId.put(sourceId, new VectorPayload(userId, toVectorLiteral(vector)));

                    Timestamp createdAt = Timestamp.from(now.minusSeconds(index));
                    memoryInsert.setLong(1, userId);
                    memoryInsert.setLong(2, sourceId);
                    memoryInsert.setString(3, memoryType(index));
                    memoryInsert.setString(4, "benchmark memory " + scale + " / " + index);
                    memoryInsert.setString(5, """
                            {"benchmark":true,"scale":%d,"ordinal":%d,"closeToQuery":%s}
                            """.formatted(scale, index, closeToQuery));
                    memoryInsert.setInt(6, 40 + (index % 61));
                    memoryInsert.setTimestamp(7, createdAt);
                    memoryInsert.setTimestamp(8, createdAt);
                    memoryInsert.addBatch();
                }
            }
            memoryInsert.executeBatch();
        }

        Map<Long, Long> memoryIdBySourceId = new HashMap<>();
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT source_id, id
                FROM user_memories
                WHERE user_id >= ?
                  AND user_id <= ?
                """)) {
            select.setLong(1, BASE_USER_ID);
            select.setLong(2, BASE_USER_ID + 10_000L);
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    memoryIdBySourceId.put(rs.getLong("source_id"), rs.getLong("id"));
                }
            }
        }

        try (PreparedStatement embeddingInsert = connection.prepareStatement("""
                INSERT INTO user_memory_embeddings (
                    user_memory_id,
                    user_id,
                    embedding_model,
                    embedding_dimension,
                    embedding,
                    status,
                    attempt_count,
                    next_attempt_at,
                    embedded_at,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, ?, ?, CAST(? AS vector), 'COMPLETED', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """)) {
            List<Map.Entry<Long, VectorPayload>> entries = vectorBySourceId.entrySet()
                    .stream()
                    .sorted(Comparator.comparingLong(Map.Entry::getKey))
                    .toList();

            for (Map.Entry<Long, VectorPayload> entry : entries) {
                Long memoryId = memoryIdBySourceId.get(entry.getKey());
                if (memoryId == null) {
                    throw new IllegalStateException("Missing user_memory id for source_id=" + entry.getKey());
                }
                VectorPayload payload = entry.getValue();
                embeddingInsert.setLong(1, memoryId);
                embeddingInsert.setLong(2, payload.userId());
                embeddingInsert.setString(3, MODEL);
                embeddingInsert.setInt(4, DIMENSION);
                embeddingInsert.setString(5, payload.vectorLiteral());
                embeddingInsert.addBatch();
            }
            embeddingInsert.executeBatch();
        }

        connection.commit();
        connection.setAutoCommit(true);
        analyze(connection);

        return new SyntheticDataset(queryVectorByScale);
    }

    private static List<BenchmarkResult> runMode(
            Connection connection,
            String mode,
            SyntheticDataset dataset,
            BenchmarkConfig config
    ) throws SQLException {
        List<BenchmarkResult> results = new ArrayList<>();
        for (int scale : SCALES) {
            String vectorLiteral = dataset.queryVector(scale);
            long userId = userId(scale);
            for (int i = 0; i < config.warmupRuns(); i++) {
                executeSearch(connection, userId, vectorLiteral);
            }

            List<Double> durations = new ArrayList<>(config.measuredRuns());
            int lastRowCount = 0;
            for (int i = 0; i < config.measuredRuns(); i++) {
                long start = System.nanoTime();
                lastRowCount = executeSearch(connection, userId, vectorLiteral);
                long elapsed = System.nanoTime() - start;
                durations.add(elapsed / 1_000_000.0d);
            }

            String explain = explainSearch(connection, userId, vectorLiteral);
            results.add(BenchmarkResult.from(mode, scale, lastRowCount, durations, explain));
        }
        return results;
    }

    private static int executeSearch(Connection connection, long userId, String vectorLiteral) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SEARCH_SQL)) {
            bindSearchParameters(statement, userId, vectorLiteral);
            int rows = 0;
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    rs.getLong("user_memory_id");
                    rs.getDouble("distance");
                    rows++;
                }
            }
            return rows;
        }
    }

    private static String explainSearch(Connection connection, long userId, String vectorLiteral) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT) " + SEARCH_SQL)) {
            bindSearchParameters(statement, userId, vectorLiteral);
            StringBuilder plan = new StringBuilder();
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    plan.append(rs.getString(1)).append(System.lineSeparator());
                }
            }
            return plan.toString();
        }
    }

    private static void bindSearchParameters(PreparedStatement statement, long userId, String vectorLiteral) throws SQLException {
        statement.setString(1, vectorLiteral);
        statement.setLong(2, userId);
        statement.setString(3, MODEL);
        statement.setInt(4, DIMENSION);
        statement.setString(5, vectorLiteral);
        statement.setInt(6, CANDIDATE_LIMIT);
        statement.setDouble(7, MAX_DISTANCE);
        statement.setInt(8, TOP_K);
    }

    private static void dropAnnIndexes(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP INDEX IF EXISTS " + HNSW_INDEX);
            statement.execute("DROP INDEX IF EXISTS " + RAG_FILTER_INDEX);
        }
    }

    private static void createAnnIndexes(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_user_memory_embeddings_rag_filter
                        ON user_memory_embeddings(user_id, embedding_model, embedding_dimension)
                        WHERE status = 'COMPLETED'
                          AND embedding IS NOT NULL
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_user_memory_embeddings_embedding_hnsw
                        ON user_memory_embeddings
                        USING hnsw (embedding vector_cosine_ops)
                        WITH (m = 16, ef_construction = 64)
                        WHERE status = 'COMPLETED'
                          AND embedding IS NOT NULL
                    """);
        }
    }

    private static void analyze(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("ANALYZE user_memories");
            statement.execute("ANALYZE user_memory_embeddings");
        }
    }

    private static void writeCsv(Path path, List<BenchmarkResult> results) throws IOException {
        StringBuilder csv = new StringBuilder();
        csv.append("mode,memory_count,row_count,min_ms,p50_ms,avg_ms,p95_ms,max_ms,hnsw_index_used\n");
        for (BenchmarkResult result : results) {
            csv.append(result.mode()).append(',')
                    .append(result.memoryCount()).append(',')
                    .append(result.rowCount()).append(',')
                    .append(format(result.minMs())).append(',')
                    .append(format(result.p50Ms())).append(',')
                    .append(format(result.avgMs())).append(',')
                    .append(format(result.p95Ms())).append(',')
                    .append(format(result.maxMs())).append(',')
                    .append(result.hnswIndexUsed())
                    .append('\n');
        }
        Files.writeString(path, csv.toString());
    }

    private static void writeMarkdown(Path path, List<BenchmarkResult> results, BenchmarkConfig config) throws IOException {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# RAG ANN Benchmark Results").append(System.lineSeparator()).append(System.lineSeparator());
        markdown.append("- generated_at: ")
                .append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .append(System.lineSeparator());
        markdown.append("- database: ").append(config.benchmarkUrl()).append(System.lineSeparator());
        markdown.append("- warmup_runs: ").append(config.warmupRuns()).append(System.lineSeparator());
        markdown.append("- measured_runs: ").append(config.measuredRuns()).append(System.lineSeparator());
        markdown.append("- top_k: ").append(TOP_K).append(System.lineSeparator());
        markdown.append("- candidate_limit: ").append(CANDIDATE_LIMIT).append(System.lineSeparator());
        markdown.append("- similarity_threshold: ").append(SIMILARITY_THRESHOLD).append(System.lineSeparator());
        markdown.append(System.lineSeparator());

        markdown.append("| mode | user memory embeddings | rows | min(ms) | p50(ms) | avg(ms) | p95(ms) | max(ms) | HNSW used |")
                .append(System.lineSeparator());
        markdown.append("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |")
                .append(System.lineSeparator());
        for (BenchmarkResult result : results) {
            markdown.append("| ")
                    .append(result.mode()).append(" | ")
                    .append(result.memoryCount()).append(" | ")
                    .append(result.rowCount()).append(" | ")
                    .append(format(result.minMs())).append(" | ")
                    .append(format(result.p50Ms())).append(" | ")
                    .append(format(result.avgMs())).append(" | ")
                    .append(format(result.p95Ms())).append(" | ")
                    .append(format(result.maxMs())).append(" | ")
                    .append(result.hnswIndexUsed() ? "yes" : "no").append(" |")
                    .append(System.lineSeparator());
        }
        Files.writeString(path, markdown.toString());
    }

    private static Path reportDirectory() {
        Path root = Path.of(System.getProperty("user.dir"));
        Path mission = root.resolve("mission");
        if (Files.isDirectory(mission)) {
            return mission.resolve("build/reports/rag-ann-benchmark");
        }
        return root.resolve("build/reports/rag-ann-benchmark");
    }

    private static String quoteIdentifier(String value) {
        if (!value.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid database name: " + value);
        }
        return '"' + value + '"';
    }

    private static long userId(int scale) {
        return BASE_USER_ID + scale;
    }

    private static long sourceId(long userId, int index) {
        return userId * 100_000L + index;
    }

    private static String memoryType(int index) {
        return switch (index % 3) {
            case 0 -> "MISSION_COMPLETION";
            case 1 -> "MISSION_REJECTION";
            default -> "MISSION_SATISFACTION";
        };
    }

    private static float[] unitVector(Random random) {
        float[] values = new float[DIMENSION];
        double normSquared = 0.0d;
        for (int i = 0; i < values.length; i++) {
            float value = (float) random.nextGaussian();
            values[i] = value;
            normSquared += value * value;
        }
        normalize(values, Math.sqrt(normSquared));
        return values;
    }

    private static float[] noisyUnitVector(float[] base, Random random, float noiseSigma) {
        float[] values = new float[base.length];
        double normSquared = 0.0d;
        for (int i = 0; i < values.length; i++) {
            float value = (float) (base[i] + (random.nextGaussian() * noiseSigma));
            values[i] = value;
            normSquared += value * value;
        }
        normalize(values, Math.sqrt(normSquared));
        return values;
    }

    private static void normalize(float[] values, double norm) {
        if (norm == 0.0d) {
            throw new IllegalArgumentException("Vector norm is zero");
        }
        for (int i = 0; i < values.length; i++) {
            values[i] = (float) (values[i] / norm);
        }
    }

    private static String toVectorLiteral(float[] values) {
        StringBuilder builder = new StringBuilder(values.length * 8);
        builder.append('[');
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(Float.toString(values[i]));
        }
        builder.append(']');
        return builder.toString();
    }

    private static double percentile(List<Double> sortedValues, double percentile) {
        if (sortedValues.isEmpty()) {
            return 0.0d;
        }
        int index = (int) Math.ceil(percentile * sortedValues.size()) - 1;
        return sortedValues.get(Math.max(0, Math.min(sortedValues.size() - 1, index)));
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private record BenchmarkConfig(
            String adminUrl,
            String benchmarkUrl,
            String database,
            String username,
            String password,
            int warmupRuns,
            int measuredRuns
    ) {
        private static BenchmarkConfig fromSystemProperties() {
            String database = property("rag.benchmark.database", "RAG_BENCHMARK_DATABASE", "po_rag_benchmark");
            String adminUrl = property("rag.benchmark.adminUrl", "RAG_BENCHMARK_ADMIN_URL", "jdbc:postgresql://localhost:5432/postgres");
            String benchmarkUrl = property(
                    "rag.benchmark.url",
                    "RAG_BENCHMARK_URL",
                    "jdbc:postgresql://localhost:5432/" + database
            );
            String username = property("rag.benchmark.username", "RAG_BENCHMARK_USERNAME", "root");
            String password = property("rag.benchmark.password", "RAG_BENCHMARK_PASSWORD", "12345678");
            int warmupRuns = Integer.parseInt(property("rag.benchmark.warmupRuns", "RAG_BENCHMARK_WARMUP_RUNS", "10"));
            int measuredRuns = Integer.parseInt(property("rag.benchmark.measuredRuns", "RAG_BENCHMARK_MEASURED_RUNS", "40"));
            return new BenchmarkConfig(adminUrl, benchmarkUrl, database, username, password, warmupRuns, measuredRuns);
        }

        private static String property(String systemProperty, String environmentVariable, String defaultValue) {
            String value = System.getProperty(systemProperty);
            if (value != null && !value.isBlank()) {
                return value;
            }
            return System.getenv().getOrDefault(environmentVariable, defaultValue);
        }
    }

    private record SyntheticDataset(Map<Integer, String> queryVectorByScale) {
        private String queryVector(int scale) {
            return Objects.requireNonNull(queryVectorByScale.get(scale), "Missing query vector for scale " + scale);
        }
    }

    private record VectorPayload(long userId, String vectorLiteral) {
    }

    private record BenchmarkResult(
            String mode,
            int memoryCount,
            int rowCount,
            double minMs,
            double p50Ms,
            double avgMs,
            double p95Ms,
            double maxMs,
            boolean hnswIndexUsed,
            String explainPlan
    ) {
        private static BenchmarkResult from(String mode, int memoryCount, int rowCount, List<Double> durations, String explainPlan) {
            List<Double> sorted = durations.stream().sorted().toList();
            double sum = durations.stream().mapToDouble(Double::doubleValue).sum();
            return new BenchmarkResult(
                    mode,
                    memoryCount,
                    rowCount,
                    sorted.getFirst(),
                    percentile(sorted, 0.50d),
                    sum / durations.size(),
                    percentile(sorted, 0.95d),
                    sorted.getLast(),
                    explainPlan.contains(HNSW_INDEX),
                    explainPlan
            );
        }
    }
}
