package cis5550.search;

import static cis5550.webserver.Server.get;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import static cis5550.webserver.Server.port;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import cis5550.kvs.KVSClient;
import cis5550.kvs.Row;

public class Searcher {
	public static KVSClient kvs;
	
	@SuppressWarnings("unchecked")
	public static void main(String[] args) {
		// TODO Auto-generated method stub
	    int myPort = Integer.valueOf(args[0]); 
	    kvs = new KVSClient(args[1]);
	    
	    port(myPort);
	    
	    get("/search", (request,response) -> {
	    	String query_raw = request.queryParams("query");
	    	String query = query_raw.replaceAll("\\<[^>]*>","").replaceAll("\\p{Punct}", " ").toLowerCase();
	    	List<String> listWord = Arrays.asList(query.split("\\s+"));
	    	
	    	Set<String> uniqueWord = new HashSet<String>(listWord);
	    	
	    	Iterator<Row> indexRow = kvs.scan("index", null, null);
	    	Iterator<Row> urlRow = kvs.scan("urls", null, null);
			Row row = null;
			
			///////////////// load PageRank ////////////////////////////////
			HashMap<String, String> urlMap = new HashMap<String, String>();
			HashMap<String, String> titleMap = new HashMap<String, String>();
			HashMap<String, String> bodyMap = new HashMap<String, String>();
			for (Iterator<Row> iter=urlRow; iter.hasNext();) {
				row = iter.next();
				urlMap.put(row.key(), row.get("acc"));
				titleMap.put(row.key(), row.get("title"));
				bodyMap.put(row.key(), row.get("body"));
			}
	    	
	    	
			String wordKey = null;
			String urls = null;
			List<String> urlOutput = new ArrayList<String>();
			Map<String, Integer> idf_ni = new HashMap<String, Integer>();
			
			Map<String, Map<String, Double>> tf_td = new HashMap<String, Map<String, Double>>();
			
			Map<String, Double> idfMap = new HashMap<String, Double>();
			
			Map<String, Double> tfIdfMap = new HashMap<String, Double>();
			
			Map<String, Double> lengthMap = new HashMap<String, Double>();
			
			Map<String, Double> words_td = null;
			int idf_N = kvs.count("urls");
			int freq_td = 0;
			double idf_value = 0.0;
			for (String word: uniqueWord) {
				row = kvs.getRow("index", word);
				if (!(row == null)) {
					urls = row.get("acc");
					String[] urlArray = urls.split(",");
					
					///////////////// TF Calculation ////////////////////////
					String[] indexArray = null;
					String doc_url = null;
					for (int i = 0; i < urlArray.length; i++) {
						String[] urlParts = urlArray[i].split(":");
//						if (urlParts.length > 3) {
//							indexArray = urlArray[i].split(":")[3].split("\\s+");
//							doc_url = urlParts[0] +  ":" + urlParts[1] + ":" + urlParts[2];
//						}else {
//							indexArray = urlArray[i].split(":")[2].split("\\s+");
//							doc_url = urlParts[0] +  ":" + urlParts[1];
//						}
//						freq_td = indexArray.length;
						doc_url = urlParts[0];
						try {
							freq_td = Integer.valueOf(urlParts[urlParts.length - 1]);
						}catch(Exception e){
							freq_td = 1;
						}
//						System.out.println(freq_td);
						
						words_td = tf_td.get(doc_url);
						if (words_td == null) {
							words_td = new HashMap<String, Double>();
						}
						words_td.put(word, 1 + Math.log10(freq_td));
						tf_td.put(doc_url, words_td); 
						
					}
					
					idf_value =  Math.log10(idf_N / Double.valueOf(urlArray.length));
					idfMap.put(word, idf_value); 
					System.out.println(word + " : " + String.valueOf(idf_value) + "," + String.valueOf(freq_td));
				}
			}
			
			///////////////// IDF Calculation ////////////////////////
//			double query_len_value = 0.0;
//			double idf_value = 0.0;
//			for (Map.Entry<String, Integer> entry : idf_ni.entrySet()) {
//				idf_value = Math.log10(idf_N / Double.valueOf(entry.getValue()));
//				idfMap.put(entry.getKey(), idf_value);
//				System.out.println(entry.getKey() + " : " + String.valueOf(idf_value) + "," + String.valueOf(entry.getValue()));
//				
////				query_len_value = query_len_value + idf_value * idf_value;
//			}
			
//			query_len_value = Math.sqrt(query_len_value);
//			System.out.println("query len: " + query_len_value);
			
			
			///////////////// TF-IDF Calculation ////////////////////////
			double tf_idf;
			double tf_value;
			double doc_len_value;
			double final_tf_value;
			for (Map.Entry<String, Map<String, Double>> entry : tf_td.entrySet()) {
				tf_idf = 0.0;
				doc_len_value = 0.0;
				for (Map.Entry<String, Double> wordEntry : entry.getValue().entrySet()) {
					wordKey = wordEntry.getKey();
					tf_value = wordEntry.getValue();
					idf_value = idfMap.get(wordKey);
					tf_idf = tf_idf +  idf_value * tf_value;
//					tf_idf = tf_idf +  idf_value * tf_value * idf_value;
					
//					doc_len_value = doc_len_value + (idf_value * tf_value) * (idf_value * tf_value);
				}
				
//				final_tf_value = tf_idf / (doc_len_value * query_len_value);
				
//				lengthMap.put(entry.getKey(), Math.sqrt(doc_len_value));
//				tf_idf = pageRankMap.get(entry.getKey()) * tf_idf;    // multiplier by pageRank
				
				if (tf_idf > 0.3) {
					tfIdfMap.put(entry.getKey(), tf_idf);
				}
//				tfIdfMap.put(entry.getKey(), final_tf_value);
//				System.out.println(entry.getKey() + " : " + String.valueOf(tf_idf));
			}
			
			Map<String, Double> sortedMap = tfIdfMap.entrySet().stream()
                    .sorted(Entry.comparingByValue(Comparator.reverseOrder()))
                    .collect(Collectors.toMap(Entry::getKey, Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
			
	    
			///////////////// Generate JSON format Output///////////////////
			JSONObject resultObject = new JSONObject();
			JSONArray resultList = new JSONArray();
			String url;
			double url_length;
			int out_cnt = 0;
	    	for (Map.Entry<String, Double> entry : sortedMap.entrySet()) {
	    		
//	    		url = entry.getKey();
//	    		url_length = lengthMap.get(url);
//				if (words_td == null) {
//					words_td = new HashMap<String, Double>();
//				} 
//				words_td.put(wordKey, 1 + Math.log10(freq_td));
//				tf_td.put(doc_url, words_td); 
	    		url = entry.getKey();
//	    		row = kvs.getRow("urls", entry.getKey()); 
	    		JSONObject urlObject = new JSONObject();
//	    		urlObject.put("url", entry.getKey());
	    		urlObject.put("url", urlMap.get(url));
	    		urlObject.put("rank_score", entry.getValue());
	    		urlObject.put("title", titleMap.get(url));
	    		urlObject.put("body", bodyMap.get(url));
	    		resultList.add(urlObject);
//	    		System.out.println(urlMap.get(url));
	    		out_cnt++;
	    		
	    		if (out_cnt > 300) {
	    			break;
	    		}
	    	}
//	    	resultObject.put("search_result", resultList);
	    	
	    	
//	    	response.header("Access-Control-Allow-Origin", "http://localhost:3000");
	    	response.header("Access-Control-Allow-Origin", "http://searchteam.cis5550.net:3000");
	        response.type("application/json");
//	        return null;
	        return resultList.toString();
//	        return "<html><head><title>Search Result</title></head><body><h3>Search Result</h3>\n" + urlOutput.toString() + "</body></html>";
	      });
	}
}
