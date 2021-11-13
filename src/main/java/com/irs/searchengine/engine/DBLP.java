package com.irs.searchengine.engine;

import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import javax.xml.parsers.SAXParserFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

public class DBLP extends DefaultHandler {
	public DBLP() throws Exception {
	}

	boolean isTitle = false;
	static Integer titles = 0;
	static Integer largeTitles = 0;
	public static Integer suspicious = 0;
	static String DBLP_FILE = "D:\\MSc\\IRS\\citeseer2.tar\\dblp-xml\\dblp.xml";
	static Random random = new Random();
//	static File  INDEX_DIR = new File("D:\\MSc\\IRS\\citeseer2.tar\\Indexed");
	static IndexWriter writer; // new IndexWriter(INDEX_DIR,new StandardAnalyzer(), true);
	static int MIN_Length = 20;

	public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
		if (qName.equals("title")) {
			isTitle = true;
		}
	}

	public void endElement(String uri, String localName, String qName) throws SAXException {

	}

	public void characters(char[] ch, int start, int length) throws SAXException {
		if (isTitle) {
			titles++;
			String title = new String(ch, start, length);
			// System.out.println(titles+"\t"+title);
			if (title.length() > MIN_Length) {
				largeTitles++;
				Document doc = new Document();
				doc.add(new StringField("path", title.hashCode() + "_" + random.nextInt(100), Field.Store.YES));
				doc.add(new TextField("contents", title, Store.YES));
				doc.add(new StringField("title", title, Field.Store.YES));
				try {
					writer.addDocument(doc);
				} catch (Exception e) {
				}
			}
			isTitle = false;
		}
	}

	public static void main(String[] args) throws Exception {

		String INDEX_DIR = "D:\\MSc\\IRS\\citeseer2.tar\\Indexed\\dblp" + MIN_Length;
		Directory dir = FSDirectory.open(Paths.get(INDEX_DIR));
		Analyzer analyzer = new StandardAnalyzer();
		IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
		writer = new IndexWriter(dir, iwc);

		SAXParserFactory factory = SAXParserFactory.newInstance();
		// out = new OutputStreamWriter (System.out, "UTF8");
		SAXParser saxParser = factory.newSAXParser();
		XMLReader reader = saxParser.getXMLReader();
		reader.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
		reader.setFeature("http://xml.org/sax/features/validation", false);
		saxParser.parse(new File(DBLP_FILE), new DBLP());
		System.out.println("titles\t" + titles + "\tlargeTitles\t" + largeTitles);

//		writer.optimize();
		writer.close();

	}
}
