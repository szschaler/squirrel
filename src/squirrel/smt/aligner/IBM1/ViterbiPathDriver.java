package squirrel.smt.aligner.IBM1;

import squirrel.ir.index.IX_Collection;

public class ViterbiPathDriver {

	public static void extract(IX_Collection col) {
		PhraseExtractor pe = new PhraseExtractor(col);
		pe.findViterbiPath();
		pe.extractPhrase();
		pe.templateAlignment();
	}
}
