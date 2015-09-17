package squirrel.ir.retrieve.models;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import squirrel.ir.IRQualityMetrics;
import squirrel.ir.index.IX_Collection;
import squirrel.ir.index.IX_Document;
import squirrel.ir.index.IX_Term;
import squirrel.ir.index.IX_TermMatch;
import squirrel.ir.index.IX_Collection.PreparedQuery;
import squirrel.ir.retrieve.RT_Query;
import squirrel.ir.retrieve.RT_Result;
import squirrel.ir.retrieve.models.MDL_XueQR.SearchConfig;
import squirrel.smt.aligner.IBM1.WordPair;

public class MDL_XueIBMQR extends MDL_GenericModel<MDL_XueIBMQR.SearchConfig> {

	public static class SearchConfig extends MDL_GenericModel.SearchConfig {
		public double alpha, beta, gamma;

		public SearchConfig(double alpha, double beta, double gamma) {
			this.alpha = alpha;
			this.beta = beta;
			this.gamma = gamma;
		}
	}


	// q-a
	private HashMap<WordPair, Double> pMap = null;

	// a-q
	private HashMap<WordPair, Double> rpMap = null;


	private double lambda = 0.001; // = 0.1;


	private double delta;

	public MDL_XueIBMQR(IX_Collection col, double delta,
			HashMap<WordPair, Double> pm, HashMap<WordPair, Double> rpm) {
		super(col);
		this.delta = delta;
		// Currently the process is based on one probability.pmap file. Relation
		// between collection - probability map file needs to be specified
		this.pMap = pm;
		this.rpMap = rpm;

	}

	private double PwD(String w, int docID, SearchConfig sc) {

		double score;
		int length = col.getDocLength(docID);

	
		score = ((length / (length + lambda)) * Pmxwqa(w, docID, sc))
				+ ((lambda / (lambda + length)) * PmlwC(w));

		return score;
	}

	private double PmlwC(String w) {

		double freq = col.getTerms().get(w).getCount();
		double length = col.getCount();

		return freq / length;
	}

	private double Pmlwq(String w, int docID) {

		double freq = 0;

		loop: for (IX_TermMatch tm : col.findTerm(w).getMatches()) {

		
			if ((tm.getDocid() == docID) && (tm.getAnsid() == 0)) {
				freq = tm.getCount();
				break loop; //
			} else if (tm.getDocid() > docID) {
				break loop;
			}
		}

		double length = col.getDocLength(docID);
		double result = freq / length;

		return result;
	}

	private double Pmlwa(String w, int docID) {

		double freq = 0;

		loop: for (IX_TermMatch tm : col.findTerm(w).getMatches()) {

		
			if ((tm.getDocid() == docID) && (tm.getAnsid() > 0)) {
				freq += tm.getCount();
			} else if (tm.getDocid() > docID) {
				break loop;
			}
		}

		double length = col.getDocLength(docID);

		return freq / length;
	}

	private double Pmxwqa(String w, int docID, SearchConfig sc) {

		
		double sum = 0.0;
		String[] Qt = col.getDocuments().get(docID).getqTerms();
		for (String t : Qt) {
			sum += Pwt(w, t) * Pmlwq(t, docID);
		}
	
		sum /= Qt.length;

	
		double result = sc.alpha * Pmlwq(w, docID) + sc.beta * sum + sc.gamma
				* Pmlwa(w, docID);

		return result;
	}

	private Double Pwt(String w, String t) {

		
		WordPair nwp = new WordPair(w, t);
		Double pwt = pMap.get(nwp);
		Double twp = rpMap.get(nwp);
		if (pwt == null)
			pwt = 0.0d;
		if (twp == null)
			twp = 0.0d;

		return pwt * delta + (1 - delta) * twp;
	}

	@Override
	protected List<RT_Result> internalSearch(IX_Collection.PreparedQuery pq,
			SearchConfig sc) {

	
		@SuppressWarnings("serial")
		class Accumulator extends HashMap<Integer, RT_Result> {
			/**
			 * Lazily creates result data.
			 * 
			 * @param docID
			 * @param score
			 */
			public void addResult(int docID, double score) {
				RT_Result rt = get(docID);
				if (rt == null) {
			
					rt = new RT_Result(docID, 1);
				}

				
				rt.mulScore(score);

				
				put(docID, rt);

			}
		}

		Accumulator acc = new Accumulator();

	
		for (IX_Document doc : col.getDocuments().values()) {

			
			for (IX_Term t : pq.getTerms()) {
				
				double score = PwD(t.getName(), doc.getDocID(), sc);

				acc.addResult(doc.getDocID(), score);
			}
		}

		
		List<RT_Result> results = new ArrayList<RT_Result>();
		for (RT_Result r : acc.values()) {
			if (r.getScore() > 0.0)
				results.add(r);
		}

		return results;

	}

	public double getDelta() {
		return delta;
	}

	public void setDelta(double delta) {
		this.delta = delta;
	}

	public IRQualityMetrics search(RT_Query query, SearchConfig sc) {
	
		List<RT_Result> results = search(query.getPreparedQuery(col), sc);

		
		if ((results == null) || results.isEmpty()) {
			return null;
		}

	
		double found = 0;
		Set<String> allPatterns = new TreeSet<String>();
		Map<String, Double> patternRanks = new HashMap<String, Double>();

		
		double foundRPrecise = 0;
		int currentRank = 0;
		int numRelevant = query.getRelevant().size();
		for (RT_Result rCurrent : results) {
			Set<String> patterns = getDocument(
					results.get(currentRank).getDocID()).getPatterns();

			if (query.getRelevant().contains(rCurrent.getDocID())) {
			
				if (numRelevant > currentRank) {
					foundRPrecise++;
				}
				if (topK > currentRank) {
					found++;
				}
			}

			if (topK > currentRank) {
			
				allPatterns.addAll(patterns);
			}

		
			for (String pattern : patterns) {
				double patternRank;
				Double dPatternRank = patternRanks.get(pattern);
				if (dPatternRank != null) {
					patternRank = dPatternRank;
				} else {
					patternRank = 0;
				}
				patternRank += 1.0d / (currentRank + 1); // 
				patternRanks.put(pattern, patternRank);
			}

			currentRank++;
		}
		Set<String> foundPatterns = new TreeSet<String>(query.getPatterns());
		foundPatterns.retainAll(allPatterns);

		int numRelevantPatterns = query.getPatterns().size();
		int numFoundPatterns = 0;
		List<Map.Entry<String, Double>> rankedPatterns = new ArrayList<Map.Entry<String, Double>>(
				patternRanks.entrySet());
		Collections.sort(rankedPatterns,
				new Comparator<Map.Entry<String, Double>>() {
					@Override
					public int compare(Entry<String, Double> l,
							Entry<String, Double> r) {
						return l.getValue().compareTo(r.getValue());
					}
				});
		for (int i = 0; i < numRelevantPatterns; i++) {
			if (query.getPatterns().contains(rankedPatterns.get(i).getKey())) {
				numFoundPatterns++;
			}
		}

		return new IRQualityMetrics(query, topK, ((double) found / topK),
				((double) foundPatterns.size() / allPatterns.size()),
				((double) found / query.getRelevant().size()),
				((double) foundPatterns.size() / query.getPatterns().size()),
				((double) foundRPrecise / numRelevant),
				((double) numFoundPatterns / numRelevantPatterns));
	}
}
