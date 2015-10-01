package squirrel.smt.aligner.IBM1;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import squirrel.ir.index.IX_Collection;
import squirrel.ir.retrieve.models.PhraseSearchLogger;
import squirrel.util.UTIL_FileOperations;
import squirrel.util.UTIL_UserInput;

public class PhraseExtractor {

	private PhraseSearchLogger psl;
	private IX_Collection col;

	public PhraseExtractor(IX_Collection col) {
		psl = new PhraseSearchLogger("PhraseExtraction");
		this.col = col;
	}

	// find viterbi paths for the bitexts
	public void findViterbiPath() {
		try {
			System.out.println("opening files");
			ArrayList<Bitext> fwList = (ArrayList<Bitext>) UTIL_FileOperations
					.openObject(col.getName() + "forward.bitext");

			ArrayList<Bitext> bwList = (ArrayList<Bitext>) UTIL_FileOperations
					.openObject(col.getName() + "reverse.bitext");

			HashMap<WordPair, Double> fwMap = (HashMap<WordPair, Double>) UTIL_FileOperations
					.openObject(col.getName() + "qaProb.pmap");

			HashMap<WordPair, Double> bwMap = (HashMap<WordPair, Double>) UTIL_FileOperations
					.openObject(col.getName() + "aqProb.pmap");
			System.out.println("files opened");
			IBM1EM.extractViterbi(fwMap, bwMap, fwList, bwList, col.getName());
		} catch (Exception e) {
			System.out
					.println("Opening files error, check files for the collection");
		}
	}

	public void extractPhrase() {

		String fwA = "forwardAlignment.vPath";
		HashMap<Bitext, AlignPair> currentMap = (HashMap<Bitext, AlignPair>) UTIL_FileOperations
				.openObject(col.getName() + fwA);
		String bwA = "backwardAlignment.vPath";
		HashMap<Bitext, AlignPair> bwAlignment = (HashMap<Bitext, AlignPair>) UTIL_FileOperations
				.openObject(col.getName() + bwA);
		String mapName = col.getName() + "forward.aMap";
		for (int i = 0; i < 2; i++) {
			if (i == 1) {
				currentMap = bwAlignment;
				mapName = col.getName() + "reverse.aMap";
			}
			// Iterator<Entry<Bitext, AlignPair>> fIt = fwAlignment.entrySet()
			// .iterator();
			HashMap<Bitext, HashMap<Integer, Integer>> alignmentMap = new HashMap<Bitext, HashMap<Integer, Integer>>();
			Iterator<Entry<Bitext, AlignPair>> cait = currentMap.entrySet()
					.iterator();
			while (cait.hasNext()) {
				HashMap<Integer, Integer> sourceTargetPair = new HashMap<Integer, Integer>();
				Map.Entry<Bitext, AlignPair> pair = cait.next();
				AlignPair forwardAlign = pair.getValue();
				Bitext currentBitext = pair.getKey();
				String ttarget[] = currentBitext.getTarget();

				ArrayList<WordPair> forwardViterbi = forwardAlign
						.getAlignments();
				Integer sourceIndex = 0;

				// for forward alignment
				for (String s : currentBitext.getSource()) {
					Integer targetIndex = 0;
					Integer closestTarget = currentBitext.getTarget().length + 1;
					for (WordPair wp : forwardViterbi) {
						if (wp.e.equals(s)) {
							String target = wp.f;
							ArrayList<Integer> targetPositions = new ArrayList<Integer>();

							for (String t : ttarget) {
								if (t.equals(target)) {
									targetPositions.add(targetIndex);
								}
								targetIndex++;
							}

							if (targetPositions.size() > 1) {

								Double sourcePercentage = (double) (sourceIndex / currentBitext
										.getSource().length);
								Double smallest = 100000000000000d;
								for (Integer tp : targetPositions) {
									Double targetPercentage = (double) (tp / currentBitext
											.getTarget().length);
									Double distance = Math.abs(sourcePercentage
											- targetPercentage);
									if (smallest > distance) {
										smallest = distance;
										closestTarget = tp;
									}
								}

							} else if (targetPositions.size() == 1) {
								closestTarget = targetPositions.get(0);
							}

							break;
						}

					}

					if (!(closestTarget >= currentBitext.getTarget().length + 1)) {
						if (closestTarget == 0) {
						}

						else {
							sourceTargetPair.put(sourceIndex, closestTarget);
							sourceIndex++;
						}
					}

				}

				Iterator<Entry<Integer, Integer>> ait = sourceTargetPair
						.entrySet().iterator();

				while (ait.hasNext()) {
					Map.Entry<Integer, Integer> ip = ait.next();

					alignmentMap.put(currentBitext, sourceTargetPair);
				}
			}

			UTIL_FileOperations.store(alignmentMap, mapName);

		}

	}

