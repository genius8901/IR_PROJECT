package Ranker;

import Tuple.MutablePair;
import Tuple.MutableTriple;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.*;

/**
 * this class gets query and return the best
 */
public class Ranker
{
    /***********************************************************************************************
     *                                      Fields                                                 *
     ***********************************************************************************************/
    private final double dAvgDocLength = 182.51711338024444;
    private final double dK1Const      = 1.2;
    private final double dBConst       = 0.5;
    private HashMap<String, MutablePair<double[], String>>         hashMapDocsGrades;
    private RandomAccessFile                                       randomAccessFile;
    private HashMap<String, MutableTriple<Integer[], Float, Long>> dictionary;
    private HashMap<String, MutablePair<String, Long>>             cache;
    /***********************************************************************************************
     *                                      constructors                                                 *
     ***********************************************************************************************/
    public Ranker(HashMap<String, MutablePair<double[], String>> hashMapDocsGrades, HashMap<String, MutableTriple<Integer[], Float, Long>> dictionary, HashMap<String, MutablePair<String, Long>> cache)
    {
        this.hashMapDocsGrades = hashMapDocsGrades;
        this.dictionary = dictionary;
        this.cache = cache;

    }

    /**
     * grade hash map setter
     * @param hashMapDocsGrades
     */
    public void setHashMapDocsGrades(HashMap<String, MutablePair<double[], String>> hashMapDocsGrades)
    {
        this.hashMapDocsGrades = hashMapDocsGrades;
    }

    /**
     * dictionary setter
     * @param dictionary
     */
    public void setDictionary(HashMap<String, MutableTriple<Integer[], Float, Long>> dictionary)
    {
        this.dictionary = dictionary;
    }

    /**
     * cach setter
     * @param cache
     */
    public void setCache(HashMap<String, MutablePair<String, Long>> cache)
    {
        this.cache = cache;
    }


    /**
     * gets Arr of words and a doc grade and return the cosim grade
     * @param wordsGrade
     * @param dDocGrade
     * @return
     */
    public double fnCosin(ArrayList<Double> wordsGrade, double dDocGrade)
    {
        double dUp       = wordsGrade.stream().mapToDouble(d -> d).sum();
        double dQueryGrade = Math.sqrt(wordsGrade.size());
        double dDown       = dDocGrade * dQueryGrade;
        return dUp / dDown;
    }

