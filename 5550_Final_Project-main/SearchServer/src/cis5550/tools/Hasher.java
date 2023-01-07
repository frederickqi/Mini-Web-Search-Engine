package cis5550.tools;

import java.security.*;
import java.util.*;

public class Hasher {
  public static String hash(String x) {
    String sha1 = "";
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-1");
      md.reset();
      md.update(x.getBytes("UTF-8"));
      sha1 = Base64.getEncoder().encodeToString(md.digest()).replace('/','@');
    } catch (Exception e) { }
    return sha1;
  }

  public static void main(String args[]) {
    for (int i=0; i<100; i++)
      System.out.println(hash(""+i));
  }
}