	public void templateAlignment() {
		// TODO Auto-generated method stub

		HashMap<Bitext, HashMap<Integer, Integer>> fwAlignment = (HashMap<Bitext, HashMap<Integer, Integer>>) UTIL_FileOperations
				.openObject(col.getName() + "forward.aMap");
		HashMap<Bitext, HashMap<Integer, Integer>> bwAlignment = (HashMap<Bitext, HashMap<Integer, Integer>>) UTIL_FileOperations
				.openObject(col.getName() + "reverse.aMap");
		HashMap<HashSet<String>, HashSet<Integer>> phraseDocMap = new HashMap<HashSet<String>, HashSet<Integer>>();
		HashMap<Integer, HashSet<HashSet<String>>> docPhraseMap = new HashMap<Integer, HashSet<HashSet<String>>>();
		// phrase(singleton, static)- documentslist('1191',........)

		Iterator<Entry<Bitext, HashMap<Integer, Integer>>> fmi = fwAlignment
				.entrySet().iterator();

		int sum = 0;

		while (fmi.hasNext()) {

			Map.Entry<Bitext, HashMap<Integer, Integer>> cfp = fmi.next();
			Bitext cb = cfp.getKey();

			HashMap<Integer, Integer> cfa = cfp.getValue();
			HashMap<Integer, Integer> cba = bwAlignment.get(cb);
			if (cba == null) {
				System.out.println("Something went wrong at templateAlignment");
				System.exit(1);
			}
			int[][] mergedTemplate = initialiseTemplate(cb, cfa, cba);

			int[][] growDiagTemplate = growDiag(mergedTemplate,
					cb.getSource().length, cb.getTarget().length);
			int[][] growDiagFinalTemplate = finalGrow(mergedTemplate,
					growDiagTemplate, cb.getSource().length,
					cb.getTarget().length);
			HashMap<HashSet<String>, HashSet<Integer>> tempPhraseDocMap = readPhrase(
					cb, growDiagFinalTemplate, cb.getSource().length,
					cb.getTarget().length, psl);
			Iterator<Entry<HashSet<String>, HashSet<Integer>>> tpdmi = tempPhraseDocMap
					.entrySet().iterator();
			while (tpdmi.hasNext()) {
				Entry<HashSet<String>, HashSet<Integer>> pair = tpdmi.next();

				// phrase-docid

				HashSet<String> phrase = pair.getKey();
				if (pair.getValue().size() >= 2) {
					System.out.println("Weird");

				}
				HashSet<Integer> dl = pair.getValue();
				if (phrase.size() > 0) {
					if (phraseDocMap.get(phrase) == null) {
						phraseDocMap.put(phrase, dl);
					} else {
						HashSet<Integer> dlsf = phraseDocMap.get(phrase);
						dlsf.addAll(dl);
						phraseDocMap.put(phrase, dlsf);
					}
				}
			}
			// learn single viterbi words from union as well and add to the
			// phrase

		}
		FileWriter fstream = null;
		BufferedWriter fout = null;
		try {
			fstream = new FileWriter(col.getName() + "pdoc.txt");
			fout = new BufferedWriter(fstream);
			Iterator<Entry<HashSet<String>, HashSet<Integer>>> ftestpmi = phraseDocMap
					.entrySet().iterator();
			while (ftestpmi.hasNext()) {

				Entry<HashSet<String>, HashSet<Integer>> pairs = ftestpmi
						.next();
				fout.write(pairs.getKey().toString() + " : " + pairs.getValue()
						+ "\n");

			}
			fstream.close();
			fout.close();
		} catch (IOException ioe) {

		}
		Iterator<Entry<HashSet<String>, HashSet<Integer>>> pdmi = phraseDocMap
				.entrySet().iterator();
		while (pdmi.hasNext()) {
			Entry<HashSet<String>, HashSet<Integer>> pair = pdmi.next();
			HashSet<Integer> phraseDocList = pair.getValue();
			HashSet<String> phrase = pair.getKey();
			for (Integer docId : phraseDocList) {
				if (docPhraseMap.get(docId) != null) {
					HashSet<HashSet<String>> docPhraseSet = docPhraseMap
							.get(docId);
					docPhraseSet.add(phrase);
					docPhraseMap.put(docId, docPhraseSet);
				} else {
					HashSet<HashSet<String>> docPhraseSet = new HashSet<HashSet<String>>();
					docPhraseSet.add(phrase);
					docPhraseMap.put(docId, docPhraseSet);
				}

			}
		}
		try {
			fstream = new FileWriter(col.getName() + "docp.txt");
			fout = new BufferedWriter(fstream);
			Iterator<Entry<Integer, HashSet<HashSet<String>>>> ftestpmi = docPhraseMap
					.entrySet().iterator();
			while (ftestpmi.hasNext()) {

				Entry<Integer, HashSet<HashSet<String>>> pairs = ftestpmi
						.next();
				fout.write(pairs.getKey().toString() + " : " + pairs.getValue()
						+ "\n");

			}
			fstream.close();
			fout.close();
		} catch (IOException ioe) {

		}
		UTIL_FileOperations.store(docPhraseMap, col.getName()
				+ "docPhraseMap.pdm");
		UTIL_FileOperations.store(phraseDocMap, col.getName()
				+ "phraseDocMap.pdm");
		psl.saveLog(col.getName());
		System.out.println("total unique phrase count: " + phraseDocMap.size());
	}

