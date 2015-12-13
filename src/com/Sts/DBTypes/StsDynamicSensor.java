//Title:        S2S: Seismic-to-simulation
//Version:
//Copyright:    Copyright (c) 2001
//Author:       Tom Lasseter
//Company:      4D Systems LLC
//Description:  Web-enabled Integrated Interpretation System

package com.Sts.DBTypes;

import com.Sts.Actions.Wizards.MakeMovie.StsMakeMovieCreate;
import com.Sts.Interfaces.StsTreeObjectI;
import com.Sts.Interfaces.StsViewSelectable;
import com.Sts.MVC.*;
import com.Sts.MVC.View3d.*;
import com.Sts.Types.*;
import com.Sts.Types.PreStack.*;
import com.Sts.UI.StsMenuItem;
import com.Sts.Utilities.Shaders.*;
import com.Sts.Utilities.*;
import com.magician.fonts.GLHelvetica18BitmapFont;

import javax.media.opengl.*;
import javax.swing.*;
import java.io.*;
import java.util.Date;

public class StsDynamicSensor extends StsSensor implements StsViewSelectable, StsTreeObjectI
{
    /** Associated Perforation */
    StsPerforationMarker marker = null;

    /** Array contianing depth values */
    transient float[] depthFloats = null;
    /** Array containing X values */
    transient float[] xFloats = null;
    /** Array containing Y values */
    transient float[] yFloats = null;
	/** triangulatedFractures constructed from event points */
	transient StsTriangulatedFracture fracture;
	/** index of first point included in fracture */
	transient int firstFracturePoint;
	/** index of last point included in fracture */
	transient int lastFracturePoint = -1;

    static public final String INITIALIZE_DISPLAY = "InitializeDisplay";
    static public final String DO_DISPLAY = "DoDisplay";

    static public boolean runTimers = false;
    static StsTimer initializeTimer;
    static StsTimer displayTimer;

    /** default constructor */
    public StsDynamicSensor()
    {
        super();
    }

    public StsDynamicSensor(StsWell well, String name)
    {
        super(well, name);
        displayType = SHOW_ALL;
    }

    static public StsDynamicSensor nullSensorConstructor(String name)
    {
        return new StsDynamicSensor(null, name);
    }

    /**
     * Compute the points that are in view in preparation for export.
     */
    public void computePointsInView()
    {
        if(!verifyDisplayable()) return;

        // Temporarily ignore settings for duration and type if user is interactively selecting a region on a time series plot.
        long duration = this.displayDuration;
        byte type = this.displayType;
        if((currentModel.getProject().getProjectTimeDuration() != 0) && (!clustering))
        {
            duration = currentModel.getProject().getProjectTimeDuration();
            type = SHOW_INCREMENT;
        }

        if(colorscale == null) super.initializeColorscale();
        if(!checkLoadVectors())
        	return;

        int points = 0;
        try
        {
            float[] xyz;
            float[] xy;

            // If exporting the sensors in view, allocate array
            int nExportPoints = 0;
            exportPoints = new int[timeLongs.length];

            for (int i = 0; i < timeLongs.length; i++)
            {
                if((handEdits != null) && !getSensorClass().getShowDeleted())
                {
                    if(handEdits[i] == 0)
                        continue;
                }
                // Determine the xyz values.
                float zValue = depthFloats[i] + zShift;

                if(zDomainOriginal != currentModel.getProject().getZDomain())
                {
                    if(zDomainOriginal == StsProject.TD_DEPTH)
                        zValue = (float) currentModel.getProject().velocityModel.getT(xFloats[i], yFloats[i], zValue, 0.0f);
                    else
                        zValue = (float) currentModel.getProject().velocityModel.getZ(xFloats[i], yFloats[i], zValue);
                }
                xy = getRotatedPoint(xFloats[i], yFloats[i]);
                xyz = new float[] { xy[0], xy[1], zValue};

                if(getDynamicSensorClass().getEnableTime())
                {
                    // Check if before current time
                    if(!checkTime(timeLongs, i, points, duration, type) && !clustering)
                	    continue;
                    // Check if in range
                    if(!checkValue(timeLongs[i]) && !clustering)
                	    continue;
                }

                if(!checkZLimit(xyz) && !clustering)
                	continue;

                if(!clustering || (clusters == null))
            		exportPoints[nExportPoints++] = i;
                else
                {
                	if(clusters[i] != -1)
                		exportPoints[nExportPoints++] = i;
                }
            }
            exportPoints = (int[])StsMath.trimArray(exportPoints, nExportPoints);
        }
        catch(Exception e)
        {
            StsException.outputException("StsSensor.display failed.", e, StsException.WARNING);
            return;
        }
    }

    public void setType(byte type)
    {
        if(type == StsSensorClass.EVENTS)
        {
            setDisplayTypeString(SYMBOL_TYPE_STRINGS[SPHERE]);
            setIsVisible(true);
        }
        else if(type == StsSensorClass.GPS)
        {
            setDisplayTypeString(SYMBOL_TYPE_STRINGS[DISK]);
            setColorBy(findColorVectorByName("Gray"));
            setBornDeathWithVector();                        
            setIsVisible(true);
        }
    }

