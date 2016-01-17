package com.Sts.DBTypes;

import com.Sts.Actions.Wizards.SurfaceCurvature.*;
import com.Sts.DB.*;
import com.Sts.Interfaces.*;
import com.Sts.MVC.*;
import com.Sts.MVC.View3d.*;
import com.Sts.Types.*;
import com.Sts.UI.Beans.*;
import com.Sts.UI.ObjectPanel.*;
import com.Sts.UI.Progress.*;
import com.Sts.Utilities.Seismic.*;
import com.Sts.Utilities.*;

import javax.media.opengl.*;
import java.awt.event.*;
import java.nio.*;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: Tom Lasseter
 * Date: May 14, 2009
 * Time: 10:35:01 AM
 * To change this template use File | Settings | File Templates.
 */
public class StsPatchVolume extends StsSeismicBoundingBox implements StsTreeObjectI, StsSerializable
{
	/** patches created are sorted by row, col, and z in this array */
	public StsPatchGrid[] rowSortedPatchGrids;
	protected StsColorscale colorscale;
	protected StsHistogram histogram;
	protected StsSeismicVolume seismicVolume;
	protected String seismicName;
	protected boolean filter = false;
	protected int boxFilterWidth = 1;
	transient public int nInterpolatedSlices;
	transient public float interpolatedZInc;
	transient public int interpolatedSliceMin;
	transient public int interpolatedSliceMax;
	transient StsPatchVolumeClass patchVolumeClass;
	transient public StsCroppedBoundingBox croppedBoundingBox;

	/**
	 * gridList contains patches which have been completed. At the end of a row, prevRowGrids contains disconnected grids which
	 * are added to gridList patches.  At the end of all rows, remaining grids are in rowGrids which are then added to gridList.
	 */
	transient ArrayList<StsPatchGrid> gridList;

	/**
	 * rowGrids contains new patches and existing patches from previous row connected to the latest row;
	 * at the completion of the row, these become the previousRowGrids. At start of row, its initialized to empty.  If a new grid is created,
	 * it is added to rowGrids.  If an existing grid is connected to a nextWindow in the row, it is added to rowGrid and removed from prevRowGrid.
	 * At the end of the row, grids still connected are in rowGrid, and disconnected ones are in prevRowGrids. These disconnected grids are
	 * added to gridList.  prevRowGrids is then set to rowGrids and rowGrids initialized for the next row.
	 */
	transient HashMap<Integer, StsPatchGrid> rowGrids = null;
	transient Iterator<StsPatchGrid> rowGridsIterator;

	/** prevRowGrids are the active patches in the previousRow; when making a connection to a nextWindow in the previous row, we look here for a patch. */
	transient HashMap<Integer, StsPatchGrid> prevRowGrids = null;
	transient Iterator<StsPatchGrid> prevRowGridsIterator;

	/** a temporary array built at initialization with final patches sorted by col, row, and z */
	transient public StsPatchGrid[] colSortedPatchGrids;
	/** total number of points on all patches on this volume */
	transient int nPointsTotal;
	/** nextWindow size in wavelengths. If zero, nextWindow size is 1/2 wavelength. */
	transient int windowSize;
	/** half nextWindow size in wavelengths. */
	transient float halfWindowSize;
	/**
	 * Minimum data amplitude that will be used for a minimum or maximum event.
	 * Max data amplitude is Min(-dataMin, dataMax). So to be used, the abs(amplitude) >= minDataAmplitude
	 */
	transient double minDataAmplitude;
	/** pick nextWindow on adjoining trace cannot be more than this many wavelengths away from picked nextWindow */
	transient float pickDifWavelengths;
	/** Type(s) of pick events to be correlated: min, max, min&max, all */
	transient byte pickType;
	/** number of points to find in half of nextWindow */
	transient int nHalfSamples;
	/** Window center is either max or min; nextWindow ends are either the same or a zero crossing;  This flag indicates nextWindow ends with zero-crossing */
	transient boolean windowEndIsZeroCrossing;
	/** multiplier of half nextWindow size yielding the max allowed pick difference */
	transient double halfWindowPickDifFactor;
	/** number of interpolation intervals between samples */
	transient int nInterpolationIntervals;
	/** indicates iterative stretchCorrel is to be applied from max down to min by inc */
	transient boolean isIterative;
	/** max value for stretchCorrelation */
	transient float autoCorMax;
	/** min value for stretchCorrelation */
	transient float autoCorMin;
	/** increment of stretch stretchCorrelation in interative pick */
	transient float autoCorInc;
	/** manual picking minimum acceptable cross-stretchCorrelation */
	transient float manualCorMin;
	/** minimum amplitude fraction of sample data max allowed for an event to be correlated */
	transient float minAmpFraction;
	/** sequence of stretchCorrelations: 1 if not iterative, max to min by inc if iterative */
	transient float[] stretchCorrelations;
	/** number of stretchCorrelations in sequence */
	transient int nIterations;
	/** min amplitudeRatio allowed for correlation */
	transient float minAmplitudeRatio;
	/** correlate using falseTypes (e.g., a false Max matches a Max, etc) */
	transient boolean useFalseTypes = false;
	/** double check connection by matching it back from selected prevWindow; accept match if backMatchWindow is null or the same */
	transient boolean checkBackMatch = true;
	/** row currently being computed: used for debugPatchGrid print out only */
	transient int row, col, volRow, volCol;
	transient int nPatchPointsMin;
	transient public byte curveType = CURVPos;
	transient public int filterSize = 0;
	transient protected boolean displaySurfs = false;
	transient protected boolean displayVoxels = false;
	transient int nSmallGridsRemoved;
	transient int nParentGrids;
	transient StsPoint currentCursorPoint;
	transient StsPatchGrid cursorPointPatch;
	transient private StsPatchGrid[] drawPatchGrids;
	transient private PatchGridGroup[] patchGridGroups;
	transient boolean displayingChildPatches = true;
	transient float[] histogramValues;
	transient int nHistogramValues = 10000;
	static protected StsObjectPanel objectPanel = null;

	public static final byte PICK_MAX = 0;
	public static final byte PICK_MIN = 1;
	public static final byte PICK_MIN_MAX = 2;
	public static final byte PICK_ALL = 3;

	public static final int largeInt = 99999999;

	public static final String[] pickTypeNames = new String[]{"All", "Min+Max", "Maximum", "Minimum"}; //, "Zero-crossing+", "Zero-crossing-", "All"};
	static public final float badCurvature = StsPatchGrid.badCurvature;

	public String getSeismicName()
	{
		return seismicName;
	}

	static StsEditableColorscaleFieldBean colorscaleBean = new StsEditableColorscaleFieldBean(StsPatchVolume.class, "colorscale");


	static public final StsFieldBean[] displayFields =
			{
					new StsBooleanFieldBean(StsPatchVolume.class, "isVisible", "Display on Cursors"),
					new StsBooleanFieldBean(StsPatchVolume.class, "displaySurfs", "Display as Surfaces"),
					new StsBooleanFieldBean(StsPatchVolume.class, "displayVoxels", "Display as Voxel cloud"),
			};

	static public final StsFieldBean[] propertyFields = new StsFieldBean[]
			{
					new StsStringFieldBean(StsPatchVolume.class, "name", true, "Name"),
					colorscaleBean
			};

	final public float getZ(int slice)
	{
		return zMin + this.interpolatedZInc * slice;
	}

	public float getDataMin()
	{
		return dataMin;
	}

	public float getDataMax()
	{
		return dataMax;
	}

	static public final int defaultTestWindow = 21;
	static public final float defaultTestWavelengths = 1.0f;

	//Curvature Attribute Types
	static public final byte CURVDip = StsSurfaceCurvatureAttribute.CURVDip;
	static public final byte CURVStrike = StsSurfaceCurvatureAttribute.CURVStrike;
	static public final byte CURVMean = StsSurfaceCurvatureAttribute.CURVMean;
	static public final byte CURVGauss = StsSurfaceCurvatureAttribute.CURVGauss;
	static public final byte CURVPos = StsSurfaceCurvatureAttribute.CURVPos;
	static public final byte CURVNeg = StsSurfaceCurvatureAttribute.CURVNeg;
	static public final byte CURVMin = StsSurfaceCurvatureAttribute.CURVMin;
	static public final byte CURVMax = StsSurfaceCurvatureAttribute.CURVMax;


	static final float largeFloat = StsParameters.largeFloat;

	/** debugPatchGrid prints showing row operations */
	static public final boolean debug = false;
	/** turn on timer for values operation */
	static final boolean runTimer = false;
	/** millisecond timer */
	static StsTimer timer;
	/** print patch operations and draw only this patch */
	static boolean drawPatchBold = debug && StsPatchGrid.debugPatchGrid;
	/** debug: connect closest points only */
	static final boolean debugConnectCloseOnly = true;
	static public String iterLabel = "";

	private static final long serialVersionUID = 1L;

	public StsPatchVolume()
	{
	}

	public StsPatchVolume(StsSeismicVolume seismicVolume)
	{
		super(false);
		StsToolkit.copySubToSuperclass(seismicVolume, this, StsRotatedGridBoundingBox.class, StsBoundingBox.class, true);
		this.seismicVolume = seismicVolume;
		seismicName = seismicVolume.getName();
		zDomain = seismicVolume.zDomain;
		stsDirectory = seismicVolume.stsDirectory;
		initialize(currentModel);
	}

	/**
	 * This is central method for constructing the volume of patchGrids.
	 * For each row, we examine stretchCorrelation with traces in same row & prev col and same col & prev row.
	 * @param pickPanel graphics panel with progress bar updated as each row completed
	 */
	public void constructPatchVolume(StsPatchPickPanel pickPanel)
	{
		StsProgressBar progressPanel = pickPanel.progressBar;
		windowSize = pickPanel.corWavelength;
		pickDifWavelengths = pickPanel.maxPickDif;
		minAmpFraction = pickPanel.minAmpFraction;
		pickType = pickPanel.pickType;
		nPatchPointsMin = pickPanel.minPatchSize;
		useFalseTypes = pickPanel.useFalseTypes;
		checkBackMatch = pickPanel.checkBackMatch;
		isIterative = pickPanel.isIterative;
		autoCorMax = pickPanel.autoCorMax;
		autoCorMin = pickPanel.autoCorMin;
		autoCorInc = pickPanel.autoCorInc;
		manualCorMin = pickPanel.manualCorMin;
		minAmplitudeRatio = pickPanel.minAmpRatio;

		StsPatchGrid.initializeDebug(pickPanel);
		initializeLists();

		if (!isIterative)
		{
			nIterations = 1;
			stretchCorrelations = new float[]{manualCorMin};
		}
		else
		{
			nIterations = StsMath.ceiling(1 + (autoCorMax - autoCorMin) / autoCorInc);
			stretchCorrelations = new float[nIterations];
			float stretchCorrelation = autoCorMax;
			for (int n = 0; n < nIterations; n++, stretchCorrelation -= autoCorInc)
				stretchCorrelations[n] = stretchCorrelation;
		}

		rowSortedPatchGrids = new StsPatchGrid[0];
		colSortedPatchGrids = null;
		initialize();
		StsPatchGrid.staticInitialize();

		if (progressPanel != null)
			progressPanel.initialize(croppedBoundingBox.nRows);

		//hack:  FIX!
		if (seismicVolume == null)
			seismicVolume = (StsSeismicVolume) currentModel.getCurrentObject(StsSeismicVolume.class);
		float absSeismicDataMax = Math.min(Math.abs(seismicVolume.dataMin), Math.abs(seismicVolume.dataMax));
		minDataAmplitude = minAmpFraction * absSeismicDataMax;
		initializeParameters();
		initializeToBoundingBox(croppedBoundingBox);
		initializeSliceInterpolation();


		try
		{
			// row & col refer to the row and col in a croppedVolume over which picker is to run
			// volRow & volCol define the actual row and col in the volume (used only for reference)
			row = 0;
			volRow = croppedBoundingBox.rowMin;
			TracePoints[] prevRowTracesPoints; // all traces in prev row
			TracePoints[] rowTracesPoints = null; // all traces in this row
			TracePoints rowPrevTracePoints; // trace in same row, prev col
			TracePoints colPrevTracePoints; // trace in same col, prev row
			for (; volRow <= croppedBoundingBox.rowMax; row++, volRow++)
			{
				//statusArea.setProgress(row*40.f/nRows);
				prevRowTracesPoints = rowTracesPoints; // all traces in prev row
				rowTracesPoints = getRowTracePoints(row, volRow, seismicVolume, croppedBoundingBox);
				incrementLists();
				// if(croppedColMin > 0) rowFloatBuffer.position(croppedColMin * nVolSlices);
				col = 0;
				volCol = croppedBoundingBox.colMin;
				TracePoints tracePoints = null;
				for (col = 0, volCol = croppedBoundingBox.colMin; volCol <= croppedBoundingBox.colMax; col++, volCol++)
				{
					rowPrevTracePoints = tracePoints;
					tracePoints = rowTracesPoints[col];
					if(tracePoints == null) continue;
					if(row == 0)
						colPrevTracePoints = null;
					else
						colPrevTracePoints = prevRowTracesPoints[col];
					// here we add the connected patchPoints
					tracePoints.connectWindows(colPrevTracePoints, rowPrevTracePoints);
				}
				processPrevRowGrids(row);
				if (progressPanel == null) continue;
				if (progressPanel.isCanceled())
				{
					progressPanel.setDescriptionAndLevel("Cancelled by user.", StsProgressBar.ERROR);
					return;
				}
				progressPanel.setValue(row + 1);
			}
			addRemainingGrids();
			finish();
			getPatchVolumeClass().setDisplayCurvature(false);
			progressPanel.finished();
		}
		catch (Exception e)
		{
			StsException.outputWarningException(this, "constructPatchVolume", e);
		}
	}

	static float[] traceValues = null;
	static float[] nullTrace;
	TracePoints[] getRowTracePoints(int row, int volRow, StsSeismicVolume seismicVolume, StsCroppedBoundingBox croppedBoundingBox)
	{
		TracePoints[] rowTracePoints = new TracePoints[croppedBoundingBox.nSlices];
		int croppedColMin = croppedBoundingBox.colMin;
		int croppedColMax = croppedBoundingBox.colMax;
		FloatBuffer rowFloatBuffer = seismicVolume.getRowPlaneFloatBuffer(volRow, croppedBoundingBox.colMin);
		if (rowFloatBuffer == null) return null;


		if(traceValues == null)
		{
			traceValues = new float[croppedBoundingBox.nSlices];
			nullTrace = new float[croppedBoundingBox.nSlices];
			Arrays.fill(nullTrace, seismicVolume.userNull);
		}
		// if(croppedColMin > 0) rowFloatBuffer.position(croppedColMin * nVolSlices);
		for (int col = 0, volCol = croppedColMin; volCol <= croppedColMax; col++, volCol++)
		{
			// StsException.systemDebug(this, "constructPatchVolume", "col loop, col: " + col);
			rowFloatBuffer.position(volCol * seismicVolume.nSlices + croppedBoundingBox.sliceMin);
			rowFloatBuffer.get(traceValues);

			TracePoints tracePoints = null;
			if (!Arrays.equals(traceValues, nullTrace))
				tracePoints = TracePoints.constructor(this, row, col, traceValues);
			rowTracePoints[col] = tracePoints;
		}
		return rowTracePoints;
	}

	static void setIterLabel(int iter)
	{
	 	iterLabel = " Iter: " + Integer.toString(iter);
	}

	public boolean setFilter()
	{
		return filter;
	}

	public void setFilter(boolean filter)
	{
		this.filter = filter;
	}

	public int getBoxFilterWidth()
	{
		return boxFilterWidth;
	}

	public void setBoxFilterWidth(int boxFilterWidth)
	{
		this.boxFilterWidth = boxFilterWidth;
	}

	/** These two grid do not overlap.  So merge newest grid (highest id number) into older grid and remove newest.
	 * @param otherPatchPoint prev row or col point to be connected to newPatchPoint
	 * @param newPatchPoint current point being processed
	 * @return mergedGrid which is older of the two (smallest id number)
	 */
	protected StsPatchGrid mergePatchGrids(PatchPoint otherPatchPoint, PatchPoint newPatchPoint)
	{
		StsPatchGrid mergedGrid, removedGrid;

		StsPatchGrid otherPatchGrid = otherPatchPoint.getPatchGrid();
		StsPatchGrid newPatchGrid = newPatchPoint.getPatchGrid();

		if (otherPatchGrid.id < newPatchGrid.id)
		{
			mergedGrid = otherPatchGrid;
			removedGrid = newPatchGrid;
		}
		else
		{
			mergedGrid = newPatchGrid;
			removedGrid = otherPatchGrid;
		}
		if (StsPatchGrid.debugPatchID != StsPatchGrid.NO_DEBUG && (mergedGrid.id == StsPatchGrid.debugPatchID || removedGrid.id == StsPatchGrid.debugPatchID))
			StsException.systemDebug(this, "mergePatchGrids", "MERGING GRID " + removedGrid.toGridString() + " TO GRID " + mergedGrid.toGridString());
		// merge shouldn't fail, so a return of false indicates a problem: bail out
		if (!mergedGrid.mergePatchPoints(removedGrid))
		{
			StsException.systemError(this, "mergePatchGrids", "Failed to merge removedGrid " + removedGrid.toGridString() + " to mergedGrid " + mergedGrid.toGridString());
			return null;
		}
		// removedGrid could be parent, child, or new; it is being merged into mergedGrid which could be any of these three as well
		// if removedGrid is a parent, then the first child needs to be made the parent
		// if removed grid is a child, it needs to be removed from the immediate parent
		// if new, we don't have to do anything

		removedGrid.removeChildOrParent(mergedGrid);

		//checkAddPatchGridToRowGrids(mergedGrid);
		//mergedGrid.addCorrelation(otherPatchPoint, newPatchPoint, correl);
		removePatchGridFromLists(removedGrid);
		if (StsPatchGrid.debugPatchGrid && removedGrid.id == StsPatchGrid.debugPatchID)
		{
			StsException.systemDebug(this, "mergePatchGrids", "debug patch removed; resetting debug patchID to merged ID " + mergedGrid.id);
			StsPatchGrid.debugPatchID = mergedGrid.id;
			// mergedGrid.originalID = removedGrid.id;
		}
		return mergedGrid;
	}

