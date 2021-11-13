package com.irs.searchengine.controllers;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.highlight.Formatter;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.InvalidTokenOffsetsException;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.SimpleHTMLFormatter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.search.spell.LuceneLevenshteinDistance;
import org.apache.lucene.search.spell.PlainTextDictionary;
import org.apache.lucene.search.spell.SpellChecker;


import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin
@RestController
@RequestMapping("/api/")
public class IndexController {

	static int counter = 0;
	static String indexPath = "D:\\MSc\\IRS\\citeseer2.tar\\Indexed";
	static String docsPath = "D:\\MSc\\IRS\\citeseer2.tar\\Data";
	static String dictionaryPath = "D:\\MSc\\IRS\\citeseer2.tar\\dictionary.txt";

	static void indexDocs(final IndexWriter writer, Path path) throws IOException {
		Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				indexDoc(writer, file);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	/** Indexes a single document */
	static void indexDoc(IndexWriter writer, Path file) throws IOException {
		InputStream stream = Files.newInputStream(file);
		BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
		String title = br.readLine();
		Document doc = new Document();
		doc.add(new StringField("path", file.toString(), Field.Store.YES));
		doc.add(new TextField("contents", new String(Files.readAllBytes(file)), Store.YES));
		doc.add(new StringField("title", title, Field.Store.YES));
		writer.addDocument(doc);
		counter++;
		if (counter % 1000 == 0)
			System.out.println("indexing " + counter + "-th file " + file.getFileName());
		;
	}

	static String spellCheck(String query) throws IOException {
		Directory directory = FSDirectory.open(Paths.get(indexPath));
		PlainTextDictionary txt_dict = new PlainTextDictionary(Paths.get(dictionaryPath));
		SpellChecker checker = new SpellChecker(directory);
		checker.indexDictionary(txt_dict, new IndexWriterConfig(new KeywordAnalyzer()), false);
		checker.setStringDistance(new LuceneLevenshteinDistance());
		String[] suggestions = checker.suggestSimilar(query, 1);
		checker.close();
		directory.close();
		if(suggestions.length!=0) {
			return suggestions[0];
		}
		return null;
	}

	@GetMapping("/indexDocuments")
	public String IndexDocuments() throws Exception {
		System.out.println("Indexing to directory '" + indexPath + "'...");
		Directory dir = FSDirectory.open(Paths.get(indexPath));
		Analyzer analyzer = new StandardAnalyzer();
		IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
		IndexWriter writer = new IndexWriter(dir, iwc);
		indexDocs(writer, Paths.get(docsPath));
		writer.close();
		return "Hello";
	}

	@GetMapping("/search")
	public Map<String, Object> search(@RequestParam String keyword)
			throws IOException, ParseException, InvalidTokenOffsetsException {
		IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexPath)));
		IndexSearcher searcher = new IndexSearcher(reader);
		Analyzer analyzer = new StandardAnalyzer();
		QueryParser parser = new QueryParser("contents", analyzer);
		Query query = parser.parse(keyword);
		TopDocs results = searcher.search(query, 30);
		Formatter formatter = new SimpleHTMLFormatter("<span style=\"background:red;\">", "</span>");
		Map<String, Object> response = new HashMap<String, Object>();
		response.put("totalHits", results.totalHits.value);
		ArrayList<Map<String, String>> resultList = new ArrayList<Map<String, String>>();
		if (results.totalHits.value > 0) {
			QueryScorer queryScorer = new QueryScorer(query);
			Highlighter highlighter = new Highlighter(formatter, queryScorer);
			resultList = searchIndex(searcher, analyzer, results, highlighter, resultList);
		} else {
			String suggestion = spellCheck(keyword);
			if (suggestion!=null) {
				query = parser.parse(suggestion);
				results = searcher.search(query, 30);
				QueryScorer queryScorer = new QueryScorer(query);
				Highlighter highlighter = new Highlighter(formatter, queryScorer);
				if (results.totalHits.value > 0) {
					resultList = searchIndex(searcher, analyzer, results, highlighter, resultList);
					response.put("Suggestion", suggestion);
					response.put("totalHits", results.totalHits.value);
				}	
				else
					resultList = null;
			}
			else {
				resultList = null;
			}
		}
		response.put("result", resultList);
		return response;
	}

	public ArrayList<Map<String, String>> searchIndex(IndexSearcher searcher, Analyzer analyzer, TopDocs results, Highlighter highlighter,
			ArrayList<Map<String, String>> resultList) throws IOException, InvalidTokenOffsetsException {
		for (int i = 0; i < results.totalHits.value; i++) {
			if(i>=30)
				break;
			Document doc = searcher.doc(results.scoreDocs[i].doc);
			String path = doc.get("path");
			String title = doc.get("title");
			if (title != null) {
				Map<String, String> item = new HashMap<String, String>();
				item.put("Title", doc.get("title"));
				String content = doc.get("contents");
				TokenStream tokenStream = analyzer.tokenStream("", new StringReader(content));
				String highlightedText = "";
				highlightedText = highlighter.getBestFragments(tokenStream, content, 2, "...</br>");
				item.put("Content", highlightedText);
				item.put("path", doc.get("path"));
				resultList.add(item);
			}
		}
		return resultList;
	}
}
