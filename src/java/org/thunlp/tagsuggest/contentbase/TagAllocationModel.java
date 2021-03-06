package org.thunlp.tagsuggest.contentbase;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Map.Entry;
import java.util.logging.Logger;

import org.thunlp.tagsuggest.common.DataSource;
import org.thunlp.tagsuggest.common.ListDataSource;
import org.thunlp.misc.AnyDoublePair;
import org.thunlp.misc.Counter;
import org.thunlp.misc.WeightString;
import org.thunlp.tagsuggest.common.SparseCounter;
import org.thunlp.text.Lexicon;
import org.thunlp.text.Lexicon.Word;

/**
 * Learn and inference about the reason of having a tag.
 * @author sixiance
 *
 */
public class TagAllocationModel {
  public static Logger LOG = Logger.getAnonymousLogger();
  /**
   * The reason is a noise.
   */
  public static String NOISE = "[NOISE]";
  public static int MAX_ITERATIONS = 500;
  private double [] alpha = {1, 1};  // a0 for noise, a1 for word.
  private double beta = 0.01;
  private double gamma = 0.01;
  private int numCombinedIterations = 1;
  private Lexicon wordlex = null;

  public static class Document {
    public String [] words;
    public String [] tags;
    public String [] reason;  // The same length as the tags.
    
    public String toString() {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < tags.length; i++) {
        sb.append(" ");
        sb.append(tags[i]);
        sb.append(":");
        sb.append(reason[i]);
      }
      for (int i = 0; i < words.length; i++) {
        sb.append(" ");
        sb.append(words[i]);
      }
      return sb.toString();
    }
    
    public String [] getWords() {
      return words;
    }
    
    public void setWords(String [] words) {
      this.words = words;
    }
    
    public String [] getTags() {
      return tags;
    }
    
    public void setTags(String [] tags) {
      this.tags = tags;
    }
    
    public String [] getReason() {
      return reason;
    }
    
