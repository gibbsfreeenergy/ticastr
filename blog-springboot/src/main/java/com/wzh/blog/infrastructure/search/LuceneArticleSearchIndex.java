package com.wzh.blog.infrastructure.search;

import com.wzh.blog.config.SearchProperties;
import com.wzh.blog.search.ArticleSearchDocument;
import com.wzh.blog.search.ArticleSearchIndex;
import com.wzh.blog.search.ArticleSearchResult;
import jakarta.annotation.PostConstruct;
import lombok.extern.log4j.Log4j2;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Pattern;


/**
 * Embedded Lucene index. Each operation owns its reader/writer so a broken or
 * replaced directory cannot leak resources into the web request lifecycle.
 */
@Component
@Log4j2
public class LuceneArticleSearchIndex implements ArticleSearchIndex {

    private static final String ARTICLE_ID = "articleId";
    private static final String TITLE = "title";
    private static final String TAGS = "tags";
    private static final String BODY = "body";
    private static final String CATEGORY_ID = "categoryId";
    private static final String SNIPPET_SOURCE = "snippetSource";
    private static final int MAX_SNIPPET_CODE_UNITS = 2_000;
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final Path indexPath;
    private final int maxQueryBytes;
    private final int maxResults;
    private final Analyzer analyzer = new StandardAnalyzer();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    @Autowired
    public LuceneArticleSearchIndex(SearchProperties properties) {
        this(resolvePath(properties.getDataRoot(), properties.getIndexPath()),
                properties.getMaxQueryBytes(), properties.getMaxResults());
    }

    public LuceneArticleSearchIndex(Path indexPath) {
        this(indexPath, 100, 20);
    }

    public LuceneArticleSearchIndex(Path indexPath, int maxQueryBytes, int maxResults) {
        this.indexPath = indexPath.toAbsolutePath().normalize();
        this.maxQueryBytes = Math.max(1, maxQueryBytes);
        this.maxResults = Math.max(1, maxResults);
    }

    @PostConstruct
    void initialize() {
        try {
            Files.createDirectories(indexPath);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create search index directory", exception);
        }
    }

