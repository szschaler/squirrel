package squirrel.ir.retrieve.models;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;

public class PhraseSearchLogger {
	StringBuilder log;
	String name;

	public PhraseSearchLogger(String name) {
		log = new StringBuilder();
		this.name = name;
	}

	public void addString(String s) {
		log.append(s);
	}

	public void addLine() {
		log.append("\n");
	}

	public void saveLog(String collectionName) {
		FileWriter fstream = null;
		BufferedWriter fout = null;
		try {
			fstream = new FileWriter(collectionName + name + ".txt");
			fout = new BufferedWriter(fstream);
			fout.write(log.toString());
			fstream.close();
			fout.close();
		} catch (IOException ioe) {

		}
	}
}
