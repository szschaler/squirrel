package squirrel.smt.aligner.IBM1;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.StringTokenizer;

import squirrel.util.UTIL_FileOperations;
import squirrel.util.UTIL_SnowballStemmer;

public class AlignmentTester {

	private static String gefn = "gtest.txt";
	private static String enfn = "etest.txt";

	public static void main(String[] args) {
		testRun();
		computeIBMForDir();
		turnToText();
	}

	private static void turnToText() {

		HashMap<WordPair, Double> ftestpm = (HashMap<WordPair, Double>) UTIL_FileOperations
				.openObject("ftest.pmap");
		HashMap<WordPair, Double> rtestpm = (HashMap<WordPair, Double>) UTIL_FileOperations
				.openObject("rtest.pmap");
		FileWriter fstream = null;
		BufferedWriter fout = null;
		FileWriter rstream = null;
		BufferedWriter rout = null;
		try {
			fstream = new FileWriter("fprob.txt");
			fout = new BufferedWriter(fstream);
			rstream = new FileWriter("bprob.txt");
			rout = new BufferedWriter(rstream);
			Iterator<Entry<WordPair, Double>> ftestpmi = ftestpm.entrySet()
					.iterator();
			Iterator<Entry<WordPair, Double>> rtestpmi = rtestpm.entrySet()
					.iterator();

			while (ftestpmi.hasNext()) {

				Entry<WordPair, Double> pairs = ftestpmi.next();
				fout.write(pairs.getKey().toString() + " : " + pairs.getValue()
						+ "\n");

			}
			while (rtestpmi.hasNext()) {
				Entry<WordPair, Double> pairs = rtestpmi.next();
				rout.write(pairs.getKey().toString() + " : " + pairs.getValue()
						+ "\n");
			}

			fout.close();
			rout.close();

		} catch (IOException e) {

			e.printStackTrace();
		}

	}

	public static void testRun() {
		System.out.println("Test run");
		FileInputStream enstream = null;
		FileInputStream gestream = null;
		BufferedReader enbr = null;
		BufferedReader gebr = null;
		ArrayList<Bitext> fbitextList = new ArrayList<Bitext>();
		ArrayList<Bitext> rbitextList = new ArrayList<Bitext>();
		try {
			enstream = new FileInputStream(enfn);
			gestream = new FileInputStream(gefn);
			enbr = new BufferedReader(new InputStreamReader(enstream));
			gebr = new BufferedReader(new InputStreamReader(gestream));
			String strLine;

			// Read File Line By Line

			int i = 0;
			while (true) {
				String ens = enbr.readLine();
				String ges = gebr.readLine();

				if (ens == null || ges == null)
					break;

				Bitext fbt = new Bitext(cleanText(ens), cleanText(ges), i, i);
				Bitext rbt = new Bitext(cleanText(ges), cleanText(ens), i, i);
				fbitextList.add(fbt);
				rbitextList.add(rbt);
			}

			// Close the input stream
			enbr.close();
			gebr.close();
			UTIL_FileOperations.store(fbitextList, "ftest.bitext");
			UTIL_FileOperations.store(fbitextList, "rtest.bitext");

		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public static boolean computeIBMForDir() {
		ArrayList<Bitext> ftestBitext = (ArrayList<Bitext>) UTIL_FileOperations
				.openObject("ftest.bitext");
		ArrayList<Bitext> rtestBitext = (ArrayList<Bitext>) UTIL_FileOperations
				.openObject("rtest.bitext");

		return UTIL_FileOperations.store(IBM1EM.train(ftestBitext),
				"ftest.pmap")
				&& UTIL_FileOperations.store(IBM1EM.train(rtestBitext),
						"rtest.pmap");

	}

	public static String[] cleanText(String str) {

		// Pipes and Filters

		// Cleaning punctuation etc.
		str = filterText(str);

		return str.split(" ");
	}

	private static String filterText(String s) {
		StringTokenizer token = new StringTokenizer(s);
		StringBuilder resultOutput = new StringBuilder();
		while (token.hasMoreTokens()) {
			char[] w = token.nextToken().toCharArray();
			StringBuilder text = new StringBuilder();
			for (int i = 0; i < w.length; i++) {
				int ch = w[i];
				if (Character.isSpaceChar((char) ch)
						|| Character.isWhitespace((char) ch)
						|| Character.isLetter/* OrDigit */((char) ch)) {
					text.append((char) ch);
				} else {
					text.append(" ");
				}
			}
			resultOutput.append(" " + text);
		}
		return stripWhiteSpace(resultOutput.toString());
	}

	// remove extra whitespace
	private static String stripWhiteSpace(String text) {
		StringTokenizer token = new StringTokenizer(text);
		StringBuilder resultOutput = new StringBuilder();
		while (token.hasMoreTokens()) {
			resultOutput.append(token.nextToken().toLowerCase() + " ");
		}
		return resultOutput.toString();
	}

}