    /**
     * Get points with real world coordinates for all the events that are in the cluster array.
     * @return double arrays with ral world positions of the events in the cluster array
     */
    public double[][] getClusteredXYZPoints(int idx)
    {
        double[][] points;

        if(!checkLoadVectors()) return null;

        try
        {
            double dXOrigin = (xOrigin - currentModel.getProject().getXOrigin());
            double dYOrigin = (yOrigin - currentModel.getProject().getYOrigin());

            if(!getClustering())
            {
                int nPoints = xFloats.length;
                points =  new double[nPoints][3];
                for(int i = 0; i < nPoints; i++)
        		{
                    points[i][0] = xFloats[i] + dXOrigin;
                    points[i][1] = yFloats[i] + dYOrigin;
                    points[i][2] = depthFloats[i];
        		}
            }
            else
        	{
                int cnt = 0;
                int nPoints = xFloats.length;
                points = new double[nPoints][3];
                for(int i = 0; i < nPoints; i++)
        		{
        			if(clusters[i] == idx)
        			{
        				points[cnt][0] = xFloats[i] + dXOrigin;
        				points[cnt][1] = yFloats[i] + dYOrigin;
        				points[cnt][2] = depthFloats[i];
                        cnt++;
                    }
        		}
                if(cnt < nPoints)
                {
                    double[][] newPoints = new double[cnt][];
                    System.arraycopy(points, 0, newPoints, 0, cnt);
                    points = newPoints;
                }
        	}
            return points;
        }
        catch(Exception e)
        {
            StsException.outputWarningException(this, "getClusteredXYZVectors", e);
            return null;
        }
    }

    /**
     * Display the sensor in 2D view
     * @param glPanel3d - Graphics handle
     * @param dirNo - X, Y or Z slice
     * @param dirCoordinate - Current slice position
     * @param axesFlipped - Is the axis flipped
     * @param xAxisReversed - Is the x axis reversed
     * @param yAxisReversed - Is the y axis reversed
     */
    public void display2d(StsGLPanel3d glPanel3d, int dirNo, float dirCoordinate, boolean axesFlipped, boolean xAxisReversed, boolean yAxisReversed)
    {
    	StsColor aColor = getDynamicSensorClass().getAccentColor();
        float aScale = getDynamicSensorClass().getAccentScale();

        if(!verifyDisplayable())
        	return;

        // Temporarily ignore settings for duration and type if user is interactively selecting a region on a time series plot.
        long duration = this.displayDuration;
        byte type = this.displayType;
        if((currentModel.getProject().getProjectTimeDuration() != 0) && (!clustering))
        {
            duration = currentModel.getProject().getProjectTimeDuration();
            type = SHOW_INCREMENT;
        }

        if(colorscale == null) super.initializeColorscale();
        if(!checkLoadVectors())
        	return;
        int points = 0;

        GL gl = glPanel3d.getGL();
        glPanel3d.setViewShift(gl, StsGraphicParameters.edgeShift);

        try
        {
            float[] xyz = null;
            float[] xy = null;

            float sizeFloat, colorFloat;

            // If exporting the sensors in view, allocate array
            int nExportPoints = 0;
            if(exportView)
            	exportPoints = new int[timeLongs.length];

            for (int i = 0; i < timeLongs.length; i++)
            {
               if((handEdits != null) && !getSensorClass().getShowDeleted())
               {
                   if(handEdits[i] == 0)
                       continue;
               }
               if(colorFloats == null)
                   colorFloat = 1;
               else
                   colorFloat = colorFloats[i];

               float zValue = depthFloats[i] + zShift;
               float xValue = xFloats[i] + xShift;
               float yValue = yFloats[i] + yShift;
               xyz = getXYZValue(xValue, yValue, zValue);

                if(getDynamicSensorClass().getEnableTime())
                {
                    // Check if before current time
                    //if(!checkTime(timeLongs, i, points, duration, type) && !clustering)
                    if(!checkTime(timeLongs, i, points, duration, type))
                	    continue;
                    // Check if in range
                    if(!checkValue(timeLongs[i]))
                    //if(!checkValue(timeLongs[i]) && !clustering)
                	    continue;
                }
                points++;

                //if(!checkZLimit(xyz) && (!clustering))
                if(!checkZLimit(xyz))
               	   continue;

                float[] pt2d = new float[2];
                switch (dirNo)
                {
                    case 1:                    // Y
                        pt2d[0] = xyz[0];
                        pt2d[1] = xyz[2];
                        break;
                    case 0:                    // X
                        pt2d[0] = xyz[1];
                        pt2d[1] = xyz[2];
                        break;
                    case 2:
                        pt2d[0] = xyz[0];
                        pt2d[1] = xyz[1];
                        break;
                }

                // Determine the size of the symbol
                if(sizeFloats == null)
                    sizeFloat = getNumberBins();
                else
                    sizeFloat = computeSize(scaleMin, scaleMax, sizeFloats[i]);
                sizeFloat *= 2;

                final StsColor color = super.defineColor(colorFloat);
                StsSensorClass sensorClass = (StsSensorClass)currentModel.getCreateStsClass("com.Sts.DBTypes.StsSensor");
                if(!clustering || (clusters == null))
                {
                	super.draw2DPoint(pt2d, color, sizeFloat, getDynamicSensorClass().getScale2D(), glPanel3d, gl);
            		if(exportView)
            			exportPoints[nExportPoints++] = i;
                }
                else
                {
                	if(clusters[i] != -1)
                	{
                		if(clusters[i] != 99)
                			super.draw2DPoint(pt2d, StsColor.colors32[clusters[i]%32], sizeFloat, sensorClass.getScale2D(), glPanel3d, gl);
                		else
                			super.draw2DPoint(pt2d, color, sizeFloat, sensorClass.getScale2D(), glPanel3d, gl);
                		if(exportView)
                			exportPoints[nExportPoints++] = i;
                	}
                }
                float aSize = sizeFloat * aScale;
                if(aSize < sizeFloat)
                    aSize = sizeFloat + 0.05f;                
                accentEvents(i, getDynamicSensorClass(), glPanel3d, gl, pt2d, aSize, sizeFloat, aColor, TWO_D);
            }
            if(exportView)
            {
            	exportPoints = (int[])StsMath.trimArray(exportPoints, nExportPoints);
            	exportView = false;
            }
        }
        catch(Exception e)
        {
            StsException.outputException("StsSensor.display failed.", e, StsException.WARNING);
            return;
        }
        finally
        {
            glPanel3d.resetViewShift(gl);
        }
    }

