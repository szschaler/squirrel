package squirrel.smt.aligner.IBM1;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;

import squirrel.util.UTIL_FileOperations;

public class IBM1EM {

	// retrieve qa pairs from current collection, turn them into bitext pairs
	// run IBM1 for all pairs
	// save the translation probability data into a file

	public static int NUM_ITERATIONS = 5;
	public static double DELTA = 5.0;

	// analyze the document's contents and insert them in the index
	// p(q|a)=p(question|answer)=p(target|source)//question target,
	// source=answer
	public static void whereAmI() {
		System.out.println("Working Directory = "
				+ System.getProperty("user.dir"));
	}

	public static boolean computeIBMForDir(String collectionName) {
		ArrayList<Bitext> forwardBitext = (ArrayList<Bitext>) UTIL_FileOperations
				.openObject(collectionName + "forward.bitext");
		ArrayList<Bitext> reverseBitext = (ArrayList<Bitext>) UTIL_FileOperations
				.openObject(collectionName + "reverse.bitext");
		return UTIL_FileOperations.store(train(forwardBitext), collectionName
				+ "qaProb.pmap")
				&& UTIL_FileOperations.store(train(reverseBitext),
						collectionName + "aqProb.pmap");

	}

	public static HashMap<WordPair, Double> train(ArrayList<Bitext> bitextList) {
		HashSet<String> sourceWords = new HashSet<String>();
		HashSet<String> targetWords = new HashSet<String>();

		HashMap<WordPair, Double> pMap = new HashMap<WordPair, Double>();

		initialiseWords(bitextList, sourceWords, targetWords);
		initialiseProbabilityMap(bitextList, pMap, targetWords);

		for (int i = 0; i < NUM_ITERATIONS; i++) {

			System.out.println("Iteration : " + i);

			HashMap<String, Double> total = new HashMap<String, Double>();
			HashMap<String, Double> s_total = new HashMap<String, Double>();

			// count(e|f) = 0 for all e,f
			HashMap<WordPair, Double> count = newCountMap(bitextList);

			// total(f) = 0 for all f
			for (String a : targetWords) {
				total.put(a, (double) 0);
			}

			// also needs to get a data structure (bitext id
			for (Bitext bt : bitextList) {
				// null added
				String source[] = bt.getSource();
				String target[] = bt.getTarget();

				for (String q : source) {
					s_total.put(q, (double) 0);
					for (String a : target) {
						Double prob = pMap.get(new WordPair(q, a));
						if (prob == null)
							prob = (double) 0;
						s_total.put(q, s_total.get(q) + prob);
					}
				}
				for (String q : source) {
					for (String a : target) {
						count.put(
								new WordPair(q, a),
								count.get(new WordPair(q, a))
										+ (pMap.get(new WordPair(q, a)) / s_total
												.get(q)));
						total.put(
								a,
								total.get(a)
										+ (pMap.get(new WordPair(q, a)) / s_total
												.get(q)));
					}
				}
			}
			// p(source|target)
			// Estimate probabilities
			for (String a : targetWords) {
				for (String q : sourceWords) {
					if (pMap.get(new WordPair(q, a)) != null) {
						pMap.put(new WordPair(q, a),
								count.get(new WordPair(q, a)) / total.get(a));
					}
				}
			}

		}

		return pMap;

	}

	private static void initialiseWords(ArrayList<Bitext> bitextList,
			HashSet<String> sourceWords, HashSet<String> targetWords) {
		// null added
		for (Bitext bt : bitextList) {

			String[] question = bt.getSource();
			String[] answer = bt.getTarget();

			for (int i = 0; i < question.length; i++)
				sourceWords.add(question[i]);

			for (int i = 0; i < answer.length; i++)
				targetWords.add(answer[i]);
		}

		System.out.println(" Question size: " + sourceWords.size()
				+ "Answer size: " + targetWords.size());
	}

	private static void initialiseProbabilityMap(ArrayList<Bitext> bitextList,
			HashMap<WordPair, Double> pMap, HashSet<String> targetWords) {
		// null added
		Double initialValue = (double) (1.0 / targetWords.size());

		for (Bitext bt : bitextList) {
			String[] question = bt.getSource();
			String[] answer = bt.getTarget();

			for (int i = 0; i < question.length; i++) {
				for (int j = 0; j < answer.length; j++) {
					pMap.put(new WordPair(question[i], answer[j]), initialValue);
				}
			}
		}

		System.out.println("Probability map size: " + pMap.size());
	}

	private static HashMap<WordPair, Double> newCountMap(
			ArrayList<Bitext> bitextList) {
		// null added
		HashMap<WordPair, Double> countMap = new HashMap<WordPair, Double>();

		for (Bitext bt : bitextList) {
			String[] question = bt.getSource();
			String[] answer = bt.getTarget();

			for (int i = 0; i < question.length; i++) {
				for (int j = 0; j < answer.length; j++) {
					countMap.put(new WordPair(question[i], answer[j]),
							(double) 0);
				}
			}
		}

		return countMap;
	}

	public static void extractViterbi(HashMap<WordPair, Double> forwardMap,
			HashMap<WordPair, Double> backwardMap,
			ArrayList<Bitext> fwbitextList, ArrayList<Bitext> bwbitextList,
			String collectionName) {
		// one to one alignment
		for (int k = 0; k < 2; k++) {

			HashMap<WordPair, Double> currentMap = forwardMap;
			String directoryName = collectionName + "forwardAlignment.vPath";
			ArrayList<Bitext> bitextList = fwbitextList;
			HashMap<Bitext, AlignPair> corpusAlignment = new HashMap<Bitext, AlignPair>();
			if (k == 1) {
				currentMap = backwardMap;
				directoryName = collectionName + "backwardAlignment.vPath";
				bitextList = bwbitextList;
			}
			int btcounter = 0;
			for (Bitext bt : bitextList) {

				String[] question = bt.getSource();
				String aanswer[] = bt.getTarget();

				ArrayList<WordPair> candidates = new ArrayList<WordPair>();

				for (int i = 0; i < question.length; i++) {
					ArrayList<WordPair> allPairs = new ArrayList<WordPair>();
					// all the word pairs for the current source word
					for (int j = 0; j < aanswer.length; j++) {
						allPairs.add(new WordPair(question[i], aanswer[j]));
					}
					Double highest = 0d;
					ArrayList<WordPair> sourceAlignments = new ArrayList<WordPair>();

					for (WordPair wp : allPairs) {

						Double currentProb = currentMap.get(wp);
						if (currentProb == null) {

						} else {
							if (currentProb > highest) {
								sourceAlignments.clear();
								highest = currentProb;
								sourceAlignments.add(wp);
							} else if (currentProb == highest) {
								if (sourceAlignments.size() == 1) {
									WordPair cb = sourceAlignments.get(0);
									if (cb.equals(wp)) {

									} else {

									}
								}

							}
						}
					}
					if (sourceAlignments.size() != 0) {

						candidates.add(sourceAlignments.get(0));
					}
				}
				corpusAlignment.put(bt, new AlignPair(bt, candidates));
			}
			UTIL_FileOperations.store(corpusAlignment, directoryName);
		}
	}

}
