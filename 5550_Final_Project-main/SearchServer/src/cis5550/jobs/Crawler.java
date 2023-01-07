package cis5550.jobs;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cis5550.flame.FlameContext;
import cis5550.flame.FlamePair;
import cis5550.flame.FlameRDD;
import cis5550.kvs.KVSClient;
import cis5550.kvs.Row;
import cis5550.tools.Hasher;
import cis5550.webserver.Route;

public class Crawler {

	public static List<String> blackList = null;
	
	
	public static void run(FlameContext flameContext, String[] args) throws Exception {
		if (args.length >= 1) {
			///////  blacklist with wildcards //////
			
//			List<String> blackList = null;
//			if (args.length == 2) {
//				Iterator<Row> listRow = FlameContext.getKVS().scan(args[1], null, null);
//				
//				Row row = null;
//				blackList = new ArrayList<String>();
//				for (Iterator<Row> iter=listRow; iter.hasNext();) {
//					row = iter.next();
//					blackList.add(row.get("pattern"));
//				}
//			}else {
//				blackList = null;
//			}
			
			flameContext.output("OK");
			FlameRDD urlQueue = flameContext.parallelize(Arrays.asList(args[0]));
			
			
			while (urlQueue.count() > 0) {
				urlQueue = urlQueue.flatMap(s -> {
					
					s = normalizeURL(s, s);
					
//					if (args.length == 2) {
//						Iterator<Row> listRow = FlameContext.getKVS().scan(args[1], null, null);
//						
//						Row row = null;
//						blackList = new ArrayList<String>();
//						for (Iterator<Row> iter=listRow; iter.hasNext();) {
//							row = iter.next();
//							blackList.add(row.get("pattern"));
//						}
//					}else {
//						blackList = null;
//					}
					
					if (!(s.startsWith("http") || s.startsWith("https"))) {
						return new ArrayList<>();
					}
					
					if (s.endsWith(".jpg") || s.endsWith(".jpeg") || s.endsWith(".gif") || s.endsWith(".png") || s.endsWith(".txt")) {
						return new ArrayList<>();
					}
					
//					if (!(blackList == null)) {
//						for (String pattern: blackList) {
//							String replacePattern = pattern.replaceAll("\\*",".*");
//							Pattern blackPattern = Pattern.compile(replacePattern, Pattern.CASE_INSENSITIVE|Pattern.DOTALL);
//							Matcher blackMatcher = blackPattern.matcher(s);
//							if (blackMatcher.find()) {
//								return new ArrayList<>();
//							}
//							
//						}
//					}
					
					
					String hashURL = Hasher.hash(s);
					String host = s.substring(0, s.indexOf(":", s.indexOf(":") + 2));
					Row row = null;
					
					///////// robot exclusion protocol ///////
					URL obj = null;
					HttpURLConnection con = null;
					int responseCode;
					BufferedReader in;
					String inputLine;
					StringBuffer response;
					boolean has_robots = true;
					String robots = null;
					if (FlameContext.getKVS().existsRow("hosts", host)) {
						row = FlameContext.getKVS().getRow("hosts", host);
						robots = row.get("robots");
						if (robots == null) {
							has_robots = false;
						}
					}else {
						has_robots = false;
					}
					
					if (!has_robots) {
						obj = new URL(host + "/robots.txt");
						con = (HttpURLConnection) obj.openConnection();
						con.setRequestMethod("GET");
						con.setRequestProperty("User-Agent", "cis5550-crawler");
						responseCode = con.getResponseCode();
						if (responseCode == 200) {
							in = new BufferedReader(new InputStreamReader(
									con.getInputStream()));
							response = new StringBuffer();
			
							while ((inputLine = in.readLine()) != null) {
								response.append(inputLine);
							}
							robots = response.toString();
							FlameContext.getKVS().put("hosts", host, "robots", robots);
						}else {
							FlameContext.getKVS().put("hosts", host, "robots", "N/A");
						}
					}else {
						row = FlameContext.getKVS().getRow("hosts", host);
						robots = row.get("robots");
						if (!(robots == null)){
							if (robots.equals("N/A")) {
								robots = null;
							}
						}
					}
					
					if (!ruleCheck(s, robots)) {
						return new ArrayList<>();
					}
					
					long accessedTime = System.currentTimeMillis();
					if (FlameContext.getKVS().existsRow("hosts", host)) {
						row = FlameContext.getKVS().getRow("hosts", host);
						String last_accessedTime_string = row.get("accessedTime");
						if (!(last_accessedTime_string == null)) {
							long last_accessedTime = Long.parseLong(last_accessedTime_string);
							if ((last_accessedTime + 1100) > accessedTime) {
								return new ArrayList<String>(Arrays.asList(normalizeURL(s, s)));
							}
						}
					}
					FlameContext.getKVS().put("hosts", host, "accessedTime", Long.toString(accessedTime));
					
					String contentType = null;
					String contentLength = null;
					String redirectURL = null;
					
					if (FlameContext.getKVS().existsRow("crawl", hashURL)) {
						return new ArrayList<>();
					}
					
					///// HEAD request ///////
					obj = new URL(s);
					con = (HttpURLConnection) obj.openConnection();
					con.setRequestMethod("HEAD");
					con.setRequestProperty("User-Agent", "cis5550-crawler");
					responseCode = con.getResponseCode();
					if (responseCode == 200) {
						contentType = con.getHeaderField("Content-Type");
						contentLength = con.getHeaderField("Content-Length");
					} else if (responseCode == 301 || responseCode == 307 || responseCode == 308 || responseCode == 302 || responseCode == 303) {
						System.out.println(responseCode);
					    if (FlameContext.getKVS().existsRow("crawl", hashURL)) {
					    	row = FlameContext.getKVS().getRow("crawl", hashURL);
					    }else {
					    	row = new Row(hashURL);
					    }
						row.put("url", s);
						row.put("responseCode", String.valueOf(responseCode));
						FlameContext.getKVS().putRow("crawl", row);
						redirectURL = con.getHeaderField("Location");
						if (redirectURL == null) {
							return new ArrayList<>();
						}else {
							return new ArrayList<String>(Arrays.asList(normalizeURL(redirectURL, s)));
						}
					} else {
						System.out.println(responseCode);
					    if (FlameContext.getKVS().existsRow("crawl", hashURL)) {
					    	row = FlameContext.getKVS().getRow("crawl", hashURL);
					    }else {
					    	row = new Row(hashURL);
					    }
						row.put("url", s);
						row.put("responseCode", String.valueOf(responseCode));
						FlameContext.getKVS().putRow("crawl", row);
						return new ArrayList<>();
					}
					
					
					///// GET request ///////
					String page = "";
					con = (HttpURLConnection) obj.openConnection();
					con.setRequestMethod("GET");
					con.setRequestProperty("User-Agent", "cis5550-crawler");
					
					responseCode = con.getResponseCode();
					if (responseCode == 200) {
						
						in = new BufferedReader(new InputStreamReader(
								con.getInputStream()));
						response = new StringBuffer();
		
						while ((inputLine = in.readLine()) != null) {
							response.append(inputLine);
						}
						
						page = response.toString();
//					    if (FlameContext.getKVS().existsRow("crawl", hashURL)) {
//					    	row = FlameContext.getKVS().getRow("crawl", hashURL);
//					    }else {
//					    	row = new Row(hashURL);
//					    }
						row = new Row(hashURL);
						row.put("url", s);
						row.put("responseCode", String.valueOf(responseCode)); 
						row.put("contentType", contentType);
						row.put("length", contentLength);
						
//						////////////////////// EC3 Anchor text extraction ////////////////////
//						
//						Row row_ec3;
//				    	if (FlameContext.getKVS().existsRow("ec3", hashURL)) {
//				    		row_ec3 = FlameContext.getKVS().getRow("ec3", hashURL);
//				    		for (String col: row_ec3.columns()) {
//				    			row.put(col, row_ec3.get(col));
//				    		}
//				    	}
						
						Row rowPage;
						if (!(contentType==null) && contentType.equals("text/html")){
							////////////////// Content-seen Test ///////////////////
							String hashPage = Hasher.hash(page);
							String canonicalURL;
							if (FlameContext.getKVS().existsRow("pages", hashPage)) {
								rowPage = FlameContext.getKVS().getRow("pages", hashPage);
								canonicalURL = rowPage.get("value");
								
								if (!(canonicalURL == null)) {
									row.put("canonicalURL", canonicalURL);
									System.out.println("found duplicate page" + canonicalURL + ", itself: " + s);
								}
							}else {
								FlameContext.getKVS().put("pages", hashPage, "value", s);
								
//								rowPage = new Row(hashPage);
//								rowPage.put("value", s);
//								FlameContext.getKVS().putRow("pages", rowPage);
								
								row.put("page", page);
							}
						}
						FlameContext.getKVS().putRow("crawl", row);
						
						
					}
					List<String> output = extractURL(page, s, blackList);
//					System.out.println("output size: " + output.size());
					return output;
					}
				);
//				System.out.println(urlQueue.count());
				Thread.sleep(500);
			}
			
		}else {
			flameContext.output("Empty Array of String: need seed URL");
		}
	}
	
