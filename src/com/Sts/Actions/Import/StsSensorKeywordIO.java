
//Title:        S2S: Seismic-to-simulation
//Version:
//Copyright:    Copyright (c) 2000
//Company:      4D Systems LLC
//Description:  Web-enabled interpretation system

/**
    This class is used to read ASCII well files that use keywords
    that are followed by values
*/

package com.Sts.Actions.Import;

import com.Sts.Actions.Wizards.SensorLoad.*;
import com.Sts.DBTypes.*;
import com.Sts.MVC.*;
import com.Sts.Types.*;
import com.Sts.Utilities.DateTime.*;
import com.Sts.Utilities.*;
import com.Sts.UI.*;

import java.io.*;
import java.util.*;

public class StsSensorKeywordIO extends StsKeywordIO
{
    /** string constants for well file type/class */

    /** keyword constants */
    static public final String WELL_NAME = "WELLNAME";
    static public final String ORIGIN = "ORIGIN";
    static public final String XY = "XY";
    static public final String YX = "YX";
    static public final String CURVE = "CURVE";
    static public final String VALUE = "VALUE";
    static public final String NULL_VALUE = "NULL";
    static public final String WELL_REF = "WELLREF";
    static public final String X = "X";
    static public final String Y = "Y";
    static public final String DEPTH = "DEPTH";
    static public final String MDEPTH = "MDEPTH";
    static public final String SSDEPTH = "SSDEPTH";
    static public final String TIME = "TIME";
    static public final String DUMMY_NAME = "IGNORE";
    static public final String Z = "Z";
    static public final String[] Z_KEYWORDS = { DEPTH, MDEPTH, Z, "DOWN", "DZ", "ZEVT"};
    static public final String[] X_KEYWORDS = { "X", "EAST", "EASTING", "DX", "XEVT"};
    static public final String[] Y_KEYWORDS = { "Y", "NORTH", "NORTHING", "DY", "YEVT" };
    static public final String[] TIME_KEYWORDS = { "TIME", "HOUR", "HMS", "TIMESTAMP", "TIMEVT", "EVENT_TIME" };
    static public final String[] DATE_KEYWORDS = { "DATE", "DAY", "DATEVT", "EVENT_DATE" };
    
    public static final byte NO_TIME = -1;
    public static final byte TIME_AND_DATE = 0;
    public static final byte TIME_ONLY = 1;
    public static final byte ELAPSED_TIME = 2;
    public static final byte TIME_OR_DATE = 3;
    public static byte timeType = TIME_AND_DATE;
    public static long multiStageLimit = 9000000L; // 2.5 hours
    //public static String dateStart = "01-01-71 00:00:00.0";

    static private boolean deleteBinaries = false;
    static public float vScalar = 1.0f;
    static public float hScalar = 1.0f;
    static public byte verticalUnits = StsParameters.DIST_NONE;
    static public byte horizontalUnits = StsParameters.DIST_NONE;
    static public boolean loadMultiStage = false;
    static double[] relativeLocation = new double[] {0.0f, 0.0f, 0.0f };
    static public StsSensorFile sensorFile = null;
    static public SortIndex[] sortIndexes = null;

    static public String getSensorName() { return name; }
    static public String getCurveName() { return subname; }

	static public void initialize(StsModel model)
	{
            ;
    }

    static public boolean checkSort(StsTimeVector timeVector, StsLogVector vector)
    {
        int monotonic =  timeVector.checkMonotonic();
        switch(monotonic)
        {
            case StsLongVector.MONOTONIC_UNKNOWN:
                new StsMessage(null, StsMessage.WARNING,  "Time vector is not monotonically increasing.");
                return false;
            case StsLongVector.MONOTONIC_NOT:
                constructSortIndexes(timeVector);
                sort(timeVector, vector);
                return true;
            case StsLongVector.MONOTONIC_INCR:
                return true;
            case StsLongVector.MONOTONIC_DECR:
                reverse(timeVector, vector);
                return true;
            default:
                return false;
        }
    }

