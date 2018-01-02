package Model;


import Documnet.Document;
import Indexer.MyIndexer;
import Parse.Parse;
import Parse.Term;
import Ranker.Ranker;
import ReadFile.ReadFile;
import Stemmer.PorterStemmer;
import Tuple.MutablePair;
import Tuple.MutableTriple;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Observable;
import java.util.TreeSet;


/**
 * @author Shaked
 * @since 23-Nov-17
 */
public class MyModel extends Observable
{

    public String                 buildBDInfo;
    public HashMap<String, int[]> mDocInfo;

    private ReadFile  readFile;
    private Parse     parse;
    private MyIndexer indexer;
    private String    sRootPath, sStopWordsPath, sPathForPosting;
    private ArrayList<String> arrayStringFilesPath;
    private PorterStemmer     stemmer;
    private boolean           bToStem;
    private int               iNumOfDocs, iNumOfParts;
    private Ranker ranker;

    /**
     * Constructor. will create a new folder name Posting in the path to keep all the files
     *
     * @param sRootPath       path for the db
     * @param bToStem         do we use stemmer or not
     * @param sPathForPosting path to where we save the posting files
     * @param iNumOfParts     how many parts we divide the db for reading.
     */
    public MyModel(String sRootPath, boolean bToStem, String sPathForPosting, int iNumOfParts)
    {
        this.sRootPath = sRootPath;
        this.readFile = new ReadFile();
        this.parse = new Parse();
        this.bToStem = bToStem;
        this.sPathForPosting = sPathForPosting + "\\Posting";
        String sPathForIndexer = fnCreateFolder();
        this.indexer = new MyIndexer(sPathForIndexer, bToStem);
        this.arrayStringFilesPath = new ArrayList<>();
        this.stemmer = new PorterStemmer();
        this.mDocInfo = new HashMap<>();
        this.iNumOfDocs = 0;
        this.iNumOfParts = iNumOfParts;
        this.ranker = new Ranker(this.indexer.getHashMapDocsGrade());

    }