	/** For this new connection between different patches, check if point connected to one patch overlaps the other.
	 *  If they mutually overlap, then ignore the connection.  If not, return the patch that the connection doesn't overlap;
	 *  this connection will be added to it.
	 *
	 * @param prevPatchPoint prev row or col point connected to the prevGrid
	 * @param patchPoint point being processed
	 * @param connection connection between two points
	 * @return patch which doesn't overlap the point at other end of connection or null if patches mutually overlap at connection
	 */
	protected StsPatchGrid checkAddOverlappingConnection(PatchPoint prevPatchPoint, PatchPoint patchPoint, Connection connection)
	{
		StsPatchGrid prevPatchGrid = prevPatchPoint.getPatchGrid();
		StsPatchGrid patchGrid = patchPoint.getPatchGrid();

		if(StsPatchVolume.debug && (StsPatchGrid.doDebugPoint(patchPoint) || StsPatchGrid.doDebugPoint(prevPatchPoint)))
			StsException.systemDebug(this, "mergeOverlappingPatchGrids", StsPatchVolume.iterLabel + " CONNECT point: " +
					patchPoint.toString() + " TO point: " + prevPatchPoint.toString());

		// if points for this connection mutually overlap the other grid, ignore this connection
		// alternatively, we could make a new patchGrid containing clones of these two connection points
		if (prevPatchGrid.patchPointOverlaps(patchPoint) && patchGrid.patchPointOverlaps(prevPatchPoint))
			return null;
		else if (prevPatchGrid.patchPointOverlaps(patchPoint))
			return patchGrid;
		else if(patchGrid.patchPointOverlaps(prevPatchPoint))
			return prevPatchGrid;
		else // connection itself doesn't overlap, so add connection to largest patch
			return StsPatchGrid.getLargestGrid(patchGrid, prevPatchGrid);
	}

	protected void checkAddPatchGridToRowGrids(StsPatchGrid patchGrid)
	{
		if(patchGrid == null) return;
		int patchID = patchGrid.id;
		if (patchGrid.rowGridAdded) return;
		StsPatchGrid value = rowGrids.put(patchID, patchGrid); // if return is null, no value exists at this key
		patchGrid.rowGridAdded = true;
		if (debug && StsPatchGrid.debugPatchID == patchGrid.id)
		{
			if (value == null)
				StsException.systemDebug(this, "checkAddPatchGridToRowGrids", "patch " + patchID + " added to rowGrids for row: " + row + " col: " + col);
			else
				StsException.systemDebug(this, "checkAddPatchGridToRowGrids", "patch " + patchID + " already exists for row: " + row + " col: " + col);
		}
	}

	protected void removePatchGridFromLists(StsPatchGrid patchGrid)
	{
		StsPatchGrid value;
		int patchID = patchGrid.id;
		boolean debug = StsPatchGrid.debugPatchGrid && patchID == StsPatchGrid.debugPatchID;
		value = prevRowGrids.remove(patchID);
		if (debug)
		{
			if (value != null)
				StsException.systemDebug(this, "removePatchGridInGridList", "patch " + patchID + " removed from prevRowGrids for row: " + row);
			else
				StsException.systemDebug(this, "removePatchGridInGridList", "patch " + patchID + " doesn't exist in prevRowGrids for row: " + row);
		}
		value = rowGrids.remove(patchID);
		if (debug)
		{
			if (value != null)
				StsException.systemDebug(this, "removePatchGridInGridList", "patch " + patchID + " removed from rowGrids for row: " + row);
			else
				StsException.systemDebug(this, "removePatchGridInGridList", "patch " + patchID + " doesn't exist in rowGrids for row: " + row);
		}
	}

	public boolean initialize(StsModel model)
	{
		initializeColorscale();
		initializeSliceInterpolation();
		initializePatchPointTotal();
		return true;
	}

	private void initializeSliceInterpolation()
	{
		nInterpolationIntervals = StsTraceUtilities.computeInterpolationInterval(zInc, 5);
		interpolatedZInc = zInc / nInterpolationIntervals;
		interpolatedSliceMin = 0;
		interpolatedSliceMax = (nSlices - 1) * nInterpolationIntervals;
		nInterpolatedSlices = interpolatedSliceMax + 1;
	}

	public void initialize()
	{
//		clearSelectedPatches();
	}

	public void initializeColorscale()
	{
		try
		{
			if (colorscale == null)
			{
				StsSpectrumClass spectrumClass = currentModel.getSpectrumClass();
				colorscale = new StsColorscale("Curvature", spectrumClass.getSpectrum(StsSpectrumClass.SPECTRUM_RAINBOW), dataMin, dataMax);
				colorscale.setEditRange(dataMin, dataMax);
				colorscale.addActionListener(this);
			}
			colorscale.setRange(dataMin, dataMax);
		}
		catch (Exception e)
		{
			StsException.outputException("StsPatchcVolume.initializeColorscale() failed.", e, StsException.WARNING);
		}
	}

	public void actionPerformed(ActionEvent e)
	{
		if (e.getSource() instanceof StsColorscale)
		{
			int modifiers = e.getModifiers();
			currentModel.viewObjectChangedAndRepaint(this, this);
		}
		else
		{
			//fireActionPerformed(e);
			currentModel.viewObjectChangedAndRepaint(this, this);
		}
		return;
	}

	private void initializeLists()
	{
		gridList = new ArrayList<>(100);
		rowGrids = new HashMap<>();
		rowGridsIterator = rowGrids.values().iterator();
		prevRowGrids = new HashMap<>(); // not used on first row
	}

	private void incrementLists()
	{
		prevRowGrids = rowGrids;
		prevRowGridsIterator = rowGridsIterator;
		clearPrevRowGridsAddedFlags();
		rowGrids = new HashMap<>();
		rowGridsIterator = rowGrids.values().iterator();
	}

	private void initializeParameters()
	{
		//maxStretchLimit = 1 + maxStretch;
		//minStretchLimit = 1/(1 + maxStretch);

		if (windowSize == 0) // windowSize is 0.5 wavelengths, half-nextWindow is 0.25 wavelengths
		{
			nHalfSamples = 1;
			windowEndIsZeroCrossing = true;
			halfWindowSize = 0.25f;
			halfWindowPickDifFactor = pickDifWavelengths / 0.25f;
		}
		else
		{
			boolean isHalfWave = !StsMath.isEven(windowSize);
			if (isHalfWave)
			{
				// nextWindow size is odd, so nextWindow ends with zero-crossing; half-nextWindow size is windowSize/2.
				// we need to find (windowSize +1)/2 zero-crossings above and below nextWindow center (which is a max or min).
				nHalfSamples = (windowSize + 1) / 2;
				windowEndIsZeroCrossing = true;
				halfWindowPickDifFactor = pickDifWavelengths * 2 / windowSize;
			}
			else
			{
				// nextWindow size is even, so nextWindow ends with same nextWindow type as center; half-nextWindow size is windowSize/2.
				// we need to find windowSize/2 points above and below with same nextWindow type as nextWindow center (which is a max or min).
				nHalfSamples = windowSize / 2;
				halfWindowPickDifFactor = pickDifWavelengths / nHalfSamples;
				windowEndIsZeroCrossing = false;
			}
		}
	}

	public StsPatchVolumeClass getPatchVolumeClass()
	{
		if (patchVolumeClass != null) return patchVolumeClass;
		patchVolumeClass = (StsPatchVolumeClass) getCreateStsClass();
		return patchVolumeClass;
	}

	private void finish()
	{
		// build patchGrid arrays. Overlapped grids will be split out into new grids
		// So construct an array of existing patchGrids and construct the various 2D arrays for each.
		// New grids generated will be added to the gridList
		int nGrids = gridList.size();
		rowSortedPatchGrids = new StsPatchGrid[nGrids];
		gridList.toArray(rowSortedPatchGrids);
		// debugCheckEmptyFraction(rowSortedPatchGrids);
		StsException.systemDebug(this, "finish", " Number of parent grids: " + nParentGrids + "Number of child grids: " + nGrids + " too small: " + nSmallGridsRemoved);
		// StsException.systemDebug(this, "finish", "max grid dimension: " + StsPatchGrid.maxGridSize);

		StsPatchGrid.sortRowFirst = true;
		Arrays.sort(rowSortedPatchGrids);
		// compute the total number of points
		initializePatchPointTotal();
		StsException.systemDebug(this, "finish", "Total number of points: " + nPointsTotal);
		// reset the index of each patch to its sequence in row-ordered array
		nGrids = 0;
		for (StsPatchGrid grid : rowSortedPatchGrids)
			grid.resetIndex(nGrids++);

		// print out grid group summary
		int nParents = 0, nNew = 0, nChildren = 0;
		for (StsPatchGrid grid : rowSortedPatchGrids)
		{
			if (grid.isParent()) nParents++;
			else if (grid.isChild()) nChildren++;
			else nNew++;
		}
		StsException.systemDebug(this, "finish", "parent grids: " + nParents + " child grids: " + nChildren + " unattached grids: " + nNew);

		int num = getPatchVolumeClass().getSize();
		String newname = seismicName + ".patchVolume" + (num + 1);
		setName(newname);
		clearConstructionArrays();
		if (!isPersistent())
		{
			currentModel.getDatabase().blockTransactions();
			addToModel();
			currentModel.getDatabase().saveTransactions();
		}
		getPatchVolumeClass().setIsVisible(true);
		setIsVisible(true);
	}

	/*
		private void debugCheckEmptyFraction(StsPatchGrid[] grids)
		{
			float nTotal = 0;
			int[] used = null;
			float nUsed = 0;
			float nActualUsed = 0;

			for(StsPatchGrid grid : grids)
			{
				nTotal += grid.getGridSize();
				used  = grid.getGridPointsUsed();
				nUsed += used[0];
				nActualUsed += used[1];
			}
			float fractionUsed = nUsed/nTotal;
			float fractionActualUsed = nActualUsed/nTotal;
			StsException.systemDebug(this, "debugCheckEmptyFraction", "Fraction used: " + fractionUsed + "Fraction actualUsed: " + fractionActualUsed);
		}
	*/
	private void clearConstructionArrays()
	{
		gridList = null;
		rowGrids = null;
		prevRowGrids = null;
	}

	private void initializePatchPointTotal()
	{
		nPointsTotal = 0;
		if (rowSortedPatchGrids == null) return;
		for (StsPatchGrid patchGrid : rowSortedPatchGrids)
			nPointsTotal += patchGrid.nPatchPoints;
	}

	private boolean checkColSortedPatchGrids()
	{
		if (colSortedPatchGrids != null) return true;
		if (rowSortedPatchGrids == null) return false;
		int nGrids = rowSortedPatchGrids.length;
		if (nGrids == 0) return false;
		colSortedPatchGrids = new StsPatchGrid[nGrids];
		System.arraycopy(rowSortedPatchGrids, 0, colSortedPatchGrids, 0, nGrids);
		StsPatchGrid.sortRowFirst = false;
		Arrays.sort(colSortedPatchGrids);
		return true;
	}

	private boolean checkRowSortedPatchGrids()
	{
		return rowSortedPatchGrids != null;
	}

	StsPatchGrid getGrid(int target)
	{
		int number = gridList.size();
		int high = number, low = -1, probe;
		while (high - low > 1)
		{
			probe = (high + low) / 2;
			int id = gridList.get(probe).originalID;
			if (id > target)
				high = probe;
			else
				low = probe;
		}
		if (low == -1 || gridList.get(low).originalID != target)
			return null;
		else
			return gridList.get(low);
	}

	private void clearPrevRowGridsAddedFlags()
	{
		Iterator<StsPatchGrid> prevRowGridIterator = prevRowGrids.values().iterator();
		while (prevRowGridIterator.hasNext())
		{
			StsPatchGrid patchGrid = prevRowGridIterator.next();
			patchGrid.rowGridAdded = false;
		}
	}

	/** Called for the last row only; unconditionally add all rowGrids unless they are too small. */
	void addRemainingGrids()
	{
		StsPatchGrid[] patchGrids = rowGrids.values().toArray(new StsPatchGrid[0]);
		for (int n = 0; n < patchGrids.length; n++)
		{
			StsPatchGrid patchGrid = patchGrids[n];
			if (!patchGrid.isTooSmall(nPatchPointsMin))
			{
				patchGrid.finish();
				gridList.add(patchGrid);
			}
		}
		StsException.systemDebug(this, "addRemainingGrids", "added " + patchGrids.length + " grids remaining.");
	}

	public void setCroppedBoundingBox(StsCroppedBoundingBox croppedBoundingBox)
	{
		this.croppedBoundingBox = croppedBoundingBox;
		croppedBoundingBox.setCroppedBoxRange();
	}

	/*
	* run the values calculation on the patches
	*/
	public void runCurvature(StsProgressPanel progressPanel, int filterSize, byte curveType, boolean runAllPatches)
	{
		this.filterSize = filterSize;

		if (runTimer)
		{
			timer = new StsTimer();
			timer.start();
		}
		StsPatchGrid[] runPatches = getRunCurvaturePatches(runAllPatches);
		int numPatches = runPatches.length;
		if (progressPanel != null)
			progressPanel.initialize(numPatches);
		int nValuePoints = 0;
		int nPoints = 0;
		float[] values = new float[nPointsTotal];
		double sum = 0;
		int progressUpdateInterval = Math.max(numPatches / 200, 1);
		int minNPoints = Math.min(filterSize * filterSize, StsQuadraticCurvature.minNPoints);
		for (int i = 0; i < numPatches; i++)
		{
			StsPatchGrid patch = runPatches[i];
			if (patch == null) continue;
			nPoints += patch.nPatchPoints;
			if (patch.computeCurvature(curveType, filterSize, minNPoints))
			{
				for (int row = patch.rowMin; row <= patch.rowMax; row++)
				{
					for (int col = patch.colMin; col <= patch.colMax; col++)
					{
						int ptCol = col - patch.colMin;
						int ptRow = row - patch.rowMin;
						float value = patch.values[ptRow][ptCol];
						if (value == StsPatchVolume.nullValue) continue;
						if (value == badCurvature || value == -badCurvature) continue;
						values[nValuePoints++] = value;
						sum += value;
					}
				}
			}

			if (progressPanel == null) continue;
			if (progressPanel.isCanceled())
			{
				progressPanel.setDescriptionAndLevel("Cancelled by user.", StsProgressBar.ERROR);
				clearPatches();
				return;
			}
			if (i % progressUpdateInterval == 0) progressPanel.setValue(i);
		}

		values = (float[]) StsMath.trimArray(values, nValuePoints);

		StsMessageFiles.infoMessage("Number values points: " + nValuePoints + " number of patch points " + nPointsTotal);
		if (debug)
			StsException.systemDebug(this, "runCurvature", "Number values points: " + nValuePoints + " number of patch points " + nPointsTotal);


		double mean = sum / nValuePoints;
		int histogramDataInc = Math.max(1, nValuePoints / nHistogramValues);
		nHistogramValues = nValuePoints / histogramDataInc;
		histogramValues = new float[nHistogramValues + 1];
		double avgDev = 0;
		double sumSqr = 0;
		nHistogramValues = 0;
		for (int n = 0; n < nValuePoints; n++)
		{
			float value = values[n];
			double dif = value - mean;
			avgDev += Math.abs(dif);
			sumSqr += dif * dif;
			if (n % histogramDataInc == 0)
				histogramValues[nHistogramValues++] = value;
		}
		avgDev = avgDev / nValuePoints;
		double variance = (sumSqr) / nValuePoints;
		double stdDev = Math.sqrt(variance);
		StsMessageFiles.infoMessage("mean " + mean + " avg dev: " + avgDev + " std dev: " + stdDev);
		if (debug)
			StsException.systemDebug(this, "runCurvature", "mean " + mean + " avg dev: " + avgDev + " std dev: " + stdDev);
		dataMin = (float) (mean - 2.0 * avgDev);
		dataMax = (float) (mean + 2.0 * avgDev);
		colorscale.setRange(dataMin, dataMax);
		StsMessageFiles.infoMessage("range set to +- 2.0*avg dev: " + dataMin + " to " + dataMax);
		if (debug)
			StsException.systemDebug(this, "runCurvature", "range set to +- 2.0*std dev: " + dataMin + " to " + dataMax);

//        // reset outliers to dataMin/dataMax
//        for (StsPatchGrid patch : rowSortedPatchGrids)
//        {
//            if (patch == null) continue;
//            for (int row = patch.rowMin; row <= patch.rowMax; row++)
//            {
//                for (int col = patch.colMin; col <= patch.colMax; col++)
//                {
//                    float value = patch.getCurvature(row, col, dataMin, dataMax);
//                    if (value == StsPatchVolume.nullValue) continue;
//                }
//            }
//        }
		calculateHistogram(histogramValues, nHistogramValues);
		progressPanel.finished();

		if (runTimer) timer.stopPrint("Time to compute values for " + numPatches + " patches.");
	}