    /**
     * Display sensor events in 3D
     * @param glPanel3d - Graphics panel
     * @param displayName - Name of the display
     */
    public void display(StsGLPanel3d glPanel3d, boolean displayName)
    {
        display(glPanel3d, displayName, false);
    }

    /**
     * display sensor events in 3d
     * @param gl - Graphics handle
     * @param glPanel - Graphics panel
     */
    public void pick(GL gl, StsGLPanel glPanel)
    {
        display((StsGLPanel3d)glPanel, true, true);
    }

    public float[] getZfromT(float[] xyt)
    {
        float[] xyz = new float[3];
        checkLoadVectors();
        xyz[0] = xyt[0];
        xyz[1] = xyt[1];
        xyz[2] = (float) currentModel.getProject().velocityModel.getZ(xyt[0], xyt[1], xyt[2]);
        return xyz;
    }
    public float getZorT(int i)
    {
        checkLoadVectors();
        float zValue = depthFloats[i] + zShift;
        try
        {
            if(zDomainOriginal != currentModel.getProject().getZDomain())
            {
                if(zDomainOriginal == StsProject.TD_DEPTH)
                    return (float) currentModel.getProject().velocityModel.getT(xFloats[i] + xShift, yFloats[i] + yShift, zValue, 0.0f);
                else
                    return (float) currentModel.getProject().velocityModel.getZ(xFloats[i] + xShift, yFloats[i] + yShift, zValue);
            }
        }
        catch(Exception ex)
        {
            StsMessageFiles.errorMessage("Error computing ZorT value for sensor(" + getName() + ")");
            return StsParameters.nullValue;
        }
        return zValue;
    }
    /**
     * Display Sensor in 3D
     * @param glPanel3d - Graphics panel
     * @param displayName - Name of the display
     * @param pick - Is picking enabled
     */
    public void display(StsGLPanel3d glPanel3d, boolean displayName, boolean pick)
    {
        if(!verifyDisplayable()) return;

        if(!checkLoadVectors()) return;

        if(runTimers && initializeTimer == null)
        {
            setupDebugTimers();
            initializeTimer.start();
        }

        GL gl = glPanel3d.getGL();

        // Temporarily ignore settings for duration and type if user is interactively selecting a region on a time series plot.
        long duration = this.displayDuration;
        byte type = this.displayType;
        if((currentModel.getProject().getProjectTimeDuration() != 0) && (!clustering))
        {
            duration = currentModel.getProject().getProjectTimeDuration();
            type = SHOW_INCREMENT;
        }

        if(colorscale == null) super.initializeColorscale();

        int nPoint = 0;
        try
        {
            float[] xyz, dsr;
            float[] xy;
            glu = glPanel3d.getGLU();
            float sizeFloat, colorFloat;
            StsColor aColor = getDynamicSensorClass().getAccentColor();
            float aScale = getDynamicSensorClass().getAccentScale();

            // If exporting the sensors in view, allocate array
            int nExportPoints = 0;
            if(exportView)
            {
            	System.out.println("Allocating points");
            	exportPoints = new int[timeLongs.length];
            }
            if(runTimers) initializeTimer.stopAccumulate();

            for (int i = 0; i < timeLongs.length; i++)
            {
                if(runTimers) initializeTimer.start();
                if((handEdits != null) && !getSensorClass().getShowDeleted())
                {
                    if(handEdits[i] == 0)
                    {
                        if(runTimers) initializeTimer.stopAccumulateIncrementCount();
                        continue;
                    }
                }

                if(runTimers) initializeTimer.start();
                if(colorFloats == null)
                    colorFloat = 1;
                else
                    colorFloat = colorFloats[i];

                // Determine the xyz values.
                float zValue = getZorT(i);
                /*
                float zValue = depthFloats[i] + zShift;

                if(zDomainOriginal != currentModel.getProject().getZDomain())
                {
                    if(zDomainOriginal == StsProject.TD_DEPTH)
                        zValue = (float) currentModel.getProject().velocityModel.getT(xFloats[i] + xShift, yFloats[i] + yShift, zValue, 0.0f);
                    else
                        zValue = (float) currentModel.getProject().velocityModel.getZ(xFloats[i] + xShift, yFloats[i] + yShift, zValue);
                }
                */
                xy = getRotatedPoint(xFloats[i] + xShift, yFloats[i] + yShift);
                xyz = new float[] { xy[0], xy[1], zValue };

                if(pointType == BEACHBALL)
                    dsr = new float[] {dipFloats[i], strikeFloats[i], rakeFloats[i]};
                else
                    dsr = new float[] {0.0f, 0.0f, 0.0f};

                if(getDynamicSensorClass().getEnableTime())
                {
                    // Check if before current time
                    //if(!checkTime(timeLongs, i, nPoint, duration, type) && !clustering)
                    if(!checkTime(timeLongs, i, nPoint, duration, type))
                    {
                        if(runTimers) initializeTimer.stopAccumulateIncrementCount();
                        continue;
                    }

                    // Check if in range
                    // if(!checkValue(timeLongs[i]) && !clustering)
                    if(!checkValue(timeLongs[i]))
                    {
                        if(runTimers) initializeTimer.stopAccumulateIncrementCount();
                        continue;
                    }
                }
                nPoint++;

                //if(!checkZLimit(xyz) && !clustering)
                if(!checkZLimit(xyz))
                {
                    if(runTimers) initializeTimer.stopAccumulateIncrementCount();
                	continue;
                }

                // Determine the size of the symbol
                if(sizeFloats == null)
                    sizeFloat = getNumberBins();
                else
                    sizeFloat = computeSize(scaleMin, scaleMax, sizeFloats[i]);

                if(runTimers) initializeTimer.stopAccumulateIncrementCount();
                if(runTimers) displayTimer.start();

                if(pick) gl.glPushName(i);
                final StsColor color = defineColor(colorFloat);

                if(!clustering || (clusters == null))
                {
                	draw3DPoint(glPanel3d, xyz, dsr, sizeFloat, color, false);
            		if(exportView)
            			exportPoints[nExportPoints++] = i;
                }
                else
                {
                	if(clusters[i] != -1)
                	{
                		if(clusters[i] != 99)
                			draw3DPoint(glPanel3d, xyz, dsr, sizeFloat, StsColor.colors32[clusters[i]%32], false);
                		else
                			draw3DPoint(glPanel3d, xyz, dsr, sizeFloat, color, false);
                		if(exportView)
                			exportPoints[nExportPoints++] = i;
                	}
                }

                float aSize = numberBins*aScale;
                if(aSize < sizeFloat)
                    aSize = sizeFloat + 0.05f;

                accentEvents(i, getDynamicSensorClass(), glPanel3d, gl, xyz, aSize, sizeFloat, aColor, THREE_D);

                if(pick) gl.glPopName();
                if(runTimers)
                {
                    gl.glFlush();
                    gl.glFinish();
                    displayTimer.stopAccumulateIncrementCount();
                }
            }
			// all this has been replaced with the new simple design and I don't have the time to figure out how to use this old illogic, fuck off,
			// so we are going thru the time vector AGAIN and figuring out the first and last to be drawn for the fracture..
			int firstPoint, lastPoint;
			for (firstPoint = 0; firstPoint < timeLongs.length; firstPoint++)
				if(checkTime(timeLongs, firstPoint, nPoint, duration, type)) break;
			for (lastPoint = timeLongs.length-1; lastPoint > 0; lastPoint--)
				if(checkTime(timeLongs, lastPoint, nPoint, duration, type)) break;

            drawHighlightedPoints(glPanel3d);
            if(exportView)
            {
            	exportPoints = (int[]) StsMath.trimArray(exportPoints, nExportPoints-1);
            	exportView = false;
            	System.out.println("# of points in view is " + nExportPoints);
            }
        }
        catch(Exception e)
        {
            StsException.outputException("StsSensor.display failed.", e, StsException.WARNING);
            return;
        }
        finally
        {
            StsShader.disableCurrentShader(gl);
            if(pointType != BEACHBALL) return;
            drawBeachballAxes(glPanel3d);
        }
    }