    static public boolean checkSort(StsTimeVector timeVector, StsLogVector[] vectors)
    {
        if(timeVector.getValues().getSize() == 1)
            return true;
        int monotonic =  timeVector.checkMonotonic();
        switch(monotonic)
        {
            case StsLongVector.MONOTONIC_UNKNOWN:
                new StsMessage(null, StsMessage.WARNING,  "Time vector is not monotonically increasing.");
                return false;
            case StsLongVector.MONOTONIC_NOT:
                constructSortIndexes(timeVector);
                sort(timeVector, vectors);
                return true;
            case StsLongVector.MONOTONIC_INCR:
                return true;
            case StsLongVector.MONOTONIC_DECR:
                reverse(timeVector, vectors);
                return true;
            default:
                return false;
        }
    }

    static public void sort(StsTimeVector timeVector, StsLogVector vector)
    {
        Arrays.sort(sortIndexes);
        int nValues = sortIndexes.length;
        float[] sortedVector = new float[nValues];
        float[] unsortedVector = null;
        unsortedVector = vector.getValuesArray();
        long[] times = timeVector.getLongs();
        for(int n = 0; n < nValues; n++)
        {
            int index = sortIndexes[n].index;
            times[n] = sortIndexes[n].time;
            sortedVector[n] = unsortedVector[index];
        }
        vector.setValues(sortedVector);
    }

    static public void sort(StsTimeVector timeVector, StsLogVector[] vectors)
    {
        int nVectors = vectors.length;
        for(int i = 0; i < nVectors; i++)
            sort(timeVector, vectors[i]);
    }

    static public void reverse(StsTimeVector timeVector, StsLogVector vector)
    {
        long[] times = timeVector.getLongs();
        int nValues = times.length;
        float[] vectorValues = null;
        vectorValues = vector.getValuesArray();
        int left  = 0;          // index of leftmost element
        int right = nValues-1; // index of rightmost element

       while (left < right)
       {
          // exchange the left and right elements
          long tempL = times[left];
          times[left]  = times[right];
          times[right] = tempL;

          float[] values = vectorValues;
          float tempF = values[left];
          values[left] = values[right];
          values[right] = tempF;

          // move the bounds toward the center
          left++;
          right--;
       }
    }

    static public void reverse(StsTimeVector timeVector, StsLogVector[] vectors)
    {
        int nVectors = vectors.length;
        for(int i = 0; i < nVectors; i++)
            reverse(timeVector, vectors[i]);
    }

    static public void constructSortIndexes(StsTimeVector timeVector)
    {
        long[] times = timeVector.getLongs();
        int nValues = times.length;
        sortIndexes = new SortIndex[nValues];
        for(int n = 0; n < nValues; n++)
            sortIndexes[n] = new SortIndex(times[n], n);
        return;
    }

    static public boolean checkWriteBinaryFiles(StsTimeVector timeVector, StsLogVector[] vectors, String binaryDataDir)
    {
        int nVectors = vectors.length;
        boolean writeOK = true;
        if(!timeVector.checkWriteBinaryFile(binaryDataDir))
            writeOK = false;
        for(int n = 0; n < nVectors; n++)
        {
        	if(vectors[n].isNull())
        		continue;
            if(!vectors[n].checkWriteBinaryFile(binaryDataDir)) 
            	writeOK = false;
        }
        return writeOK;
    }

     /** check that binary files exist */
      static public boolean binaryFileExist(String binaryDir, String filePrefix, byte type)
      {
          File file;

          try
          {
              String binaryFilename = filePrefix + ".bin." + name + "." + StsLogVector.getStringFromType(type) + "." + version;
              file = new File(binaryDir + File.separator + binaryFilename);
              return file.exists();
          }
          catch (Exception e)
          {
              StsException.outputException("StsSensorKeywordIO.logVectorDatesOK() failed.", e, StsException.WARNING);
              return false;
          }
      }

	  static public void deleteBinaryFiles(String binaryDir, String[] curveNames, String filePrefix)
      {
          	  deleteBinaryFiles(name, binaryDir, curveNames, filePrefix);
      }