    /**
     * Build the data base and index from the path we got in the builder.
     * first we will collect all the files paths, and then will send each path to the readFile.
     * after the reading we will send each document to parsing.
     */
    public void fnBuildDB()
    {
        long startTime = System.currentTimeMillis();
        fnCreateFolder();
        fnFindFilesInDB();
        this.parse.setHashSetStopWords(readFile.fnReadStopWords(this.sStopWordsPath));
        int                 iNumOfFiles        = this.arrayStringFilesPath.size(); // Num of files in the db
        int                 iPercent           = iNumOfFiles / this.iNumOfParts;
        ArrayList<Document> listDocumentInFile = new ArrayList<>();
        // Looping over all the files in the db
        for (int iIndex = 0, iItr = 0, iTimeToRead = 0; iIndex < iNumOfFiles; iIndex++, iItr++)
        {
            File fileToRead = new File(this.arrayStringFilesPath.get(iIndex));
            listDocumentInFile.addAll(readFile.fnReadFile(fileToRead)); // All the documents in a file
            if (iItr < iPercent && iTimeToRead < 8)
            {
                continue;
            }

            if ((iTimeToRead == (this.iNumOfParts - 1)) && (iIndex < (iNumOfFiles - 1)))
            {
                continue;
            }

            ArrayList<Term> listOfTerms = new ArrayList<>(); // All the terms in the file

            // Looping over all the documents in each file
            int listDocumentInFileLength = listDocumentInFile.size();
            iNumOfDocs += listDocumentInFileLength;

            for (int iIndex2 = 0; iIndex2 < listDocumentInFileLength; iIndex2++)
            {
                StringBuilder sbTextToParse = listDocumentInFile.get(iIndex2).getText();
                StringBuilder sbDocName     = new StringBuilder(listDocumentInFile.get(iIndex2).getName());
                sbDocName.deleteCharAt(0);
                sbDocName.deleteCharAt(sbDocName.length() - 1);
                MutablePair<ArrayList<Term>, int[]> m = parse.fnParseText1(sbTextToParse, String.valueOf(sbDocName));

                listOfTerms.addAll(m.getLeft());
                mDocInfo.put(String.valueOf(sbDocName), m.getRight());
            }
            if (this.bToStem)
            {
                for (int iIndex3 = 0, iSize = listOfTerms.size(); iIndex3 < iSize; iIndex3++)
                {
                    Term termTemp = listOfTerms.get(iIndex3);
                    if (termTemp.getType() == 1)
                    {
                        String sStemmed = this.stemmer.stemTerm(termTemp.getsName());
                        termTemp.setsName(sStemmed);

                    }
                }
            }

            indexer.fnIndexWords(listOfTerms);

            listDocumentInFile = new ArrayList<>();
            iItr = 0;
            iTimeToRead++;
        }

        indexer.setTotalDocs(iNumOfDocs);
        try
        {
            indexer.fnMergePostings();
            long   endTime   = System.currentTimeMillis();
            double totalTime = (endTime - startTime) / Math.pow(10, 3);
            buildBDInfo = "" + fnCreateEndMessage(totalTime);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        setChanged();
        notifyObservers("index end");
    }


    /**
     * Creates the end message to represent in the gui
     *
     * @param dTotalTime the total running time in seconds
     */
    private StringBuilder fnCreateEndMessage(double dTotalTime)
    {
        StringBuilder sbEndMessage = new StringBuilder("");
        sbEndMessage.append("The number of document indexed is: ").append(iNumOfDocs).append("\n");
        sbEndMessage.append("The size of index in bytes is: ").append(this.indexer.fnGetIndexSize()).append("\n");
        sbEndMessage.append("The size of cache in bytes is: ").append(this.indexer.getCacheSize()).append("\n");
        sbEndMessage.append("The total time is: ").append(dTotalTime).append("\n");
        return sbEndMessage;
    }

    /**
     * Create new folder for saving the index files.
     *
     * @return path for the folder who created
     */
    private String fnCreateFolder()
    {
        File   file = new File(sPathForPosting);
        String sResult;
        if (!file.exists())
        {
            file.mkdir();
        }
        if (this.bToStem)
        {
            sResult = sPathForPosting + "\\Stemmed";
            File file2 = new File(sResult);
            if (!file2.exists())
            {
                file2.mkdir();
            }
            else
            {
                fnClearFolder(file2);
            }
        }
        else
        {
            sResult = sPathForPosting + "\\Non Stemmed";
            File file2 = new File(sResult);
            if (!file2.exists())
            {
                file2.mkdir();
            }
            else
            {
                fnClearFolder(file2);
            }
        }
        return sResult;
    }

    /**
     * this function will get all the files in the path we got in the builder
     */
    private void fnFindFilesInDB()
    {
        File   fileDirToRead = new File(this.sRootPath);
        File[] files         = fileDirToRead.listFiles(); // Use this method to make sure we will not get an npe in listFiles() method
        if (null != files)
        {
            for (int iIndex = 0, filesLength = files.length; iIndex < filesLength; iIndex++)
            {
                File fileEntry = files[iIndex];
                if (null != fileEntry && fileEntry.isDirectory())
                {
                    File[] files2 = fileEntry.listFiles();
                    if (null != files2)
                    {
                        int files2Length = files2.length;

                        for (int i = 0; i < files2Length; i++)
                        {
                            this.arrayStringFilesPath.add(files2[i].getAbsolutePath());
                        }
                    }
                }
                else
                {
                    if (null != fileEntry)
                    {
                        this.sStopWordsPath = fileEntry.getAbsolutePath();
                    }

                }
            }
        }
        else
        {
            System.out.println("The root path you gave is not a folder");
        }
    }

    /**
     * Reset the whole db
     */
    public void fnReset()
    {
        String sPathForIndexer = fnCreateFolder();
        this.indexer.fnResetIndex();
        File file  = new File(this.sPathForPosting);
        File file2 = new File("Resources");
        if (file2.exists())
        {
            File[] files = file2.listFiles();
            if (null != files)
            {
                for (int i = 0, filesLength = files.length; i < filesLength; i++)
                {
                    File file3 = files[i];
                    if (file3.getName().equals("Cache Words") || file.getName().equals("Stemmed Cache Words"))
                    {
                        continue;
                    }
                    file3.delete();
                }
            }

        }
        this.indexer = null;
        System.gc();
        this.indexer = new MyIndexer(sPathForIndexer, bToStem);
    }

    /**
     * Clears the folder from all it's files
     *
     * @param file objects represent the folder
     */
    private void fnClearFolder(File file)
    {
        File[] files = file.listFiles();
        if (files != null)
        {
            if (files.length != 0)
            {
                for (int iIndex = 0, iSize = files.length; iIndex < iSize; iIndex++)
                {
                    File file2 = files[iIndex];
                    if (!file2.isDirectory())
                    {
                        file2.delete();
                    }
                    else
                    {
                        fnClearFolder(file2);
                    }
                }
            }
        }
    }

    /**
     * Set the boolean true if we need to use stemmer and false if we don't need to use stemmer
     *
     * @param bToStem to stem or not to stem
     */
    public void setbToStem(boolean bToStem)
    {
        this.bToStem = bToStem;
        this.indexer.setbToStem(bToStem);
    }

    /**
     * Save the Dictionary and Cache in a specific path
     *
     * @param sPathForObjects where to save the files
     */
    public void fnSaveDicAndCache(String sPathForObjects)
    {
        this.indexer.fnWriteDicAndCache(sPathForObjects);
    }

    /**
     * Load the dictionary and cache from a path.
     * The files needed to be in specific form and in specific name.
     *
     * @param sPathForObjects from where to load the files
     */
    public void fnLoadObjects(String sPathForObjects)
    {
        this.indexer.setPathForObjects(sPathForObjects);
        if (this.sPathForPosting.equals(""))
        {
            File file = new File(sPathForObjects + "cacheStemmed");
            if (file.exists())
            {
                this.bToStem = true;
                this.indexer.setbToStem(true);
            }
            else
            {
                this.bToStem = false;
                this.indexer.setbToStem(false);
            }
        }
        this.indexer.fnReadCache(sPathForObjects);
        this.indexer.fnReadDictionary(sPathForObjects);
        this.indexer.fnReadDocsGrades(sPathForObjects);
    }


    /**
     * Save the cache in text file under the Resources folder for show on screen.
     */
    public void fnShowCache()
    {
        File file = new File("Resources\\Cache.txt");
        if (!file.exists())
        {
            try
            {
                file.createNewFile();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }

        BufferedWriter bw = null;
        try
        {
            bw = new BufferedWriter(new FileWriter(file));
            this.indexer.fnWriteCache(bw);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        finally
        {
            if (bw != null)
            {
                try
                {
                    bw.close();
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Save the dictionary under the Resources folder for show on screen
     */
    public void fnShowDictionary()
    {
        HashMap<String, MutableTriple<Integer[], Float, Long>> dictionary = this.indexer.getDictionary();
        TreeSet<String>                                        treeSet    = new TreeSet<>(dictionary.keySet());

        File file = new File("Resources\\Dictionary.txt");
        if (!file.exists())
        {
            try
            {
                file.createNewFile();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }

        BufferedWriter bw = null;
        try
        {
            bw = new BufferedWriter(new FileWriter(file));
            for (String sTerm : treeSet)
            {
                MutableTriple<Integer[], Float, Long> triple = dictionary.get(sTerm);
                bw.write(sTerm + ", " + triple.getLeft()[1]);
                bw.newLine();
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        finally
        {
            if (null == bw)
            {
                try
                {
                    bw.close();
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        }
    }

    public void fnSetPostingFileReader(String sReadPosting)
    {
        this.ranker.fnRandomAccessFileInitialize(sReadPosting);
    }


}