    private void setupDebugTimers()
    {
        initializeTimer = StsTimer.constructor(INITIALIZE_DISPLAY);
        StsSumTimer.addTimer(initializeTimer);
        displayTimer = StsTimer.constructor(DO_DISPLAY);
        StsSumTimer.addTimer(displayTimer);
    }

    /**
     * check and load the positioning, time and required attribute arrays.
     * @return - success
     */
    public boolean checkLoadVectors()
    {
        if(timeCurves == null)
            return false;
        if(timeCurves.getSize() == 0)
            return false;

        colorFloats = null;
        sizeFloats = null;

        if(((StsTimeCurve)timeCurves.getElement(0)).getNumValues() == 0)
            return true;  // Realtime will be zero initially.

        StsTimeCurve curve = getTimeCurve(StsLogVector.X);
        if(curve == null)
        {
            StsMessageFiles.errorMessage("This sensor (" + this.getName() + ") appears to be static but is attempting to load XYZ vectors. If realtime, redefine the monitor object and select static sensor option.");
            return false;
        }
        xFloats = curve.getValuesVectorFloats();
        yFloats = getTimeCurve(StsLogVector.Y).getValuesVectorFloats();
        depthFloats = getTimeCurve(StsLogVector.DEPTH).getValuesVectorFloats();
        timeLongs = ((StsTimeCurve)timeCurves.getElement(0)).getTimeVectorLongs();
        sizeFloats = getPropertyAsFloats();
        colorFloats = getColorByAsFloats();

        return super.checkLoadVectors();
    }

	protected void displayPolygons(StsGLPanel3d glPanel3d)
	{
		int nPoint = 0;
		long duration = this.displayDuration;
		byte type = this.displayType;
		int firstPoint, lastPoint;
		for (firstPoint = 0; firstPoint < timeLongs.length; firstPoint++)
			if(checkTime(timeLongs, firstPoint, nPoint, duration, type)) break;
		for (lastPoint = timeLongs.length-1; lastPoint > 0; lastPoint--)
			if(checkTime(timeLongs, lastPoint, nPoint, duration, type)) break;

		if(fracture == null || firstPoint != firstFracturePoint || lastPoint != lastFracturePoint)
		{
			firstFracturePoint = firstPoint;
			lastFracturePoint = lastPoint;
			int nFracturePoints = lastFracturePoint - firstFracturePoint + 1;
            if(nFracturePoints < 2) return;
			double[][] fracturePoints = new double[nFracturePoints][3];
			double dXOrigin = (xOrigin - currentModel.getProject().getXOrigin());
			double dYOrigin = (yOrigin - currentModel.getProject().getYOrigin());
			for(int n = firstFracturePoint, i = 0; n <= lastFracturePoint; n++, i++)
			{
				fracturePoints[i][0] = xFloats[n] + dXOrigin;
				fracturePoints[i][1] = yFloats[n] + dYOrigin;
				fracturePoints[i][2] = depthFloats[n];
			}
			fracture = StsTriangulatedFracture.constructor(fracturePoints, name, StsColor.RED);
		}
		fracture.displayPolygons(glPanel3d);
	}

