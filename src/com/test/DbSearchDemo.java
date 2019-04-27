package com.test;

import java.nio.file.FileSystems;
import java.sql.ResultSet;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.SimpleHTMLFormatter;
import org.apache.lucene.search.highlight.SimpleSpanFragmenter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.wltea.analyzer.lucene.IKAnalyzer;

import com.test.util.JdbcUtil;

/**
 * 基于Lucene5.5.4的数据库搜索demo
 *
 * @Author 章先森
 * @CreateDate 2018年4月9日
 * @ www.zhngguimin.cn
 */
public class DbSearchDemo {
    public static final String INDEX_PATH = "F:\\luncene\\lucene-db";
    public static final String JDBC_URL = "jdbc:mysql://192.168.1.33:3306/lucene-demo?useUnicode=true&characterEncoding=utf-8";
    public static final String USER = "root";
    public static final String PWD = "123456";

    /**
     * 创建索引
     */
    public void creatIndex() {
        IndexWriter indexWriter = null;
        try {
            Directory directory = FSDirectory.open(FileSystems.getDefault().getPath(INDEX_PATH));
            // 使用IK分词
            Analyzer analyzer = new IKAnalyzer();
            // 创建索引
            IndexWriterConfig indexWriterConfig = new IndexWriterConfig(analyzer);
            indexWriter = new IndexWriter(directory, indexWriterConfig);
            indexWriter.deleteAll();// 清除以前的index

            JdbcUtil jdbc = new JdbcUtil(JDBC_URL, USER, PWD);
            ResultSet rs = jdbc.query("select * from news");
            while (rs.next()) {
                Document document = new Document();
                document.add(new Field("id", rs.getString("id"), TextField.TYPE_STORED));
                document.add(new Field("title", rs.getString("title"), TextField.TYPE_STORED));
                document.add(new Field("content", rs.getString("content"), TextField.TYPE_STORED));
                document.add(new Field("tags", rs.getString("tags"), TextField.TYPE_STORED));
                document.add(new Field("url", rs.getString("url"), TextField.TYPE_STORED));
                indexWriter.addDocument(document);
            }
            jdbc.closeAll();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (indexWriter != null) {
                    indexWriter.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    public static void main(String args[]) {
        DbSearchDemo demo = new DbSearchDemo();
        demo.creatIndex();
        demo.search("清华北大");
    }

    /**
     * 搜索
     */
    public void search(String keyWord) {
        DirectoryReader directoryReader = null;
        try {
            // 1、创建Directory
            Directory directory = FSDirectory.open(FileSystems.getDefault().getPath(INDEX_PATH));
            // 2、创建IndexReader
            directoryReader = DirectoryReader.open(directory);
            // 3、根据IndexReader创建IndexSearch
            IndexSearcher indexSearcher = new IndexSearcher(directoryReader);
            // 4、创建搜索的Query
            // Analyzer analyzer = new StandardAnalyzer();
            // 使用IK分词
            Analyzer analyzer = new IKAnalyzer();

            // 简单的查询，创建Query表示搜索域为content包含keyWord的文档
            // Query query = new QueryParser("content", analyzer).parse(keyWord);

            String[] fields = {"title", "content", "url", "tags"};
            // MUST 表示and，MUST_NOT 表示not ，SHOULD表示or，有几个field就需要几个clauses
            BooleanClause.Occur[] clauses = {BooleanClause.Occur.SHOULD, BooleanClause.Occur.SHOULD, BooleanClause.Occur.SHOULD, BooleanClause.Occur.SHOULD};
            // MultiFieldQueryParser表示多个域解析， 同时可以解析含空格的字符串，如果我们搜索"上海 中国"
            Query multiFieldQuery = MultiFieldQueryParser.parse(keyWord, fields, clauses, analyzer);

            // 5、根据searcher搜索并且返回TopDocs
            // 搜索前100条结果
            TopDocs topDocs = indexSearcher.search(multiFieldQuery, 100);
            System.out.println("共找到匹配处：" + topDocs.totalHits);
            // 6、根据TopDocs获取ScoreDoc对象ses
            ScoreDoc[] scoreDocs = topDocs.scoreDocs;
            System.out.println("共找到匹配文档数：" + scoreDocs.length);

            QueryScorer scorer = new QueryScorer(multiFieldQuery);
            SimpleHTMLFormatter htmlFormatter = new SimpleHTMLFormatter("<span style=\"backgroud:red\">", "</span>");
            Highlighter highlighter = new Highlighter(htmlFormatter, scorer);
            highlighter.setTextFragmenter(new SimpleSpanFragmenter(scorer));
            for (ScoreDoc scoreDoc : scoreDocs) {
                Document document = indexSearcher.doc(scoreDoc.doc);
                String content = document.get("content");
                System.out.println("-----------------------------------------");
                System.out.println("文章标题：" + document.get("title"));
                System.out.println("文章地址：" + document.get("url"));
                System.out.println("文章地址：" + document.get("tags"));
                System.out.println("文章内容：");
                System.out.println(highlighter.getBestFragment(analyzer, "content", content));
                // 8、根据Document对象获取需要的值
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (directoryReader != null) {
                    directoryReader.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}