	private void clearPatches()
	{
		for (StsPatchGrid patch : rowSortedPatchGrids)
		{
			if (patch != null) patch.clear();
		}
	}

	private StsPatchGrid[] getRunCurvaturePatches(boolean runAllPatches)
	{
		if (runAllPatches || this.drawPatchGrids == null) return rowSortedPatchGrids;
		else return drawPatchGrids;
	}

	private ArrayList<TracePoints> getOtherTraces(TracePoints newTrace, TracePoints prevColTrace, TracePoints[] prevRowTraces)
	{
		ArrayList<TracePoints> otherTraces = new ArrayList<>();
		if (prevColTrace != null)
		{
			addOtherTrace(otherTraces, prevColTrace);
		}
		int col = newTrace.col;
		if (prevRowTraces != null)
		{
			// if (col > colMin)
			//    addOtherTrace(otherTraces, prevRowTraces[col - 1]);
			addOtherTrace(otherTraces, prevRowTraces[col]);
			// if (col < colMax)
			//    addOtherTrace(otherTraces, prevRowTraces[col + 1]);
		}
		return otherTraces;
	}

	private void addOtherTrace(ArrayList<TracePoints> otherTraces, TracePoints otherTrace)
	{
		if (otherTrace.nTracePatchPoints == 0) return;
		otherTraces.add(otherTrace);
	}

	private StsPatchGrid getPatchGrid(int id)
	{
		StsPatchGrid patchGrid = rowGrids.get(id);
		// if patchGrid exists in rowGrids, then it has already been added there and deleted from prevRowGrids
		if (patchGrid != null)
		{
			if (StsPatchGrid.debugPatchID != -1 && (id == StsPatchGrid.debugPatchID))
				StsException.systemDebug(this, "getPatchGrid", "patch grid " + id +
						" gotten from rowGrids at row: " + row + " col: " + col);
			return patchGrid;
		}
		// if patchGrid is not in rowGrids, then add it there and delete it from prevRowGrids
		else if (prevRowGrids != null)
		{
			patchGrid = prevRowGrids.get(id);
			if (patchGrid != null)
			{
				rowGrids.put(id, patchGrid);
				prevRowGrids.remove(id);
				if (StsPatchGrid.debugPatchID != -1 && (id == StsPatchGrid.debugPatchID))
					StsException.systemDebug(this, "getPatchGrid", "patch grid " + id +
							" gotten and deleted from prevRowsGrids and added to rowGrids at row: " + row + " col: " + col);
				return patchGrid;
			}
		}
		StsException.systemError(this, "getPatchGrid", "Couldn't get patchGrid for id " + id + " at row: " + " col: " + col);
		return null;
	}

	/**
	 * This PatchGridSet is for the row before row just finished.
	 * If a grid in this prev row is disconnected (doesn't have same patch in row just finished),
	 * then either delete it if it is a small nextWindow, or add it to volume set.
	 */
	void processPrevRowGrids(int row)
	{
		if (row == 0) return;
		StsPatchGrid[] prevRowPatchGrids = prevRowGrids.values().toArray(new StsPatchGrid[0]);
		int nDisconnectedGrids = 0;
		for (StsPatchGrid patchGrid : prevRowPatchGrids)
		{
			boolean disconnected = patchGrid.isDisconnected(row);
			if (!disconnected) continue;
			if (patchGrid.isTooSmall(nPatchPointsMin))
				nSmallGridsRemoved++;
			else
			{
				patchGrid.finish();
				gridList.add(patchGrid);
				nDisconnectedGrids++;
			}
			prevRowGrids.remove(patchGrid.id);
		}
		StsException.systemDebug(this, "processPrevRowGrids", "prev row: " + (row - 1) + " added " + nDisconnectedGrids + " disconnected grids");
	}

	public StsColorscale getCurvatureColorscale()
	{
		return colorscale;
	}

	/* Draw any map edges on all 2d sections */
	public void drawOnCursor2d(StsGLPanel3d glPanel3d, int dirNo, float dirCoordinate, boolean axesFlipped,
							   boolean xAxisReversed, boolean yAxisReversed)
	{
		if (!getIsVisible()) return;

		GL gl = glPanel3d.getGL();
		if (gl == null) return;
		boolean displayCurvature = getPatchVolumeClass().getDisplayCurvature();
		// StsColor drawColor = StsColor.BLACK; //getStsColor();
		gl.glLineWidth(getPatchVolumeClass().getEdgeWidth());

		if (dirNo == StsCursor3d.XDIR) /* map edge is along a col	*/
		{
			if (!checkColSortedPatchGrids()) return;
			int volCol = getNearestColCoor(dirCoordinate);
			gl.glDisable(GL.GL_LIGHTING);
			gl.glShadeModel(GL.GL_SMOOTH);

			float x = dirCoordinate;
			// int nFirst = -1;
			// int n = -1;

			for (StsPatchGrid patchGrid : colSortedPatchGrids)
			{
				int col = volCol - patchGrid.colMin;
				if (col < 0) break;;
				if (col >= patchGrid.nCols) continue;

				if(!displayCurvature)
					StsTraceUtilities.getPointTypeColor(patchGrid.patchType).setGLColor(gl);
				if (drawPatchBold && patchGrid.id == StsPatchGrid.debugPatchID)
					gl.glLineWidth(2 * getPatchVolumeClass().getEdgeWidth());
				patchGrid.drawVolColGridLine(gl, volCol, colorscale, displayCurvature, false);
				if (drawPatchBold && patchGrid.id == StsPatchGrid.debugPatchID);
				gl.glLineWidth(getPatchVolumeClass().getEdgeWidth());
				// if (nFirst == -1) nFirst = n;
			}
			gl.glEnable(GL.GL_LIGHTING);
		}
		else if (dirNo == StsCursor3d.YDIR)
		{
			if (!checkRowSortedPatchGrids()) return;
			int row = getNearestRowCoor(dirCoordinate);
			gl.glDisable(GL.GL_LIGHTING);
			gl.glLineWidth(StsGraphicParameters.edgeLineWidth);
			gl.glShadeModel(GL.GL_SMOOTH);

			for (StsPatchGrid patchGrid : rowSortedPatchGrids)
			{
				if (patchGrid.rowMin > row) break;
				// n++;
				if (patchGrid.rowMax < row) continue;
				if (drawPatchBold && patchGrid.id == StsPatchGrid.debugPatchID)
					gl.glLineWidth(2 * getPatchVolumeClass().getEdgeWidth());
				patchGrid.drawRowGridLine(gl, row, dirCoordinate, colorscale, displayCurvature, false);
				if (drawPatchBold && patchGrid.id == StsPatchGrid.debugPatchID) ;
				gl.glLineWidth(getPatchVolumeClass().getEdgeWidth());
				// if (nFirst == -1) nFirst = n;
				if (drawPatchBold && patchGrid.id == StsPatchGrid.debugPatchID) break;
			}
			gl.glEnable(GL.GL_LIGHTING);
		}
	}

	/** Draw any map edges on section */
	public void drawOnCursor3d(StsGLPanel3d glPanel3d, int dirNo, float dirCoordinate)
	{
		if (!getIsVisible()) return;
		GL gl = glPanel3d.getGL();
		if (gl == null) return;
		boolean displayCurvature = getPatchVolumeClass().getDisplayCurvature();
		try
		{
			if (dirNo == StsCursor3d.ZDIR)
			{
				if (getDisplaySurfs())
					// displayPatchesNearXYZCursors(glPanel3d);
					displayPatchesNearZCursor(glPanel3d, dirCoordinate);
				return;
			}
			if (dirNo == StsCursor3d.YDIR)
			{
				if (!checkRowSortedPatchGrids()) return;
				gl.glDisable(GL.GL_LIGHTING);
				gl.glShadeModel(GL.GL_SMOOTH);
				// StsColor drawColor = StsColor.BLACK.setGLColor(gl);
				gl.glLineWidth(getPatchVolumeClass().getEdgeWidth());
				glPanel3d.setViewShift(gl, StsGraphicParameters.gridShift);
				int row = getNearestRowCoor(dirCoordinate);
				if (row == -1) return;
				for (StsPatchGrid patchGrid : rowSortedPatchGrids)
				{
					if (patchGrid.rowMin > row) break;
					if (patchGrid.rowMax < row) continue;
					if(!displayCurvature)
						StsTraceUtilities.getPointTypeColor(patchGrid.patchType).setGLColor(gl);
					if (drawPatchBold && patchGrid.id == StsPatchGrid.debugPatchID)
						gl.glLineWidth(2 * getPatchVolumeClass().getEdgeWidth());
					patchGrid.drawVolRowGridLine(gl, row, colorscale, displayCurvature, true);
					if (drawPatchBold && patchGrid.id == StsPatchGrid.debugPatchID) ;
					gl.glLineWidth(getPatchVolumeClass().getEdgeWidth());
				}
			}
			else if (dirNo == StsCursor3d.XDIR)
			{
				if (!checkColSortedPatchGrids()) return;
				gl.glDisable(GL.GL_LIGHTING);
				gl.glShadeModel(GL.GL_SMOOTH);
				StsColor drawColor = StsColor.BLACK; //getStsColor();
				drawColor.setGLColor(gl);
				gl.glLineWidth(getPatchVolumeClass().getEdgeWidth());
				glPanel3d.setViewShift(gl, StsGraphicParameters.gridShift);
				int volCol = getNearestColCoor(dirCoordinate);
				if (volCol == -1) return;
				for (StsPatchGrid patchGrid : colSortedPatchGrids)
				{
					if (patchGrid.colMin > volCol) break;
					if (patchGrid.colMax < volCol) continue;
					if(!displayCurvature)
						StsTraceUtilities.getPointTypeColor(patchGrid.patchType).setGLColor(gl);
					if (drawPatchBold && patchGrid.id == StsPatchGrid.debugPatchID)
						gl.glLineWidth(2 * getPatchVolumeClass().getEdgeWidth());
					patchGrid.drawVolColGridLine(gl, volCol, colorscale, displayCurvature, true);
					if (drawPatchBold && patchGrid.id == StsPatchGrid.debugPatchID) ;
					gl.glLineWidth(getPatchVolumeClass().getEdgeWidth());
				}
			}
		}
		catch (Exception e)
		{
			StsException.outputWarningException(this, "drawOnCursor3d", e);
		}
		finally
		{
			glPanel3d.resetViewShift(gl);
			gl.glEnable(GL.GL_LIGHTING);
		}

	}

	public void pickOnCursor3d(StsGLPanel3d glPanel3d)
	{
		StsCursor3d cursor3d = glPanel3d.window.getCursor3d();
		if (cursor3d == null) return;
		for (int dir = 0; dir < 2; dir++)
		{
			float dirCoordinate = cursor3d.getCurrentDirCoordinate(dir);
			drawOnCursor3d(glPanel3d, dir, dirCoordinate);
		}
	}

	public void display(StsGLPanel glPanel)
	{
		boolean debugDisplay = debug && StsPatchGrid.debugPatchGrid && StsPatchGrid.debugPatchDraw;
		boolean display = getDisplaySurfs();
		if (!display && !debugDisplay) return;

		GL gl = glPanel.getGL();

		boolean displayCurvature = getPatchVolumeClass().getDisplayCurvature();

		initializePatchDraw(gl);

		boolean displayChildPatches = getPatchVolumeClass().getDisplayChildPatches();
		/*
		if(displayChildPatches != displayingChildPatches)
		{
			displayingChildPatches = displayChildPatches;
			rebuildDrawPatches();
		}
        */
		if(debugDisplay)
		{
			StsPatchGrid patchGrid = getRowSortedPatchGrid(StsPatchGrid.debugPatchID);
			if(patchGrid == null) return;
			patchGrid.draw(gl, displayCurvature, colorscale);
			return;
		}

		for(PatchGridGroup gridGroup : patchGridGroups)
			gridGroup.drawGrids(gl, displayChildPatches, displayCurvature, colorscale);

		if (getDisplayVoxels())
		{
			displayVoxels(glPanel);
		}
	}

	public void displayVoxels(StsGLPanel glPanel3d)
	{
		//System.out.println("Display Voxels");

		GL gl = glPanel3d.getGL();
		if (gl == null) return;

		{
			gl.glDisable(GL.GL_LIGHTING);
			gl.glLineWidth(StsGraphicParameters.edgeLineWidth);
			gl.glColor4f(1.f, 1.f, 1.f, 1.f);
			float xMin = getXMin();
			float yMin = getYMin();
			for (StsPatchGrid patchGrid : rowSortedPatchGrids)
				patchGrid.drawRowVox(gl, yMin, xMin, colorscale);
			gl.glEnable(GL.GL_LIGHTING);
		}

	}

	// surfaces near cursor only to keep clutter down
	public void displayPatchesNearZCursor(StsGLPanel glPanel3d, float z)
	{
		//System.out.println("Display Surfaces");
		GL gl = glPanel3d.getGL();
		if (gl == null) return;

		initializePatchDraw(gl);
		boolean displayCurvature = getPatchVolumeClass().getDisplayCurvature();
		for (StsPatchGrid patchGrid : rowSortedPatchGrids)
		{
			if (patchGrid.isPatchGridNearZCursor(z))
				patchGrid.draw(gl, displayCurvature, colorscale);
		}
	}

	private void initializePatchDraw(GL gl)
	{
		gl.glEnable(GL.GL_LIGHTING);
		gl.glShadeModel(GL.GL_SMOOTH);
		gl.glEnable(GL.GL_NORMALIZE);
		gl.glLightModeli(GL.GL_LIGHT_MODEL_TWO_SIDE, GL.GL_TRUE);
		// gl.glLightModeli(GL.GL_LIGHT_MODEL_COLOR_CONTROL, GL.GL_SEPARATE_SPECULAR_COLOR);
		//gl.glDisable(GL.GL_CULL_FACE);
		//gl.glDisable(GL.GL_POLYGON_STIPPLE);
		gl.glColor4f(1.f, 1.f, 1.f, 1.f);
	}

	public void displayPatchesNearXYZCursors(StsGLPanel glPanel3d)
	{
		StsCursor3d cursor3d = currentModel.win3d.getCursor3d();
		GL gl = glPanel3d.getGL();
		if (gl == null) return;

		float x = cursor3d.getCurrentDirCoordinate(XDIR);
		float y = cursor3d.getCurrentDirCoordinate(YDIR);
		float z = cursor3d.getCurrentDirCoordinate(ZDIR);
		StsPoint cursorPoint = new StsPoint(x, y, z);
		boolean displayCurvature = getPatchVolumeClass().getDisplayCurvature();
		if (currentCursorPoint != null && currentCursorPoint.equals(cursorPoint) && cursorPointPatch != null)
		{
			drawPatch(cursorPointPatch, displayCurvature, gl);
			return;
		}

		currentCursorPoint = null;

		int volumeRow = getNearestRowCoor(y);
		if (volumeRow == -1) return;
		int volumeCol = getNearestColCoor(x);
		if (volumeCol == -1) return;
		int slice = getNearestSliceCoor(z);
		if (slice == -1) return;
		float dzPatch = largeFloat;
		cursorPointPatch = null;
		for (StsPatchGrid patchGrid : rowSortedPatchGrids)
		{
			if (patchGrid == null) continue;
			// if(patchGrid.values == null) continue;
			if (patchGrid.rowMin > volumeRow) break;
			if (patchGrid.rowMax >= volumeRow)
			{
				if (patchGrid.colMin <= volumeCol && patchGrid.colMax >= volumeCol)
				{
					float dz = patchGrid.getZDistance(volumeRow, volumeCol, z);
					if (dz < dzPatch)
					{
						cursorPointPatch = patchGrid;
						dzPatch = dz;
						currentCursorPoint = new StsPoint(x, y, z);
					}
				}
			}
		}
		if (cursorPointPatch != null)
				drawPatch(cursorPointPatch, displayCurvature, gl);
	}

	private void drawPatch(StsPatchGrid patch, boolean displayCurvature, GL gl)
	{
		initializePatchDraw(gl);
		patch.draw(gl, displayCurvature, colorscale);
	}

//	public void setWizard(StsVolumeCurvatureWizard wizard) {
//		this.wizard = wizard;
//	}
//
//	public StsVolumeCurvatureWizard getWizard() {
//		return wizard;
//	}