    /**
     * Get the positioning vectors
     * @return
     */
    public float[][] getXYZVectors()
    {
        if(!checkLoadVectors()) return null;

        try
        {
            return new float[][] { xFloats, yFloats, depthFloats};
        }
        catch(Exception e)
        {
            StsException.outputWarningException(this, "getXYZDoubles", e);
            return null;
        }
    }

	public double[][] getXYZDoublePoints()
	{
		if(!checkLoadVectors()) return null;

		try
		{
			double dXOrigin = (xOrigin - currentModel.getProject().getXOrigin());
			double dYOrigin = (yOrigin - currentModel.getProject().getYOrigin());

			int nPoints = xFloats.length;
			double[][] points = new double[nPoints][3];
			for(int n = 0; n < nPoints; n++)
			{
				double[] point = points[n];
				point[0] = xFloats[n] + dXOrigin;
				point[1] = yFloats[n] + dYOrigin;
				point[2] = depthFloats[n];
			}
			return points;
		}
		catch(Exception e)
		{
			StsException.outputWarningException(this, "getXYZDoubles", e);
			return null;
		}
	}

    /**
     * Determine if this sensor is displayable. May not be displayable because it does not support
     * the current domain or it is turned off.
     * @return
     */
    public boolean verifyDisplayable()
    {
        if(!super.verifyDisplayable())
            return false;
        if(!getDynamicSensorClass().getEnable())
            return false;
        //if(processing)
        //    return false;
        return true;
    }

    /**
     * Get the positioning vectors for all the events that are in the cluster array.
     * @return float arrays with positions of the events in the cluster array
     */
    public float[][] getClusteredXYZVectors()
    {
    	int cnt = 0;
        if(!checkLoadVectors()) return null;

        float[] xVals = new float[xFloats.length];
        float[] yVals = new float[xFloats.length];
        float[] zVals = new float[xFloats.length];
        try
        {
        	if(!getClustering())
        		return new float[][] { xFloats, yFloats, depthFloats};
        	else
        	{
        		for(int i=0; i<xFloats.length; i++)
        		{
        			if(clusters[i] != -1)
        			{
        				xVals[cnt] = xFloats[i];
        				yVals[cnt] = yFloats[i];
        				zVals[cnt++] = depthFloats[i];
        			}
        		}
        		xVals = (float[])StsMath.trimArray(xVals, cnt);
        		yVals = (float[])StsMath.trimArray(yVals, cnt);
        		zVals = (float[])StsMath.trimArray(zVals, cnt);
        		return new float[][] { xVals, yVals, zVals };
        	}
        }
        catch(Exception e)
        {
            StsException.outputWarningException(this, "getClusteredXYZVectors", e);
            return null;
        }
    }

    /**
     * Export the sensor events in view
     * @param file - output file
     * @param outputAtts - selected attributes to output
     * @return number of events exported
     */
    public int exportSensorsInView(File file, StsObject[] outputAtts)
    {
    	computePointsInView();
        return super.exportSensorsInView(file, outputAtts);
    }
     /**
     * Get the sensor class containing this object
     * @return
     */
    static public StsDynamicSensorClass getDynamicSensorClass()
    {
        return (StsDynamicSensorClass)currentModel.getCreateStsClass(StsDynamicSensor.class);
    }
    /**
     * Convert the coordinates and attributes to a string
     * @param pointIdx - index of the desired point
     * @return - string containing the point information
     */
    public String toString(int pointIdx)
    {
        xFloats = getTimeCurve(StsLogVector.X).getValuesVectorFloats();
        yFloats = getTimeCurve(StsLogVector.Y).getValuesVectorFloats();
        depthFloats = getTimeCurve(StsLogVector.DEPTH).getValuesVectorFloats();
        timeLongs = ((StsTimeCurve)timeCurves.getElement(0)).getTimeVector().getLongs();
        sizeFloats = getProperty().getValuesVectorFloats();
        colorFloats = getColorBy().getValuesVectorFloats();
        float sizeFloat, colorFloat;
        if(sizeFloats == null)
            sizeFloat = 0;
        else
            sizeFloat = sizeFloats[pointIdx];
        if(colorFloats == null)
            colorFloat = 0;
        else
            colorFloat = colorFloats[pointIdx];
        double x = getTimeCurve(StsLogVector.X).getValueVector().getOrigin() + xShift + xFloats[pointIdx];
        double y = getTimeCurve(StsLogVector.Y).getValueVector().getOrigin() + yShift + yFloats[pointIdx];
        String timeStg = currentModel.getProject().getTimeDateFormat().format(new Date(timeLongs[pointIdx]));
        return new String("Sensor(" + this.getName() + "): Time=" + timeStg + " X=" + x + " Y=" + y + " Z=" + (depthFloats[pointIdx] + zShift) +
                    " " + propertyVector.getName() + "=" + sizeFloat + " " + colorVector.getName() + "=" + colorFloat);
    }

