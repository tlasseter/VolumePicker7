package com.Sts.DBTypes;

/**
 * <p>Title: S2S Development</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: S2S Systems LLC</p>
 * @author unascribed
 * @version 1.1
 */

import com.Sts.DB.StsSerializable;
import com.Sts.Interfaces.*;
import com.Sts.MVC.View3d.StsGLPanel3d;
import com.Sts.Types.*;
import com.Sts.UI.Beans.*;
import com.Sts.Utilities.StsMath;

import javax.media.opengl.GL;
import java.util.ArrayList;
import java.util.Iterator;

public class StsGolderFractureSetClass extends StsClass implements StsSerializable, StsClassTimeDisplayable, StsClassCursorDisplayable, StsClassTimeSeriesDisplayable
{
	/**
	 *
	 */
	private static final long serialVersionUID = -2677014498211804642L;

	boolean enableTime = false;
    float maximumDistance = 100f;
    byte correlationType = NEAREST;
    byte correlateWith = NONE;
    boolean azimuthLimit = false;
    float principleStressDirection = 0.0f;
    float azimuthRange = 5.0f;
    boolean colorActive = false;
    StsColor inactiveColor = new StsColor(StsColor.GRAY);
    StsColor activeColor = new StsColor(StsColor.BRONZE);
    
	private String defaultSpectrumName = "RedWhiteBlue";
	private StsColor defaultFractureSetColor = new StsColor(StsColor.BLUE);

    static final byte NEAREST = 0;
    static final byte INSIDE = 1;
    static final String[] correlationTypeStrings = new String[]{"Nearest", "Inside Limit"};

    static final byte NONE = 0;
    static final byte EVENTS = 1;
    static final byte EVENTS_AND_PLANE = 2;
    static final byte FRACTURES = 3;
    static final String[] correlateWithStrings = new String[]{"None", "Microseismic Events", "Events & Best Fit Plane", "Fracture Sets"};

    static StsBooleanFieldBean azimuthLimitBean;
    static StsFloatFieldBean stressBean;
    static StsFloatFieldBean rangeBean;
    static StsFloatFieldBean distanceBean;
    static StsComboBoxFieldBean correlationTypeBean;

	public StsGolderFractureSetClass()
	{
        userName = "Sets of Golder Fractures";
	}

	public void initializeFields()
	{
        azimuthLimitBean = new StsBooleanFieldBean(this, "azimuthLimit", "Azimuth Constrain");
        azimuthLimitBean.setToolTipText("Limit fracture correlation to corridor orthogonal to principle stress direction.");
        stressBean = new StsFloatFieldBean(this, "principleStressDirection", 0.0f, 360.0f, "Principle Stress Direction:");
        stressBean.setToolTipText("Azimuth of principle stress.");
        rangeBean = new StsFloatFieldBean(this, "azimuthRange", 0.0f, 180.0f, "Azimuth Range:");
        rangeBean.setToolTipText("Define azimuth corridor orthogonal to principle stress direction.");
        distanceBean = new StsFloatFieldBean(this, "maximumDistance", 1.0f, 1000.0f, "Correlation Distance:");
        distanceBean.setToolTipText("Define the maximum distance to look for a fracture to correlate with a sensor event");
        correlationTypeBean = new StsComboBoxFieldBean(this, "correlationTypeString", "Correlation Type:", correlationTypeStrings );
		displayFields = new StsFieldBean[]
		{
			new StsBooleanFieldBean(this, "enableTime", "Enable Time"),
            new StsComboBoxFieldBean(this, "correlateWithString", "Correlate With:", correlateWithStrings ),
            correlationTypeBean,
            distanceBean,
            azimuthLimitBean, stressBean, rangeBean,
            new StsBooleanFieldBean(this, "colorActive", "Color By Activation"),
            new StsColorComboBoxFieldBean(this, "inactiveColor", "Inactive Color:", StsColor.colors32),
            new StsColorComboBoxFieldBean(this, "activeColor", "Activated Color:", StsColor.colors32)
		};

		defaultFields = new StsFieldBean[]
		{
			new StsColorComboBoxFieldBean(this, "defaultFractureSetColor", "Color:", StsColor.colors32),
			new StsComboBoxFieldBean(this, "defaultSpectrumName", "Spectrum:", StsSpectrumClass.cannedSpectrums)
		};
	}