	public int[] getPatchRangeForRow(int volumeRow)
	{
		int rowMin = -1;
		int rowMax = -1;
		int nPatchGrids = rowSortedPatchGrids.length;
		int n;
		for (n = 0; n < nPatchGrids; n++)
		{
			StsPatchGrid patchGrid = rowSortedPatchGrids[n];
			if (patchGrid == null) continue;
			rowMax = n - 1;
			if (rowMin == -1)
				if (patchGrid.rowMin <= volumeRow && patchGrid.rowMax >= volumeRow) rowMin = n;
				else if (patchGrid.rowMin > volumeRow)
					break;
		}
		if (rowMin == -1)
			return new int[]{0, 0};
		else
			return new int[]{rowMin, rowMax};
	}

	public int[] getPatchRangeForCol(int col)
	{
		int colMin = -1;
		int colMax = -1;
		int nPatchGrids = colSortedPatchGrids.length;
		for (int n = 0; n < nPatchGrids; n++)
		{
			StsPatchGrid patchGrid = rowSortedPatchGrids[n];
			if (patchGrid == null) continue;
			if (colMin == -1)
			{
				if (patchGrid.colMin <= col && patchGrid.colMax >= col) colMin = n;
			}
			else if (patchGrid.colMin > col)
			{
				colMax = n - 1;
				break;
			}
		}
		if (colMin == -1 || colMax == -1)
			return new int[]{0, 0};
		else
			return new int[]{colMin, colMax};
	}

	public boolean getTraceCurvature(int volRow, int volCol, float[] buffer, int[] patchRange)
	{
		try
		{
			Arrays.fill(buffer, nullValue);
			int nPatchMin = patchRange[0];
			int nPatchMax = patchRange[1];
			boolean traceLoaded = false;
			for (int nPatch = nPatchMin; nPatch <= nPatchMax; nPatch++)
			{
				StsPatchGrid patchGrid = rowSortedPatchGrids[nPatch];
				if (patchGrid == null) continue;
				if (patchGrid.values == null) continue;
				int patchRow = volRow - patchGrid.rowMin;
				int patchCol = volCol - patchGrid.colMin;
				if (patchRow < 0 || patchCol < 0 || patchRow >= patchGrid.nRows || patchCol >= patchGrid.nCols)
					continue;
				float[][] pointsZ = patchGrid.getPointsZ();
				if (pointsZ == null) continue;
				float z = pointsZ[patchRow][patchCol];
				if (z == StsParameters.nullValue) continue;
				float val = patchGrid.values[patchRow][patchCol];
				if (val == nullValue) continue;
				int slice = getNearestSliceCoor(z);
				buffer[slice] = val;
				traceLoaded = true;
			}
			return traceLoaded;
		}
		catch (Exception e)
		{
			StsException.outputWarningException(this, "getTraceCurvature", e);
			return false;
		}
	}

	public int getNearestSliceCoor(float z)
	{
		int slice = Math.round((z - zMin) / interpolatedZInc);
		if (slice < 0 || slice >= nInterpolatedSlices) return -1;
		return slice;
	}

	public String toString()
	{
		return name;
	}

	public void treeObjectSelected()
	{
		getPatchVolumeClass().selected(this);
		currentModel.getGlPanel3d().checkAddView(StsView3d.class);
		currentModel.win3dDisplayAll();
	}


	public Object[] getChildren()
	{
		return new Object[0];
	}

	public StsFieldBean[] getDisplayFields()
	{
		//displayAttributeBean.setListItems(displayAttributes);
		return displayFields;
	}

	public StsFieldBean[] getPropertyFields()
	{
		return propertyFields;
	}


	public StsObjectPanel getObjectPanel()
	{
		if (objectPanel == null)
		{
			objectPanel = StsObjectPanel.constructor(this, true);
		}
		return objectPanel;
	}

	public boolean anyDependencies()
	{
		return false;
	}

	public StsColorscale getColorscale()
	{
		//setDataHistogram();
		return colorscale;
	}

	public void setColorscale(StsColorscale colorscale)
	{
		this.colorscale = colorscale;
		currentModel.win3dDisplayAll();
	}

	public void setDisplaySurfs(boolean displaySurfs)
	{
		if (this.displaySurfs == displaySurfs)
			return;
		this.displaySurfs = displaySurfs;
		if(!displaySurfs) clearSelectedPatches();
		currentModel.win3dDisplayAll();
	}

	public boolean getDisplaySurfs()
	{
		return displaySurfs;
	}

	public void setDisplayVoxels(boolean displayVoxels)
	{
		if (this.displayVoxels == displayVoxels)
			return;
		this.displayVoxels = displayVoxels;
		currentModel.win3dDisplayAll();
	}

	public boolean getDisplayVoxels()
	{
		return displayVoxels;
	}

	public void setIsVisible(boolean vis)
	{
		super.setIsVisible(vis);
		currentModel.win3dDisplayAll();
	}

	public void setDataMin(float min)
	{
		dataMin = min;
		if (colorscale == null) return;
		colorscale.setRange(dataMin, dataMax);
	}

	public void setDataMax(float max)
	{
		dataMax = max;
		if (colorscale == null) return;
		colorscale.setRange(dataMin, dataMax);
	}

	class PatchGridGroup
	{
		StsPatchGrid selectedGrid;
		ArrayList<StsPatchGrid> groupGrids;

		PatchGridGroup(StsPatchGrid selectedGrid)
		{
			this.selectedGrid = selectedGrid;
			groupGrids = getGroupGrids(selectedGrid);
		}

		public void drawGrids(GL gl, boolean displayChildPatches, boolean displayCurvature, StsColorscale colorscale)
		{
			if(!displayChildPatches)
				selectedGrid.draw(gl, displayCurvature, colorscale);
			else
			{
				for (StsPatchGrid grid : groupGrids)
					grid.draw(gl, displayCurvature, colorscale);
			}
		}

		/** For this selected grid: 1) add to a new group of grids, 2) hierarchically add it's connected grids as long as they meet the group criteria.
		 *
		 * @param selectedGrid  the grid selected which will be first in the group
		 * @return  the group of grids including the selected grid and all qualified connecting grids
		 */
		ArrayList<StsPatchGrid> getGroupGrids(StsPatchGrid selectedGrid)
		{
			ArrayList<StsPatchGrid> groupGrids = new ArrayList<>();   // set of grids for this group
			ArrayList<StsPatchGrid> neighborGrids = new ArrayList<>();  // potential neighbor grids which might belong to grid
			ArrayList<StsPatchGrid> rejectedGrids = new ArrayList<>();  // grids which don't qualify for group (overlap too much, etc)

			// selected grid belongs to group, so add
			neighborGrids.add(selectedGrid);
			// array of booleans for all points added to group by grids indicating point has been filled; used to check for overlaps
			//boolean[][] gridHasPoint = new boolean[nRows][nCols];
			PatchPoint[][] groupPoints = new PatchPoint[nRows][nCols];
			// loop over neighbor grids; add grid if it qualifies; regardless add it's new neighbors to end of neighborGrids array
			// so neighborGrids will keep growing until all qualified grids are added and list is exhausted
			for(int n = 0; n < neighborGrids.size(); n ++)
			{
				StsPatchGrid neighborGrid = neighborGrids.get(n);
				if(!groupGrids.contains(neighborGrid) && !rejectedGrids.contains(neighborGrid))
				{
					int nOverlaps = 0;
					int nNonOverlaps = 0;
					PatchPoint[][] patchPoints = neighborGrid.patchPoints;
					ArrayList<StsPatchGrid> newNeighborGrids = new ArrayList<>(); // newNeighborGrids is array of grids connected to this neighborGrid
					ArrayList<NewGroupPoint> newGroupPoints = new ArrayList<>(); // possible new points; will not be added if grid not added
					for(int r = 0, row = neighborGrid.rowMin; r < neighborGrid.nRows; r++, row++)
					{
						for (int c = 0, col = neighborGrid.colMin; c < neighborGrid.nCols; c++, col++)
						{
							PatchPoint patchPoint = patchPoints[r][c];
							if (patchPoint == null) continue;
							if (groupPoints[row][col] != null)
								nOverlaps++;
							else
							{
								newGroupPoints.add(new NewGroupPoint(row, col, patchPoint));
								// groupPoints[row][col] = patchPoint;
								nNonOverlaps++;
							}
							patchPoint.checkAddNewNeighborGrids(newNeighborGrids);
						}
					}
					// criteria for new neighborGrid to be added to group: add regardless if few than 16 points
					// or if number of non-overlapped points is more than twice the number of overlapped points
					// don't add if none of this new neighborGrids connected grids (newNeighborGrids) connect back to a group grid.
					// this prevents a grid from being added to the group which is not connected to the group;
					// this might occur if the potentially connected grids didn't qualify for the group
					if ( (neighborGrid.nPatchPoints < 16 || nNonOverlaps > 2 * nOverlaps ) &&
							isNeighborGridConnected(newNeighborGrids, groupGrids))
					{
						addNewGroupPoints(groupPoints, newGroupPoints);
						groupGrids.add(neighborGrid);
						// for each of the new neighbor grids to this grid, add them to the neighborGrids array if not already in the array or rejected
						for(StsPatchGrid newNeighborGrid : newNeighborGrids)
							if(!alreadyDrawingGrid(newNeighborGrid) && !neighborGrids.contains(newNeighborGrid) && !rejectedGrids.contains(newNeighborGrid))
								neighborGrids.add(newNeighborGrid);
					}
					else // if the grid doesn't qualify, put it on the rejected grids list; a subsequent neighbor grid might be connected
					     // so we check the rejectedGrids to make sure it won't be processed again
						rejectedGrids.add(neighborGrid);
				}
			}
			return groupGrids;
		}

		void addNewGroupPoints(PatchPoint[][] groupPoints, ArrayList<NewGroupPoint> newGroupPoints)
		{
			for(NewGroupPoint newGroupPoint : newGroupPoints)
			{
				newGroupPoint.addTo(groupPoints);
			}
		}

		public String toString()
		{
			return "selectedGrid: " + selectedGrid.toString() + " groupGrids: " + Arrays.toString(groupGrids.toArray());
		}

		class NewGroupPoint
		{
			int row;
			int col;
			PatchPoint point;

			NewGroupPoint(int row, int col, PatchPoint point)
			{
				this.row = row;
				this.col = col;
				this.point = point;
			}

			void addTo(PatchPoint[][] groupPoints)
			{
				groupPoints[row][col] = point;
			}
		}

		boolean alreadyDrawingGrid(StsPatchGrid newNeighborGrid)
		{
			return StsMath.arrayContains(drawPatchGrids, newNeighborGrid);
		}

		boolean isNeighborGridConnected(ArrayList<StsPatchGrid> newNeighborGrids, ArrayList<StsPatchGrid> groupGrids)
		{
			if(groupGrids.size() == 0) return true;
			for(StsPatchGrid newNeighborGrid : newNeighborGrids)
				if(groupGrids.contains(newNeighborGrid)) return true;
			return false;
		}
	}

	public void addRemoveSelectedPatch(StsCursorPoint cursorPoint)
	{
		float[] xyz = cursorPoint.point.v;
		int volumeRow = getNearestRowCoor(xyz[1]);
		int volumeCol = getNearestColCoor(xyz[0]);
		float z = xyz[2];
		StsPatchGrid selectedPatch = getNearestPatch(volumeRow, volumeCol, z);
		if (selectedPatch == null) return;

		float iline = getRowNumFromRow(volumeRow);
		float xline = getColNumFromCol(volumeCol);
//		StsMessageFiles.logMessage("Picked patch: " + selectedPatch.getPatchTypeString() + " " + selectedPatch.toFamilyString() + " at iline: " + iline + " xline: " + xline + " z: " + z);
	/*
		if(cursorPoint.dirNo == StsCursor3d.YDIR)
            StsMessageFiles.logMessage("     volumeRow correl: " + selectedPatch.getVolumeRowCorrel(volumeRow, volumeCol));
        else //dirNo == XDIR
            StsMessageFiles.logMessage("     volumeRow correl: " + selectedPatch.getVolumeColCorrel(volumeRow, volumeCol));
    */
		// int nSheet = selectedPatch.nSheet;

//		boolean displayChildPatches = getPatchVolumeClass().getDisplayChildPatches();
//		if(displayChildPatches)
//			selectedPatch = selectedPatch.getParentGrid();

		String addRemove;
		// if this selected patch is already being drawn, then selecting it means we wish to remove it and all grids in its group
		PatchGridGroup removedGroup = patchGridGroupsContains(selectedPatch);
		if (removedGroup != null)
			addRemove = " removed ";
		else
			addRemove = " added ";

//		String gridsString = selectedPatch.getGridDescription();
		StsMessageFiles.logMessage(addRemove + " patch: " + selectedPatch.getPatchTypeString() + " " + selectedPatch.toFamilyString() + " at iline: " + iline + " xline: " + xline + " z: " + z);

		if (removedGroup != null)
			// remove
			removePatchGridGroup(removedGroup);
		else
		{
			addPatchGridGroup(selectedPatch);
		}
		currentModel.win3dDisplayAll();
	}

	PatchGridGroup patchGridGroupsContains(StsPatchGrid selectedPatch)
	{
		if(patchGridGroups == null) return null;
		for(PatchGridGroup gridGroup : patchGridGroups)
			if(gridGroup.selectedGrid == selectedPatch) return gridGroup;
		return null;
	}

	void removePatchGridGroup(PatchGridGroup removedGroup)
	{
		patchGridGroups = (PatchGridGroup[])StsMath.arrayDeleteElement(patchGridGroups, removedGroup);
		rebuildDrawPatches();
	}

	void rebuildDrawPatches()
	{
		if(patchGridGroups == null) return;
		ArrayList<StsPatchGrid> drawPatchesList = new ArrayList<>();
		for(PatchGridGroup gridGroup : patchGridGroups)
		{
			if(displayingChildPatches)
				drawPatchesList.addAll(gridGroup.groupGrids);
			else
				drawPatchesList.add(gridGroup.selectedGrid);
		}
		drawPatchGrids = drawPatchesList.toArray(new StsPatchGrid[0]);
	}


	void addPatchGridGroup(StsPatchGrid selectedPatch)
	{
		PatchGridGroup newGridGroup = new PatchGridGroup(selectedPatch);
		patchGridGroups = (PatchGridGroup[])StsMath.arrayAddElement(patchGridGroups, newGridGroup);
		rebuildDrawPatches();
	}

	private void clearSelectedPatches()
	{
		patchGridGroups = null;
	}

	public StsPatchGrid getRowSortedPatchGrid(int index)
	{
		if (index <= 0 ||index >= rowSortedPatchGrids.length)
		{
			StsException.systemError(this, "getRowSortedPatchGrid", "index " + index +
					" out of rowSortedPatchGrid array of length " + rowSortedPatchGrids.length);
			return null;
		}
		StsPatchGrid patchGrid =  rowSortedPatchGrids[index];
		if (index != patchGrid.id)
		{
			StsException.systemError(this, "getRowSortedPatchGrid", "grid index " + index +
					" does not correspond to grid id " + patchGrid.id);
			return null;
		}
		return patchGrid;
	}

	public StsPatchGrid getNearestPatch(int volumeRow, int volumeCol, float z)
	{
		StsPatchGrid nearestPatch = null;
		float nearestPatchZ = largeFloat;
		//TODO make an iterator which returns patches which cross this volumeRow
		int[] patchRange = this.getPatchRangeForRow(volumeRow);
		int patchMin = patchRange[0];
		int patchMax = patchRange[1];
		for (int n = patchMin; n <= patchMax; n++)
		{
			StsPatchGrid patchGrid = rowSortedPatchGrids[n];
			float patchZ = patchGrid.getVolumePointZ(volumeRow, volumeCol);
			if (patchZ == nullValue) continue;
			float dz = Math.abs(z - patchZ);
			if (dz < nearestPatchZ)
			{
				nearestPatch = patchGrid;
				nearestPatchZ = dz;
			}
		}
		return nearestPatch;
	}
}