    /**
     * Determine if an event was selected by the mouse.
     * @param gl - Graphics handle
     * @param glPanel - Graphcis panel
     */
    public void pickX(GL gl, StsGLPanel glPanel)
    {
        if(!verifyDisplayable()) return;
        StsGLPanel3d glPanel3d = (StsGLPanel3d)glPanel;

        // Temporarily ignore settings for duration and type if user is interactively selecting a region on a time series plot.
        long duration = this.displayDuration;
        byte type = this.displayType;
        if(currentModel.getProject().getProjectTimeDuration() != 0)
        {
            duration = currentModel.getProject().getProjectTimeDuration();
            type = SHOW_INCREMENT;
        }

        if(!checkLoadVectors())
        	return;

        int points = 0;
        try
        {
            float[] xyz = null;
            float sizeFloat;
            for (int i = 0; i < timeLongs.length; i++)
            {
                if(sizeFloats == null)
                   sizeFloat = getNumberBins();
                else
                   sizeFloat = sizeFloats[i];

                xyz = new float[] { xFloats[i], yFloats[i], depthFloats[i], sizeFloat};
                if(!checkTime(timeLongs, i, points, duration, type))
                   	continue;
                points++;

                if(!checkValue(timeLongs[i]))
                	continue;

                if(!checkZLimit(xyz))
                	continue;

                gl.glPushName(i);
                if(sizeFloats != null)
                    sizeFloat = computeSize(getProperty().getCurveMin(), getProperty().getCurveMax(), sizeFloats[i]);
                draw3DPoint(glPanel3d, xyz, sizeFloat, StsColor.BLACK, false);
                gl.glPopName();
            }
        }
        catch(Exception e)
        {
            StsException.outputException("StsSensor.display failed.", e, StsException.WARNING);
            return;
        }
    }

    /**
     * Draw highlighted points
     * @param glPanel3d - Graphics panel
     */
    public void drawHighlightedPoints(StsGLPanel3d glPanel3d)
    {
    	float size = this.getNumberBins();
    	StsColor color = getSensorClass().getAccentColor();
    	if(highlightedPoints == null) return;
        if(runTimers) displayTimer.start();
    	for(int i=0; i<highlightedPoints.length; i++)
    	{
    		float[] xyz = getXYZ(highlightedPoints[i]);
    		if(sizeFloats != null)
    			size = computeSize(scaleMin, scaleMax, sizeFloats[highlightedPoints[i]]);
    		draw3DPoint(glPanel3d, xyz, size, color, true);
    	}
        if(runTimers) displayTimer.stopAccumulateAddCount(highlightedPoints.length);
    }
    /**
     * Get the XYZ values
     * @param pointIdx - index of the desired point
     * @return - XYZ value for desired point
     */
    public float[] getXYZ(int pointIdx)
    {
    	float zt = 0.0f;
    	float[] xyz = null;
    	checkLoadVectors();
    	if((xFloats != null) && (yFloats != null) && (depthFloats != null))
    	{
            float[] xy = getRotatedPoint(xFloats[pointIdx] + xShift, yFloats[pointIdx] + yShift);
            try
            {
            	if(zDomainOriginal != currentModel.getProject().getZDomain())
            	{
            		if(zDomainOriginal == StsProject.TD_DEPTH)
            			zt = (float) currentModel.getProject().velocityModel.getT(xy[0], xy[1], depthFloats[pointIdx] + zShift, 0.0f);
            		else
            			zt = (float) currentModel.getProject().velocityModel.getZ(xy[0], xy[1], depthFloats[pointIdx] + zShift);
            	}
            	else
            		zt = depthFloats[pointIdx] + zShift;
            	xyz = new float[] { xy[0], xy[1],  zt};
            }
            catch(Exception ex)
            {
            	StsException.outputException("StsSensor:getXYZ()Unable to get xyz from supplied index", ex, StsException.WARNING);
            	return null;
            }
    		return xyz;
    	}
    	return null;
    }
    /**
     * Get the XYZT values
     * @param pointIdx - index of the desired point
     * @return - XYZ value for desired point
     */
    public float[] getXYZMT(int pointIdx)
    {
    	float[] xyzt = new float[5];
    	checkLoadVectors();
    	if((xFloats != null) && (yFloats != null) && (depthFloats != null))
    	{
            float[] xy = getRotatedPoint(xFloats[pointIdx] + xShift, yFloats[pointIdx] + yShift);
            xyzt[0] = xy[0]; xyzt[1] = xy[1];
            xyzt[2] = depthFloats[pointIdx] + zShift;
            xyzt[3] = 0.0f;
            xyzt[4] = 0.0f;
            try
            {
                if(currentModel.getProject().velocityModel != null)
                	xyzt[4] = (float) currentModel.getProject().velocityModel.getT(xy[0], xy[1], depthFloats[pointIdx] + zShift, 0.0f);
            }
            catch(Exception ex)
            {
            	StsException.outputException("StsSensor:getXYZT()Unable to get xyzt from supplied index", ex, StsException.WARNING);
            	return null;
            }
    		return xyzt;
    	}
    	return null;
    }
    /**
     * Get event as a point
     * @param pointIdx - index of the desired point
     * @return point
     */
    public StsPoint getZPoint(int pointIdx)
    {
        float[] xy = getRotatedPoint(xFloats[pointIdx] + xShift, yFloats[pointIdx] + yShift);
    	return new StsPoint(new float[] {xy[0], xy[1], depthFloats[pointIdx] + zShift});
    }