	public void displayTimeClass(StsGLPanel3d glPanel3d, long time)
	{
		Iterator<Object> iter = getVisibleObjectIterator();
		GL gl = glPanel3d.getGL();
		while (iter.hasNext())
		{
			StsGolderFractureSet fracSet = (StsGolderFractureSet) iter.next();
            if(fracSet.isAlive(time) && enableTime)
                fracSet.decimationChanged = true;

			fracSet.display(glPanel3d);
		}
		gl.glLightModeli(GL.GL_LIGHT_MODEL_TWO_SIDE, GL.GL_FALSE);
	}

	public void drawOnCursor3d(StsGLPanel3d glPanel3d, int dirNo, float dirCoordinate, StsPoint[] planePoints, boolean isDragging) { }

	public void drawOnCursor2d(StsGLPanel3d glPanel3d, int dirNo, float dirCoordinate, boolean axesFlipped, boolean xAxisReversed, boolean yAxisReversed)
	{
//        long time = currentModel.getProject().getProjectTime();
//        Iterator iter = getVisibleObjectIterator();
//        while (iter.hasNext())
//        {
//            StsSensor sensor = (StsSensor) iter.next();
//            if ((enableTime && sensor.isAlive(time)) || (!enableTime))
//                sensor.display2d(glPanel3d, dirNo, dirCoordinate, axesFlipped, xAxisReversed, yAxisReversed);
//        }
	}


	public void drawOn3dCurtain(StsGLPanel3d glPanel3d, StsSeismicCurtain seismicCurtain) { }
	// fails if object is null
	// should simply use superclass default method:
	// update Properties should be handled when the object is added to model
	// TJL June 3 08

	/*
		   public boolean setCurrentObject(StsObject object)
		   {
				if (currentObject == object)
					return false;
				currentObject = object;
				((StsSensor)object).updateProperties();
				return super.setCurrentObject(object);
		   }
	   }
	   */
//    public void setDisplayNames(boolean displayNames)
//    {
//        if(this.displayNames == displayNames) return;
//        this.displayNames = displayNames;
////      setDisplayField("displayNames", displayNames);
//        currentModel.win3dDisplayAll();
//    }
//
//    public boolean getDisplayNames() {	return displayNames; }
//
	public void setEnableTime(boolean enable)
	{
		if(this.enableTime == enable) return;
		this.enableTime = enable;
		currentModel.win3dDisplayAll();
	}

	public boolean getEnableTime() { return enableTime; }

	public void setColorActive(boolean enable)
	{
		if(this.colorActive == enable) return;
		this.colorActive = enable;
		currentModel.win3dDisplayAll();
	}

	public boolean getColorActive() { return colorActive; }

    public void setActiveColor(StsColor color)
    {
        if (activeColor == color) return;
        activeColor = color;
        currentModel.win3dDisplayAll();
    }
    public StsColor getActiveColor() { return activeColor; }

    public void setInactiveColor(StsColor color)
    {
        if (inactiveColor == color) return;
        inactiveColor = color;
        currentModel.win3dDisplayAll();
    }
    public StsColor getInactiveColor() { return inactiveColor; }

	public StsColor getNextColor()
	{
		int i = list.getSize();
		return StsColor.colors32[i % 32];
	}

	public void setDefaultFractureSetColor(StsColor defaultFractureSetColor)
	{
		this.defaultFractureSetColor = defaultFractureSetColor;
	}

	public StsColor getDefaultFractureSetColor()
	{
		return defaultFractureSetColor;
	}

	public String getDefaultSpectrumName() { return defaultSpectrumName; }

	public void setDefaultSpectrumName(String value)
	{
		if(this.defaultSpectrumName.equals(value)) return;
		this.defaultSpectrumName = value;
	}

    /**
     * Set the minimum acceptable distance to correlate an event with a fracture
     * @param dist - maximum distance between event and fracture
     */
    public void setMaximumDistance(float dist)
    {
        maximumDistance = dist;
        clearFractureHighlights();
        computeHighlightedFractures();
    }
    
    public float getMaximumDistance() { return maximumDistance; }

