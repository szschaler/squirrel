package squirrel.smt.aligner.IBM1;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Scanner;
import java.util.Map.Entry;

import squirrel.data.json.JSON_Document;
import squirrel.data.json.JSON_IO;
import squirrel.data.json.JSON_Document.Answer;
import squirrel.ir.index.IX_Collection;
import squirrel.util.UTIL_FileOperations;
import squirrel.util.UTIL_TextClean;

public class IBM1Driver {

	public static void IBM(IX_Collection col) {
		String[] eng = { "the book", "a house", "the big book", "a small book",
				"the big house", "a small house" };
		String[] de = { "das buch", "ein haus", "das grosse buch",
				"ein kleines buch", "das grosse haus", "ein kleines haus" };
		ArrayList<Bitext> ftb = new ArrayList<Bitext>();
		ArrayList<Bitext> btb = new ArrayList<Bitext>();

		IBM1EM.whereAmI();

		ArrayList<Bitext> forwardBitext = null;
		ArrayList<Bitext> reverseBitext = null;
		String dirName = "";
		Scanner in = new Scanner(System.in);
		// read the dir
		System.out.println("Please enter the documents directory:");
		dirName = in.nextLine();
		File dir = new File(dirName);
		if (dir.isDirectory()) {
			File[] files = dir.listFiles();
			forwardBitext = new ArrayList<Bitext>();
			reverseBitext = new ArrayList<Bitext>();
			// create bitext pairs from the directory and store them in array
			// list
			// example q1-a1,a2,a3 is turned into 3 pairs of bitext q1a1, q1a2,
			// q1a3
			int i = 0;
			for (File file : files) {
				int docID = Integer.parseInt(file.getName().replaceFirst(
						"[.][^.]+$", ""));
				JSON_Document json = JSON_IO.retrieveDocument(file, docID);

				// extract document info
				String url = json.getUrl();
				String title = json.getTitle();
				String ques = json.getQuestion();
				ArrayList<Answer> answers = json.getAnswers();
				System.out.println(docID);
				String q = title + " " + ques;
				String[] qsplits = UTIL_TextClean.cleanText(q, true);
				ArrayList<String> hashSplit = new ArrayList<String>();
				for (int l = 0; l < qsplits.length; l++) {
					if (!hashSplit.contains(qsplits[l])) {
						hashSplit.add(qsplits[l]);
						System.out.println("added");
					}

				}
				System.out.println(hashSplit);

				for (Answer ans : answers) {

					// get the filtered words from answer text
					// (true for stopword removal)
					ArrayList<String> ansplits = new ArrayList<String>();
					String[] asplits = UTIL_TextClean.cleanText(ans.getAns(),
							true);
					for (int l = 0; l < asplits.length; l++) {
						if (!ansplits.contains(asplits[l])) {
							ansplits.add(asplits[l]);
						}

					}
					System.out.println(ansplits);
					// create new Bitext and add to @bitextList

					// Xue pooling strategy
					/*
					 * bitextList.add(new Bitext(qsplits, asplits));
					 * bitextList.add(new Bitext(asplits, qsplits));
					 */

					// currently unidirectional q->a
					forwardBitext.add(new Bitext(hashSplit
							.toArray(new String[hashSplit.size()]), ansplits
							.toArray(new String[ansplits.size()]), i, docID));

					reverseBitext.add(new Bitext(ansplits
							.toArray(new String[ansplits.size()]), hashSplit
							.toArray(new String[hashSplit.size()]), i, docID));
					i++;
				}

			}
		}

		// train and store the pairs
		UTIL_FileOperations.store(forwardBitext, col.getName()
				+ "forward.bitext");
		UTIL_FileOperations.store(reverseBitext, col.getName()
				+ "reverse.bitext");
		IBM1EM.computeIBMForDir(col.getName());
	}

	public static void turnToText(String colName) {

		HashMap<WordPair, Double> ftestpm = (HashMap<WordPair, Double>) UTIL_FileOperations
				.openObject(colName + "qaProb.pmap");
		HashMap<WordPair, Double> rtestpm = (HashMap<WordPair, Double>) UTIL_FileOperations
				.openObject(colName + "aqProb.pmap");
		FileWriter fstream = null;
		BufferedWriter fout = null;
		FileWriter rstream = null;
		BufferedWriter rout = null;
		try {
			fstream = new FileWriter(colName + "qarob.txt");
			fout = new BufferedWriter(fstream);
			rstream = new FileWriter(colName + "aqrob.txt");
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
}