class TracePoints
{
	StsPatchVolume patchVolume;
	/** volume row for this trace */
	int row;
	/** volume col for this trace */
	int col;
	/** array of PatchPoints for this trace of various pointTypes (min, max, +zero-crossing, -zero-crossing; can be false or missing) */
	PatchPoint[] tracePatchPoints = new PatchPoint[0];
	/** length of tracePatchPoints array */
	int nTracePatchPoints;
	/** offset to first plus-zero-crossing in tracePatchPoints array */
	int zeroPlusOffset;
	/** a half-wave length nextWindow for each legitimate nextWindow (acceptable pointType) */
	CorrelationWindow[] windows;
	/** number of windows in this trace; one for which tracePatchPoint */
	int nWindows;
	/** double-linked list of connections between this trace and prevCol */
	ConnectionList colConnections;
	/** double-linked list of connections between this trace and prevCol */
	ConnectionList rowConnections;
	/** closest connections between this trace and the prevColTrace */
	CorrelationWindow[] colCloseConnectWindows;
	/** closest connections between this trace and the prevRowTrace */
	CorrelationWindow[] rowCloseConnectWindows;
	/**
	 * From the traceValues array, create an array of PatchPoint for each traceValue which qualifies as a pickType (all, min, max, +/- zero-crossing.
	 * This will be a sequential subset of the values array with nTracePatchPoints in this tracePatchPoints array
	 * @param patchVolume
	 * @param row volume row of this trace
	 * @param col volume col of this trace
	 * @param traceValues original seismic values for this trace
	 */
	private TracePoints(StsPatchVolume patchVolume, int row, int col, float[] traceValues) throws StsException
	{
		this.patchVolume = patchVolume;
		this.row = row;
		this.col = col;
		// tracePoints - uniform cubic interpolation of traceValues
		float[] tracePoints = StsTraceUtilities.computeCubicInterpolatedPoints(traceValues, patchVolume.nInterpolationIntervals);
		float z = patchVolume.croppedBoundingBox.zMin;
		if (tracePoints == null) return;
		int nTracePoints = tracePoints.length;
		tracePatchPoints = new PatchPoint[nTracePoints];
		int nTracePatchPoint = 0;
		byte[] tracePointTypes = StsTraceUtilities.getPointTypes(tracePoints);

		// create tracePoints from values and pointTypes for legitimate events (zero+, max, zero-, min)
		// count the number missing so we have the final array length with missing points added
		int nTotalMissing = 0;
		int nMissing;
		PatchPoint prevPoint, nextPoint = null;

		try
		{
			for (int n = 0; n < nTracePoints; n++, z += patchVolume.interpolatedZInc)
			{
				byte tracePointType = tracePointTypes[n];
				if (StsTraceUtilities.isMaxMinZeroOrFalseMaxMin(tracePointType))
				{

					prevPoint = nextPoint;
					nextPoint = new PatchPoint(this, n, z, tracePoints[n], tracePointType, nTracePatchPoint);
					tracePatchPoints[nTracePatchPoint] = nextPoint;
					nTracePatchPoint++;
					nMissing = getNMissingPoints(prevPoint, nextPoint);
					nTotalMissing += nMissing;
				}
			}
			// insert any missing points so that we have a series of zero+, max, zero-, min points with any missing filled as ZPM, MXM, MNM, ZMM

			nTracePatchPoints = nTracePatchPoint;
			if (nTotalMissing > 0)
			{
				int nTotalPoints = nTracePatchPoints + nTotalMissing;
				PatchPoint[] addedPoints = new PatchPoint[nTotalPoints];
				nextPoint = tracePatchPoints[0];
				nTracePatchPoint = 0;
				for (int n = 1; n < nTracePatchPoints; n++)
				{
					prevPoint = nextPoint;
					nextPoint = tracePatchPoints[n];

					addedPoints[nTracePatchPoint] = prevPoint.resetIndex(nTracePatchPoint);
					nTracePatchPoint++;
					nMissing = getNMissingPoints(prevPoint, nextPoint);
					if( nMissing > 0)
					{
						byte missingType = StsTraceUtilities.pointTypesAfter[prevPoint.getPointType()];
						for (int i = 0; i < nMissing; i++, missingType++)
						{
							if (missingType > 4) missingType -= 4;
							addedPoints[nTracePatchPoint] = new PatchPoint(this, prevPoint, missingType, nTracePatchPoint);
							nTracePatchPoint++;
						}
					}
				}
				addedPoints[nTracePatchPoint] = nextPoint.resetIndex(nTracePatchPoint);
				nTracePatchPoint++;
				//if (nTracePatchPoint != nTotalPoints)
				//	StsException.systemError(this, "new TracePoints", " nTotalPoints " + nTotalPoints + " not equal to nTracePatchPoints " + nTracePatchPoint);
				tracePatchPoints = addedPoints;
				nTracePatchPoints = nTracePatchPoint;
			}
			else
				tracePatchPoints = (PatchPoint[]) StsMath.trimArray(tracePatchPoints, nTracePatchPoints);

			if(tracePatchPoints.length < 2)  throw new StsException("TracePoints.constructor()", "Less than 2 tracePatchPoints.");

			zeroPlusOffset = StsTraceUtilities.zeroPlusOffset[tracePatchPoints[0].getPointType()];
			constructTraceWindows();
		}
		catch (StsException stse)
		{
			throw new StsException("TracePoints.constructor()", stse.getMessage());
		}
		catch (Exception e)
		{
			StsException.outputWarningException(TracePoints.class, "constructor", e);
			throw new StsException("TracePoints.constructor()", e.getMessage());
		}
	}

	public static TracePoints constructor(StsPatchVolume patchVolume, int row, int col, float[] traceValues)
	{
		try
		{
			return new TracePoints(patchVolume, row, col, traceValues);
		}
		catch(StsException e)
		{
			return null;
		}
	}

	static int getNMissingPoints(PatchPoint prevPoint, PatchPoint nextPoint)
	{
		if(prevPoint == null || nextPoint == null) return 0;
		byte prevType = StsTraceUtilities.coercedPointTypes[prevPoint.getPointType()];
		byte nextType = StsTraceUtilities.coercedPointTypes[nextPoint.getPointType()];
		if(StsTraceUtilities.pointTypesAfter[prevType] == nextType) return 0;
		byte pointTypeStart = StsTraceUtilities.pointTypesAfter[prevPoint.getPointType()];
		byte pointTypeEnd = StsTraceUtilities.pointTypesBefore[nextPoint.getPointType()];
		return StsTraceUtilities.getNumPointTypesBetweenInclusive(pointTypeStart, pointTypeEnd);
	}

	/**
	 * creates correlated connections between this trace and traces at prev row & same col and prev col & same row
	 * @param colPrevTrace prev trace in same col, prev row
	 * @param rowPrevTrace prev trace in same row, prev col
	 */
	void connectWindows(TracePoints colPrevTrace, TracePoints rowPrevTrace)
	{
		if (rowPrevTrace == null && colPrevTrace == null) return;

		if (windows == null || windows.length < 1) return;

		// The ConnectionLists for this trace is initialized with top and bot inactive connections to prev row and col traces.
		// These inactive connections are used to limit the search process.
		// a connection is from the first nextWindow in this patchPointsList to the first nextWindow in the corresponding row or col trace
		if(colPrevTrace != null)
		{
			colConnections = new ConnectionList(this, colPrevTrace);
			colPrevTrace.colConnections = colConnections;
		}
		if(rowPrevTrace != null)
		{
			rowConnections = new ConnectionList(this, rowPrevTrace);
			rowPrevTrace.rowConnections = rowConnections;
		}
		// create initial guesses of connections from the windows on this trace to windows on prevCol and prevRow traces
		// guesses are the closest windows vertically on the other traces to the nextWindow on this trace
		if(colPrevTrace != null)
			createClosestConnectWindow(this, colPrevTrace, false);
		if(rowPrevTrace != null)
			createClosestConnectWindow(this, rowPrevTrace, true);
		// Iterate from maxCorrelation down to minCorrelation in nIterations steps.  At each iteration,
		// make connections from prevCol and prevRow traces to this trace */
		for (int iter = 0; iter < patchVolume.nIterations; iter++)
		{
			StsPatchVolume.setIterLabel(iter);
			connectWindows(patchVolume, colPrevTrace, rowPrevTrace, iter);
		}
	}

	/** This prevents crossing connections.
	 *  Create lists of closest points of same type from trace to otherTrace and back.
	 *  If connections are identical, then retain in the trace->otherTrace list; if not null out.
	 * @param trace connections will be from this trace to otherTrace
	 * @param prevTrace connections list back will be compared with trace list
	 * @param isRow indicates trace and otherTrace are on the same row (other trace is prevCol)
	 */
	static void createClosestConnectWindow(TracePoints trace, TracePoints prevTrace, boolean isRow)
	{
		CorrelationWindow window, prevWindow, backWindow;
		int n;
		CorrelationWindow[] windows = trace.windows;
		int nWindows = trace.nWindows;
		CorrelationWindow[] connectWindows = trace.createClosestConnectWindows(prevTrace);
		CorrelationWindow[] otherConnectWindows = prevTrace.createClosestConnectWindows(trace);
		try
		{
			CorrelationWindow lastWindow = null;
			for (n = 0; n < nWindows; n++)
			{
				window = windows[n];
				prevWindow = connectWindows[n];

				if(prevWindow == null)
					continue;
				int otherIndex = prevWindow.windowIndex;
				backWindow = otherConnectWindows[otherIndex];
				if (backWindow != window)
				{
					connectWindows[n] = null;
					otherConnectWindows[otherIndex] = null;
				}
				else if(lastWindow != null && prevWindow.getCenterPoint().getSlice() <= lastWindow.getCenterPoint().getSlice())
					connectWindows[n] = null;
				else
					lastWindow = prevWindow;
			}
			// check for crossing connections; remove connection if it crosses
			if (isRow)
			{
				trace.rowCloseConnectWindows = connectWindows;
				prevTrace.rowCloseConnectWindows = otherConnectWindows;
			}
			else
			{
				trace.colCloseConnectWindows = connectWindows;
				prevTrace.colCloseConnectWindows = otherConnectWindows;
			}
		}
		catch (Exception e)
		{
			StsException.outputWarningException(TracePoints.class, "createClosestConnectWindow", e);
		}
	}

	CorrelationWindow[] createClosestConnectWindows(TracePoints prevTrace)
	{
		CorrelationWindow[] closeConnectWindows = new CorrelationWindow[nWindows];

		// assign closest prevWindow to this nextWindow regardless of pointType
		CorrelationWindow[] prevWindows = prevTrace.windows;
		int nPrevWindows = prevTrace.nWindows;
		CorrelationWindow prevWindowAbove = prevWindows[0];
		CorrelationWindow prevWindowBelow = prevWindows[1];
		int otherNextIndex = 2;
		for (int i = 0; i < nWindows; i++)
		{
			CorrelationWindow window = windows[i];
			if (window.isBelowOrEqual(prevWindowAbove) && window.isAboveOrEqual(prevWindowBelow))
			{
				CorrelationWindow prevWindow = window.getClosestWindow(prevWindowAbove, prevWindowBelow);
				closeConnectWindows[i] = prevWindow;
			}
			else if (!window.isAboveOrEqual(prevWindowAbove)) // nextWindow is below prevWindowBelow, so move prevWindows down
			{
				while (window.isBelowOrEqual(prevWindowBelow) && otherNextIndex < nPrevWindows)
				{
					prevWindowAbove = prevWindowBelow;
					prevWindowBelow = prevWindows[otherNextIndex++];
				}
				if (window.isBelowOrEqual(prevWindowAbove) && window.isAboveOrEqual(prevWindowBelow))
				{
					CorrelationWindow prevWindow = window.getClosestWindow(prevWindowAbove, prevWindowBelow);
					closeConnectWindows[i] = prevWindow;
				}
			}
		}
		// now adjust guesses to nearest prevWindow of this pointType
		for (int i = 0; i < nWindows; i++)
		{
			CorrelationWindow window = windows[i];
			CorrelationWindow prevWindow = closeConnectWindows[i];
			if(prevWindow == null) continue;
			int pointTypeOffset = getPointTypeDif(window, prevWindow);
			if(pointTypeOffset == 0) continue; // we have the correct type: so don't adjust
			if (pointTypeOffset >= 2) pointTypeOffset -= 4;
			int prevWindowIndex = prevWindow.windowIndex + pointTypeOffset;
			if (prevWindowIndex < 0)
				prevWindowIndex += 4;
			else if(prevWindowIndex >= nPrevWindows)
				prevWindowIndex -= 4;
			prevWindow = prevWindows[prevWindowIndex];
			closeConnectWindows[i] = prevWindowIsInside(window, prevWindow);
		}
		// now compute correlation factors
		for (int i = 0; i < nWindows; i++)
		{
			CorrelationWindow prevWindow = closeConnectWindows[i];
			if(prevWindow == null) continue;
			windows[i].computeCorrelation(prevWindow);
		}
		return closeConnectWindows;
	}

	private CorrelationWindow prevWindowIsInside(CorrelationWindow window, CorrelationWindow prevWindow)
	{
		int prevWindowCenterSlice = prevWindow.getCenterPoint().getSlice();
		int prevPointSlice = getPrevWindow(window).getCenterPoint().getSlice();
		if(prevWindowCenterSlice < prevPointSlice) return null;
		int nextPointSlice = getNextWindow(window).getCenterPoint().getSlice();
		if(prevWindowCenterSlice > nextPointSlice) return null;
		return prevWindow;
	}

	private void connectWindows(StsPatchVolume patchVolume, TracePoints prevColTrace, TracePoints prevRowTrace, int iter)
	{
		CorrelationWindow matchingWindow, backMatchingWindow;
		Connection colConnection, rowConnection;
		CorrelationWindow otherClosestWindow, closestWindow;
		CorrelationWindow connectedWindowAbove, connectedWindowBelow;
		CorrelationWindow window;

		try
		{
			reinitializeTraceIndices(prevRowTrace, prevColTrace);
			for (int n = 0; n < nWindows; n++)
			{
				window = windows[n];

				if(StsPatchVolume.debug && StsPatchGrid.debugPoint && (StsPatchGrid.doDebugPoint(window.getCenterPoint())))
					StsException.systemDebug(this, "connectWindows", StsPatchVolume.iterLabel + " MATCH THIS WINDOW: " + window.toString());

				colConnection = null;
				if (prevColTrace != null && !window.hasColConnection())
				{
					otherClosestWindow = colCloseConnectWindows[n];
					connectedWindowAbove = colConnections.connectionAbove.getPrevWindow();
					connectedWindowBelow = colConnections.connectionBelow.getPrevWindow();
					matchingWindow = TracePoints.connectWindows(patchVolume, window, otherClosestWindow, prevColTrace, connectedWindowAbove, connectedWindowBelow, iter);
					if (matchingWindow != null && patchVolume.checkBackMatch)
					{
						closestWindow = prevColTrace.colCloseConnectWindows[matchingWindow.windowIndex];
						connectedWindowAbove = prevColTrace.colConnections.connectionAbove.getNextWindow();
						connectedWindowBelow = prevColTrace.colConnections.connectionBelow.getNextWindow();
						backMatchingWindow = TracePoints.connectWindows(patchVolume, matchingWindow, closestWindow, this, connectedWindowAbove, connectedWindowBelow, iter);
						if (backMatchingWindow != null && backMatchingWindow != window && backMatchingWindow.stretchCorrelation >= matchingWindow.stretchCorrelation)
							matchingWindow = null;
					}
					if (matchingWindow != null)
						colConnection = new Connection(matchingWindow, window);
				}
				rowConnection = null;
				if (prevRowTrace != null && !window.hasRowConnection())
				{
					otherClosestWindow = rowCloseConnectWindows[n];
					connectedWindowAbove = rowConnections.connectionAbove.getPrevWindow();
					connectedWindowBelow = rowConnections.connectionBelow.getPrevWindow();
					matchingWindow = TracePoints.connectWindows(patchVolume, window, otherClosestWindow, prevRowTrace, connectedWindowAbove, connectedWindowBelow, iter);
					if (matchingWindow != null && patchVolume.checkBackMatch)
					{
						closestWindow = prevRowTrace.rowCloseConnectWindows[matchingWindow.windowIndex];
						connectedWindowAbove = prevRowTrace.rowConnections.connectionAbove.getNextWindow();
						connectedWindowBelow = prevRowTrace.rowConnections.connectionBelow.getNextWindow();
						backMatchingWindow = TracePoints.connectWindows(patchVolume, matchingWindow, closestWindow, this, connectedWindowAbove, connectedWindowBelow, iter);
						if (backMatchingWindow != null && backMatchingWindow != window && backMatchingWindow.stretchCorrelation >= matchingWindow.stretchCorrelation)
							matchingWindow = null;
					}
					if (matchingWindow != null)
						rowConnection = new Connection(matchingWindow, window);
				}
				// Check if we have a cycle skip.
				// If the prevRowConnection.prevPoint overlaps the currentGrid at this point which was connected via the prevColConnection,
				// then we have a cycle skip. If we do, allow either the prevRowConnection or the prevColConnection, which ever has
				// the highest correlation, but not both. If we use the prevRowConnection and the prevColConnection already exists, we
				// need to delete the prevColConnection from the connected point..

				// if prevColConnection is not null, this is a new prevColConnection
				// Set currentColConnection to either this new prevColConnection or the existing prevColConnection
				// The prevRowConnection must be new as

				Connection currentRowConnection = rowConnection;
				if(currentRowConnection != null)
					currentRowConnection = window.getRowConnection();
				Connection currentColConnection = colConnection;
				if(currentColConnection != null)
					currentColConnection = window.getColConnection();
				if(currentRowConnection != null && currentColConnection != null)
				{
					StsPatchGrid colPatchGrid = currentColConnection.getPrevPatchGrid();
					StsPatchGrid rowPatchGrid = currentRowConnection.getPrevPatchGrid();
					if(colPatchGrid != null && rowPatchGrid != null && colPatchGrid != rowPatchGrid)
					{
						if(rowConnectionIsBetter(currentRowConnection, currentColConnection))
						{
							if(rowConnection == null) // this must be an existing connections
								window.deleteRowConnection();
							else // prevRowConnection != null: a new connection, so remove it
								rowConnection = null;

						}
						else // eliminate the col connection
						{
							if (colConnection == null)
								window.deleteColConnection();
							else
								colConnection = null;
						}
					}
				}
				processNewConnections(window, colConnection, rowConnection);
			}
		}
		catch (Exception e)
		{
			StsException.outputWarningException(this, "connectWindows(vol,trace,trace,iter)", e);
		}
	}