	public static List<String> extractURL(String page, String baseURL, List<String> blackList) throws Exception{
		List<String> output = new ArrayList<>();
		Map<String, String> textExtractMap = new HashMap<String, String>();
		String foundURL;
		String normalizedURL;
		String textExtract;
		String allTextExtract;
		String regex = "<a.*?href=\"(.*?)\".*?>(.*?)<\\/a>";
		Pattern linkPattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE|Pattern.DOTALL);
		Matcher pageMatcher = linkPattern.matcher(page);
		while(pageMatcher.find()){
			foundURL = pageMatcher.group(1);
			textExtract = pageMatcher.group(2);
			normalizedURL = normalizeURL(foundURL, baseURL);
			
//			try {
//				allTextExtract = textExtractMap.get(normalizedURL);
//				if (allTextExtract == null) {
//					textExtractMap.put(normalizedURL, textExtract);
//				}else {
//					allTextExtract = allTextExtract + " " + textExtract;
//					textExtractMap.put(normalizedURL, allTextExtract);
//				}
//			} catch (Exception e) {
//				System.out.println("concurrent modification");
//			}
				
			
			if (!(normalizedURL == null)) {
				String s = normalizedURL;
				boolean valid_url = true;
				if (!(s.startsWith("http") || s.startsWith("https"))) {
					valid_url = false;
				}
				if (s.endsWith(".jpg") || s.endsWith(".jpeg") || s.endsWith(".gif") || s.endsWith(".png") || s.endsWith(".txt")) {
					valid_url = false;
				}
				
//				////// Blacklist with wildcards ////////
//				if (!(blackList == null)) {
//					for (String pattern: blackList) {
//						String replacePattern = pattern.replaceAll("\\*",".*");
//						Pattern blackPattern = Pattern.compile(replacePattern, Pattern.CASE_INSENSITIVE|Pattern.DOTALL);
//						Matcher blackMatcher = blackPattern.matcher(normalizedURL);
//						if (blackMatcher.find()) {
//							valid_url = false;
//						}
//						
//					}
//				}
				
				if (valid_url) {
					output.add(normalizedURL);
				}
			}
		}
		
//		////////////////////// EC3 Anchor text extraction ////////////////////
//		for (Map.Entry<String, String> entry : textExtractMap.entrySet()) {
//		    String key = Hasher.hash(entry.getKey());
//		    String value = entry.getValue();
////		    System.out.println(entry.getKey() + "/" + entry.getValue());
//		    
//		    Row row = null;
//		    String columnName = "anchor_" + Hasher.hash(baseURL);
//		    if (FlameContext.getKVS().existsRow("crawl", key)) {
//		    	row = FlameContext.getKVS().getRow("crawl", key);
//		    	row.put(columnName, value);
//		    	FlameContext.getKVS().putRow("crawl", row);
//		    }else {
//		    	if (FlameContext.getKVS().existsRow("ec3", key)) {
//		    		row = FlameContext.getKVS().getRow("ec3", key);
//		    		row.put(columnName, value);
//		    		FlameContext.getKVS().putRow("ec3", row);
//		    	}else {
//		    		row = new Row(key);
//		    		row.put(columnName, value);
//		    		FlameContext.getKVS().putRow("ec3", row);
//		    	}
//		    }
//		}
		return output;
		
	}
	
	public static String normalizeURL(String url, String baseURL){
		
		// document fragments (#xyz)
		int idx_frag = url.indexOf("#");
		if (idx_frag != -1) {
			if (idx_frag == 0) {
				return null;
			}else {
				url = url.substring(0, url.indexOf("#"));
			}
		}
		
		if (url.startsWith("/")) {
			baseURL = baseURL.substring(0, baseURL.indexOf("/", baseURL.indexOf("/") + 2));
			url = baseURL + url;
		}else if (url.startsWith("../")) {
			baseURL = baseURL.substring(0, baseURL.indexOf("/", baseURL.indexOf("/") + 2));
			url = baseURL + url.substring(2);
		}else if (url.startsWith("http://")) {
			int idx_slash = url.indexOf("/", url.indexOf("/") + 2);
			if (idx_slash == -1) {
				url = url + "/";
				idx_slash = url.length() - 1;
			}
			int idx_port = url.indexOf(":", url.indexOf(":") + 1);
			if (idx_port == -1) {
				url = url.substring(0, idx_slash) + ":80" + url.substring(idx_slash);
			}
		}else if (url.startsWith("https://")) {
			int idx_slash = url.indexOf("/", url.indexOf("/") + 2);
			int idx_port = url.indexOf(":", url.indexOf(":") + 1);
			if ((idx_port == -1) || (idx_port > idx_slash)) {
				url = url.substring(0, idx_slash) + ":443" + url.substring(idx_slash);
			}
		}else {
			// relative link case
			baseURL = baseURL.substring(0, baseURL.lastIndexOf("/") + 1);
			url = baseURL + url;
		}
		return url;
	}
	
	public static boolean ruleCheck(String url, String robotText){
		if (robotText == null) {
			return true;
		}
		
		int idx_agent = robotText.indexOf("User-agent: cis5550-crawler");
		int idx_next_agent;
		int idx_allow;
		int idx_disallow;
		String allowPrefix = null;
		String disAllowPrefix;
		String ruleString;
		if (idx_agent == -1) {
			idx_agent = robotText.indexOf("User-agent: *");
			
			if (idx_agent == -1) {
				return true;
			}
		}
		
		idx_next_agent = robotText.indexOf("User-agent:", idx_agent + 1);
		if (idx_next_agent != -1) {
			ruleString = robotText.substring(idx_agent, idx_next_agent);
		}else {
			ruleString = robotText.substring(idx_agent);
		}
		idx_allow = ruleString.indexOf("Allow:");
		idx_disallow = ruleString.indexOf("Disallow:");
		
		if (idx_disallow == -1) {
			return true;
		}
		
		if (idx_allow == -1) {
			disAllowPrefix = ruleString.substring(idx_disallow + 9).trim();
		}else {
			if (idx_allow > idx_disallow) {
				allowPrefix = ruleString.substring(idx_allow + 6).trim();
				disAllowPrefix = ruleString.substring(idx_disallow + 9, idx_allow).trim();
			}else {
				allowPrefix = ruleString.substring(idx_allow + 6, idx_disallow).trim();
				disAllowPrefix = ruleString.substring(idx_disallow + 9).trim();
			}
		}
		
		String urlRulePart;
		int idx_slash = url.indexOf("/", url.indexOf("/") + 2);
		if (idx_slash != -1) {
			int idx_next_slash = url.indexOf("/", idx_slash + 1);
			if (idx_next_slash != -1) {
				urlRulePart = url.substring(idx_slash, idx_next_slash);
			}else {
				urlRulePart = url.substring(idx_slash);
			}
			if (urlRulePart.startsWith(disAllowPrefix)) {
				System.out.println(urlRulePart);
				System.out.println(url);
				if (!(allowPrefix == null)) {
					if (idx_allow < idx_disallow && urlRulePart.startsWith(allowPrefix)) {
						System.out.println("allow idx: " + idx_allow);
						System.out.println("idx_disallow: " + idx_disallow);
						return true;
					}else {
						return false;
					}
				}else {
					return false;
				}
			}
		}
		
		return true;
	}
}
