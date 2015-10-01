package squirrel.ir.retrieve.models;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import squirrel.ir.index.IX_Collection;
import squirrel.ir.index.IX_Document;
import squirrel.ir.index.IX_Term;
import squirrel.ir.index.IX_Collection.PreparedQuery;
import squirrel.ir.retrieve.RT_Result;
import squirrel.smt.aligner.IBM1.Bitext;

import squirrel.smt.aligner.IBM1.WordPair;
import squirrel.util.UTIL_FileOperations;
import squirrel.util.UTIL_TextClean;

//p(q|a)= 
public class MDL_LexicalWeightSum extends
		MDL_GenericModel<MDL_LexicalWeightSum.SearchConfig> {
	// need docID-Phrase set map
	// need q-a prob map
	// need a-q prob map

	// limit the scoring to the words aligned appearing together
	private HashMap<HashSet<String>, HashSet<Integer>> pidm;
	private HashMap<Integer, HashSet<HashSet<String>>> idpm;
	private HashMap<WordPair, Double> qaprob;
	private HashMap<WordPair, Double> aqprob;
	private PhraseSearchLogger log;
	private ArrayList<Bitext> bl;
	private HashMap<Integer, HashSet<String>> ds;
	private HashMap<Integer, HashSet<String>> dt;

	public static class SearchConfig extends MDL_GenericModel.SearchConfig {

		public SearchConfig() {

		}
	}

	public MDL_LexicalWeightSum(IX_Collection col) {
		super(col);
		this.col = col;
		// TODO Auto-generated constructor stub
		System.out.println("Opening probability tables");
		// open phrase table
		// HashMap<int:docID, HashSet<Phrase>>
		// p(q|a)=wordPair(q,a)
		idpm = (HashMap<Integer, HashSet<HashSet<String>>>) UTIL_FileOperations
				.openObject(col.getName() + "docPhraseMap.pdm");
		ds = new HashMap<Integer, HashSet<String>>();
		dt = new HashMap<Integer, HashSet<String>>();
		qaprob = (HashMap<WordPair, Double>) UTIL_FileOperations.openObject(col
				.getName() + "qaProb.pmap");

		bl = (ArrayList<Bitext>) UTIL_FileOperations.openObject(col.getName()
				+ "forward.bitext");
		log = new PhraseSearchLogger("phraseSelection");
		for (Bitext bt : bl) {
			String[] s = bt.getSource();
			String[] t = bt.getTarget();
			int docID = bt.getDocID();
			if (ds.get(docID) != null) {
				dt.get(docID).addAll(new ArrayList<String>(Arrays.asList(t)));
			} else {
				HashSet<String> sterms = new HashSet<String>();
				HashSet<String> tterms = new HashSet<String>();
				ArrayList<String> temp = new ArrayList<String>(Arrays.asList(s));
				ArrayList<String> ttemp = new ArrayList<String>(
						Arrays.asList(t));
				sterms.addAll(temp);
				tterms.addAll(ttemp);
				ds.put(docID, sterms);
				dt.put(docID, tterms);
			}
		}
	}

	@Override
	protected List<RT_Result> internalSearch(PreparedQuery pq,
			SearchConfig config) {
		// TODO Auto-generated method stub
		String o = pq.getOrigQuery();

		String[] splits = UTIL_TextClean.cleanText(o, true);
		ArrayList<String> cleanQuery = new ArrayList<String>();

		for (String split : splits) {
			IX_Term t = col.findTerm(split);
			if (t != null) {
				cleanQuery.add(split);
			}
		}

		System.out.println(cleanQuery);
		return search(cleanQuery);
	}

	private List<RT_Result> search(ArrayList<String> cleanQuery) {
		// TODO Auto-generated method stub
		List<RT_Result> rl = new ArrayList<RT_Result>();
		Iterator<Entry<Integer, IX_Document>> coli = col.getDocuments()
				.entrySet().iterator();
		while (coli.hasNext()) {
			Entry<Integer, IX_Document> pair = coli.next();
			Integer docId = pair.getKey();

			// score for doc(phrase)-query comb pair
			Double score = dynamicMonotone(cleanQuery, docId);
			RT_Result r = new RT_Result(docId, 0);
			r.addScore(score);
			rl.add(r);

		}
		log.saveLog(col.getName());
		return rl;
	}

	// finding best phrase among phrases lists
	private Double lexicalMaximization(HashSet<HashSet<String>> ps,
			ArrayList<String> sq) {
		// TODO Auto-generated method stub
		// for each query combination phrase
		// ps phrase set
		// query comb query set
		// calc score for queries based on qa and aq and weight them

		// forward direction
		Double best = 0d; // best of the sums of the most optimal alignment per
							// phrase and
		HashSet<String> bestPhrase = new HashSet<String>();
		for (HashSet<String> p : ps) {
			HashMap<String, Integer> asf = new HashMap<String, Integer>();
			for (String qw : sq) {
				// p currentPhrase
				// sq whole sub query
				// qw element of sq
				Double viterbiScore = 0d;
				String viterbiTerm = null;

				for (String term : p) {
					Double wordProb = qaprob.get(new WordPair(qw, term));
					if (wordProb != null) {

						if (wordProb > viterbiScore) {

							viterbiScore = wordProb;
							viterbiTerm = term;
						}
					} else {

					}
				}
				if (viterbiTerm != null) {
					if (asf.get(viterbiTerm) == null) {
						asf.put(viterbiTerm, 1);
					} else {
						asf.put(viterbiTerm, asf.get(viterbiTerm) + 1);
					}
				}
			}
			Double scoreSoFar = 0d;

			for (String qw : sq) {

				Double bestTermScore = 0d;

				Iterator<Entry<String, Integer>> asfi = asf.entrySet()
						.iterator();
				while (asfi.hasNext()) {
					Entry<String, Integer> asfip = asfi.next();
					Double scor = qaprob.get(new WordPair(qw, asfip.getKey()));
					if (scor != null) {
						if (scor > bestTermScore) {

							bestTermScore = scor;
						}
					} else {

					}
				}
				Double max = bestTermScore;
				scoreSoFar += max;

			}

			if (scoreSoFar > best) {
				best = scoreSoFar;
				bestPhrase = p;
			} else if (scoreSoFar == best) {
				if (p.size() < bestPhrase.size()) {
					best = scoreSoFar;
					bestPhrase = p;
				}
			}
		}
		if (best == 1) {
			best = 0d;
			bestPhrase = new HashSet<String>();
		}
		log.addString(sq.toString() + "-----" + bestPhrase.toString());
		log.addLine();
		return best;
	}

	// @Var cpp : current phrase probability
	private Double dynamicMonotone(ArrayList<String> cleanQuery, int bitextId) {
		ArrayList<String> tq = new ArrayList<String>(cleanQuery);
		ArrayList<Double> result = new ArrayList<Double>();
		log.addString("document id: " + bitextId);
		log.addLine();
		if (idpm.get(bitextId) != null) {
			ArrayList<HashSet<String>> phraseList = new ArrayList<HashSet<String>>(
					idpm.get(bitextId));

			HashSet<String> sterms = ds.get(bitextId);
			HashSet<String> tterms = dt.get(bitextId);

			int queryLength = 0;
			int index = result.size();
			int value = 0;
			// a[index]=aj
			result.add(0, 1d);
			for (int i = 0; i < tq.size(); i++) {
				// queryLength is 3 {Query: A B C}

				int j = i; // a0, a1, a2
				Double sum = 0d;
				// aj=a0 =1
				// aj=a1= a0*P(query(1-1)|best Phrase)
				// aj=a2=a0*P(query(1-2)|bestForQueryAmongValidPhrases)+a1*P(query(2-2)|bfqamvp)
				// aj=a3=a0*p(query(1-3)
				// |bfqamvp)+a1*p(query(2-3)|bfqamvp)+a2*p(query(3-3)|bfqamvp)
				// sum +=result.get(k)*p(query1-i|best valid phrase)
				for (int k = 0; k <= j; k++) {
					Double ajPrevious = result.get(k);
					/*
					 * Double cpp=(subquery(k-j)|findBestPhrase(subquery(k-j)))
					 * sum+ =ajPrevious*cpp
					 */
					ArrayList<String> subquery = new ArrayList<String>(
							tq.subList(k, j + 1));
					Double cpp = lexicalMaximization(idpm.get(bitextId),
							subquery);
					sum += ajPrevious * cpp;
				}
				result.add(sum);

			}
			return result.get(result.size() - 1);
		} else
			return 0d;
	}

}