     /**
     * Verify that the current event meets the Z constraints
     * @param xyz - XYZ of the current event
     * @return success
     */
    public boolean checkZLimit(float[] xyz)
    {
    	float ztCriteria = getDynamicSensorClass().computeZTCriteria();
        // Check ZLimit Criteria
        if(getDynamicSensorClass().getEnableZLimit())
        {
        	float currentZ = currentModel.getCursor3d().getCurrentDirCoordinate(StsCursor3d.ZDIR);
        	if((xyz[2] > currentZ + ztCriteria) || (xyz[2] < currentZ - ztCriteria))
        		return false;
        }
        return true;
    }

    /**
     * Get the relative X origin
     * @return X origin
     */
    public double getXOrigin()
    {
    	StsTimeCurve xCurve = getTimeCurve(StsLogVector.X);
    	if(xCurve != null)
    		return xCurve.getValueVector().getMinValue();
        else
            return 0.0f;
    }

    /**
     * Get the relative Y origin
     * @return Y Origin
     */
    public double getYOrigin()
    {
    	StsTimeCurve yCurve = getTimeCurve(StsLogVector.Y);
    	if(yCurve != null)
    		return yCurve.getValueVector().getMinValue();
    	else
    		return 0.0f;
    }

    /**
     *   Zoom into the last point added to the series.
     */
    public boolean goToLastPoint()
    {
        float x = 0.0f, y = 0.0f, z = 0.0f, distance = 0.0f;
    	StsTimeCurve xCurve = getTimeCurve(StsLogVector.X);
    	StsTimeCurve yCurve = getTimeCurve(StsLogVector.Y);
        StsTimeCurve depth = getTimeCurve(StsLogVector.DEPTH);
    	if((xCurve != null) && (yCurve != null))
    	{
    		x = xCurve.getValueVector().getValuesArray()[xCurve.getNumValues()-1] + xShift;
    		y = yCurve.getValueVector().getValuesArray()[xCurve.getNumValues()-1] + yShift;
            z = depth.getValueVector().getMinValue() + zShift;
    		distance = numberBins * getSensorClass().getGoToOffset();
    	}
        // Determine the xyz values.
        float[] xyz = getXYZValue(x, y, z);
    	return goToPoint(xyz, distance, BOTH_D);
    }

    public float[] getAttributeInCylinder(StsTimeCurve curve, float[] xyz, float horzDist, float vertDist)
    {
        if(getNumValues() == 0) return new float[0];
        // Loop through points to determine if they are within the distance
        float[][] xyzVectors = getXYZVectors();
        String curveName = null;
        if(curve == null)
            curveName = ((StsTimeCurve)timeCurves.getElements()[0]).getName();
        else if(curve.getName().equalsIgnoreCase("None"))
            curveName = ((StsTimeCurve)timeCurves.getElements()[0]).getName();
        else
            curveName = curve.getName();
        float[] attribute = getTimeCurve(curveName).getValuesVectorFloats();
        if(attribute == null) return null;
        float[] selectedAtt = new float[attribute.length];
        int cnt = 0;
        for(int i=0; i<getNumValues(); i++)
        {
            float[] xy = getRotatedPoint(xyzVectors[0][i], xyzVectors[1][i]);
            if(StsMath.distance(xyz, new float[] {xy[0], xy[1], xyzVectors[2][i] + zShift}, 2) < horzDist)
            {
                // Check the vertical
                if(Math.abs((xyzVectors[2][i]+zShift) - xyz[2]) < vertDist)
                    selectedAtt[cnt++] = attribute[i];
            }
        }
        // Accumulate the attributes
        selectedAtt = (float[])StsMath.trimArray(selectedAtt, cnt);
        return selectedAtt;
    }

    public float[] getAttributeInRadius(StsTimeCurve curve, float[] xyz, float distance)
    {
        if(getNumValues() == 0) return new float[0];
        // Loop through points to determine if they are within the distance
        float[][] xyzVectors = getXYZVectors();
        String curveName = null;
        if(curve == null)
            curveName = ((StsTimeCurve)timeCurves.getElements()[0]).getName();
        else if(curve.getName().equalsIgnoreCase("None"))
            curveName = ((StsTimeCurve)timeCurves.getElements()[0]).getName();
        else
            curveName = curve.getName();
        float[] attribute = getTimeCurve(curveName).getValuesVectorFloats();
        if(attribute == null) return null;
        float[] selectedAtt = new float[attribute.length];
        int cnt = 0;
        for(int i=0; i<getNumValues(); i++)
        {
            float[] xy = getRotatedPoint(xyzVectors[0][i], xyzVectors[1][i]);
            if(StsMath.distance(xyz, new float[] {xy[0], xy[1], xyzVectors[2][i] + zShift}, 3) < distance)
                selectedAtt[cnt++] = attribute[i];
        }
        // Accumulate the attributes
        selectedAtt = (float[])StsMath.trimArray(selectedAtt, cnt);
        return selectedAtt;
    }

     /**
     *  Zoom into the center of all the points in the series.
     */
    public boolean goTo(double x, double y, double z)
    {
    	float distance = 1.0f;

    	StsTimeCurve xCurve = getTimeCurve(StsLogVector.X);
    	StsTimeCurve yCurve = getTimeCurve(StsLogVector.Y);
        StsTimeCurve depth = getTimeCurve(StsLogVector.DEPTH);
    	if((xCurve != null) && (yCurve != null))
    	{
    		float range = xCurve.getValueVector().getMaxValue() - xCurve.getValueVector().getMinValue();
    		x = xCurve.getValueVector().getMinValue() + range/2.0f;
    		range = yCurve.getValueVector().getMaxValue() - yCurve.getValueVector().getMinValue();
    		y = yCurve.getValueVector().getMinValue() + range/2.0f;
    		range = depth.getValueVector().getMaxValue() - depth.getValueVector().getMinValue();
    		z = yCurve.getValueVector().getMinValue() + range/2.0f;
    		distance = range * 2;
    	}
        // Determine the xyz values.
        float[] xyz = getXYZValue((float)x, (float)y, (float)z);
    	return goToPoint(xyz, distance, BOTH_D);
    }
     /**
     * Add new data to this sensor from real-time monitoring
     * @param source - source of the data
     * @param stype - source type
     * @param lastPollTime - last time data was polled
     * @return true when new data is found and successfully loaded
     */
    public int addNewData(String source, byte stype, long lastPollTime, boolean compute, boolean reload, boolean replace)
    {
    	int added = super.addNewData(source, stype, lastPollTime, compute, reload, replace);
        if(getSensorClass().getEnableGoTo())
        	goToLastPoint();
    	return added;
    }