    public void setReason(String [] reason) {
      this.reason = reason;
    }
  }

  /**
   * An empty model for training.
   */
  public TagAllocationModel() {
    this.ntw = new SparseCounter<String>();
    this.nw = new Counter<String>();
  }

  /**
   * Read an existing model.
   */
  public TagAllocationModel(InputStream input) throws IOException {
    loadFrom(input);
  }

  public TagAllocationModel(File file) throws IOException {
    FileInputStream input = new FileInputStream(file);
    loadFrom(input);
    input.close();
  }

  private void loadFrom(InputStream input) throws IOException {
    BufferedReader reader =
      new BufferedReader(new InputStreamReader(input, "UTF-8"));
    String line;

    // Load locked.    
    line = reader.readLine();
    if (line == null)
      throw new IOException("number of tags wrong.");
    locked = line.equals("locked");

    // Load alpha, beta, gamma.
    line = reader.readLine();
    if (line == null)
      throw new IOException("number of tags wrong.");
    alpha = new double[2];
    alpha[0] = Double.parseDouble(line);    
    line = reader.readLine();
    if (line == null)
      throw new IOException("number of tags wrong.");
    alpha[1] = Double.parseDouble(line);
    line = reader.readLine();
    if (line == null)
      throw new IOException("number of tags wrong.");
    beta = Double.parseDouble(line);
    line = reader.readLine();
    if (line == null)
      throw new IOException("number of tags wrong.");
    gamma = Double.parseDouble(line);
    line = reader.readLine();
    if (line == null)
      throw new IOException("number of tags wrong.");
    numCombinedIterations = Integer.parseInt(line);
    // Load tag-reason count.
    line = reader.readLine();
    if (line == null)
      throw new IOException("number of tags wrong.");
    int numTags = Integer.parseInt(line);
    ntw = new SparseCounter<String>();   
    for (int i = 0; i < numTags; i++) {
      line = reader.readLine();
      if (line == null)
        throw new IOException("no data for the " + i + "-th tag");
      String [] cols = line.split(" ");
      String tag = cols[0];
      for (int j = 1; j < cols.length; j+=2) {
        long count = Long.parseLong(cols[j+1]);
        ntw.inc(tag, cols[j], count);
      }
    }
    // Load word count.
    line = reader.readLine();
    if (line == null)
      throw new IOException("number of tags wrong.");
    int numWords = Integer.parseInt(line);
    nw = new Counter<String>(); 
    for (int i = 0; i < numWords; i++) {
      line = reader.readLine();
      if (line == null)
        throw new IOException("no data for the " + i + "-th word");
      String [] cols = line.split(" ");
      String word = cols[0];
      long count = Long.parseLong(cols[1]);
      nw.inc(word, count);
    }
    LOG.info("Load " + ntw.rows().size() + " tags and " +
        nw.size() + " words. alpha:" + alpha[0] + "," + alpha[1] +                             
        " beta:" + beta + " gamma:" + gamma);
  }

  public void saveTo(File file) throws IOException {
    FileOutputStream output = new FileOutputStream(file);
    saveTo(output);
    output.close();
  }

  public void saveTo(OutputStream output) throws IOException {
    BufferedWriter writer = 
      new BufferedWriter(new OutputStreamWriter(output, "UTF-8"));
    // Write locked.
    writer.write(locked ? "locked\n" : "unlocked\n");
    // Write alpha, beta and gamma.
    writer.write(Double.toString(alpha[0]));
    writer.write("\n");
    writer.write(Double.toString(alpha[1]));
    writer.write("\n");
    writer.write(Double.toString(beta));
    writer.write("\n");
    writer.write(Double.toString(gamma));
    writer.write("\n");
    writer.write(Integer.toString(numCombinedIterations));
    writer.write("\n");
    // Write ntw.
    Set<String> tags = ntw.rows();
    writer.write(Integer.toString(tags.size()));
    writer.write("\n");
    for (String tag : tags) {
      writer.write(tag);

      Set<String> words = ntw.columns(tag);
      for (String word : words) {
        writer.write(" ");
        writer.write(word);
        writer.write(" ");
        writer.write(Long.toString(ntw.get(tag, word)));
      }
      writer.write("\n");
    }
    // Write nw.
    writer.write(Integer.toString(nw.size()));
    writer.write("\n");
    for (Entry<String, Long> e : nw) {
      writer.write(e.getKey());
      writer.write(" ");
      writer.write(Long.toString(e.getValue()));
      writer.write("\n");
    }
    writer.flush();
  }
  public Counter<String> generateTags(String [] doc, int n) {
    return generateTags(doc, n, null);
  }
  /**
   * Generate a tag according to TAM's generative process. We do not generate
   * via NOISE.
   * @param doc
   * @return the tag.
   */
  public Counter<String> generateTags(
      String [] doc, int n, StringBuilder explain) {
    Map<String, Double> p = new Hashtable<String, Double>();
    Map<String, Double> prwd = new Hashtable<String, Double>();
    Counter<String> nwd = new Counter<String>();
    for (String w : doc) {
      nwd.inc(w, 1);
    }

    // Estimate p(r=w|w_d,c=c_m,beta).
    for (Entry<String, Long> e : nwd) {
      if (!ntw.columns().contains(e.getKey()))
        continue;
      double prw = prw(e.getKey());
      prwd.put(e.getKey(), e.getValue() * prw);
    }

    Counter<String> tags = nwd;  // Use existing Counter class.
    tags.clear();
    boolean warned = true;
    for (int i = 0; i < n; i++) {
      // Sample a reason.
      String reason = sample(prwd);
      if (reason == null ) {
        if (warned == false)
          LOG.info("reason == null, prwd.size()=" + 
              prwd.size() + " doc.length=" + doc.length);
        warned = true;
        continue;
      }
      // Sample a tag.
      p.clear();
      for (String tag : ntw.rows(reason)) {
        p.put(tag, ptr(tag, reason));
      }
      if (p.size() > 0) {
        String tag = sample(p);
        tags.inc(tag, 1);
        if (explain != null) {
          explain.append("from " + reason + " => " + tag + "<br>");
        }
      }
    }

    // Output the result.
    return tags;
  }


  /**
   * The likelihood of a tag in given document.
   * @param doc
   * @param tag
   * @return
   */
  public double likelihood(String [] doc, String tag) {
    double likelihood = 0;
    if (ntw.rowSum(tag) == 0) {
      return likelihood;  // No such tag in the model.
    }
    Counter<String> nwd = new Counter<String>();
    for (String w : doc) {
      nwd.inc(w, 1);
    }
    double norm = 0;
    Map<String, Double> prwd = new Hashtable<String, Double>();
    for (Entry<String, Long> e : nwd) {
      double prw = prw(e.getKey());
      norm += prw;
      prwd.put(e.getKey(), prw);
    }
    for (Entry<String, Double> e : prwd.entrySet()) {
      double prw = e.getValue() / norm;
      likelihood += ptr(tag, e.getKey()) * prw * (1.0 - pcm());
    }
    likelihood += ptr(tag, NOISE) * pcm();
    return likelihood;
  }

  public double inference(Document doc) {
    return inference(doc, null);
  }
  
  public double inference(
      Document doc, Map<String, AnyDoublePair<Integer>> perTagLL) {
    double loglikelihood = 0;
    Map<String, Double> prtd = new Hashtable<String, Double>();
    Counter<String> nwd = new Counter<String>();
    for (String w : doc.words) {
      nwd.inc(w, 1);
    }

    // Estimate p(r=w|w_d,c=c_m,beta).
    Map<String, Double> prwd = new Hashtable<String, Double>();
    double norm = 0;
    for (Entry<String, Long> e : nwd) {
      double p = prw(e.getKey()) * (double)e.getValue();
      norm += p;
      prwd.put(e.getKey(), p);
    }
    for (Entry<String, Double> e : prwd.entrySet()) {
      double p = e.getValue() / norm;
      checkProb(p);
      prwd.put(e.getKey(), p);
    }

    for (int t = 0; t < doc.tags.length; t++) {
      String tag = doc.tags[t];
      double likelihood = 0;
      if (!locked) {
        ntw.inc(doc.tags[t], doc.reason[t], -1);
      }
      // Generate sampling vector (prtd) for words.
      prtd.clear();  
      for (Entry<String, Long> e : nwd) {
        String word = e.getKey();
        double p = ptr(tag, word) * prwd.get(word) * (1 - pcm());
        checkProb(p);
        likelihood += p;
        checkDouble(likelihood);
        prtd.put(word, p);
      }

      // Generate noise sample rate.
      double ptm = ptr(tag, NOISE) * pcm();
      checkProb(ptm);
      likelihood += ptm;
      checkDouble(likelihood);
      prtd.put(NOISE, ptm);

      // Sample.
      doc.reason[t] = sample(prtd);

      if (!locked) {
        ntw.inc(doc.tags[t], doc.reason[t], 1);
      }
      if (perTagLL != null) {
        AnyDoublePair<Integer> v = perTagLL.get(tag);
        if (v == null) {
          v = new AnyDoublePair();
          v.first = 0;
          v.second = 0;
          perTagLL.put(tag, v);
        }
        v.first++;
        v.second += Math.log(likelihood);
      }
      loglikelihood += Math.log(likelihood);
      if (Double.isNaN(loglikelihood)) {
        LOG.warning("NaN! " + likelihood);
      }
    }
    return loglikelihood;
  }

  private void checkProb(double p) {
    if (p < 0 || p > 1) {
      throw new RuntimeException("p = " + p);
    }
  }

  private void checkDouble(double likelihood) {
    if (likelihood <= 0 || likelihood > 1
        || Double.isNaN(likelihood) 
        || Double.isInfinite(likelihood)) {
      throw new RuntimeException("likelihood = " + likelihood);
    }
  }
  
  /**
   * Train the model with given set of documents.
   * @param docs
   * @return A list of likelihoods, of each iteration.
   */
  public List<Double> train(
      DataSource<Document> docs, int numIterations, int numBurnIn) {
    LOG.info("Training start, num_iter:" + numIterations +
        " num_burn_in:" + numBurnIn);
    List<Double> loglikelihoods = new ArrayList<Double>();

    setLocked(false);
    // Initialization.
    long numTagTokens = 0;
    int totalNumDocs = 0;
    for (Document d : docs) {
      initializeDocument(d);
      
      numTagTokens += d.tags.length;
      totalNumDocs++;
    }
    docs.rewind();
    
    LOG.info("Number of tag tokens: " + numTagTokens + " N/R: " + pcm());
    SparseCounter<String> meanNtw = new SparseCounter<String>();
    Counter<String> meanNw = new Counter<String>();
    for (int i = 0; i < numIterations; i++) {
      double l = 0;
      Map<String, AnyDoublePair<Integer>> perTagLikelihood = 
        new Hashtable<String, AnyDoublePair<Integer>>();
      long numDocs = 0;
      for (Document d : docs) {
        l += inference(d, perTagLikelihood);
        numDocs ++;
        if (numDocs % 1000 == 0) {
          System.err.print(
              "Sampling " + (numDocs * 100 / totalNumDocs) + "%  \r");
          System.err.flush();
        }
      }
      docs.rewind();
      l /= numTagTokens;
      
      // Compute macro-average likelihood.
      double pertagl = 0;
      for (Entry<String, AnyDoublePair<Integer>> e : perTagLikelihood.entrySet()) {
        pertagl += e.getValue().second / (double) e.getValue().first;
      }
      pertagl /= perTagLikelihood.size();
      
      LOG.info(
          " No: " + i + 
          " LL: " + String.format("%.4f", l) +
          " PL: " + String.format("%.4f", pertagl) +
          " NR: " + String.format("%.4f", pcm()) + 
          " SP: " + String.format("%.4f", ntw.sparsity()) +
          " NZ: " + ntw.numNonZeroElements());
      if (i > numBurnIn) {
        meanNtw.inc(ntw);
        meanNw.inc(nw);
      }
      loglikelihoods.add(l);
    }

    // Use aggregated result.
    ntw = null;
    ntw = meanNtw;
    nw = null;
    nw = meanNw;
    numCombinedIterations = numIterations - numBurnIn;
    setLocked(true);
    return loglikelihoods;
  }

  public void setLocked(boolean locked) {
    this.locked = locked;
  }

  public boolean getLocked() {
    return locked;
  }

  public double [] getAlpha() {
    return alpha;
  }

  public void setAlpha(double a0, double a1) {
    alpha[0] = a0;
    alpha[1] = a1;
  }

  public double getBeta() {
    return beta;
  }

  public void setBeta(double d) {
    beta = d;
  }

  public double getGamma() {
    return gamma;
  }

  public void setGamma(double d) {
    gamma = d;
  }

  public void setWordLexicon(Lexicon l) {
    wordlex = l;
  }

  public int getNumCombinedIterations() {
    return numCombinedIterations;
  }

  public Set<String> getRelatedTags(String word) {
    return ntw.rows(word);
  }

  public Set<String> getRelatedWords(String tag) {
    return ntw.columns(tag);
  }
  
  public Set<String> getAllTags() {
    return ntw.rows();
  }

  public Set<String> getAllFeatures() {
    return ntw.columns();
  }

  public double ptr(String tag, String reason) {
    long nr = ntw.columnSum(reason);
    double p = (ntw.get(tag, reason) + gamma * numCombinedIterations)
    / (nr + ntw.rows().size() * gamma * numCombinedIterations);
    if (p > 1 || p < 0)
      throw new RuntimeException("ptr=" + p + ":" + tag +"/" + reason + ":"
          + ntw.get(tag, reason) + " " +nw.get(reason));
    return p;
  }

  public double prt(String reason, String tag) {
    long nt = ntw.rowSum(tag); // - ntw.get(tag, NOISE); 
    double p = (ntw.get(tag, reason) + gamma * numCombinedIterations)
    / (nt + ntw.columns().size() * gamma * numCombinedIterations);
    if (p > 1 || p < 0)
      throw new RuntimeException(tag +"/" + reason + ":"
          + ntw.get(tag, reason) + " " +nw.get(reason));
    return p;
  }

  public double pt(String tag) {
    double nt = ntw.rowSum(tag);
    return nt / (double)ntw.total();
  }

  public double pr(String reason) {
    return (double)nw.get(reason) / (double)nw.total();
  }

  public double pcm() {
    return (ntw.columnSum(NOISE) + alpha[0] * numCombinedIterations)
    / (ntw.total() + (alpha[0] + alpha[1]) * numCombinedIterations);
  }

  public double prw(String word) {
    return (ntw.columnSum(word) + beta * numCombinedIterations)
    / (nw.get(word) + nw.size() * beta * numCombinedIterations);
  }

  public long nt(String tag) {
    return ntw.rowSum(tag);
  }

  //////////////////////////////////////////////////////////////////////////////
  // FOLLOWING ARE INTERNAL METHODS.
  // If you just want to use the model, instead of understanding the internal
  // mechanism, you can stop here safely.
  //////////////////////////////////////////////////////////////////////////////
  /**
   * Count of tag|word. NOISE is treated as a word. The key is "NOISE".
   */
  public SparseCounter<String> ntw;
  public Counter<String> nw;
  public Random random = new Random();

  /**
   * Training only. Initialize the document's tag allocation, and add the
   * document's allocation to the global count of the model.
   * @param doc
   */
  public void initializeDocument(Document doc) {
    Map<String, Double> d = new Hashtable<String, Double>();
    double numDocs = wordlex == null ? 0 : wordlex.getNumDocs();
    for (int i = 0; i < doc.tags.length; i++) {
      if (wordlex == null) {
        int rindex = random.nextInt(doc.words.length + 1);
        if (rindex < doc.words.length) {
          doc.reason[i] = doc.words[rindex];
        } else {
          doc.reason[i] = TagAllocationModel.NOISE;
        }
      } else {
        d.clear();
        double sampleWeight = 0;
        for (int j = 0; j < doc.words.length; j++) {
          Word w = wordlex.getWord(doc.words[j]);
          if (w != null) {
            double idf = numDocs / w.getDocumentFrequency();
            d.put(doc.words[j], idf);
            sampleWeight += idf;
          }
        }
        d.put(TagAllocationModel.NOISE, sampleWeight / d.size());
        doc.reason[i] = sample(d);
      }

      if (!locked) { 
        ntw.inc(doc.tags[i], doc.reason[i], 1);
      }
    }
    for (String word : doc.words) {
      nw.inc(word, doc.tags.length);
    }
  }

  public String sample(Map<String, Double> p) {
    double sum = 0;
    for (Entry<String, Double> e : p.entrySet()) {
      sum += e.getValue();
    }
    double r = random.nextDouble() * sum;
    sum = 0;
    String lastKey = null;
    for (Entry<String, Double> e : p.entrySet()) {
      sum += e.getValue();
      if (r < sum)
        return e.getKey();
      lastKey = e.getKey();
    }
    return lastKey;
  }

  public boolean locked = false;

  //////////////////////////////////////////////////////////////////////////////
  // DEBUG METHODS.

  /**
   * Show current model in HTML format.
   */
  public String toTagIndexedString() {
    StringBuilder sb = new StringBuilder();
    // Write the header.
    sb.append("<html><head>");
    sb.append("<meta content='text/html; charset=utf-8' ");
    sb.append("http-equiv='content-type' />");
    sb.append("</head>");
    sb.append("<style>body {font-family:Consolas;}");
    sb.append("span.word {margin-right:3px}</style>");
    sb.append("<body><h1>");
    sb.append("Visualization of TAM ");
    sb.append("</h1>");
    sb.append("<small>alpha:" + alpha[0] + "," + alpha[1] + " beta:" + beta);
    sb.append(" gamma:" + gamma + " p(c=c_m):" + pcm() + "</small>");

    // Sort all tags by frequency.
    double maxTagWeight = 0;
    List<WeightString> rows = new ArrayList<WeightString>();
    for (String tag : ntw.rows()) {
      if (tag.length() > 15)
        continue;
      if (ntw.rowSum(tag) < 10)
        continue;
      // double pn = (double)ntw.rowSum(row);
      double pn = prt(NOISE, tag);
      rows.add(new WeightString(tag, pn));
      if (pn > maxTagWeight)
        maxTagWeight = pn;
    }
    Collections.sort(rows);
    /*
    Collections.sort(rows, new Comparator<WeightString>() {
      @Override
      public int compare(WeightString o1, WeightString o2) {
        return Double.compare(o2.weight, o1.weight);
      }
    });
    */

    // Sort all words for each tag and output.
    List<WeightString> words = new ArrayList<WeightString>();
    for (int i = 0; i < rows.size(); i++) {
      String tag = rows.get(i).text;
      words.clear();
      for (String word : ntw.columns(tag)) {
        if (word.equals(NOISE))
          continue;
        words.add(new WeightString(word, ptr(tag, word)));
      }
      if (words.size() == 0) {
        LOG.info("drop " + tag);
        continue;
      }
      Collections.sort(words, new Comparator<WeightString>() {
        @Override
        public int compare(WeightString o1, WeightString o2) {
          return Double.compare(o2.weight, o1.weight);
        }
      });
      int cutpos = 0;
      double maxWeight = words.get(0).weight;
      for (int j = 0; j < words.size(); j++) {
        if (words.get(j).weight < maxWeight * 0.4)
          break;
        cutpos = j;
      }
      sb.append("<div>{");
      for (int j = cutpos; j >= 0; j--) {
        sb.append("<span class='word' style='");
        sb.append(getWeightedStyle(words.get(j).weight / maxWeight));
        sb.append("'>" + words.get(j).text + "</span>");
      }
      sb.append("} =&gt; ");
      sb.append("<span class='tag' style='");
      sb.append(getWeightedStyle(rows.get(i).weight / maxTagWeight));
      sb.append("'>" + tag + "(" + nt(tag) + " " +
          String.format("%.3f", rows.get(i).weight) + ")</span></div>");
    }
    sb.append("</body></html>");
    return sb.toString();
  }

  /**
   * Show current model in HTML format.
   */
  public String toWordIndexedString() {
    StringBuilder sb = new StringBuilder();
    // Write the header.
    sb.append("<html><head>");
    sb.append("<meta content='text/html; charset=utf-8' ");
    sb.append("http-equiv='content-type' />");
    sb.append("</head>");
    sb.append("<style>body {font-family:Consolas;}");
    sb.append("span.tag {margin-right:3px}</style>");
    sb.append("<body><h1>");
    sb.append("Visualization of TAM ");
    sb.append("</h1>");
    sb.append("<small>alpha:" + alpha[0] + "," + alpha[1] + " beta:" + beta);
    sb.append(" gamma:" + gamma + " p(c=c_m):" + pcm() + "</small>");
    sb.append("<table>");

    // Sort all features by likelihood to be selected.
    List<WeightString> features = new ArrayList<WeightString>();
    for (String feature : ntw.columns()) {
      if (feature.length() > 15)
        continue;
      if (ntw.columnSum(feature) < 10)
        continue;
      if (!feature.equals(NOISE)) {
        double prw = prw(feature);
        features.add(new WeightString(feature, prw));
      } else {
        features.add(new WeightString(NOISE, 1));
      }

    }
    Collections.sort(features, new Comparator<WeightString>() {
      @Override
      public int compare(WeightString o1, WeightString o2) {
        return Double.compare(o2.weight, o1.weight);
      }
    });
    double maxFeatureWeight = features.get(0).weight;
    if (features.get(0).text.equals(NOISE)) {
      maxFeatureWeight = features.get(1).weight;
    }
    
    // Sort all words for each tag and output.
    List<WeightString> tags = new ArrayList<WeightString>();
    for (int i = 0; i < features.size(); i++) {
      String feature = features.get(i).text;
      tags.clear();
      for (String tag : ntw.rows(feature)) {
        tags.add(new WeightString(tag, ptr(tag, feature)));
      }
      Collections.sort(tags, new Comparator<WeightString>() {
        @Override
        public int compare(WeightString o1, WeightString o2) {
          return Double.compare(o2.weight, o1.weight);
        }
      });
      sb.append("<tr>");
      sb.append("<td><span class='feature' style='");
      sb.append(getWeightedStyle(features.get(i).weight / maxFeatureWeight));
      sb.append("'>" + feature + "</span></td><td>");
      double maxWeight = tags.get(0).weight;
      for (int j = 0; j < tags.size() && j < 10; j++) {
        sb.append("<span class='tag' style='");
        sb.append(getWeightedStyle(tags.get(j).weight / maxWeight));
        sb.append("'>" + tags.get(j).text + "</span>");
      }
      sb.append("</td></tr>");
    }
    sb.append("</table></body></html>");
    return sb.toString();
  }


  private String getWeightedStyle(double weight) {
    if (weight > 1)
      weight = 1;
    if (weight < 0)
      weight = 0;
    int background = (int)((1.0 - weight) * 255.0);
    int foreground = weight > 0.1 ? 255 : 178;
    StringBuilder sb = new StringBuilder();
    sb.append("background-color:rgb(");
    sb.append(Integer.toString(background));
    sb.append(",");
    sb.append(Integer.toString(background));
    sb.append(",");
    sb.append(Integer.toString(background));
    sb.append(");color:rgb(");
    sb.append(Integer.toString(foreground));
    sb.append(",");
    sb.append(Integer.toString(foreground));
    sb.append(",");
    sb.append(Integer.toString(foreground));
    sb.append(")");
    return sb.toString();
  }


  public String getHtmlResult(Document d, int numIter) {
    StringBuilder html = new StringBuilder();
    html.append("<div class='result'>");
    html.append("<div class='doc'>");
    // Print the weighted words of the document.
    double [] weights = new double[d.words.length];
    double max = 0;
    for (int i = 0; i < d.words.length; i++) {
      weights[i] = prw(d.words[i]);
      max = (weights[i] > max) ? weights[i] : max;
    }
    for (int i = 0; i < d.words.length; i++) {
      weights[i] /= max;
      String cssColor = getWeightedStyle(weights[i]);
      html.append("<span class='words' style='");
      html.append(cssColor);
      html.append("'>");
      html.append(d.words[i]);
      html.append("</span>");
    }
    html.append("</div>");

    html.append("<div class='reasons'>");
    Counter<String> [] reasonCounters = new Counter[d.tags.length];
    for (int i = 0; i < reasonCounters.length; i++)
      reasonCounters[i] = new Counter<String>();

    for (int i = 0; i < numIter; i++) {
      inference(d);
      for (int j = 0; j < d.tags.length; j++) {
        reasonCounters[j].inc(d.reason[j], 1);
      }
    }
    for (int i = 0; i < d.tags.length; i++) {
      html.append("<div class='tagreasonline'>");
      html.append("<span class='tag'>");
      html.append(d.tags[i]);
      html.append("</span>");
      html.append(getSortedReasons(reasonCounters[i]));
      html.append("</div>");
    }
    html.append("</div>");
    // End of block.
    html.append("</div>");
    return html.toString();
  }

  public String getSortedReasons(Counter<String> c) {
    List<WeightString> reasons = new ArrayList<WeightString>();
    double max = 0;
    for (Entry<String, Long> e : c) {
      reasons.add(new WeightString(e.getKey(), e.getValue()));
      max = (e.getValue() > max) ? e.getValue() : max;
    }
    Collections.sort(reasons, new Comparator<WeightString>() {

      @Override
      public int compare(WeightString o1, WeightString o2) {
        return Double.compare(o2.weight, o1.weight);
      }

    });
    StringBuilder html = new StringBuilder();
    for (WeightString r : reasons) {
      html.append("<span class='word' style='");
      html.append(getWeightedStyle(r.weight / max));
      html.append("'>");
      html.append(r.text);
      html.append("</span>");
    }
    return html.toString();
  }
}