	 /** remove all binary log curves associated with this ascii file */
	  static public void deleteBinaryFiles(String name, String binaryDir, String[] curveNames, String filePrefix)
	  {
		  File file;

		  try
		  {
             String binaryFilename = filePrefix + ".bin." + name + ".TIME." + version;
             file = new File(binaryDir + File.separator + binaryFilename);
             file.delete();

             int nNames = curveNames.length;
             for(int n = 0; n < nNames; n++)
             {
                 String curveName = curveNames[n];
                 if(curveName.equals("") || curveName.equalsIgnoreCase("ignore")) continue;
                 binaryFilename = filePrefix + ".bin." + name + "." + curveName + "." + version;
                 file = new File(binaryDir + File.separator + binaryFilename);
                 file.delete();
             }
		  }
		  catch (Exception e)
		  {
			  StsException.outputException("StsSensorKeywordIO.logVectorDatesOK() failed.",  e, StsException.WARNING);
		  }
	  }

      /* read the curve names */
      static public StsTimeVector constructTimeVector(String filePrefix, int timeIdx, int dateIdx)
      {
          try
          {
              String binaryFilename = filePrefix + ".bin." + name + ".TIME." + version;
              StsTimeVector timeVector = new StsTimeVector(filename, binaryFilename, "TIME", version, timeIdx, dateIdx);
              return timeVector;
          }
          catch (Exception e)
          {
              StsException.outputException("StsSensorKeywordIO.constructTimeVector() failed.",
                                           e, StsException.WARNING);
              return null;
          }
      }

    /* read the curve names */
    static public StsLogVector[] constructLogVectors(String[] curveNames, String filePrefix)
    {
        try
        {
            int nNames = curveNames.length;

            StsLogVector[] curveVectors = new StsLogVector[0];
            for(int n = 0; n < nNames; n++)
            {
                String curveName = curveNames[n];
				if(curveName.equals("") || curveName.equalsIgnoreCase("ignore")) continue;
                String binaryFilename = filePrefix + ".bin." + name + "." + curveName + "." + version;
                StsLogVector curveVector = new StsLogVector(filename, binaryFilename, curveName, version, n);
                curveVector.setMinMaxAndNulls(StsParameters.largeFloat);
                curveVectors = (StsLogVector[])StsMath.arrayAddElement(curveVectors, curveVector);
            }

            return curveVectors;
        }
        catch (Exception e)
        {
            StsException.outputException("StsSensorKeywordIO.constructLogVectors() failed.",
                e, StsException.WARNING);
            return null;
        }
    }
    
    static public StsTimeCurve[] readTimeCurves(StsRotatedBoundingBox rBox, String[] filenames, String sensorName, StsSensorFile sensorFile, String dataDir, String binaryDataDir,
            boolean deleteBins, byte vUnits, int dateOrder, String startTime)
    {
    	return StsSensorKeywordIO.readTimeCurves(rBox, filenames, sensorName, sensorFile, dataDir, 
				binaryDataDir, deleteBinaries, vUnits, dateOrder, false, 1, startTime);
    }
    
    static public StsTimeCurve[] readTimeCurves(StsRotatedBoundingBox rBox, String[] filenames, String sensorName, StsSensorFile sensorFile, String dataDir, String binaryDataDir,
                                              boolean deleteBins, byte vUnits, int dateOrder, boolean multiStage, int stageNum, String startTime)
    {
        deleteBinaries = deleteBins;
        verticalUnits = vUnits;
        loadMultiStage = multiStage;
        relativeLocation = new double[] {sensorFile.getRelativeX(), sensorFile.getRelativeY(), sensorFile.getRelativeZ()};
        return readTimeCurves(filenames, sensorName, sensorFile, dataDir, binaryDataDir, dateOrder, stageNum, startTime);
    }