    @Override
    public void upsert(ArticleSearchDocument document) {
        lock.writeLock().lock();
        try {
            ensureDirectory();
            try (Directory directory = FSDirectory.open(indexPath);
                 IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(analyzer))) {
                writer.deleteDocuments(new Term(ARTICLE_ID, String.valueOf(document.articleId())));
                writer.addDocument(toLuceneDocument(document));
                writer.commit();
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to update article search index", exception);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void delete(Integer articleId) {
        if (articleId == null) {
            return;
        }
        lock.writeLock().lock();
        try {
            ensureDirectory();
            try (Directory directory = FSDirectory.open(indexPath);
                 IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(analyzer))) {
                writer.deleteDocuments(new Term(ARTICLE_ID, String.valueOf(articleId)));
                writer.commit();
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to delete article from search index", exception);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public List<ArticleSearchResult> search(String queryText, int offset, int limit) {
        String query = normalizeQuery(queryText);
        if (query.isBlank()) {
            return List.of();
        }
        int safeOffset = Math.max(0, offset);
        int safeLimit = Math.min(Math.max(1, limit), maxResults);
        int topN = Math.min(Integer.MAX_VALUE - safeOffset, safeOffset + safeLimit);
        lock.readLock().lock();
        try {
            try (Directory directory = FSDirectory.open(indexPath);
                 DirectoryReader reader = openReader(directory)) {
                if (reader == null) {
                    return List.of();
                }
                IndexSearcher searcher = new IndexSearcher(reader);
                Query parsed = buildQuery(query);
                TopDocs topDocs = searcher.search(parsed, Math.max(1, topN));
                List<ArticleSearchResult> results = new ArrayList<>();
                for (int index = safeOffset; index < topDocs.scoreDocs.length && results.size() < safeLimit; index++) {
                    ScoreDoc scoreDoc = topDocs.scoreDocs[index];
                    Document document = searcher.storedFields().document(scoreDoc.doc);
                    results.add(new ArticleSearchResult(
                            Integer.valueOf(document.get(ARTICLE_ID)),
                            document.get(TITLE),
                            snippet(document.get(SNIPPET_SOURCE), query),
                            scoreDoc.score));
                }
                return results;
            }
        } catch (Exception exception) {
            log.warn("Article search index unavailable", exception);
            return List.of();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void rebuild(List<ArticleSearchDocument> documents) {
        List<ArticleSearchDocument> safeDocuments = documents == null ? List.of() : List.copyOf(documents);
        lock.writeLock().lock();
        Path temporary = indexPath.resolveSibling(indexPath.getFileName() + ".rebuild-" + UUID.randomUUID());
        Path backup = indexPath.resolveSibling(indexPath.getFileName() + ".backup-" + UUID.randomUUID());
        try {
            Files.createDirectories(temporary);
            try (Directory directory = FSDirectory.open(temporary);
                 IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(analyzer))) {
                for (ArticleSearchDocument document : safeDocuments) {
                    writer.addDocument(toLuceneDocument(document));
                }
                writer.commit();
            }
            if (Files.exists(indexPath)) {
                move(indexPath, backup);
            }
            move(temporary, indexPath);
            deleteTree(backup);
        } catch (Exception exception) {
            deleteTree(temporary);
            if (!Files.exists(indexPath) && Files.exists(backup)) {
                try {
                    move(backup, indexPath);
                } catch (IOException restoreFailure) {
                    exception.addSuppressed(restoreFailure);
                }
            }
            throw new IllegalStateException("Unable to rebuild article search index", exception);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public long documentCount() {
        lock.readLock().lock();
        try {
            if (!Files.exists(indexPath)) {
                return 0;
            }
            try (Directory directory = FSDirectory.open(indexPath)) {
                if (!DirectoryReader.indexExists(directory)) {
                    return 0;
                }
                try (DirectoryReader reader = DirectoryReader.open(directory)) {
                    return reader.numDocs();
                }
            }
        } catch (IOException exception) {
            return 0;
        } finally {
            lock.readLock().unlock();
        }
    }

    public Path indexPath() {
        return indexPath;
    }

    private Document toLuceneDocument(ArticleSearchDocument source) {
        Document document = new Document();
        document.add(new StringField(ARTICLE_ID, String.valueOf(source.articleId()), Field.Store.YES));
        document.add(new TextField(TITLE, source.title(), Field.Store.YES));
        document.add(new StringField(CATEGORY_ID,
                source.categoryId() == null ? "" : String.valueOf(source.categoryId()), Field.Store.YES));
        document.add(new TextField(TAGS, String.join(" ", source.tagNames()), Field.Store.YES));
        document.add(new TextField(BODY, source.body(), Field.Store.NO));
        document.add(new StoredField(SNIPPET_SOURCE, truncate(source.body(), MAX_SNIPPET_CODE_UNITS)));
        return document;
    }

    private Query buildQuery(String query) throws Exception {
        BooleanQuery.Builder fields = new BooleanQuery.Builder();
        for (String field : new String[]{TITLE, TAGS, BODY}) {
            QueryParser parser = new QueryParser(field, analyzer);
            parser.setDefaultOperator(QueryParser.Operator.AND);
            fields.add(parser.parse(QueryParser.escape(query)), BooleanClause.Occur.SHOULD);
        }
        return fields.build();
    }

    private String normalizeQuery(String value) {
        if (value == null) {
            return "";
        }
        String normalized = WHITESPACE.matcher(value.trim()).replaceAll(" ");
        byte[] bytes = normalized.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maxQueryBytes) {
            normalized = new String(bytes, 0, maxQueryBytes, StandardCharsets.UTF_8);
            while (!normalized.isEmpty() && normalized.getBytes(StandardCharsets.UTF_8).length > maxQueryBytes) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
        }
        return normalized;
    }

    private String snippet(String source, String query) {
        if (source == null || source.isBlank()) {
            return "";
        }
        String snippet = source;
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        int position = source.toLowerCase(Locale.ROOT).indexOf(normalizedQuery);
        if (position > 120) {
            snippet = "…" + source.substring(position - 80);
        }
        snippet = truncate(snippet, 240);
        return snippet;
    }

    private String truncate(String value, int length) {
        if (value == null || value.length() <= length) {
            return value == null ? "" : value;
        }
        return value.substring(0, length) + "…";
    }

    private void ensureDirectory() {
        try {
            Files.createDirectories(indexPath);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create search index directory", exception);
        }
    }

    private static Path resolvePath(String dataRoot, String configuredIndexPath) {
        Path root = Path.of(dataRoot == null || dataRoot.isBlank() ? "./data" : dataRoot)
                .toAbsolutePath().normalize();
        Path configured = Path.of(configuredIndexPath == null || configuredIndexPath.isBlank()
                ? "search-index" : configuredIndexPath);
        Path resolved = configured.isAbsolute()
                ? configured.toAbsolutePath().normalize()
                : root.resolve(configured).normalize();
        if (!resolved.startsWith(root) || resolved.equals(root)) {
            throw new IllegalStateException("app.search.index-path must be inside app.search.data-root");
        }
        return resolved;
    }

    private static DirectoryReader openReader(Directory directory) throws IOException {
        return DirectoryReader.indexExists(directory) ? DirectoryReader.open(directory) : null;
    }

    private static void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteTree(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var paths = Files.walk(path)) {
            paths.sorted(Comparator.reverseOrder()).forEach(item -> {
                try {
                    Files.deleteIfExists(item);
                } catch (IOException ignored) {
                    // Best effort cleanup; the active index is left untouched on failure.
                }
            });
        } catch (IOException ignored) {
            // Best effort cleanup of a known temporary directory.
        }
    }
}
