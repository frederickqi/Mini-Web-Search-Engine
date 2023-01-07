package cis5550.jobs;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import static cis5550.webserver.Server.port;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import cis5550.flame.FlameContext;
import cis5550.flame.FlamePair;
import cis5550.flame.FlamePairRDD;
import cis5550.flame.FlameRDD;
import cis5550.kvs.KVSClient;
import cis5550.kvs.Row;
import cis5550.tools.Hasher;

//import org.jsoup.Jsoup;
//import org.jsoup.nodes.Document;


public class Indexer {
	public static KVSClient kvs;
	
//	@SuppressWarnings("unchecked")
	public static void main(String[] args) throws FileNotFoundException, IOException {
		// TODO Auto-generated method stub
	    kvs = new KVSClient(args[1]);
	    String tableName = args[0];
    	Iterator<Row> crawlerRow = kvs.scan(tableName, null, null);
//    	System.out.println(crawlerRow.next().key());
		Row row = null;
		String url = "";
		String page = "";
		String hashURL = "";
		int url_cnt = 0;
		for (Iterator<Row> iter=crawlerRow; iter.hasNext();) {
			row = iter.next();
			url = row.get("url");
			page = row.get("page");
			hashURL= Hasher.hash(url).substring(0, 10);
			
			if (url.length() > 1000) {
				continue;
			}else {
				
			}
		
			
			if (url.contains(",")) {
				continue;
			}
			
			if (page == null) {
				continue;
			}
			
			if (page.length() < 100) {
				continue;
			}
			
			if (kvs.existsRow("urls", hashURL)) {
				url_cnt++;
				System.out.println("Row Exists: " + url_cnt);
				continue;
			}
			
			kvs.put("urls", hashURL, "acc", url);
			url = hashURL;
			
//			page = page.replaceAll("\\<[^>]*>"," ").replaceAll("\\p{Punct}", " ").toLowerCase();
			Document doc = Jsoup.parse(page);
			String body = doc.body().text();
			String title = doc.title();
//			System.out.println("title: " + doc.title());
			int body_len = body.length();
			
			if (body_len > 0) {
				if (title.length() < 2) {
					kvs.put("urls", hashURL, "title", body.substring(0, Math.min(body_len, 50)));
				}else {
					kvs.put("urls", hashURL, "title", title);
				}
				kvs.put("urls", hashURL, "body", body.substring(0, Math.min(body_len, 200)));
			}else {
				continue;
			}
			
			
			page = body.replaceAll("\\<[^>]*>"," ").replaceAll("\\p{Punct}", " ").toLowerCase();

			List<String> listWord = Arrays.asList(page.split("\\s+"));
			
		    Set<String> uniqueWord = new HashSet<String>(listWord);
		    
		    
		    if (uniqueWord.size() < 2) {
		    	continue;
		    }

		    int cnt = 0;
		    String url_TF;
		    String cur_url_TF;
		    int max_freq = 0;
		    
		    for (String word: uniqueWord) {
		    	if (!word.matches("[a-zA-Z0-9]*")) {
		    		continue;
		    	}
		    	
		    	if (word.isEmpty()) {
		    		continue;
		    	}
		    	
		    	if (word.length() > 15) {
		    		continue;
		    	}
		    	int index = page.indexOf(word);
		    	url_TF = url + ":" + String.valueOf(index);
		    	cnt = 1;
		    	while (index >= 0) {
		    	    index = page.indexOf(word, index + word.length());
		    	    if (index >= 0) {
		    	    	cnt++;
		    	    }
		    	}
		    	
		    	if (cnt > max_freq) {
		    		max_freq = cnt;
		    	}
		    	url_TF = url + ":" + String.valueOf(cnt);
		    	
		    	try {
					if (kvs.existsRow("index", word)){
//						System.out.println("getting wrong 1");
						row = kvs.getRow("index", word);
						cur_url_TF = row.get("acc");
						kvs.put("index", word, "acc", cur_url_TF + "," + url_TF);
					}else {
//						System.out.println("getting wrong 2 " + word + " " + url_TF );
						kvs.put("index", word, "acc", url_TF);
					}
					
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					continue;
				}
		    }
		    
		    kvs.put("urls", hashURL, "max_freq", String.valueOf(max_freq));
		}
    	
	}
	    
}