    /**
     * the main function of the class
     * we get the query as arr and we run for each word and make a calculated filds for all the docs we find.
     * the formula is based of cosim mixed with BM25 algorithms
     * @param query
     * @param iNumReturn
     * @return Pair Arr of document and its grade
     */
    public ArrayList<Map.Entry<String, Double>> fnGetBestDocs(ArrayList<String> query, int iNumReturn){
        HashMap<String,Integer> dupTerm=new HashMap<>();
        for (int i=0;i<query.size();i++){
            String t=query.get(i);
            if(dupTerm.containsKey(t)){
                dupTerm.put(t,dupTerm.get(t)+1);
            }
            else dupTerm.put(t,1);
        }
        HashMap<String,Double>  rankList  =new HashMap<>();
        HashMap<String, Double> rankList2 = new HashMap<>();
        String                  sLine;
        String[]                strings;
        long                    lPointer;
        for(int i=0;i<query.size();i++){// loop for each word in q
            String term=query.get(i);
            if(dupTerm.get(term)==0){ continue;}
            if(!dictionary.containsKey(term))
                continue;
            lPointer = this.dictionary.get(term).getRight();
            if(dictionary.containsKey(term)){
                float termIDF=dictionary.get(term).getMiddle();
                if(cache.containsKey(term)){
                    lPointer=cache.get(term).getRight();
                    sLine=cache.get(term).getLeft();
                    strings = sLine.split("!#");

                    for (int iIndex = 1, iLength = strings.length; iIndex < iLength; iIndex++)//compute docs in cache
                    {
                        String sDocTemp = strings[iIndex];
                        double maxTFi=hashMapDocsGrades.get(sDocTemp).getLeft()[0];
                        iIndex++;
                        int Fi = Integer.parseInt(strings[iIndex]);
                        if(rankList.containsKey(sDocTemp)){
                            double dUp    = Fi * (dK1Const + 1);
                            double dDown  = Fi + dK1Const * (1 - dBConst + dBConst * (this.hashMapDocsGrades.get(sDocTemp).getLeft()[1] / dAvgDocLength));
                            double dGrade = termIDF * (dUp / dDown);
                            //
                            rankList.put(sDocTemp,rankList.get(sDocTemp)+((((double)Fi)/maxTFi)*termIDF)*dupTerm.get(term));
                            rankList2.put(sDocTemp, rankList2.get(sDocTemp) + dGrade*dupTerm.get(term));
                            }
                        else{
                            double dUp    = Fi * (dK1Const + 1);
                            double dDown  = Fi + dK1Const * (1 - dBConst + dBConst * (this.hashMapDocsGrades.get(sDocTemp).getLeft()[1] / dAvgDocLength));
                            double dGrade = termIDF * (dUp / dDown);
                            //
                            rankList.put(sDocTemp, ((((double) Fi) / maxTFi) * termIDF)*dupTerm.get(term));
                            rankList2.put(sDocTemp, dGrade*dupTerm.get(term));

                        }
                    }
                }
                try
                {
                    randomAccessFile.seek(lPointer);
                    sLine = randomAccessFile.readLine();
                    strings = sLine.split("!#");
                    for (int iIndex = 1, iLength = strings.length; iIndex < iLength; iIndex++)//compute docs in posting
                    {
                        String sDocTemp = strings[iIndex];
                        double maxTFi   = hashMapDocsGrades.get(sDocTemp).getLeft()[0];
                        iIndex++;
                        int Fi = Integer.parseInt(strings[iIndex]);
                        if (rankList.containsKey(sDocTemp))
                        {
                            double dUp    = Fi * (dK1Const + 1);
                            double dDown  = Fi + dK1Const * (1 - dBConst + dBConst * (this.hashMapDocsGrades.get(sDocTemp).getLeft()[1] / dAvgDocLength));
                            double dGrade = termIDF * (dUp / dDown);
                            //
                            rankList.put(sDocTemp, rankList.get(sDocTemp) + ((((double) Fi) / maxTFi) * termIDF)*dupTerm.get(term));
                            rankList2.put(sDocTemp, rankList2.get(sDocTemp) + dGrade*dupTerm.get(term));
                        }
                        else
                        {
                            double dUp    = Fi * (dK1Const + 1);
                            double dDown  = Fi + dK1Const * (1 - dBConst + dBConst * (this.hashMapDocsGrades.get(sDocTemp).getLeft()[1] / dAvgDocLength));
                            double dGrade = termIDF * (dUp / dDown);
                            //
                            rankList.put(sDocTemp, ((((double) Fi) / maxTFi) * termIDF)*dupTerm.get(term));
                            rankList2.put(sDocTemp, dGrade*dupTerm.get(term));
                        }
                    }

                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
            dupTerm.put(term,0);
        }
        ArrayList<Map.Entry<String, Double>> cosin = new ArrayList<>(rankList.entrySet());

        for (int i = 0; i < cosin.size(); i++)
        {//normlize the cosim for each doc
            String doc  = cosin.get(i).getKey();
            double docG = hashMapDocsGrades.get(doc).getLeft()[2];
            double docW = cosin.get(i).getValue();
            cosin.get(i).setValue(docW / (docG * Math.sqrt((double) query.size())));
        }
        ArrayList<Map.Entry<String, Double>> ans = new ArrayList<>(rankList.entrySet());
        for (int j = 0, iSize = cosin.size(); j < iSize; j++)
        {
            String doc      = cosin.get(j).getKey();
            double cos      = cosin.get(j).getValue();
            double bm       = rankList2.get(doc);
            double newVluae = 0.9 * bm + 0.1 * cos;
            ans.get(j).setValue(newVluae);
        }
        Collections.sort(ans, new Comparator<Map.Entry<String, Double>>()
        {
            @Override
            public int compare(Map.Entry<String, Double> o1, Map.Entry<String, Double> o2)
            {
                if (o1.getValue() > o2.getValue())
                {
                    return -1;
                }
                if (o1.getValue() < o2.getValue())
                {
                    return 1;
                }
                else
                {
                    return 0;
                }
            }
        });
        if (cosin.size() > iNumReturn)
        {
            ArrayList<Map.Entry<String, Double>> ansRlist2 = new ArrayList<>(ans.subList(0, iNumReturn));
            return ansRlist2;
        }

        return cosin;
    }

    /**
     * initialize the our random access file
     * @param sReadPosting
     */
    public void fnRandomAccessFileInitialize(String sReadPosting)
    {
        File posting = new File(sReadPosting);
        try
        {
            this.randomAccessFile = new RandomAccessFile(posting, "r");
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

    }


}
