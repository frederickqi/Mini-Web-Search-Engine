package cis5550.jobs;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cis5550.flame.FlameContext;
import cis5550.flame.FlameContextImpl;
import cis5550.flame.FlamePair;
import cis5550.flame.FlamePairRDD;
import cis5550.flame.FlameRDD;

public class PageRank {

	public static void run(FlameContext flameContext, String[] args) throws Exception{
		FlameRDD rdd = flameContext.fromTable("crawl", r -> r.get("url") + "," + r.get("page"));
		System.out.println("finish getting crawl table");
//		rdd.saveAsTable("test.table");
		FlamePairRDD pairRdd = rdd.mapToPair(s -> {
			String baseURL = s.split(",")[0];
			baseURL = normalizeURL(baseURL, baseURL);
			String page = s.substring(s.indexOf(",") + 1);
			List<String> outURL = extractURL(page, baseURL);
//			if (outURL.size() == 0)
			System.out.println("ourURL List Length: " + outURL.size());
			String outLink = "";
			for (String link: outURL) {
				if (outLink.equals("")) {
					outLink = link;
				}else {
					outLink = outLink + "," + link;
				}
			}
			return new FlamePair(baseURL, "1.0,1.0," + outLink);
		});
//		
//		System.out.println("Finish url generation: " + "!!!!!!!!!!!!!");
		int iteration = 0;
		while (true) {
			FlamePairRDD transfer = pairRdd.flatMapToPair(s -> {
				
				List<FlamePair> output = new ArrayList<FlamePair>();
				
				String url = s._1();
				String rankURL = s._2();
				String[] rankURLArray = rankURL.split(",");
				System.out.println("array_length: " + rankURLArray.length + " : " + url);
				String rc = rankURLArray[0];
				String rp = rankURLArray[1];
				int n = rankURLArray.length - 2;
				double v = 0;
				FlamePair rankPair;
				for (int i = 2; i < rankURLArray.length; i++) {
					v = 0.85 * Double.valueOf(rc) / n;
					System.out.println("url: " + rankURLArray[i] + " : " + String.valueOf(v));
					rankPair = new FlamePair(rankURLArray[i], String.valueOf(v));
					output.add(rankPair); 
				}
				return output;
			}).foldByKey("0", (a,b) -> ""+(Double.valueOf(a)+Double.valueOf(b)));
//			
//			System.out.println("iteration number: " + iteration + "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
//			
			FlamePairRDD out = pairRdd.join(transfer);
//			FlamePairRDD out = transfer.join(pairRdd);
			pairRdd = out.flatMapToPair(s -> {
				List<FlamePair> output = new ArrayList<FlamePair>();
				String url = s._1();
				String rankURL = s._2();
				String latestScore = rankURL.substring(rankURL.lastIndexOf(",") + 1);
				latestScore = String.valueOf(Double.valueOf(latestScore) + 0.15);
				rankURL = rankURL.substring(0, rankURL.lastIndexOf(","));
				
				String[] rankURLArray = rankURL.split(",");
				String rc = rankURLArray[0];
				rankURL = latestScore + "," + rc + rankURL.substring(rankURL.indexOf(",", rankURL.indexOf(",") + 1));
				output.add(new FlamePair(url, rankURL));
				return output;
			});
			
			FlameRDD rdd_diff = pairRdd.flatMap(s -> {
				List<String> output = new ArrayList<String>();
				String rankURL = s._2();
				String[] rankURLArray = rankURL.split(",");
				String rc = rankURLArray[0];
				String rp = rankURLArray[1];
				
				double absDiff = Math.abs(Double.valueOf(rc) - Double.valueOf(rp));
				output.add(String.valueOf(absDiff));
				return output;
			});
			
			String maxDiff = rdd_diff.fold("0", (s1,s2) -> ""+ Math.max(Double.valueOf(s1), Double.valueOf(s2)));
			
			System.out.println(String.valueOf(iteration) + " maximum difference: " +maxDiff);
			
			double t = 0.01;
			if (args.length > 0) {
				t = Double.valueOf(args[0]);
			}
			
			///////////////// EC2: Enhanced convergence criterion /////////////////////
			if (args.length == 2) {
				
				int totalCount = rdd_diff.count();
				FlameRDD test = rdd_diff.flatMap(s -> {
					List<String> output = new ArrayList<String>();
					if (Double.valueOf(s) < Double.valueOf(args[0])) {
						output.add(s);
					}
					return output;
				});
				
				int belowCount = test.count();
				System.out.println("total Count: " + totalCount);
				System.out.println("total Below Count: " + test.count());
//				
				Double belowRatio = Double.valueOf(belowCount) / Double.valueOf(totalCount);
				System.out.println("total Below Count: " + belowRatio);
				if (belowRatio >= (Double.valueOf(args[1]) / 100.0)) {
					pairRdd.flatMapToPair(s -> {
						List<FlamePair> output = new ArrayList<FlamePair>();
						
						String url = s._1();  
						String rankURL = s._2();
						String[] rankURLArray = rankURL.split(",");
						String rc = rankURLArray[0];
						
						FlameContext.getKVS().put("pageranks", url, "rank", rc.getBytes());
						
						return output;
					});
//					pairRdd.saveAsTable("pageRanks");
					break;
				}
			}
			
			if (Double.valueOf(maxDiff) < t) {
				pairRdd.flatMapToPair(s -> {
					List<FlamePair> output = new ArrayList<FlamePair>();
					
					String url = s._1();  
					String rankURL = s._2();
					String[] rankURLArray = rankURL.split(",");
					String rc = rankURLArray[0];
					
					FlameContext.getKVS().put("pageranks", url, "rank", rc.getBytes());
					
					return output;
				});
//				pairRdd.saveAsTable("pageRanks");
				break;
			}
			
			iteration++;
		}
		
	}
	
	public static List<String> extractURL(String page, String baseURL) throws Exception{
		List<String> output = new ArrayList<>();
		String foundURL;
		String normalizedURL;
		String regex = "<a.*?href=\"(.*?)\".*?>(.*?)<\\/a>";
		Pattern linkPattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE|Pattern.DOTALL);
		Matcher pageMatcher = linkPattern.matcher(page);
		while(pageMatcher.find()){
			foundURL = pageMatcher.group(1);
			normalizedURL = normalizeURL(foundURL, baseURL);
				
			
			if (!(normalizedURL == null)) {
				String s = normalizedURL;
				boolean valid_url = true;
				if (!(s.startsWith("http") || s.startsWith("https"))) {
					valid_url = false;
				}
				if (s.endsWith(".jpg") || s.endsWith(".jpeg") || s.endsWith(".gif") || s.endsWith(".png") || s.endsWith(".txt")) {
					valid_url = false;
				}
				
				if (valid_url) {
					output.add(normalizedURL);
				}
			}
		}
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
			if (idx_slash == -1) {
				return url + ":443";
			}
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
}