	private static int[][] finalGrow(int[][] mergedTemplate,
			int[][] growDiagTemplate, int nsw, int ntw) {

		for (int i = 0; i < ntw; i++) {
			for (int j = 0; j < nsw; j++) {
				if (mergedTemplate[i][j] == 1) {
					// check row
					boolean rowEmpty = true;
					boolean columnEmpty = true;
					for (int r = 0; r < ntw; r++) {
						if (growDiagTemplate[r][j] == 2) {
							rowEmpty = false;
							break;
						}
					}
					for (int c = 0; c < nsw; c++) {
						if (growDiagTemplate[i][c] == 2) {
							columnEmpty = false;
							break;
						}
					}
					// check column
					if (rowEmpty && columnEmpty) {
						growDiagTemplate[i][j] = 2;
					}
				}
			}
		}

		for (int i = 0; i < ntw; i++) {
			for (int j = 0; j < nsw; j++) {

			}

		}
		return growDiagTemplate;
	}

	private static HashMap<HashSet<String>, HashSet<Integer>> readPhrase(
			Bitext cb, int[][] finalTemplate, int nsw, int ntw,
			PhraseSearchLogger psl) {
		// 0, 0, 2, 0
		// 0, 0, 0, 2
		// 0, 2, 0, 0

		HashMap<HashSet<String>, HashSet<Integer>> temp = new HashMap<HashSet<String>, HashSet<Integer>>();

		int phraseCount = 0;
		for (int phraseLength = 1; phraseLength < 6; phraseLength++) {
			// for all source words in phrases of length phraseLength
			boolean unaligned = false;

			for (int sourceIndex = 0; sourceIndex < nsw - phraseLength + 1; sourceIndex++) {

				int lastIndex = sourceIndex + phraseLength - 1;
				int globalHighest = -1;
				int globalLowest = ntw;

				targetloop: for (int lengthCount = 1; lengthCount <= phraseLength; lengthCount++) {
					int currentWord = sourceIndex + lengthCount - 1;
					int smallest = -1;
					int highest = ntw;

					// Searching for phrase block of current source word
					// for all possible current word-target words pair
					for (int targetIndex = 0; targetIndex < ntw; targetIndex++) {
						int point = finalTemplate[targetIndex][currentWord];
						// if the point is in alignment
						if (point == 2) {
							smallest = targetIndex;
							if (smallest <= globalLowest) {
								globalLowest = smallest;
							}
							break;
						}
					}
					for (int targetIndex = ntw - 1; targetIndex >= 0; targetIndex--) {
						int point = finalTemplate[targetIndex][currentWord];
						// if the point is in alignment
						if (point == 2) {
							highest = targetIndex;
							if (highest >= globalHighest) {
								globalHighest = highest;
							}
							break;
						}
					}
					if (smallest >= 0 && highest < ntw) {
						// checking for other alignments

						for (int rangeIndex = smallest; rangeIndex <= highest; rangeIndex++) {

							for (int si = 0; si < nsw; si++) {
								if (finalTemplate[rangeIndex][si] == 2) {
									if (si < sourceIndex || si > lastIndex) {
										unaligned = true;

										break targetloop;
										// found source word alignment out of
										// the
										// source phrase range
										// the current phrase in question cannot
										// be
										// used;
									}
								}
							}
						}
					}

				}
				if (!unaligned) {
					// (source start, source end)X(lowest, highest) phrase
					// aligned, can be stored;
					// index ranges into ArrayList<String>, ArrayList<String>
					// data structure and save

					HashSet<String> tsp = new HashSet<String>();
					HashSet<String> ttp = new HashSet<String>();
					for (int s = sourceIndex; s <= lastIndex; s++) {
						String currentWord = cb.getSource()[s];
						tsp.add(currentWord);
					}

					for (int t = globalLowest; t <= globalHighest; t++) {
						String currentWord = cb.getTarget()[t];
						ttp.add(currentWord);
					}
					if ((ttp.size() >= 1 && ttp.size() <= 5)
							&& (tsp.size() >= 1 && tsp.size() <= 5)) {

						if (temp.get(tsp) == null) {
							HashSet<Integer> mdoclist = new HashSet<Integer>();
							mdoclist.add(cb.getDocID());
							temp.put(tsp, mdoclist);
						} else {
							HashSet<Integer> mdoclist = temp.get(tsp);
							mdoclist.add(cb.getDocID());
							temp.put(tsp, mdoclist);

						}
						if (temp.get(ttp) == null) {
							HashSet<Integer> mdoclist = new HashSet<Integer>();
							mdoclist.add(cb.getDocID());
							temp.put(ttp, mdoclist);
						} else {
							HashSet<Integer> mdoclist = temp.get(ttp);
							mdoclist.add(cb.getDocID());
							temp.put(ttp, mdoclist);

						}
						psl.addString("Phrase : " + tsp + "----" + ttp);
						psl.addLine();

					}
				}
			}

		}

		// all the words with 2- 6 words
		//additional read heuristics
		for (int i = 0; i < nsw; i++) {
			for (int j = 0; j < ntw; j++) {
				if (finalTemplate[j][i] == 2) {
					int startSindex = i;
					int startTindex = j;
					int endSIndex = findContiguous(startSindex, startTindex,
							finalTemplate, nsw, ntw);

					int length = endSIndex - startSindex + 1;
					int endTindex = startTindex + length - 1;
					if (length > 5) {
						length = 5;
						endSIndex = startSindex + 4;
						endTindex = endTindex + 4;

					}
					HashSet<String> tsp = new HashSet<String>();
					HashSet<String> ttp = new HashSet<String>();
					if (startSindex != endSIndex && startTindex != endTindex) {
						for (int ii = startSindex; ii < endSIndex; ii++) {
							tsp.add(cb.getSource()[ii]);
						}
						for (int ii = startTindex; ii < endTindex; ii++) {
							ttp.add(cb.getTarget()[ii]);
						}
						if (temp.get(tsp) == null) {
							HashSet<Integer> mdoclist = new HashSet<Integer>();
							mdoclist.add(cb.getDocID());
							temp.put(tsp, mdoclist);
						} else {
							HashSet<Integer> mdoclist = temp.get(tsp);
							mdoclist.add(cb.getDocID());
							temp.put(tsp, mdoclist);

						}
						if (temp.get(ttp) == null) {
							HashSet<Integer> mdoclist = new HashSet<Integer>();
							mdoclist.add(cb.getDocID());
							temp.put(ttp, mdoclist);
						} else {
							HashSet<Integer> mdoclist = temp.get(ttp);
							mdoclist.add(cb.getDocID());
							temp.put(ttp, mdoclist);

						}
					}

				}
			}
		}

		return temp;
	}