    /**
     * Display seismic data related to the selected sensor event.
     *
     * UNDER DEVELOPMENT
     */
    public boolean displayGather(int eventIdx, StsViewGatherMicroseismic view)
    {
    	try
    	{
    		// Load the vectors and get the event time.
    		checkLoadVectors();
    		long eventTime = timeLongs[eventIdx];

    		// See if a a microseismic gather set exists in this time range.
    		StsPreStackMicroseismicSetClass microSetClass = (StsPreStackMicroseismicSetClass)currentModel.getStsClass(StsPreStackMicroseismicSet.class);
    		StsPreStackMicroseismicSet microSet = microSetClass.findMicroseismicSet(eventTime);
    		StsMicroseismicGatherFile microGather = microSet.getGather(eventTime);

    		// Display the gather
    		view.setLineSet(microSet, microGather);
    		return true;
    	}
    	catch(Exception ex)
    	{
    		StsException.outputException("Error getting microseismic gather or creating view.", ex, StsException.WARNING);
    		return false;
    	}
    	// Set Cursor position to the sensor location
        //return getRotatedPoint(xFloats[eventIdx], yFloats[eventIdx]);
    }

    public void setPerforationMarker(StsPerforationMarker marker)
    {
        this.marker = marker;
    }
    public StsPerforationMarker getPerforationMarker() { return marker; }
    public boolean computeDistanceToPerforation()
    {
        if(marker == null)
        {
            StsMessageFiles.errorMessage("Cannot compute distance to perforation until perforation is assigned to this sensor.");
            return false;
        }

        StsPoint perfLocation = marker.getLocation();
        double[] xy = currentModel.getProject().getAbsoluteXYCoordinates(perfLocation);
        perfLocation.setX((float)xy[0]);
        perfLocation.setY((float)xy[1]);
        if(!checkLoadVectors())
            return false;

        if(xFloats == null || yFloats == null || depthFloats == null)
        {
            StsMessageFiles.errorMessage("Cannot compute distance to perforation unable to load XYZ vectors for this sensor.");
            return false;
        }

        attributeName = "DistanceToPerf";
        attribute = new float[xFloats.length];
        double x = 0.0f;
        double y = 0.0f;
        for(int i=0; i<attribute.length; i++)
        {
            x = getTimeCurve(StsLogVector.X).getValueVector().getOrigin() + xShift + xFloats[i];
            y = getTimeCurve(StsLogVector.Y).getValueVector().getOrigin() + yShift + yFloats[i];
            attribute[i] = StsMath.distance(new StsPoint(x, y, depthFloats[i]+zShift), perfLocation);
        }
        saveAttribute();

        attributeName = "AzimuthFromPerf";
        for(int i=0; i<attribute.length; i++)
        {
            x = getTimeCurve(StsLogVector.X).getValueVector().getOrigin() + xShift + xFloats[i];
            y = getTimeCurve(StsLogVector.Y).getValueVector().getOrigin() + yShift + yFloats[i];
            double dX = perfLocation.getX() - x;
            double dY = perfLocation.getY() - y;
            attribute[i] = StsMath.atan2((float)dX, (float)dY);
        }
        saveAttribute();

        return true;
    }


    public void showPopupMenu(StsGLPanel glPanel, StsMouse mouse)
    {
        JPopupMenu tp = new JPopupMenu(name + " Properties");
        glPanel.add(tp);

        JLabel title = new JLabel("Sensor Popup Menu - " + name, JLabel.CENTER);
        title.setFont(new java.awt.Font("Dialog", 1, 14));
        tp.add(title);
        tp.addSeparator();

        StsMenuItem objectListWindow = new StsMenuItem();
        objectListWindow.setMenuActionListener("Group List...", this, "popupClassListPanel", null);
        tp.add(objectListWindow);

        if(Main.viewerOnly)
        {
            StsMenuItem classPropertyWindow = new StsMenuItem();
            classPropertyWindow.setMenuActionListener("Group Properties...", this, "popupClassPropertyPanel", null);
            tp.add(classPropertyWindow);

            StsMenuItem propertyWindow = new StsMenuItem();
            propertyWindow.setMenuActionListener("Display Properties...", this, "popupPropertyPanel", null);
            tp.add(propertyWindow);
            tp.addSeparator();
        }

        StsMenuItem deleteBtn = new StsMenuItem();
        StsMenuItem clearBtn = new StsMenuItem();
        StsMenuItem clearAllBtn = new StsMenuItem();

        deleteBtn.setMenuActionListener("Hide", this, "handDelete", mouse);
        clearBtn.setMenuActionListener("Show", this, "handClear", mouse);
        clearAllBtn.setMenuActionListener("Show All", this, "handClearAll", null);


        tp.add(deleteBtn);
        tp.add(clearBtn);
        tp.add(clearAllBtn);

        tp.show(glPanel, mouse.getX(), mouse.getY());
    }
}