    static public StsTimeCurve[] readTimeCurves(String[] filenames, String sensorName, StsSensorFile sensorFile, String dataDir,
                                                String binaryDataDir, int dateOrder, int stageNum, String startTime)
    {
		StsTimeCurve[] logCurves = new StsTimeCurve[0];
		StsSensorKeywordIO.sensorFile = sensorFile;
        BufferedReader bufRdr = null;
        try
        {
            if(filenames == null) return logCurves;
            int nFilenames = filenames.length;
            if(nFilenames == 0) return logCurves;

			StsLogVector[] logVectors;
            StsTimeVector timeVector;
            for(int n = 0; n < nFilenames; n++)
            {
                filename = filenames[n];
                if(filename == null)
                    continue;
                if(!setParseFilename(filename)) return null;
                bufRdr = new BufferedReader(new FileReader(dataDir + File.separator + filename));
                
                // Handle multi-stage file
                String[] curveNames = sensorFile.curveNames;                
                int linesInStage = sensorFile.getLinesInAllStages();
                if(!loadMultiStage)
                {
                	sensorFile.positionToStage(bufRdr, 1);                	
                }
                else
                {
                	sensorFile.positionToStage(bufRdr, stageNum);
                	linesInStage = sensorFile.getLinesInStage(stageNum);
                	name = name + stageNum;
                }
                
                timeVector = constructTimeVector("sensor", sensorFile.getColLocation(sensorFile.TIME),  sensorFile.getColLocation(sensorFile.DATE));
                logVectors = constructLogVectors(curveNames, "sensor");
                if ((logVectors == null) || (timeVector == null))
                    return null;

                logVectors = readLogVectors(sensorName, binaryDataDir, timeVector, logVectors, bufRdr, curveNames, dateOrder, stageNum, linesInStage, sensorFile.delimiters, startTime);
                if(logVectors == null)
                {
                    StsMessageFiles.logMessage("Failed to construct sensor curves for file: " + filenames[n]);
                }
                else
                {
                    StsTimeCurve[] newLogCurves = StsTimeCurve.constructTimeCurves(timeVector, logVectors, 0);
                    if (newLogCurves.length == 0)
                        continue;
                    logCurves = (StsTimeCurve[])StsMath.arrayAddArray(logCurves, newLogCurves);
                }
            }
			return logCurves;
        }
    	catch (Exception e)
        {
            StsMessageFiles.logMessage("Sensor curve read failed for well " + sensorName);
			return logCurves;
       	}
        finally
        {
            closeBufRdr(bufRdr);
        }
    }
    static public void setTimeType(byte type)
    {
        timeType = type;
    }
    static public StsLogVector[] readLogVectors(String sensorName, String binaryDataDir, StsTimeVector timeVector,
                                                StsLogVector[] logVectors, BufferedReader bufRdr, String[] names,
                                                int dateOrder, int stageNum, int linesInStage, String delimiters, String startTime)
    {
        try
        {
			if(!readCurveValues(bufRdr, binaryDataDir, names, timeVector, logVectors, false, dateOrder, stageNum, linesInStage, delimiters, startTime)) return null;
			return logVectors;
        }
    	catch (Exception e)
        {
            StsMessageFiles.logMessage("Log curve read failed for well " + sensorName);
            return null;
       	}
    }