    public void setAzimuthLimit(boolean limit)
    {
        azimuthLimit = limit;
        stressBean.setEditable(azimuthLimit);
        rangeBean.setEditable(azimuthLimit);
        clearFractureHighlights();
        computeHighlightedFractures();
    }
    public boolean getAzimuthLimit() { return azimuthLimit; }
    public void setPrincipleStressDirection(float dir)
    {
        if(principleStressDirection == dir) return;
        principleStressDirection = dir;
        clearFractureHighlights();
        computeHighlightedFractures();
    }
    public float getPrincipleStressDirection() { return principleStressDirection; }
    public void setAzimuthRange(float range)
    {
        if(azimuthRange == range) return;
        azimuthRange = range;
        clearFractureHighlights();
        computeHighlightedFractures();
    }
    public float getAzimuthRange() { return azimuthRange; }
    /**
     * Clear all highlighted fractures
     */
    public void clearFractureHighlights()
	{
		for(StsObject fractureSetObject : getElements())
		{
			StsGolderFractureSet fractureSet = (StsGolderFractureSet)fractureSetObject;
			for(StsGolderFracture fracture : fractureSet.fractures)
			{
               fracture.clearHighlight();
			}
            currentModel.viewObjectRepaint(fractureSet, fractureSet);
		}
		return;
	}
    /**
     * Highlight any fractures that intersect an interpreted fracture set
     */
    public void correlateWithFractures()
    {
		//StsBoundingBox fractureSetsBoundingBox = new StsBoundingBox();
		//for(StsObject fractureSetObject : getElements())
		//	fractureSetsBoundingBox.addUnrotatedBoundingBox((StsGolderFractureSet)fractureSetObject);

		ArrayList<StsFractureDisplayable> interpretedFractures = new ArrayList<StsFractureDisplayable>();
		StsArrayList interpretedFractureClasses = currentModel.getFractureDisplayableClasses();
		int nInterpretedFractureClasses = interpretedFractureClasses.size();
		for(int n = 0; n < nInterpretedFractureClasses; n++)
		{
			StsClassFractureDisplayable interpretedFractureClass = (StsClassFractureDisplayable)interpretedFractureClasses.get(n);
			interpretedFractures.addAll(interpretedFractureClass.getDisplayableFractures());
		}
		if(interpretedFractures.size() == 0) return;

		boolean viewObjectChanged = false;

		for(StsObject fractureSetObject : getElements())
		{
			StsGolderFractureSet fractureSet = (StsGolderFractureSet)fractureSetObject;
			viewObjectChanged = viewObjectChanged | fractureSet.highlightIntersectedFractures(interpretedFractures);
		}
		if(viewObjectChanged)
		{
			StsObject fSet = getFirst();
			currentModel.viewObjectRepaint(fSet, fSet);
		}
	}

    /**
     * Highlight any fractures that are within a user specified distance to a microseismic event.
     */
    public void correlateWithSensors()
    {
		//StsBoundingBox fractureSetsBoundingBox = new StsBoundingBox();
		//for(StsObject fractureSetObject : getElements())
		//	fractureSetsBoundingBox.addUnrotatedBoundingBox((StsGolderFractureSet)fractureSetObject);

		boolean viewObjectChanged = false;
        StsDynamicSensor[] sensors = (StsDynamicSensor[])currentModel.getCastObjectList(StsDynamicSensor.class);
        for(StsDynamicSensor sensor: sensors)
        {
            if(!sensor.isVisible) continue;
            if(sensorIntersectsFractureSet(sensor))
             {
                for(int j = 0; j<sensor.getNumValues(); j++)
                {
                    if(correlationType == NEAREST)
                    {
                        StsGolderFracture fracture = findNearestFracture(sensor.getXYZ(j), maximumDistance);
					    if(fracture != null) fracture.setHighlight(sensor, j);
                    }
                    else
                    {
                        highlightFracturesInsideLimits(sensor, j, maximumDistance);
                    }
                    if(correlateWith == EVENTS_AND_PLANE)
                    {
                        if(sensor.getFitPlane())
                        {
                            // ToDo: TOM - Best fit plane is on so compute intersecting DFN fractures and highlight
                            ;
                        }
                    }
                }
                viewObjectChanged = true;
             }
        }
		if(viewObjectChanged) 
		{
			StsObject fractureSet = getFirst();
			currentModel.viewObjectRepaint(fractureSet, fractureSet); 
		}
	}
    /**
     * Set the sensor correlation type
     */
    public void setCorrelateWithString(String typeString)
    {
        if(typeString.equals(correlateWithStrings[correlateWith]))
            return;
        for(int i=0; i<correlateWithStrings.length; i++)
        {
            if(correlateWithStrings[i].equals(typeString))
            {
                correlateWith = (byte)i;
                clearFractureHighlights();
                if((correlateWith == EVENTS) || (correlateWith == EVENTS_AND_PLANE))
                {
                    correlationTypeBean.setEditable(true);
                    distanceBean.setEditable(true);
                }
                else if(correlateWith == FRACTURES)
                {
                    correlationTypeBean.setEditable(false);
                    distanceBean.setEditable(false);
                }
                else
                {
                    correlationTypeBean.setEditable(false);
                    distanceBean.setEditable(false);
                }
                computeHighlightedFractures();
                return;
            }
        }
    }