	private static int findContiguous(int startSindex, int startTindex,
			int[][] finalTemplate, int nsw, int ntw) {
		// TODO Auto-generated method stub

		int nextSIndex = startSindex + 1;
		int nextTIndex = startTindex + 1;
		if (nextSIndex < nsw - 1 && startTindex < ntw - 1
				&& finalTemplate[startTindex][startSindex] == 2) {
			return findContiguous(nextSIndex, nextTIndex, finalTemplate, nsw,
					ntw);
		} else {
			return startSindex;
		}

	}

	private static int[][] growDiag(int[][] mergedTemplate, int nsw, int ntw) {
		// int[ntw][nsw]
		// TODO Auto-generated method stub

		int[][] currentPoints = new int[ntw][nsw];

		for (int i = 0; i < ntw; i++) {
			for (int j = 0; j < nsw; j++) {
				if (mergedTemplate[i][j] == 2)
					currentPoints[i][j] = 2;
				else
					currentPoints[i][j] = 0;
			}
		}
		for (int i = 0; i < ntw; i++) {
			for (int j = 0; j < nsw; j++) {

			}

		}

		boolean added = true;
		int[][] temp;
		while (added) {
			added = false;
			temp = currentPoints.clone();
			for (int i = 0; i < ntw; i++) {
				for (int j = 0; j < nsw; j++) {
					if (currentPoints[i][j] == 2) {

						int miny = i - 1;
						int maxy = i + 1;
						int minx = j - 1;
						int maxx = j + 1;

						int nc = 1;
						int sc = 1;

						for (int k = miny; k < maxy + 1; k++) {
							for (int l = minx; l < maxx + 1; l++) {

								nc++;
								boolean selfCheck = (k == i) && (l == j);
								if (!selfCheck) {
									// loop over neighbours

									sc++;
									try {

										if (mergedTemplate[k][l] == 1
												&& (!(covered(l, currentPoints,
														"Source")) || !(covered(
														k, currentPoints,
														"Target")))) {
											temp[k][l] = 2;

											added = true;

										}
									} catch (ArrayIndexOutOfBoundsException aio) {

									}
								}
							}
						}

					}
				}
			}

			currentPoints = temp.clone();
		}

		return currentPoints;
	}