	static boolean rowConnectionIsBetter(Connection rowConnection, Connection colConnection)
	{
		CorrelationWindow rowWindow = rowConnection.getNextWindow();
		CorrelationWindow colWindow = colConnection.getNextWindow();

		if(rowWindow.amplitudeRatio > 2*colWindow.amplitudeRatio) return true;
		if(rowWindow.amplitudeRatio < 0.5*colWindow.amplitudeRatio) return false;

		return rowWindow.stretchCorrelation > colWindow.stretchCorrelation;
	}

	/** from the current connectionAbove for this connectionList, find index offset to this nextWindow and apply it to the otherTrace nextWindow
	 *  to get the middle matchingWindow.  Check connections to otherTrace windows above and below.
	 *
	 * @param patchVolume
	 * @param newWindow newWindow we want to match on this otherTrace
	 * @param centerOtherWindow center candidate nextWindow for matching
	 * @param otherTrace other trace to which we want to make a connection from this trace
	 * @param otherConnectWindowAbove otherTrace windows on connections above
	 * @param otherConnectWindowBelow otherTrace windows on connections below
	 * @param iter iteration we are on
	 * @return best connection between newWindow and a matchingWindow on this trace; return null of non exists or don't qualify
	 */
	static private CorrelationWindow connectWindows(StsPatchVolume patchVolume, CorrelationWindow newWindow,
													CorrelationWindow centerOtherWindow, TracePoints otherTrace,
													CorrelationWindow otherConnectWindowAbove, CorrelationWindow otherConnectWindowBelow,
													int iter)
	{
		// matchingWindow we wish to find
		CorrelationWindow matchingWindow = null;
		// index of candidate centerOtherWindow
		int centerOtherWindowIndex;
		// indexes for the two other matchingWindow candidates above and below the center (offsets of -4 and +4)
		int aboveWindowIndex, belowWindowIndex;
		// candidate matching windows above and below
		CorrelationWindow aboveOtherWindow, belowOtherWindow;

		try
		{
			if(centerOtherWindow == null) return null;

			float correlation = patchVolume.stretchCorrelations[iter];
			float minAmplitudeRatio = patchVolume.minAmplitudeRatio;
			// set a penalty except for the last iteration
			float correlPenalty = 0.0f;
			if(iter < patchVolume.nIterations-1)
				correlPenalty = patchVolume.autoCorInc;

			// centerOtherWindow must be between bounding connections above and below and cannot cross
			// nextWindow is already between them, so move centerOtherWindow up or down to be between as well
			// new nextWindow selected must be of same type so move is +/- 4 index

			if(centerOtherWindow.isAboveOrEqual(otherConnectWindowAbove))
			{
				if(StsPatchVolume.debugConnectCloseOnly) return null;
				//CorrelationWindow prevWindow = centerOtherWindow;
				while(centerOtherWindow != null && centerOtherWindow.isAboveOrEqual(otherConnectWindowAbove))
				{
					int index = centerOtherWindow.windowIndex + 4;
					if(index >= otherTrace.nWindows)
						return null;
					//prevWindow = centerOtherWindow;
					centerOtherWindow = otherTrace.windows[index];
					if(centerOtherWindow.isBelowOrEqual(otherConnectWindowBelow))
						return null;
				}
				//centerOtherWindow = newWindow.getClosestWindow(prevWindow, centerOtherWindow);
			}
			else if(centerOtherWindow.isBelowOrEqual(otherConnectWindowBelow))
			{
				if(StsPatchVolume.debugConnectCloseOnly) return null;
				//CorrelationWindow prevWindow = centerOtherWindow;
				while(centerOtherWindow != null && centerOtherWindow.isBelowOrEqual(otherConnectWindowBelow))
				{
					int index = centerOtherWindow.windowIndex - 4;
					if(index < 0)
						return null;
					//prevWindow = centerOtherWindow;
					centerOtherWindow = otherTrace.windows[index];
					if(centerOtherWindow.isAboveOrEqual(otherConnectWindowAbove))
						return null;
				}
				//centerOtherWindow = newWindow.getClosestWindow(prevWindow, centerOtherWindow);
			}
			// centerOtherWindow is between above and below connection points on otherTrace, so compute stretchCorrelation with nextWindow on this trace
			// this centerOtherWindow has already been determined to be the closest if their are two bracketing windows (@see
			if (newWindow.correlationOK(correlation, correlPenalty, minAmplitudeRatio))
				matchingWindow = centerOtherWindow;

			if(StsPatchVolume.debugConnectCloseOnly)
				return matchingWindow;

			// try to make a match with prevWindow above centerOtherWindow
			centerOtherWindowIndex = centerOtherWindow.windowIndex;
			aboveWindowIndex = centerOtherWindowIndex - 4;
			if (aboveWindowIndex > otherConnectWindowAbove.windowIndex) // index must be below connectionAbove
			{
				aboveOtherWindow = otherTrace.windows[aboveWindowIndex];
				if (newWindow.correlationOK(correlation, correlPenalty, minAmplitudeRatio))
				{
					matchingWindow = aboveOtherWindow;
					correlation = aboveOtherWindow.stretchCorrelation;
				}
			}

			belowWindowIndex = centerOtherWindowIndex + 4;
			if (belowWindowIndex < otherConnectWindowBelow.windowIndex)
			{
				belowOtherWindow = otherTrace.windows[belowWindowIndex];
				if (newWindow.correlationOK(correlation, correlPenalty, minAmplitudeRatio))
					matchingWindow = belowOtherWindow;
			}
			return matchingWindow;
		}
		catch (Exception e)
		{
			StsException.outputWarningException(TracePoints.class, "connectWindows", e);
			return null;
		}
	}

	static int getPointTypeDif(CorrelationWindow newWindow, CorrelationWindow prevWindow)
	{
		int typeDif = newWindow.getCenterPoint().getPointType() - prevWindow.getCenterPoint().getPointType();
		if(typeDif < 0) typeDif += 4;
		return typeDif;
	}
	/**
	 * Given a newPatchPoint at newRow-newCol, which correlates with a prevPatchPoint at prevRow-prevCol which is possibly part of a patchGrid in the prevPatchGridsSet,
	 * combine these two points in the same patch.  The prevPatchPoint may be on the previous col (same row), or previous row (same col).
	 * If the previousPatchPoint is not part of an existing patchGrid (prevID == -1), then we will create a new patchGrid and add both points to it.
	 * If the previousPatchPoint is part of a patchGrid we will add the newPatchPoint to this patchGrid, unless the newPatchPoint already belongs to another patchGrid
	 * (this occurs when we first correlate with the previous column and find one patchGrid and also correlate with the previous row and find a different patchGrid).
	 */
	public Connection addPatchConnection(Connection connection, ConnectionList connectionList)
	{
		StsPatchGrid patchGrid;

		if(connectionList.connectionsCross(connection)) return null;

		PatchPoint newPatchPoint = connection.getNextPoint();
		PatchPoint otherPatchPoint = connection.getPrevPoint();

		double distance = Math.abs(otherPatchPoint.getSlice() - newPatchPoint.getSlice());

		if(distance > 20)
			StsException.systemDebug(this, "addPatchConnection", "DISTANCE LARGE FOR Connection: " + connection.toString());

		StsPatchGrid otherPatchGrid = otherPatchPoint.getPatchGrid();
		StsPatchGrid newPatchGrid = newPatchPoint.getPatchGrid();

		if(StsPatchVolume.debug && (StsPatchGrid.doDebugPoint(newPatchPoint) || StsPatchGrid.doDebugPoint(otherPatchPoint)))
			StsException.systemDebug(this, "addPatchConnection", StsPatchVolume.iterLabel + " CONNECT point: " +
					newPatchPoint.toString() + " TO point: " + otherPatchPoint.toString());

		// normally we can insert a new connectedPoint in the trace patchPointsList and split the connected interval;
		// but if we have cloned this new nextWindow and it is already connected, don't add/split the trace again
		//boolean splitIntervalOK = true;
		if (newPatchGrid == null)
		{
			if (otherPatchGrid == null) // prevPatchGrid doesn't exist, so create it and add otherPoint to it
			{
				patchGrid = StsPatchGrid.construct(patchVolume, newPatchPoint.getPointType(patchVolume.useFalseTypes));
				patchGrid.addChangePatchPoint(otherPatchPoint);
			}
			else // otherPatchGrid does exist, so use it
			{
				// if this newPatchPoint overlaps the otherPatchGrid, we can't add it;
				// So create a new patch and add a clone of the otherPatchPoint
				if (otherPatchGrid.patchPointOverlaps(newPatchPoint)) // return null;
				{
					patchGrid = StsPatchGrid.construct(patchVolume, newPatchPoint.getPointType());
					patchGrid.addChangePatchPoint(newPatchPoint);
				/*
					if (StsPatchVolume.debugCloneOK)
					{
						PatchPoint clonedOtherPatchPoint = otherPatchPoint.cloneAndClear();
						patchGrid.addChangePatchPoint(clonedOtherPatchPoint);
						connection.setPrevPoint(clonedOtherPatchPoint);
					}
				*/
					otherPatchGrid.combineChildGrids(patchGrid);
					//splitIntervalOK = false;
				}
				else // no overlap, so we will only need to add the newPatchPoint to it (below else)
					patchGrid = otherPatchGrid;
			}
			patchGrid.addChangePatchPoint(newPatchPoint);
		}
		else // newPatchGrid != null which means this nextWindow was just added to a patch from prevColTrace and the patchGrid would have been added to the rowGrids array
		{
			if (otherPatchGrid == null) // the otherPoint is not assigned to a patch; assign it to this one; don't add nextWindow to rowGrids unless it overlaps and addedGrid created
			{
				patchGrid = newPatchGrid;
				// otherPatchPoint doesn't have a patchGrid, but newPatchPoint does; try to add otherPatchPoint to newPatchGrid,
				// but if it overlaps, created a new patchGrid containing otherPatchPoint and a clone of newPatchPoint
				if (patchGrid.patchPointOverlaps(otherPatchPoint))
				{
					patchGrid = StsPatchGrid.construct(patchVolume, patchGrid.patchType);
					patchGrid.addChangePatchPoint(otherPatchPoint);
					/*
					if(StsPatchVolume.debugCloneOK)
					{
						PatchPoint clonedNextPatchPoint = newPatchPoint.cloneAndClear();
						patchGrid.addChangePatchPoint(clonedNextPatchPoint);
						connection.setNextPoint(clonedNextPatchPoint);
					}
                    */
					newPatchGrid.combineChildGrids(patchGrid);
				}
				else
					patchGrid.addChangePatchPoint(otherPatchPoint);

				// patchGrid = patchGrid.checkAddPatchPoint(otherPatchPoint, newPatchPoint);
				//patchGrid.addCorrelation(otherPatchPoint, newPatchPoint, correl);
				//checkAddPatchGridToRowGrids(patchGrid);
			}
			else if (otherPatchGrid.id == newPatchGrid.id) // otherPoint is already assigned to the same patch: addCorrelation
			{
				patchGrid = newPatchGrid;
				//if (patchGrid == null) return null; // patchGrid not found; systemDebugError was printed
				//checkAddPatchGridToRowGrids(patchGrid);
				//patchGrid.addCorrelation(otherPatchPoint, newPatchPoint, correl);
			}
			// otherPatchPoint and newPatchPoint belong to different patches: merge newPatchGrid into otherPatchGrid and add connection (below)
			// if the grids don't overlap.
			// If they do overlap, then we merge as many points from the smaller grid to the larger (@see mergeOverlappingPatchGrids);
			// the connection is added in mergeOverlappingPatchGrids (two connections may have been generated,
			// @see moveNonOverlappingPointsTo for details); connection is fully-handled, so we don't redundantly handle connection below.
			else
			{
				if (StsPatchGrid.mergePatchPointsOK(otherPatchGrid, newPatchGrid))
				{
					patchGrid = patchVolume.mergePatchGrids(otherPatchPoint, newPatchPoint);
					// if patchGrid==null error occurred: systemError written in mergePatchGrids routine
				}
				else
				{
					//addConnectionBetweenPatches(connection);
					//Connection otherConnection = connection.getOtherConnection();
					//if(otherConnection != null)
					//	addConnectionBetweenPatches(otherConnection);
					//otherPatchGrid.checkAddPatchPoint(newPatchPoint);
					//newPatchGrid.checkAddPatchPoint(otherPatchPoint);
					patchGrid = newPatchGrid;
					otherPatchGrid.combineChildGrids(newPatchGrid);
					//patchGrid = patchVolume.checkAddOverlappingConnection(otherPatchPoint, newPatchPoint, connection);
					//	patchGrid = patchVolume.mergeOverlappingPatchGrids(otherPatchPoint, newPatchPoint, connection);
					patchVolume.checkAddPatchGridToRowGrids(patchGrid);
				}
			}
		}
		if (patchGrid == null) return null;

		connection.addConnectionToPoints();

		// patchGrid is the grid this connection is to go on to;
		// if both points are on the patchGrid, then we can add the connection to the connection.nextWindow.centerPoint (nextPoint);
		// if points are on different grids, we have two cases from processing above (mergeOverlappingPatchGrids takes care of itself):
		//   1) nextPoint is on the patchGrid and prevPoint is on the otherGrid:
		//      replace the connection.prevPoint with  a cloneAndClear copy of the prevPoint
		//   2) prevPoint is on the patchGrid and nextPoint is on the otherGrid:
		//      replace the nextPoint.connection with a cloned connection which has the isMoved flag set to true;
		//      the connection.nextPoint will be replaced by a clone of the nextPoint added to the patchGrid
		// checkResetClonedPoints(connection, otherPatchPoint, newPatchPoint, isRow);
		// processConnection(connection, patchGrid);
		// connection.addConnectionToPoint();
		// ??? if merging overlapped grids, we have two adjust grids; do we need to call checkAddPatchGridToRowGrids for the other as well?
		patchVolume.checkAddPatchGridToRowGrids(patchGrid);
		//if (splitIntervalOK)
		return connection;
		//else
		//	return null;
	}

	CorrelationWindow getNextWindow(CorrelationWindow window)
	{
		int index = window.windowIndex + 1;
		if(index >= nWindows) return window;
		return windows[index];
	}

	CorrelationWindow getPrevWindow(CorrelationWindow window)
	{
		int index = window.windowIndex - 1;
		if(index < 0) return window;
		return windows[index];
	}

	/** For this new nextWindow, we may have a new row and/or col connection or no connection.
	 *  If we have any new connection, then split our bounded connection interval at the new nextWindow
	 *  and move the interval down to this new interval. If the nextWindow was cloned, we need to use the
	 *  original nextWindow (nextWindow.clonedPoint) for these operations as it has the trace links for the
	 *  split and move operations.  If no connections, just move the interval down.
	 * @param window
	 * @param colConnection connection from the prevColPoint (same col, prev row) to this new nextWindow or its clone
	 * @param rowConnection connection from the prevRowPoint (same row, prev col) to this new nextWindow or its clone
	 */
	private void processNewConnections(CorrelationWindow window, Connection colConnection, Connection rowConnection)
	{
		try
		{
			// if we have a new row and/or col connection, split the connection interval
			// by inserting this connection into it
			if (colConnection != null)
			{
				colConnection = addPatchConnection(colConnection, colConnections);
				if(colConnection != null) colConnections.insert(colConnection);

			}
			if (rowConnection != null)
			{
				rowConnection = addPatchConnection(rowConnection, rowConnections);
				if(rowConnection != null) rowConnections.insert(rowConnection);
			}
			PatchPoint windowCenterPoint = window.getCenterPoint();

			Connection prevColConnection = windowCenterPoint.getPrevColConnection();
			Connection prevRowConnection = windowCenterPoint.getPrevRowConnection();
			if(prevColConnection != null) colConnections.movePatchInterval(prevColConnection);
			if(prevRowConnection != null) rowConnections.movePatchInterval(prevRowConnection);
		}
		catch(Exception e)
		{
			StsException.outputWarningException(this, "processNewConnections", e);
		}
	}
	/*
		private Connection checkAddRowConnection(CorrelationWindow nextWindow, TracePoints otherTrace, float minStretchCorrelation)
		{
			if (otherTrace == null) return null;
			if (nextWindow.centerPoint.prevRowConnection != null) return null;
			return nextWindow.checkAddConnection(otherTrace, minStretchCorrelation, true);
		}

		private Connection checkAddColConnection(CorrelationWindow nextWindow, TracePoints otherTrace, float minStretchCorrelation)
		{
			if (otherTrace == null) return null;
			if (nextWindow.centerPoint.prevColConnection != null) return null;
			return nextWindow.checkAddConnection(otherTrace, minStretchCorrelation, false);
		}

		Connection addConnection(boolean isRow, CorrelationWindow prevWindow, CorrelationWindow nextWindow, float stretchCorrelation)
		{
			return new Connection(prevWindow, nextWindow, stretchCorrelation);

		}
    */
	private void constructTraceWindows() throws StsException
	{
		windows = new CorrelationWindow[nTracePatchPoints];
		nWindows = 0;
		PatchPoint prevPoint;
		PatchPoint point = null;
		PatchPoint nextPoint = tracePatchPoints[0];
		CorrelationWindow window;

		try
		{
			for (int n = 1; n < nTracePatchPoints - 1; n++)
			{
				prevPoint = point;
				point = nextPoint;
				nextPoint = tracePatchPoints[n];

				window = checkCreateWindow(this, prevPoint, point, nextPoint, nWindows);
				if (window == null) continue;
				if(nWindows != window.windowIndex)
					StsException.systemDebug(this, "constructTraceWindows", "Index out of sequence.");
				windows[nWindows] = window;
				nWindows++;
			}
			if (nWindows < nTracePatchPoints)
				windows = (CorrelationWindow[]) StsMath.trimArray(windows, nWindows);
			if(nWindows == 0)
				throw new StsException("TracePoints.constructTraceWindows()", "nWindows == 0");
		}
		catch (Exception e)
		{
			StsException.outputWarningException(this, "constructTraceWindows", e);
			throw new StsException("TracePoints.constructTraceWindows()", e.getMessage());
		}
	}