    public void computeHighlightedFractures()
    {
        if((correlateWith == EVENTS) || (correlateWith == EVENTS_AND_PLANE))
            correlateWithSensors();
        else if(correlateWith == FRACTURES)
            correlateWithFractures();
    }
    /**
     * Get the sensor correlation type
     */
    public String getCorrelateWithString()
    { return correlateWithStrings[correlateWith]; }

    /**
     * Set the sensor correlation type
     */
    public void setCorrelationTypeString(String typeString)
    {
        if(typeString.equals(correlationTypeStrings[correlationType]))
            return;
        for(int i=0; i<correlationTypeStrings.length; i++)
        {
            if(correlationTypeStrings[i].equals(typeString))
            {
                correlationType = (byte)i;
                clearFractureHighlights();                
                computeHighlightedFractures();
                return;
            }
        }
    }
    /**
     * Get the sensor correlation type
     */
    public String getCorrelationTypeString()
    { return correlationTypeStrings[correlationType]; }

    /**
     * Determine if a given set of sensor events overlaps with any fracture sets
     * @param sensor
     * @return true if an intersection exists
     */
    public boolean sensorIntersectsFractureSet(StsDynamicSensor sensor)
    {
        return true;
        /*
		for(StsObject fractureSetObject : getElements())
		{
			StsGolderFractureSet fractureSet = (StsGolderFractureSet)fractureSetObject;
            if(fractureSet.intersectsBoundingBox(sensor.getUnrotatedBoundingBox()))
                return true;
		}
        return false;
        */
    }

    /**
     * Find the fracture closest to a given point.
     * @param xyz
     * @return  fracture closest to the point
     */
	public StsGolderFracture findNearestFracture(float[] xyz, float maxDistance)
	{
		float distance = maxDistance;
		StsGolderFracture nearestFracture = null;
        float minAzimuth = principleStressDirection-(0.5f * azimuthRange);
        float maxAzimuth = principleStressDirection+(0.5f * azimuthRange);
		for(StsObject fractureSetObject : getElements())
		{
			StsGolderFractureSet fractureSet = (StsGolderFractureSet)fractureSetObject;
			if(fractureSet.isInsideXYZ(xyz))
			{
				for(StsGolderFracture fracture : fractureSet.fractures)
				{
                    if(fracture.getCenter() == null) continue;
					float fractureDistance = StsMath.distance(xyz, fracture.getCenter(), 3);
					if(getAzimuthLimit())
                    {
                        float azimuth = fracture.getAzimuth();
                        if((azimuth > maxAzimuth) || (azimuth < minAzimuth))
                             continue;
                    }
					if(fractureDistance < distance)
					{
						nearestFracture = fracture;
						distance = fractureDistance;
					}
				}
			}			
		}
		return nearestFracture;
	}

    /**
     * Find all the fractures inside specified range of given sensor point.
     * @param sensor - the microseismic event set
     * @param index - the index of the sensor point
     * @param maxDistance - the search distance
     * @return  fracture closest to the point
     */
	public void highlightFracturesInsideLimits(StsDynamicSensor sensor, int index, float maxDistance)
	{
        float minAzimuth = principleStressDirection-(0.5f * azimuthRange);
        float maxAzimuth = principleStressDirection+(0.5f * azimuthRange);
		boolean azimuthLimit = getAzimuthLimit();
		for(StsObject fractureSetObject : getElements())
		{
			StsGolderFractureSet fractureSet = (StsGolderFractureSet)fractureSetObject;
			fractureSet.highlightFracturesInsideLimits(sensor, index, maxDistance, azimuthLimit, minAzimuth, maxAzimuth);
		}
	}

}