    /* read the curve values and set the log vectors */
    static private boolean readCurveValues(BufferedReader bufRdr, String binaryDataDir, String[] curveNames,
                                           StsTimeVector timeVector, StsLogVector[] curveVectors, boolean loadValues, 
                                           int dateOrder, int stageNum, int linesInStage, String delimiters, String startTime)
    {
        try
        {
            if(sensorFile.attIndices == null)
                return false;

             deleteBinaryFiles(binaryDataDir, curveNames, StsLogVector.SENSOR_PREFIX);

			if(!readAsciiCurveValues(bufRdr, curveNames, timeVector, curveVectors, loadValues, dateOrder, linesInStage, delimiters, startTime, sensorFile.timeType)) return false;
            checkSort(timeVector, curveVectors);
            return checkWriteBinaryFiles(timeVector, curveVectors, binaryDataDir);
        }
        catch (Exception e)
        {
            StsException.outputException("StsSensorKeywordIO.readCurveValues() failed.", e, StsException.WARNING);
            return false;
        }
    }
/*
    static public String getDateFromString(String format)
    {
    	int firstColon = format.indexOf(":");
    	int lastSpace = format.lastIndexOf(" ");
    	if(firstColon > lastSpace)
    		return format.substring(0,lastSpace);
    	else
    	{
    		String subString = format.substring(0,lastSpace);
    		lastSpace = subString.lastIndexOf(" ");
    		return subString.substring(0,lastSpace);
    	}
    }
*/
    static private boolean readAsciiCurveValues(BufferedReader bufRdr, String[] curveNames, StsTimeVector timeVector,
    						StsLogVector[] curveVectors, boolean loadValues, int dateOrder, int linesInStage, String delimiters, String startTime, byte timeType)
	{
        int xIdx = -1, yIdx = -1, depthIdx = -1;
        double x = 0.0, y = 0.0;
        String timeToken = null, dateToken = null;
        
		try
		{
            // read lines until we hit the eof
            StsLongVector timeValues = null;
            timeValues = new StsLongVector(5000, 2000);
            timeVector.setValues(timeValues);

            int nCurveVectors = curveVectors.length;
            StsFloatVector[] curveValues = new StsFloatVector[nCurveVectors];
            for (int n = 0; n < nCurveVectors; n++)
            {
                curveValues[n] = new StsFloatVector(5000, 2000);
                curveVectors[n].setValues(curveValues[n]);
                if(curveVectors[n].getType() == StsLogVector.X)
                    xIdx = n;
                if(curveVectors[n].getType() == StsLogVector.Y)
                    yIdx = n;
                if(curveVectors[n].getType() == StsLogVector.DEPTH)
                    depthIdx = n;                
            }
            curveVectors[0].getType();
		    String line;
			int nLines = 0;
            boolean originSet = false;
            line = StsSensorFile.readLine(bufRdr);  // First Line in Stage
            while(line != null && nLines < linesInStage)
            {
				if(line == null)
					break;
                line = line.trim();
                line = StsStringUtils.detabString(line);
                line = StsStringUtils.deQuoteString(line);                            
                line = StsStringUtils.cleanLine(line, delimiters);

                StringTokenizer stok = new StringTokenizer(line, delimiters);
				int nTokens = stok.countTokens();
				if(nTokens < nCurveVectors)               // Addresses lines with nothing but commas
				{
					line = StsSensorFile.readLine(bufRdr);  // Next Line					
					continue;
				}
                int tIdx = timeVector.getAsciiFileTimeColumn();
                int dIdx = timeVector.getAsciiFileDateColumn();
                int idx = 0;
                double value = 0.0f;
                long lvalue = 0;
                int addOne = 0;
                dateToken = StsProject.getDateFromString(startTime);
                for(int col = 0; col < nTokens; col++)
                {
                    String token = stok.nextToken();
                    if(tIdx == col)
                    {
                    	timeToken = token;
                    }
                    else if(dIdx == col)
                    {
                    	dateToken = token;
                    }
                    else
                    {
                        //if(isSelectedIndex(sensorFile.attIndices, col + addOne))
                        //{
                            try { value = Double.parseDouble(token); }
                            catch (Exception e)  { value = 0.0f; }

                            if(idx == xIdx)
                                x = value + relativeLocation[0];
                            else if(idx == yIdx)
                                y = value + relativeLocation[1];
                            else if(idx == depthIdx)
                            	curveVectors[idx].append(value + relativeLocation[2]);
                            else
                                curveVectors[idx].append(value);
                            idx++;
                        //}
                        //else
                        //    continue;
                    }
                }
            	lvalue = getTime(timeType, timeToken, dateToken, startTime, dateOrder);
                timeVector.append(lvalue);
                if((xIdx != -1) && (yIdx != -1) && (nTokens != 0))
                {
                    if(!originSet)
                    {
                        curveVectors[xIdx].setOrigin(x);
                        curveVectors[yIdx].setOrigin(y);
                        originSet = true;
                    }
                    curveVectors[xIdx].append(x - curveVectors[xIdx].getOrigin());
                    curveVectors[yIdx].append(y - curveVectors[yIdx].getOrigin());
                }
				if(++nLines%10 == 0) StsMessageFiles.logMessage("File: " + filename + ": " + nLines + " lines read.");
				line = StsSensorFile.readLine(bufRdr);  // Next Line
            }
            StsMessageFiles.logMessage("File: " + filename + ": " + nLines + " lines read.");

            // finish up values: trim, min, max, null
            timeValues.trimToSize();
            timeValues.setMinMax();
            if(timeVector.isNull())
                timeVector.setValues(null);
            for(int n = 0; n < nCurveVectors; n++)
			{
               curveValues[n].trimToSize();
               curveValues[n].setMinMax();
               if(curveVectors[n].isNull())
                   curveVectors[n].setValues((StsFloatVector)null);
            }
            bufRdr.close();
            return true;
        }
        catch (Exception e)
        {
            StsException.outputException("StsSensorKeywordIO.readCurveValues() failed.\n", e, StsException.WARNING);
            return false;
        }
    }
    public static long getTime(byte timeType, String timeToken, String dateToken, String startTime, int dateOrder)
    {
        long start = 0l;
        try
    	{
            Calendar cal = CalendarParser.parse(startTime, dateOrder , true);
            start = cal.getTimeInMillis();
        }
        catch(Exception ex)
        {
            ;
        }
        return getTime(timeType, timeToken, dateToken, start, dateOrder);
    }