	private CorrelationWindow checkCreateWindow(TracePoints tracePoints, PatchPoint prevPoint, PatchPoint point, PatchPoint nextPoint, int windowIndex)
	{
		if(!arePointTypesOK(prevPoint, point, nextPoint)) return null;
		return new CorrelationWindow(tracePoints, prevPoint, point, nextPoint, windowIndex);
	}

	private boolean arePointTypesOK(PatchPoint prevPoint, PatchPoint point, PatchPoint nextPoint)
	{
		if(prevPoint != null)
		{
			if(nextPoint != null)
				return StsTraceUtilities.arePointTypesOK(prevPoint.getPointType(), point.getPointType(), nextPoint.getPointType());
			else
				return StsTraceUtilities.arePointTypesAboveOK(prevPoint.getPointType(), point.getPointType());
		}
		else if(nextPoint != null)
			return StsTraceUtilities.arePointTypesBelowOK(point.getPointType(), nextPoint.getPointType());
		else
			return false;
	}

	private void reinitializeTraceIndices(TracePoints prevRowTrace, TracePoints prevColTrace)
	{
		reinitializeTraceIndexing();
		if (prevRowTrace != null) prevRowTrace.reinitializeTraceIndexing();
		if (prevColTrace != null) prevColTrace.reinitializeTraceIndexing();
	}

	void reinitializeTraceIndexing()
	{
		if(rowConnections != null) rowConnections.reinitializeTraceIndexing();
		if(colConnections != null) colConnections.reinitializeTraceIndexing();
	}

	/**
	 * currentPoint is the last currentPoint on this trace in the previous search operation, so is a good starting nextWindow for this search
	 * @param slice slice for which we want to find the nearest tracePoint
	 * @return the nearestTracePoint
	 */
	/*
		private PatchPoint nearestPatchPoint(int slice)
		{
			int distance;
			// if currentNearestPoint is above slice, search down for nearest
			if (currentPoint.slice < slice)
			{
				distance = slice - currentPoint.slice;
				PatchPoint point = currentPoint;

				for (int index = currentPoint.traceIndex + 1; index < nTracePatchPoints; index++)
				{
					PatchPoint lastPoint = point;
					int lastDistance = distance;
					point = tracePatchPoints[index];
					// if nextWindow is now below slice, then we have bracketed nextWindow: set and return currentPoint
					if (point.slice >= slice)
					{
						distance = point.slice - slice;
						if (distance < lastDistance)
							currentPoint = point;
						else
							currentPoint = lastPoint;
						return currentPoint;
					}
					else
						distance = slice - point.slice;
				}
				// didn't bracket, so slice is still below last nextWindow; return last nextWindow
				currentPoint = point;
			}
			// if currentNearestPoint is below slice, search up for nearest
			else if (currentPoint.slice > slice)
			{
				distance = currentPoint.slice - slice;
				PatchPoint point = currentPoint;

				for (int index = currentPoint.traceIndex - 1; index >= 0; index--)
				{
					PatchPoint lastPoint = point;
					int lastDistance = distance;
					point = tracePatchPoints[index];

					// if nextWindow is now above slice, then we have bracketed nextWindow: set and return currentPoint
					if (point.slice <= slice)
					{
						distance = slice - point.slice;
						if (distance < lastDistance)
							currentPoint = point;
						else
							currentPoint = lastPoint;
						return currentPoint;
					}
				}
				currentPoint = point;
			}
			return currentPoint;
		}

		private PatchPoint getOtherConnectedPatchPointAbove(boolean isRow)
		{
			return patchPointsList.getConnectionAbove(isRow).otherPoint;
		}

		private PatchPoint getOtherConnectedPatchPointBelow(boolean isRow)
		{
			return patchPointsList.getConnectionBelow(isRow).otherPoint;
		}
	*/
}

class PatchPoint implements Comparable<PatchPoint>
{
	Temp temp;
	float value;
	float z = StsParameters.nullValue;
	StsPatchGrid patchGrid;
	/** connections in four cardinal directions */
	private Connection[] connections;

	PatchPoint(TracePoints tracePoints, int slice, float z, float value, byte pointType, int traceIndex)
	{
		temp = new Temp(tracePoints.row, tracePoints.col, slice, pointType, traceIndex);
		this.z = z;
		this.value = value;
	}

	PatchPoint(TracePoints tracePoints, PatchPoint point, byte pointType, int traceIndex)
	{
		temp = new Temp(tracePoints.row, tracePoints.col, point.getSlice(), pointType, traceIndex);
		this.z = point.z;
		this.value = point.value;
	}

	void nullTemps()
	{
		temp = null;
		for(Connection connection : connections)
			if(connection != null) connection.temp = null;
	}

	/** Geometry is a temporary structure used during construction and nulled when the patch is finished */
	class Temp
	{
		/** patch row */
		int row;
		/** patch col */
		int col;
		/** patch slice */
		int slice;
		/** point type (MAX, MIN, etc) */
		byte pointType;
		/** index of this nextWindow in the trace containing it */
		int traceIndex;

		Temp(int row, int col, int slice, byte pointType, int traceIndex)
		{
			this.row = row;
			this.col = col;
			this.pointType = pointType;
			this.slice = slice;
			this.traceIndex = traceIndex;
		}
	}

	public int compareTo(PatchPoint otherPoint)
	{
		if (getSlice() > otherPoint.getSlice()) return 1;
		else if (getSlice() < otherPoint.getSlice()) return -1;
		else return 0;
	}

	void checkAddNewNeighborGrids(ArrayList<StsPatchGrid> newNeighborGrids)
	{
		for(int i = 0; i < 4; i++)
		{
			if(connections[i] == null) continue;
			StsPatchGrid newNeighborGrid = connections[i].getConnectedPatch(this);
			if(newNeighborGrid != patchGrid && !newNeighborGrids.contains(newNeighborGrid))
				newNeighborGrids.add(newNeighborGrid);
		}
	}

	public String toString()
	{
		String connectString = (getPrevRowConnection() != null ? " RC" : "") + (getPrevColConnection() != null ? " CC" : "") + " ";
		if(temp != null)
		{
			String tempString = "r " + getRow() + " c " + getCol() + " s " + getSlice() + " v " + value +
						" z " + z + " t " + connectString + StsTraceUtilities.typeStrings[getPointType()];
			return patchToString() + tempString;
		}
		else
			return patchToString();
	}

	private String patchToString()
	{
		int id = -1;
		if (patchGrid != null) id = patchGrid.id;
		return "id " + id + " ";
	}

	static public String staticToString(PatchPoint point)
	{
		if(point == null) return "NULL";
		else return point.toString();
	}

	StsPatchGrid getPatchGrid()
	{
		return patchGrid;
	}

	public void setPatchGrid(StsPatchGrid patchGrid)
	{
		this.patchGrid = patchGrid;
	}

	public PatchPoint resetIndex(int index)
	{
		if(temp == null)
		{
			StsException.systemError(this, "resetIndex", "patchPoint.temp is null.");
			return null;
		}
		temp.traceIndex = index;
		return this;
	}

	/** connection from this tracePoint to the tracePoint on the adjacent trace at this row, col+1 (same row) */
	public Connection getNextRowConnection() { return getConnection(0); }
	/** connection from this tracePoint to the tracePoint on the adjacent trace at this row+1, col (same col) */
	public Connection getNextColConnection()
	{
		return getConnection(1);
	}
	/** connection from this tracePoint to the tracePoint on the adjacent trace at this row, col-1 (same row) */
	public Connection getPrevRowConnection() { return getConnection(2); }
	/** connection from this tracePoint to the tracePoint on the adjacent trace at this row-1, col (same col) */
	public Connection getPrevColConnection() { return getConnection(3); }

	protected Connection[] getConnections()
	{
		return connections;
	}

	protected Connection getConnection(int i)
	{
		if(connections == null) return null;
		return connections[i];
	}

	public void setNextRowConnection(Connection rowConnection)
	{
		if(StsPatchVolume.debug)
		{
			if(StsPatchGrid.doDebugPoint(this))
				StsException.systemDebug(this, "setNextRowConnection", StsPatchVolume.iterLabel + " ROW CONNECTION SET FOR Point: " +
						toString() + " prevRowConnection: " + rowConnection.toString());
		}
		setConnection(rowConnection, 0);
	}

	public void setNextColConnection(Connection connection)
	{
		if(StsPatchVolume.debug)
		{
			if(StsPatchGrid.doDebugPoint(this))
				StsException.systemDebug(this, "setNextColConnection", StsPatchVolume.iterLabel + " COL CONNECTION SET FOR Point: " +
						toString() + " prevColConnection: " + connection.toString());
		}
		setConnection(connection, 1);
	}

	public void setPrevRowConnection(Connection prevRowConnection)
	{
		if(StsPatchVolume.debug)
		{
			if(StsPatchGrid.doDebugPoint(this))
				StsException.systemDebug(this, "setPrevRowConnection", StsPatchVolume.iterLabel + " ROW CONNECTION SET FOR Point: " +
						toString() + " prevRowConnection: " + prevRowConnection.toString());
		}
		setConnection(prevRowConnection, 2);
	}

	public void setPrevColConnection(Connection connection)
	{
		if(StsPatchVolume.debug)
		{
			if(StsPatchGrid.doDebugPoint(this))
				StsException.systemDebug(this, "setPrevColConnection", StsPatchVolume.iterLabel + " COL CONNECTION SET FOR Point: " +
						toString() + " prevColConnection: " + connection.toString());
		}
		setConnection(connection, 3);
	}

	private void setConnection(Connection connection, int i)
	{
		if(connections == null) connections = new Connection[4];
		connections[i] = connection;
	}

	final protected int getRow()
	{
		if (temp == null)
		{
			StsException.systemError(this, "getRow", "patchPoint.temp is null.");
			return -1;
		}
		return temp.row;
	}

	final protected int getCol()
	{
		if (temp == null)
		{
			StsException.systemError(this, "getCol", "patchPoint.temp is null.");
			return -1;
		}
		return temp.col;
	}

	int getSlice()
	{
		if (temp == null)
		{
			StsException.systemError(this, "getSlice", "patchPoint.temp is null.");
			return -1;
		}
		return temp.slice;
	}

	void adjustSlice(int adjust)
	{
		if (temp == null)
		{
			StsException.systemError(this, "getSlice", "patchPoint.temp is null.");
			return;
		}
		temp.slice += adjust;
	}

	int getID()
	{
		if (patchGrid == null) return -1;
		else return patchGrid.id;
	}

	byte getPointType()
	{
		if (temp == null)
		{
			StsException.systemError(this, "getPointType", "patchPoint.temp is null.");
			return -1;
		}
		return temp.pointType;
	}

	public byte getPointType(boolean useFalseTypes)
	{
		if (!useFalseTypes) return getPointType();
		return StsTraceUtilities.coercedPointTypes[getPointType()];
	}
}

/** A CorrelationWindow has a pointCenter and is bounded by trace points above and below of the appropriate point types.
 *  The nextWindow is essentially a half-wave.  Windows have the type of the center point.  A MAX nextWindow for example has
 *  a ZP (zero-plus) point above an a ZM (zero-minus) point below.  The skewness of the half-wave is considered in matching
 *  it to other windows so we retain for the nextWindow the minus and plus half-widths defined by the slice value difference.
 *  When matched with another windows, the stretchCorrelation is computed as the average of how much each side of the haf-wave
 *  has to be stretched to match the other.  A nextWindow with half-widths of -4 and +2 when matched with one with half-widths of
 *  -3 and +4 would have stretch ratios of .75 and 0.5 with an average of .667.  The instantaneous amplitude has been computed
 *  for each nextWindow and is used in computing the amplitude ratio (always <= 1.0) between the two windows.  The correlation is
 *  acceptable if this ratio is above a minimum.  The amplitudeRatio is saved in the CorrelationWindow.
 */
class CorrelationWindow implements Cloneable
{
	private PatchPoint centerPoint;
	/** windowType: BELOW if no nextWindow above center, ABOVE if no nextWindow below or CENTER */
	byte windowType;
	/** slice difference from center nextWindow to top nextWindow */
	int dSliceMinus;
	/** slice difference from bot nextWindow to center nextWindow */
	int dSlicePlus;
	/** stretchCorrelation between this nextWindow and the connected nextWindow */
	float stretchCorrelation;
	/** amplitudeRatio between this nextWindow and the connected nextWindow */
	float amplitudeRatio;
	/** index of this nextWindow in the windows array */
	int windowIndex;
	/** instantaneous amplitude for this nextWindow; average of min and max values of nextWindow (1 or 2 points) */
	float amplitude;

	TracePoints tracePoints;

	public static final byte CENTER = 0;
	public static final byte ABOVE = -1;
	public static final byte BELOW = 1;

	CorrelationWindow(TracePoints tracePoints, PatchPoint prevPoint, PatchPoint centerPoint, PatchPoint nextPoint, int windowIndex)
	{
		this.tracePoints = tracePoints;
		this.setCenterPoint(centerPoint);
		this.windowIndex = windowIndex;
		float dValueMinus = 0.0f, dValuePlus = 0.0f;
		if(prevPoint != null)
		{
			dSliceMinus = centerPoint.getSlice() - prevPoint.getSlice();
			dValueMinus = Math.abs(centerPoint.value - prevPoint.value);
			if (nextPoint != null)
			{
				dSlicePlus = nextPoint.getSlice() - centerPoint.getSlice();
				dValuePlus = Math.abs(centerPoint.value - nextPoint.value);
				windowType = CENTER;
			}
			else // nextPoint == null && prevPoint != null
			{
				dSlicePlus = dSliceMinus;
				dValuePlus = dValueMinus;
				windowType = ABOVE;
			}
		}
		else if(nextPoint != null)
		{
			dSlicePlus = nextPoint.getSlice() - centerPoint.getSlice();
			dValuePlus = Math.abs(centerPoint.value - nextPoint.value);
			dSliceMinus = dSlicePlus;
			dValueMinus = dValuePlus;
			windowType = BELOW;
		}
		else // error both prev and next points are null: shouldn't happen: print message, don't throw exception
		{
			StsException.systemError(this, "constructor", "prev and next points are both null!");
		}
		amplitude = (dValueMinus + dValuePlus)/2;
	}

	public CorrelationWindow clone()
	{
		try
		{
			CorrelationWindow window = (CorrelationWindow) super.clone();
			// nextWindow.centerPoint = centerPoint.clone();
			return window;
		}
		catch (Exception e)
		{
			StsException.systemError(this, "clone");
			return null;
		}
	}

	boolean hasRowConnection() { return getCenterPoint().getPrevRowConnection() != null; }
	boolean hasColConnection() { return getCenterPoint().getPrevColConnection() != null; }

	Connection getRowConnection()  { return getCenterPoint().getPrevRowConnection(); }
	Connection getColConnection()  { return getCenterPoint().getPrevColConnection(); }

	boolean isAboveOrEqual(CorrelationWindow prevWindow)
	{
		return getCenterPoint().getSlice() <= prevWindow.getCenterPoint().getSlice();
	}

	boolean isBelowOrEqual(CorrelationWindow prevWindow)
	{
		return getCenterPoint().getSlice() >= prevWindow.getCenterPoint().getSlice();
	}

	/** check for closest of two windows where one must be above or equal to and the other must be below or equal to this nextWindow.
	 *  two windows can't be at same slice nor the order switched (above is below, below is above) as debug sanity checks
	 *
	 * @param windowAbove nextWindow above or equal in slice value to this nextWindow
	 * @param windowBelow nextWindow below or equal in slice value to this nextWindow
	 * @return closest of windows above and below to this window
	 */
	CorrelationWindow getClosestWindow(CorrelationWindow windowAbove, CorrelationWindow windowBelow)
	{
		if(windowAbove == null)
			return windowBelow;
		else if(windowBelow == null)
			return windowAbove;
		int difAbove = getCenterPoint().getSlice() - windowAbove.getCenterPoint().getSlice();
		int difBelow = windowBelow.getCenterPoint().getSlice() - getCenterPoint().getSlice();
		if(StsPatchVolume.debug && (difAbove < 0 || difBelow < 0))
		{
			StsException.systemDebug(this, "getClosestWindow", "nextWindow not between windows above and below");
			return null;
		}
		if(StsPatchVolume.debug && (difAbove == 0 && difBelow == 0))
		{
//			StsException.systemDebug(this, "getClosestWindow", "other windows are the same, so can't be between");
			return null;
		}
		if(difAbove <= difBelow) return windowAbove;
		else					 return windowBelow;
	}