	private static boolean covered(int s, int[][] mergedTemplate, String string) {
		// TODO Auto-generated method stub
		int row = mergedTemplate.length;
		int col = mergedTemplate[0].length;
		boolean found = false;
		if (string.equals("Source")) {
			// check if the row has a 2
			for (int i = 0; i < row; i++) {
				if (mergedTemplate[i][s] == 2) {
					found = true;
					break;
				}
			}
		} else if (string.equals("Target")) {
			// check if the column has a 2
			for (int i = 0; i < col; i++) {
				if (mergedTemplate[s][i] == 2) {
					found = true;
					break;
				}
			}
		}
		return found;
	}

	// @Method initialiseTemplate: takes forward and backward alignment of the
	// bitext and returns the whole template. Word position x-y ==Array[y][x]

	private static int[][] initialiseTemplate(Bitext cb,
			HashMap<Integer, Integer> cfa, HashMap<Integer, Integer> cba) {

		// @Var nsw: number of source words
		int nsw = cb.getSource().length;
		int ntw = cb.getTarget().length;
		int[][] template = new int[ntw][nsw];
		for (int i = 0; i < nsw; i++) {
			for (int j = 0; j < ntw; j++) {
				template[j][i] = 0;
			}
		}
		Iterator<Entry<Integer, Integer>> cfai = cfa.entrySet().iterator();
		while (cfai.hasNext()) {
			Map.Entry<Integer, Integer> cfaie = cfai.next();
			int source = cfaie.getKey();
			int target = cfaie.getValue() - 1;
			template[target][source]++;
		}

		Iterator<Entry<Integer, Integer>> cbai = cba.entrySet().iterator();
		while (cbai.hasNext()) {
			Map.Entry<Integer, Integer> cbaie = cbai.next();
			int source = cbaie.getKey();
			int target = cbaie.getValue() - 1;
			template[source][target]++;
		}

		return template;
	}
}