    public static long getTime(byte timeType, String timeToken, String dateToken, long start, int dateOrder)
    {
    	String sTime = null;
    	Date date = null;
    	long lvalue = 0L;
        Calendar cal = null;
    	try
    	{
            if(timeType == StsSensorKeywordIO.ELAPSED_TIME)
            {
                lvalue = start + (long)(Double.valueOf(timeToken) * 60000); // Assuming minutes
            }
    		else if((timeType == TIME_OR_DATE) || (timeType == StsSensorKeywordIO.TIME_ONLY))
    		{
    			sTime = dateToken.trim() + " " + timeToken.trim();
                cal = CalendarParser.parse(sTime, dateOrder, true);
                lvalue = cal.getTimeInMillis();
    		}
    		else
    		{
                cal = CalendarParser.parse(timeToken, dateOrder, true);
                lvalue = cal.getTimeInMillis();
    		}
    		return lvalue;
    	}
    	catch(Exception ex)
    	{
    		StsMessageFiles.infoMessage("Unable to parse the time value, setting to 12-31-68 16:00:00");  
    		return lvalue;
    	}		
    }
    
    static public boolean isSelectedIndex(int[] indices, int index)
    {
        for(int i=0; i<indices.length; i++)
            if(indices[i] == index)
                return true;
        return false;
    }
    static public boolean setParseFilename(String _filename)
    {
        filename = _filename;
        if (!parseAsciiFilename(filename))
            return false;
        return true;
    }

    static String cleanDateString(String ds)
    {
        if(ds.length() == 8)
            return ds;
        if(ds.indexOf("-") == 1)
            ds = "0" + ds;
        return ds;
    }

    static String cleanTimeString(String ts)
    {
        // hh:mm:ss.S OR
        // dd-mm-yy hh:mm:ss.S
        if(ts.length() == 21)
            return ts;
        if(ts.indexOf(".") == -1)
            ts = ts + ".0";
        if(ts.indexOf(".") == 7)
            ts = "0" + ts;
        if(ts.indexOf("-") == 1)
            ts = "0" + ts;
        return ts;
    }

    // these are multi-column files with name: group.format.name.version
    static public boolean parseAsciiFilename(String filename)
    {
        File file = new File(filename);
        fileCreationDate = file.lastModified();
        int tokenCount = 0;

        String[] fields = new String[numberFields];
        for(int i=0; i<numberFields; i++)
            fields[i] = "";
        group = ""; format = ""; name = ""; version = 0;
        StringTokenizer sTokens = new StringTokenizer(filename, delimiter);
        while(sTokens.hasMoreTokens())
        {
            fields[tokenCount] = sTokens.nextToken();
            tokenCount++;
            if(tokenCount == numberFields)
                break;
        }
        name = fields[0];
        format = fields[1];
        return true;
    }

    static public boolean isType(String token, String[] keychars)
	{
        for(int i=0; i<keychars.length; i++)
        {
            if(token.equalsIgnoreCase(keychars[i]))
                return true;
        }
        return false;
    }
}

class SortIndex implements Comparable
{
    long time;
    int index;

    SortIndex(long time, int index)
    {
        this.time = time;
        this.index = index;
    }

    public int compareTo(Object o)
    {
        long otherTime = ((SortIndex)o).time;
        return (int)(time - otherTime);
    }
}