	void computeCorrelation(CorrelationWindow prevWindow)
	{
		amplitudeRatio = amplitude/prevWindow.amplitude;
		if(amplitudeRatio > 1.0f) amplitudeRatio = 1.0f/amplitudeRatio;

		// check stretchCorrelation stretch
		if (windowType == BELOW)
			stretchCorrelation = computePlusStretchFactor(prevWindow);
		else if (windowType == ABOVE)
			stretchCorrelation = computeMinusStretchFactor(prevWindow);
		else
			stretchCorrelation = (computePlusStretchFactor(prevWindow) + computeMinusStretchFactor(prevWindow)) / 2;
	}
	/** Correlation between two windows is OK if the amplitudeRatio is above a minimum (minAmplitudeRatio*correlation where
	 *  correlation is the current iteration correlation value) and
	 *  the stretchCorrelation is greater than the current correlation including penalties (false min or max).
	 *  store the stretchCorrelation value in the prevWindow
	 * @param correlation
	 * @param correlPenalty
	 * @param minAmplitudeRatio
	 * @return true if amplitudeRatio and stretchCorrelation are >= correlation
	 */
	boolean correlationOK(float correlation, float correlPenalty, float minAmplitudeRatio)
	{
		if(amplitudeRatio < minAmplitudeRatio*correlation)
			return false;

		float totalPenalty = 0.0f;
		if(correlPenalty > 0.0f)
		{
			if(windowType != CENTER) totalPenalty = correlPenalty;
			if(StsTraceUtilities.isPointTypeFalse(getCenterPoint().getPointType()) || StsTraceUtilities.isPointTypeFalse(this.getCenterPoint().getPointType()))
				totalPenalty += correlPenalty;
		}
		stretchCorrelation -= totalPenalty;
		return stretchCorrelation >= correlation;
	}

		/*
			private Connection checkAddConnection(TracePoints otherTrace, float minStretchCorrelation, boolean isRow)
			{
				CorrelationWindow otherMatchingWindow = findOtherMatchingWindow(otherTrace, isRow, minStretchCorrelation);
				if (otherMatchingWindow == null) return null;
				return addPatchConnection(otherMatchingWindow, isRow);
			}

			private ConnectionList getConnectionList(boolean isRow)
			{
				if(isRow) return rowConnections;
				else	  return colConnections;
			}

			boolean pointTypesMatch(CorrelationWindow prevWindow)
			{
				byte otherCenterType = prevWindow.centerPointType;
				if (centerPointType == otherCenterType) return true;
				if (!useFalseTypes) return false;

				byte centerType = StsTraceUtilities.coercedPointTypes[centerPointType];
				otherCenterType = StsTraceUtilities.coercedPointTypes[otherCenterType];
				return centerType == otherCenterType;
			}
        */
	/**
	 * check the above and below types to see that they match.
	 * We are assuming the centers have already been checked for matches
	 * @param nextWindow the nextWindow
	 * @param prevWindow prevWindow we are comparing it to
	 * @return true if centerTypes, and above and below types match
	 */
		/*
			boolean windowTypesMatch(CorrelationWindow nextWindow, CorrelationWindow prevWindow)
			{
				byte above = nextWindow.pointAbove.getPointType();
				byte below = nextWindow.pointBelow.getPointType();
				byte otherAbove = prevWindow.pointAbove.getPointType();
				byte otherBelow = prevWindow.pointBelow.getPointType();
				return above == otherAbove && below == otherBelow;
			}
        */
	/** if we have two windows with the exactly identical centerPoint, they must be equivalent if not equal windows. */
	boolean sameAs(CorrelationWindow prevWindow)
	{
		return prevWindow.getCenterPoint() == getCenterPoint();
	}

	float computeMinusStretchFactor(CorrelationWindow prevWindow)
	{
		float minusStretchFactor = ((float) dSliceMinus) / prevWindow.dSliceMinus;
		if (minusStretchFactor > 1.0f)
			minusStretchFactor = 1 / minusStretchFactor;
		return minusStretchFactor;
	}

	float computePlusStretchFactor(CorrelationWindow prevWindow)
	{
		float plusStretchFactor = ((float) dSlicePlus) / prevWindow.dSlicePlus;
		if (plusStretchFactor > 1.0f)
			plusStretchFactor = 1 / plusStretchFactor;
		return plusStretchFactor;
	}
	/*
		boolean isCenterSliceOutsideWindow(int centerSlice)
		{
			return centerSlice < minSlice || centerSlice > maxSlice;
		}
	*/
	public String toString()
	{
		return " index " + windowIndex + " centerPoint: " + PatchPoint.staticToString(centerPoint); // + " stretchCorrelation: " + stretchCorrelation + " amplitudeRatio: " + amplitudeRatio;
	}

	/** nextWindow in center of this nextWindow */
	public PatchPoint getCenterPoint()
	{
		if(centerPoint == null)
			StsException.systemError(this, "getCenterPoint", "CENTER POINT IS NULL!");
		return centerPoint;
	}

	public void setCenterPoint(PatchPoint centerPoint)
	{
		if(StsPatchVolume.debug)
		{
			if(StsPatchGrid.doDebugPoint(centerPoint))
				StsException.systemDebug(this, "setCenterPoint", StsPatchVolume.iterLabel + " CENTER POINT SET: " +
						toString() + " TO point: " + centerPoint.toString());
		}
		if(centerPoint == null)
			StsException.systemError(this, "setCenterPoint", "NEW CERNTER POINT IS NULL!");
		this.centerPoint = centerPoint;
	}

	void deleteRowConnection()
	{
	 	centerPoint.setPrevRowConnection(null);
	}

	void deleteColConnection()
	{
		centerPoint.setPrevColConnection(null);
	}
	/*
	private float computeStretchCorrelation(CorrelationWindow prevWindow)
	{
		if (prevWindow == null) return 0.0f;

		TracePoints traceOther = prevWindow.getTracePoints();
		int centerOther = prevWindow.centerSlice;
		int minOther = prevWindow.minSlice;
		int maxOther = prevWindow.maxSlice;

		// translate and stretch/shrink pointsOther z values so they line up with pointsNew

		int dSliceMinusOther = centerOther - minOther;
		int dSliceMinusNew = centerSlice - minSlice;
		float dSliceMinusOtherScalar = (float) dSliceMinusNew / dSliceMinusOther;
		// if(dzMinusOtherScalar < minStretchLimit || dzMinusOtherScalar > maxStretchLimit) return 0.0f;

		float minusStretchFactor = dSliceMinusOtherScalar;
		if (minusStretchFactor > 1.0f)
			minusStretchFactor = 1 / minusStretchFactor;

		int dSlicePlusOther = maxOther - centerOther;
		int dSlicePlusNew = maxSlice - centerSlice;
		float dSlicePlusOtherScalar = (float) dSlicePlusNew / dSlicePlusOther;
		// if(dzPlusOtherScalar < minStretchLimit || dzPlusOtherScalar > maxStretchLimit) return 0.0f;

		float plusStretchFactor = dSlicePlusOtherScalar;
		if (plusStretchFactor > 1.0f)
			plusStretchFactor = 1 / plusStretchFactor;

		return Math.min(minusStretchFactor, plusStretchFactor);
	}
	*/
}
/**
 * tracePoints has a series of row connections and col connections which are maintained during construction of this trace.
 * They will be removed after construction.  When checking on a new connection between this trace and the otherTrace
 * which is either row or col aligned with this trace, we bracket the search by connections above and below to prevent
 * crossing them.  On completion, if a new connection is created, it is added to to either trace.rowConnections or trace.colConnections.
 * These connection lists are currently double-linked, but could perhaps be only single-linked.
 */
class Connection implements Cloneable
{
	PatchPoint prevPoint, nextPoint;
	public float correlation;

	Temp temp;

	static final public byte LINK_NONE = -1;
	static final public byte LINK_RIGHT = 0;
	static final public byte LINK_UP = 1;
	static final public byte LINK_LEFT = 2;
	static final public byte LINK_DOWN = 3;

	static float nullValue = StsParameters.nullValue;

	Connection() {}

	/** construct connection from nextWindow to prevWindow. Clone the nextWindow since it may be shared by two connections
	 *  and one may affect the other. The nextWindow and cloned nextWindow share the same centerPoint which has the row and col connections.
	 * @param prevWindow
	 * @param nextWindow
	 */
	Connection(CorrelationWindow prevWindow, CorrelationWindow nextWindow)
	{
		temp = new Temp(prevWindow, nextWindow);
		prevPoint = prevWindow.getCenterPoint();
		nextPoint = nextWindow.getCenterPoint();
	}

	CorrelationWindow getPrevWindow()
	{
		if(temp == null)
		{
			StsException.systemError(this, "getPrevWindow", "Connection.temp is null.");
			return null;
		}
		return temp.prevWindow;
	}

	CorrelationWindow getNextWindow()
	{
		if(temp == null)
		{
			StsException.systemError(this, "getNextWindow", "Connection.temp is null.");
			return null;
		}
		return temp.nextWindow;
	}

	PatchPoint getPrevPoint() { return prevPoint; }
	PatchPoint getNextPoint() { return nextPoint; }

	StsPatchGrid getPrevPatchGrid() { return prevPoint.patchGrid; }
	StsPatchGrid getNextPatchGrid() { return nextPoint.patchGrid; }

	StsPatchGrid getNextPatchGrid(int pointCcwIndex )
	{
		if(StsPatchGrid.isForwardDir[pointCcwIndex])
			return nextPoint.patchGrid;
		else
			return prevPoint.patchGrid;
	}

	Connection getNextConnection()
	{
		return temp.next;
	}

	void setPrevConnection(Connection prev)
	{
		temp.prev = prev;
	}

	void setNextConnection(Connection next)
	{
		temp.next = next;
	}

	public final void addConnectionToPoints()
	{
		temp.addConnectionToPoints();
	}

	static public float staticGetPrevZ(Connection link)
	{
		if(link == null) return nullValue;
		return link.prevPoint.z;
	}

	static public float staticGetNextZ(Connection connection)
	{
		if(connection == null) return nullValue;
		return connection.getNextZ();
	}

	public float getPrevZ()
	{
		return prevPoint.z;
	}

	public float getNextZ()
	{
		return nextPoint.z;
	}

	/** For this connection and connected point, get the other connection point.
	 *
	 * @param point point from which this connection emanates
	 * @return the other point.patch in the connection
	 */
	StsPatchGrid getConnectedPatch(PatchPoint point)
	{
		if(point == prevPoint)
			return nextPoint.patchGrid;
		else
			return prevPoint.patchGrid;
	}

	public String toString()
	{
		return " Point: " + prevPoint.toString() + " to point: " + nextPoint.toString();
	}

	class Temp
	{
		Connection next;
		Connection prev;
		/** connected window on this trace */
		CorrelationWindow nextWindow;
		/** connected window on other (prev) trace */
		CorrelationWindow prevWindow;
		/** connection is in same row */
		boolean isRow;
		/** average of slice values for two connected points; since connections can't cross or be identical, it will provide correct order */
		float sliceAvg;

		Temp(CorrelationWindow prevWindow, CorrelationWindow nextWindow)
		{
			this.nextWindow = nextWindow.clone();
			this.prevWindow = prevWindow;
			sliceAvg = (this.nextWindow.getCenterPoint().getSlice() + prevWindow.getCenterPoint().getSlice())/2.0f;
			//this.stretchCorrelation = nextWindow.stretchCorrelation;
			//this.amplitudeRatio = nextWindow.amplitudeRatio;
			isRow = prevWindow.getCenterPoint().getRow() == this.nextWindow.getCenterPoint().getRow();
		}

		public final void addConnectionToPoints()
		{
			PatchPoint patchPoint = nextWindow.getCenterPoint();
			PatchPoint prevPatchPoint = this.prevWindow.getCenterPoint();
			if (isRow)
			{
				patchPoint.setPrevRowConnection(Connection.this);
				prevPatchPoint.setNextRowConnection(Connection.this);
				// otherPatchPoint.rowCorrel = connection.stretchCorrelation;
			}
			else
			{
				patchPoint.setPrevColConnection(Connection.this);
				prevPatchPoint.setNextColConnection(Connection.this);
				// otherPatchPoint.colCorrel = connection.stretchCorrelation;
			}
		}

		public String toString()
		{
			String rowColString = (isRow) ? "ROW" : "COL";
			return rowColString + " CONNECTION nextWindow: " + nextWindow.toString() + "     TO prevWindow: " + prevWindow.toString() +
					" sliceAvg: " + sliceAvg; //  + " correl: " + stretchCorrelation + " amplitudeRatio: " + amplitudeRatio;
		}
	}
}

/**
 * doubly linked list of Connection[s].  There are two lists for each trace: rowConnections to the prev col on same row,
 * and colConnections to the prev row on the same col.  Connections in the list are in order, and must not cross or be identical.
 * Order is determined by the avg of the two slice values of the connected points (@see Connection).
 */
class ConnectionList
{
	/** first nextWindow in link list (connected to first actual nextWindow in list) */
	final Connection first;
	/** last nextWindow in link list (connected to last actual nextWindow in list) */
	final Connection last;
	/** last connected nextWindow in linked list just above current nextWindow */
	Connection connectionAbove;
	/** connected nextWindow just below connectionAbove in linked list */
	Connection connectionBelow;
	/** last connected nextWindow that was set; a convenient starting nextWindow for any search */
	// Connection currentConnection;

	/** Insert inactive row and col connections at the top and bottom of the connectionLists.
	 * New connections are added to these lists in order of the sliceAvg of the connection.
 	 * @param trace connection is from trace back to the otherTrace
	 * @param prevTrace trace connected which is either prevRow or prevCol
	 */
	ConnectionList(TracePoints trace, TracePoints prevTrace)
	{

		CorrelationWindow firstWindow = trace.windows[0].clone();
		firstWindow.windowIndex -= 1;
		firstWindow.getCenterPoint().adjustSlice(-1);

		CorrelationWindow lastWindow = trace.windows[trace.nWindows-1].clone();
		lastWindow.windowIndex = trace.nWindows;
		lastWindow.getCenterPoint().adjustSlice(1);

		CorrelationWindow firstPrevWindow = prevTrace.windows[0].clone();
		firstPrevWindow.windowIndex = -1;
		firstPrevWindow.getCenterPoint().getSlice();

		CorrelationWindow lastPrevWindow = prevTrace.windows[prevTrace.nWindows-1].clone();
		lastPrevWindow.windowIndex = prevTrace.nWindows;
		lastPrevWindow.getCenterPoint().adjustSlice(1);

		first = new Connection(firstPrevWindow, firstWindow);
		last = new Connection(lastPrevWindow, lastWindow);
		first.temp.next = last;
		last.temp.prev = first;
		// currentConnection = first;
		connectionAbove = first;
		connectionBelow = last;
		connectionAbove.setNextConnection(last);
		connectionBelow.setPrevConnection(first);
	}

	void reinitializeTraceIndexing()
	{
		connectionAbove = first;
		connectionBelow = first.getNextConnection();
	}

	/** we have moved down to a new existing correlated patchPoint; set the interval to the one between this patchPoint and the nextWindow below */
	void movePatchInterval(Connection connectionAbove)
	{
		this.connectionAbove = connectionAbove;
		if(connectionAbove.getNextConnection() != null)
			connectionBelow = connectionAbove.getNextConnection();
	}

	/**
	 * we are inserting this connectedPoint in an interval between connectionAbove and connectionBelow, either of which could be null
	 * meaning that it could be an openInterval with above and/or below undefined.  The interval (open or closed) is
	 * split into two subintervals and the current interval is set to the lower subinterval.
	 * @param connection between pointAbove and pointBelow where interval is to be split into two subIntervals.
	 */
	void insert(Connection connection)
	{
		if ( StsPatchVolume.debug && (connection == connectionAbove || connection == connectionBelow))
		{
			StsException.systemDebug(this, "insert", " connection " + connection.toString() + " same as " + connectionAbove.toString() + " or " + connectionBelow.toString());
			return;
		}
		connectionAbove.setNextConnection(connection);
		connection.setPrevConnection(connectionAbove);
		connection.setNextConnection(connectionBelow);
		connectionBelow.setPrevConnection(connection);
	}

	boolean connectionsCross(Connection connection)
	{
		if (!connectionsCross(connection, connectionAbove) && !connectionsCross(connection, connectionBelow))
			return false;

		if(StsPatchVolume.debug && StsPatchGrid.debugPoint && (StsPatchGrid.doDebugPoint(connection.nextPoint) || StsPatchGrid.doDebugPoint(connection.prevPoint)))
			StsException.systemDebug(this, "connectionCrosses", StsPatchVolume.iterLabel + connection.toString());

		return true;
	}

	boolean connectionsCross(Connection c1, Connection c2)
	{
		int crosses = StsMath.signProduct(c1.nextPoint.getSlice() - c2.getNextPoint().getSlice(), c1.getNextPoint().getSlice() - c2.getNextPoint().getSlice());
		return crosses < 0;
	}
}