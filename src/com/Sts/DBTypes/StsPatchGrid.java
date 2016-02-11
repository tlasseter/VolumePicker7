package com.Sts.DBTypes;

import com.Sts.Actions.Wizards.SurfaceCurvature.*;
import com.Sts.DB.*;
import com.Sts.MVC.View3d.*;
import com.Sts.Types.*;
import com.Sts.Utilities.Seismic.*;
import com.Sts.Utilities.*;

import javax.media.opengl.*;
import java.awt.*;
import java.util.*;

/**
 * A patchGrid has a XY boundingBox containing a row-col array of transient patchPoints. It is constructed by 2D and 3D auto-pickers.
 * For the volume auto-picker, there may be a group of patchGrids which are connected together and overlapping.  The patchGrid with
 * the smallest ID serves as the parent and the others are children in a linked list with the first being the childGrid.
 */
public class StsPatchGrid extends StsXYGridBoundingBox implements Comparable<StsPatchGrid>, StsSerializable
{
	private static final long serialVersionUID = 1L;
	/** this is the ID of this  patchGrid; it will be reset to a rowSorted sequence index when patch is completed */
	public int id;
	/** parent grid of this grid; null if this is the root grid */
	public StsPatchGrid parentGrid = null;
	/** childGrid is the next grid in the linked-list. */
	public StsPatchGrid childGrid = null;
	/** the original id of this patch in gridList; saved when patch is merged to another grid and is used for debugging purposes */
	int originalID = -1;
	/** type of this grid: Min, Max +Zero-crossing, or -Zero-crossing */
	byte patchType;
	boolean isVisible = true;
	StsPatchVolume patchVolume;
	int nPatchPoints;
	float dataMin;
	float dataMax;
	float zMin = StsParameters.largeFloat;
	float zMax = -StsParameters.largeFloat;

	float[][] pointsZ;
	Connection[][][] gridConnections;

	transient PatchPoint[][] patchPoints = null;
	/** flag indicating this patchGrid has been added to current patchVolume.rowGrids hashmap list; avoids overhead of trying to re-add */
	transient public boolean rowGridAdded = false;

	transient RowColGrid rowColGrid = new RowColGrid(rowMin, rowMax, colMin, colMax);

	transient float[][] values;

	transient int[][] colorIndices;

	transient GridCells gridCells;

	transient public int nValuePatchPoints = 0;
	transient public double sum = 0;
	/** points on this grid are in a hashMap with a hash key whose value is the grid index: col + row*patchVolume.nCols */
	// transient HashMap<Integer, StsPatchPoint> patchPointsHashMap;
	/** max size of all patches generated; printed out for diagnostics */
	static int maxGridSize = 0;
	/**
	 * index to be assigned to next patchGrid created; this is the index during construction for a multilayered patch;
	 * incremented for each one; reset on initialization
	 */
	static int nextPatchID = 0;
	/** final index in gridList reset on initialization */
	static int nextFinalPatchID = 0;

	static final float nullValue = StsParameters.nullValue;

	// Multiply # pts in SVD to get ChiSqr limit
	static final private double chiSqrMultiplyer = 2;
	//static final private double stdDevFactor = 1.5;
	static final byte FILTER_NONE = 0;
	static final byte FILTER_ON_CHI_SQ = 1;
	static final byte FILTER_ON_STD_DEV = 2;

	static public byte filterType = FILTER_ON_CHI_SQ;

	static public final float badCurvature = StsQuadraticCurvature.badCurvature;
	static public final float curvatureTest = StsQuadraticCurvature.curvatureTest;

	static boolean sortRowFirst = true;

	static final int largeInt = Integer.MAX_VALUE;

	static final float[] verticalNormal = new float[]{0.0f, 0.0f, -1.0f};

	/** Connections are defined in forward direction starting from lower left  */
	static final boolean[] isForwardDir = { true, true, false, false };
	/** row index offsets from lower-left corner of cell to each of the four corner points */
	static final int[] ccwDRow = { 0, 0, 1, 1 };
	/** col index offsets from lower-left corner of cell to each of the four corner points */
	static final int[] ccwDCol = { 0, 1, 1, 0 };
	/** index before this index */
	static final int[] prevCcwIndex = { 3, 0, 1, 2 };
	/** index after this index */
	static final int[] nextCcwIndex = { 1, 2, 3, 0 };
	/** row index offsets from this index to prev index */
	static final int[] prevDRow = { 1, 0, -1, 0 };
	/** col index offsets from this index to prev index */
	static final int[] prevDCol = { 0, -1, 0, 1 };
	/** row index offsets from this index to next index */
	static final int[] nextDRow = { 0, 1, 0, -1 };
	/** col index offsets from this index to next index */
	static final int[] nextDCol = { 1, 0, -1, 0 };

	static final public byte LINK_NONE = Connection.LINK_NONE;
	static final public byte LINK_UP = Connection.LINK_UP;
	static final public byte LINK_LEFT = Connection.LINK_LEFT;
	static final public byte LINK_DOWN = Connection.LINK_DOWN;
	static final public byte LINK_RIGHT = Connection.LINK_RIGHT;

	/*---------------------------- SYSTEM DEBUG FLAGS (DON'T EDIT) ---------------------------------------------------*/
	/** a static final: if false, all blocks bounded by this boolean will not be compiled or checked */
	static public final boolean debug = StsPatchVolume.debug;
	/** standard no-debug flag */
	static public final int NO_DEBUG = -1;
	/** patchPoint patch debug. This will be set to true if debug==true and debugPatchInitialID is set. */
	static public boolean debugPatchGrid;
	/** debugPoint is true if we have debugRow && debugCol set and either debugPatchGrid is set or debugSlice is set */
	static boolean debugPoint;
	/*---------------------------- USER DEBUG FLAGS (SET IN PatchPickPanel GUI (DON't EDIT) --------------------------*/
	/** various debugPatchGrid print of patches in rowGrid and prevRowGrid arrays;
	 * check if this is not -1 and prints if id matches this value.  Make this -1 if you want no debugPatchGrid prints. */
	static public int debugPatchInitialID = NO_DEBUG;
	/**  if a patch is being debugged (specified by patchID or debug point row,col,slice), draw this patch and not any others. */
	static public boolean debugPatchDraw = false;
	/** debugPatchID may change if original patch is merged into a new one; when this occurs, set debugCurrentPatchID to new one and track it */
	static int debugPatchID = NO_DEBUG;
	/** patchPoint row debug; used when row & ool and either slice or patchID are set. Set if you want a specific row & col at either a slice or patch debugged. */
	static int debugPointRow = NO_DEBUG;
	/** patchPoint col debug; used when row & ool and either slice or patchID are set. Set if you want a specific row & col at either a slice or patch debugged. */
	static int debugPointCol = NO_DEBUG;
	/** patchPoint slice debug. Set if you want a specific row & col & slice debugged. */
	static int debugPointSlice = NO_DEBUG;
	/*----------------------------------------------------------------------------------------------------------------*/


	public StsPatchGrid()
	{
	}

	private StsPatchGrid(StsPatchVolume patchVolume, byte patchType)
	{
		this.patchVolume = patchVolume;
		this.patchType = patchType;
	}

	static public StsPatchGrid construct(StsPatchVolume patchVolume, byte patchType)
	{
		StsPatchGrid patchGrid = new StsPatchGrid(patchVolume, patchType);
		patchGrid.setup();
		return patchGrid;
	}

	static public void staticInitialize()
	{
		nextPatchID = 0;
		nextFinalPatchID = 0;
		debugPatchID = debugPatchInitialID;
	}

	private void setup()
	{
		id = nextPatchID++;
		if (debugPatchID != NO_DEBUG && id == debugPatchID)
			StsException.systemDebug(this, "setup", "patch " + id + " initialized");
	}

	static public void initializeDebug(StsPatchPickPanel pickPanel)
	{
		debugPatchInitialID = pickPanel.patchId;
		debugPatchDraw = pickPanel.isPatchDraw();
		debugPatchID = debugPatchInitialID;
		debugPatchGrid = debug && debugPatchInitialID != NO_DEBUG;
		debugPointRow = pickPanel.pointRow;
		debugPointCol = pickPanel.pointCol;
		debugPointSlice = pickPanel.pointSlice;
		debugPoint = debug && debugPointRow != NO_DEBUG && debugPointCol != NO_DEBUG && (debugPatchGrid || debugPointSlice != NO_DEBUG);
	}

	public boolean debug()
	{
		return debugPatchID != -1 && id == debugPatchID;
	}

	/**
	 * give two different size grids with this intersection, check if we can merge the two;
	 * possible only if they don't have common occupancy of any row-col location
	 * @param patchGrid1 first of patchGrids to merge
	 * @param patchGrid2 second of patchGrids to merge
	 */
	static public boolean mergePatchPointsOK(StsPatchGrid patchGrid1, StsPatchGrid patchGrid2)
	{
		// check for any overlap between this grid and patchPointGrid
		RowColGridFilled intersectGrid = patchGrid1.rowColGridIntersect(patchGrid2);
		int r1 = intersectGrid.rowMin - patchGrid1.rowMin;
		int r2 = intersectGrid.rowMin - patchGrid2.rowMin;
		int c1 = intersectGrid.colMin - patchGrid1.colMin;
		int c2 = intersectGrid.colMin - patchGrid2.colMin;
		for (int row = 0; row < intersectGrid.nRows; row++, r1++, r2++)
		{
			int cc1 = c1;
			int cc2 = c2;
			for (int col = 0; col < intersectGrid.nCols; col++, cc1++, cc2++)
			{
				if (patchGrid1.patchPoints[r1][cc1] != null && patchGrid2.patchPoints[r2][cc2] != null)
					return false;
			}
		}
		return true;
	}

	static public StsPatchGrid getLargestGrid(StsPatchGrid patchGrid0, StsPatchGrid patchGrid1)
	{
		if(patchGrid0.nPatchPoints >= patchGrid1.nPatchPoints)
			return patchGrid0;
		else
			return patchGrid1;
	}

	/**
	 * We wish to merge the points from removedGrid into this one.  Copy both sets of points to a new grid which is union of two.
	 * reset the removedGrid patchPoints.id to this id
	 * @param removedGrid newPatchGrid to be merged to this (otherPatchGrid).
	 * @return true if merged successfully
	 */
	boolean mergePatchPoints(StsPatchGrid removedGrid)
	{
		RowColGrid union = rowColGridUnion(removedGrid); //union of this and newPatchGrid
		PatchPoint[][] newMergedPatchPoints = new PatchPoint[union.nRows][union.nCols]; // create empty mergedPoints grid
		if (!copyPatchPointsTo(newMergedPatchPoints, union)) return false; // copy this patchPoints to mergedPoints
		if (!removedGrid.copyPatchPointsTo(newMergedPatchPoints, union)) return false;
		removedGrid.resetPatchPointsGrid(this);
		resetPatchPoints(union, newMergedPatchPoints);
		nPatchPoints += removedGrid.nPatchPoints;
		return true;
	}

	/**
	 * this patchGrid is being merged to another patchGrid with this ID. Reset all the merged patchPoints to this id.
	 * @param newPatchGrid patch which this patch is being merged into
	 */
	private void resetPatchPointsGrid(StsPatchGrid newPatchGrid)
	{
		for (int row = 0; row < nRows; row++)
			for (int col = 0; col < nCols; col++)
				if (patchPoints[row][col] != null) patchPoints[row][col].setPatchGrid(newPatchGrid);
	}

	RowColGridFilled rowColGridIntersect(StsPatchGrid otherGrid)
	{
		int rowMin, rowMax, colMin, colMax;
		rowMin = Math.max(this.rowMin, otherGrid.rowMin);
		rowMax = Math.min(this.rowMax, otherGrid.rowMax);
		colMin = Math.max(this.colMin, otherGrid.colMin);
		colMax = Math.min(this.colMax, otherGrid.colMax);
		return new RowColGridFilled(rowMin, rowMax, colMin, colMax);
	}

	RowColGrid rowColGridUnion(StsPatchGrid otherGrid)
	{
		int rowMin, rowMax, colMin, colMax;
		rowMin = Math.min(this.rowMin, otherGrid.rowMin);
		rowMax = Math.max(this.rowMax, otherGrid.rowMax);
		colMin = Math.min(this.colMin, otherGrid.colMin);
		colMax = Math.max(this.colMax, otherGrid.colMax);
		return new RowColGrid(rowMin, rowMax, colMin, colMax);
	}

	static final int maxChildCount = 10000;

	/**
	 * Combine link-list from two grids together into a new link-list attached to one of the two grids.
	 * If a grid has a non-null parentGrid, then it belongs to the childGrid list for that parentGrid; it is a childGrid.
	 * if a grid nas a null parent and a non-null child, then it has a list of childGrids; it is a parentGrid.
	 * If a grid has null parentGrid and null childGrid, then it is brand new.
	 * So: if one grid is a parent and the other a child, add child to this parent.
	 * if both grids are parents, merge the two lists together with the one with the lowest ID as the new parent.
	 * if one grid is a child and the other new, then add the new one to the child.parent list.
	 * if both grids are children, use the parent of the two with the lowest ID as the parent for the other.
	 * Given the first grid as thisGrid and the second as newChildGrid, we have nine possible combinations:
	 * parent-parent, parent-child, parent-new, child-parent, new-parent, child-child, child-new, new-child, and new-new
	 */
	public void combineChildGrids(StsPatchGrid newChildGrid)
	{
		if (debugPatchID != NO_DEBUG && (newChildGrid.id == debugPatchID || id == debugPatchID))
			StsException.systemDebug(this, "combineChildGrids", "DEBUG COMBINE CHILD GRIDS: " + newChildGrid.toFamilyString() + " AND GRID: " + toFamilyString());

		// check for parent-parent, parent-child, parent-new, child-parent, new-parent, child-child, child-new, new-child, and new-new combinations
		if (isParent()) // this grid is a parent
		{
			if (newChildGrid.isParent()) // parent-parent: assume children are mutually exclusive
			{
				checkAddConnectedGrid(newChildGrid);
			}
			else if (newChildGrid.isChild()) // parent-child: use parent-child.parent
			{
				checkAddConnectedGrid(newChildGrid.parentGrid);
			}
			else // parent-new
			{
				addChildGrids(newChildGrid);
			}
		}
		else if (newChildGrid.isParent())
		{
			if (isChild())  // child-parent
			{
				parentGrid.checkAddConnectedGrid(newChildGrid);
			}
			else // new-parent
			{
				newChildGrid.addChildGrids(this);
			}
		}
		else // neither grid is a parent, so find a child with the best parent
		{
			if (isChild())
			{
				if (newChildGrid.isChild()) // child-child, use parent with lowest ID
				{
					newChildGrid.parentGrid.checkAddConnectedGrid(parentGrid);
				}
				else // child-new
				{
					parentGrid.addChildGrids(newChildGrid);
				}
			}
			else if (newChildGrid.isChild())
			{
				newChildGrid.parentGrid.addChildGrids(this); // new-child

			}
			else // new-new; make parent of one with lowest ID
			{
				checkAddConnectedGrid(newChildGrid);
			}
		}
	}

	public final boolean isParent()
	{
		return parentGrid == null && childGrid != null;
	}

	public final boolean isChild()
	{
		return parentGrid != null;
	}

	public final boolean isNew()
	{
		return parentGrid == null && childGrid == null;
	}

	static private String getGridsRelation(StsPatchGrid grid, StsPatchGrid newGrid)
	{
		return grid.getFamilyTypeString() + "-" + newGrid.getFamilyTypeString();
	}

	public String getFamilyTypeString()
	{
		if (isParent()) return " PARENT ";
		else if (isChild()) return " CHILD ";
		else return " NEW ";
	}

	public String getPatchTypeString()
	{
		return " " + StsTraceUtilities.typeStrings[patchType] + " ";
	}

	public String getGridDescription()
	{
		StsPatchGrid parentGrid;
		String gridFamilyType = getFamilyTypeString();
		if (isNew())
			return " gridType: New grids: 1 nPoints: " + nPatchPoints;

		if (this.parentGrid == null)
			parentGrid = this;
		else
			parentGrid = this.parentGrid;

		int nGrids = 1;
		int nPatchPoints = parentGrid.nPatchPoints;
		StsPatchGrid childGrid = parentGrid.childGrid;
		while (childGrid != null)
		{
			nGrids++;
			nPatchPoints += childGrid.nPatchPoints;
			childGrid = childGrid.childGrid;
		}
		return " gridType: " + gridFamilyType + " grids: " + nGrids + " nPoints: " + nPatchPoints;
	}

	private final void checkAddConnectedGrid(StsPatchGrid newChildGrid)
	{
		if (this == newChildGrid) return;
		if (newChildGrid.id < id)
			newChildGrid.addChildGrids(this);
		else
			addChildGrids(newChildGrid);
	}

	/**
	 * Add this newChildGrid and its link-list to this grid link-list and remove the newChildGrid link-list and set its
	 * parentGrid to this grid.
	 * If a link-list exists, then the grid it's associated with must be a parentGrid, otherwise it is a childGrid.
	 * A grid can exist in only one link-list unless it is a rootGrid (no parent, but has children).
	 * @param newChildGrid grid and its link-list to be added to this grid
	 */
	private void addChildGrids(StsPatchGrid newChildGrid)
	{
		if (debugPatchID != NO_DEBUG && (newChildGrid.id == debugPatchID || id == debugPatchID))
			StsException.systemDebug(this, "addChildGrid", "DEBUG CHILD GRID: " + getGridsRelation(this, newChildGrid) + toFamilyString() + " AND " + newChildGrid.toFamilyString());

		// check if parentGrid already contains this childGrid; if not, add it
		// if parent already has child, method returns false; if false, return with no further action
		if (!checkSetChildGrid(this, newChildGrid)) return;
		// set the parentGrid for these added childGrids to this grid
		resetParentForChildren(newChildGrid, this);
	}

	static private boolean checkSetChildGrid(StsPatchGrid parentGrid, StsPatchGrid newChildGrid)
	{
		if (parentGrid == newChildGrid)
			return true;
		int[] gridIDs;
		int count = 0;
		if (debug)
		{
			gridIDs = new int[nextPatchID];
			Arrays.fill(gridIDs, -1);
			gridIDs[parentGrid.id] = count++;
		}
		// check if this newChildGrid is not already a child of this grid
		StsPatchGrid childGrid = parentGrid.childGrid;
		while (childGrid != null)
		{
			if (childGrid == newChildGrid)
			{
				return false; // already in list
			}
			if (debug)
			{
				if (gridIDs[childGrid.id] != -1)
					StsException.systemDebug(StsPatchGrid.class, "addChildGrids", "CHILD GRID REPEATED " + " id: " + childGrid.id);
				else
					gridIDs[childGrid.id] = count;

				count++;
				if (count > maxChildCount)
				{
					StsException.systemDebug(StsPatchGrid.class, "addChildGrids", "MAX CHILD GRID COUNT " + maxChildCount + " exceeded.");
					return false;
				}
			}
			parentGrid = childGrid;
			childGrid = parentGrid.childGrid;
		}
		// we have found the last non-null child in list (parentGrid); add the newChildGrid to it
		parentGrid.childGrid = newChildGrid;
		return true;
	}

	static public void resetParentForChildren(StsPatchGrid firstChildGrid, StsPatchGrid newParentGrid)
	{
		int count = 0;
		int[] gridIDs;
		if (debug)
		{
			gridIDs = new int[nextPatchID];
			Arrays.fill(gridIDs, -1);
			gridIDs[newParentGrid.id] = count++;
		}

		StsPatchGrid childGrid = firstChildGrid;
		while (childGrid != null)
		{
			if (debugPatchID != NO_DEBUG && childGrid.id == debugPatchID)
				StsException.systemDebug(StsPatchGrid.class, "resetParentForChildren", "GRID PARENT SET. GRID: " + childGrid.toFamilyString() + " NEW PARENT " + newParentGrid.id);

			childGrid.parentGrid = newParentGrid;
			if (debug)
			{
				if (gridIDs[childGrid.id] != -1)
					StsException.systemDebug(StsPatchGrid.class, "addChildGrids", "RESET ID CHILD GRID REPEATED " + " id: " + childGrid.id);
				else
					gridIDs[childGrid.id] = count;

				count++;
				if (count > maxChildCount)
				{
					StsException.systemDebug(StsPatchGrid.class, "addChildGrids", "RESET ID MAX CHILD GRID COUNT " + maxChildCount + " exceeded.");
					return;
				}
			}
			childGrid = childGrid.childGrid;
		}
	}

	/**
	 * This grid is the removedGrid which is being merged with mergedGrid.
	 * removedGrid could be parent, child, or new;
	 * if removedGrid is a parent, then the first child needs to be made the parent and all its children need to have parent reset to first child
	 * if removed grid is a child, it needs to be removed from the parent (deleted from link-list)
	 * if new, we don't have to do anything
	 * @param mergedGrid included for debugging only
	 */
	public void removeChildOrParent(StsPatchGrid mergedGrid)
	{
		if (debugPatchID != NO_DEBUG && (id == debugPatchID || mergedGrid.id == debugPatchID))
			StsException.systemDebug(this, "removeChildOrParent", "DEBUG GRID. REMOVED: " + toString() + " MERGED TO: " + mergedGrid.toString());

		if (isParent())
		{
			if (debugPatchID != NO_DEBUG && (id == debugPatchID || mergedGrid.id == debugPatchID))
				StsException.systemDebug(this, "removeChildOrParent", "GRID PARENT SET TO NULL. GRID: " + childGrid.toFamilyString());
			// parent is removed, and its child is new parent
			childGrid.parentGrid = null;
			StsPatchGrid.resetParentForChildren(childGrid.childGrid, childGrid);

		}
		if (isChild())
		{
			StsPatchGrid parentGrid = this.parentGrid;
			StsPatchGrid childGrid = parentGrid.childGrid;
			while (childGrid != null)
			{
				if (childGrid == this)
				{
					parentGrid.childGrid = this.childGrid;
					this.childGrid = null;
				}
				parentGrid = childGrid;
				childGrid = childGrid.childGrid;
			}
		}
	}

	private RowColGrid getRowColGrid()
	{
		return new RowColGrid(rowMin, rowMax, colMin, colMax);
	}

	class RowColGrid
	{
		int rowMin = largeInt;
		int rowMax = -largeInt;
		int colMin = largeInt;
		int colMax = -largeInt;
		int nRows = 0, nCols = 0;

		RowColGrid(int rowMin, int rowMax, int colMin, int colMax)
		{
			this.rowMin = rowMin;
			this.rowMax = rowMax;
			this.colMin = colMin;
			this.colMax = colMax;
			nRows = rowMax - rowMin + 1;
			nCols = colMax - colMin + 1;
		}

		public String toString()
		{
			return new String(" rowMin: " + rowMin + " rowMax: " + rowMax + " colMin: " + colMin + " colMax: " + colMax);
		}
	}

	class RowColGridFilled extends RowColGrid
	{
		boolean[][] isFilled;
		int nFilled = 0;

		RowColGridFilled(int rowMin, int rowMax, int colMin, int colMax)
		{
			super(rowMin, rowMax, colMin, colMax);
			isFilled = new boolean[nRows][nCols];
		}

		void fill(int row, int col)
		{
			gridFill(row - rowMin, col - colMin);
		}

		boolean gridFill(int row, int col)
		{
			if (isFilled[row][col]) return false;
			isFilled[row][col] = true;
			nFilled++;
			return true;
		}
	}

	/**
	 * return true if patchPoint overlaps this grid; i.e., there already is a point on this grid at the same row and col
	 * @param patchPoint point at whose row and col we want to see if point already exists on this grid
	 * @return true if this point overlaps an existing point on this grid
	 */
	boolean patchPointOverlaps(PatchPoint patchPoint)
	{
		try
		{
			if (getVolPatchPoint(patchPoint) == null) return false;
			if (debug && debugPoint && (doDebugPoint(patchPoint)))
				StsException.systemDebug(this, "patchPointOverlaps", StsPatchVolume.iterLabel + "patchPoint " + patchPoint.toString() +
						" overlaps nextWindow " + getVolPatchPoint(patchPoint));
			return true;
		}
		catch (Exception e)
		{
			StsException.outputWarningException(this, "patchPointOverlaps", "FAILED FOR WINDOW: " + patchPoint.toString() +
					" ON GRID: " + toGridString(), e);
			return false;
		}
	}

	boolean addChangePatchPoint(PatchPoint patchPoint)
	{
		if(!addPatchPoint(patchPoint)) return false;
		patchPoint.setPatchGrid(this);
		return true;
	}

	/** NOT CURRENTLY USED.
	 *  Add patchPoint to this patchGrid.
	 *  If point already exists here, don't add and return true.
	 *
	 * @param patchPoint point to be added to grid
	 * @return true if point added or point already exists here.
	 */
	boolean checkAddPatchPoint(PatchPoint patchPoint)
	{
		if (debug && debugPoint && (doDebugPoint(patchPoint)))
			StsException.systemDebug(this, "checkAddPatchPoint", StsPatchVolume.iterLabel + "CHECK ADD POINT TO GRID: " + id + " POINT: " + patchPoint.toString());

		if(!initializeAddPatchPoint(patchPoint)) return false;
		PatchPoint currentPoint = patchPoints[patchPoint.getRow() - rowMin][patchPoint.getCol() - colMin];
		if (currentPoint != null) return true;
		patchPoints[patchPoint.getRow() - rowMin][patchPoint.getCol() - colMin] = patchPoint;
		nPatchPoints++;
		return true;
	}
	/** Try to add this point to this patch.  If there is the same point already at this location,
	 *  don't readd and return true.  If there is a different point at this location, return false;
	 *  If no point at this location, expand grid to contain it if necessary and add and return true.
	 *
	 * @param patchPoint
	 * @return
	 */
	boolean addPatchPoint(PatchPoint patchPoint)
	{
		// if (debugPatchGrid && id == debugPatchID)
		if (debug && debugPoint && (doDebugPoint(patchPoint)))
			StsException.systemDebug(this, "addPatchPoint", StsPatchVolume.iterLabel + "ADD POINT TO GRID: " + id + " POINT: " + patchPoint.toString());

		if(!initializeAddPatchPoint(patchPoint)) return false;

		PatchPoint currentPoint = patchPoints[patchPoint.getRow() - rowMin][patchPoint.getCol() - colMin];
		if (currentPoint != null)
		{
			if(currentPoint == patchPoint) return true;

			StsException.systemError(this, "addPatchPoint", "pointGrid " + rowColGrid.toString() + " ALREADY HAS Point with different id. " + currentPoint.toString() +
				" so can't add " + patchPoint.toString());
			return false;
		}
		patchPoints[patchPoint.getRow() - rowMin][patchPoint.getCol() - colMin] = patchPoint;
		nPatchPoints++;
		return true;
	}

	boolean initializeAddPatchPoint(PatchPoint patchPoint)
	{
		if (patchPoints == null)
			initializePatchPoints(patchPoint);
		else
			checkAdjustGrid(patchPoint);
		if (contains(patchPoint))  return true;

		StsException.systemError(this, "addPatchPoint", "pointGrid " + rowColGrid.toString() + " doesn't contain nextWindow " + patchPoint.toString());
		return false;
	}

	void setPoint(PatchPoint patchPoint)
	{
		patchPoints[getGridRow(patchPoint)][getGridCol(patchPoint)] = patchPoint;
	}

	final public int getGridRow(PatchPoint patchPoint)
	{
		return patchPoint.getRow() - rowMin;
	}

	final public int getGridCol(PatchPoint patchPoint)
	{
		return patchPoint.getCol() - colMin;
	}

	void checkAdjustGrid(PatchPoint patchPoint)
	{
		int row = patchPoint.getRow();
		int col = patchPoint.getCol();

		int rowMinNew = rowMin, rowMaxNew = rowMax, colMinNew = colMin, colMaxNew = colMax;

		boolean gridChanged = false;
		if (row < this.rowMin)
		{
			rowMinNew = row;
			gridChanged = true;
		}
		if (row > this.rowMax)
		{
			rowMaxNew = row;
			gridChanged = true;
		}
		if (col < this.colMin)
		{
			colMinNew = col;
			gridChanged = true;
		}
		if (col > this.colMax)
		{
			colMaxNew = col;
			gridChanged = true;
		}

		if (!gridChanged) return;

		RowColGrid newRowColGrid = new RowColGrid(rowMinNew, rowMaxNew, colMinNew, colMaxNew);
		copyResetRowColGrid(newRowColGrid);
	}

	void copyResetRowColGrid(RowColGrid newRowColGrid)
	{
		if (debug && debugPatchGrid && id == debugPatchID)
			StsException.systemDebug(this, "copyResetRowColGrid", StsPatchVolume.iterLabel + "grid reset from " + rowColGrid + " to " + newRowColGrid);
		PatchPoint[][] newPatchPoints = copyPatchPoints(newRowColGrid);
		resetPatchPoints(newRowColGrid, newPatchPoints);
	}

	/**
	 * points have been removed from this grid, so resize down.
	 * @return false if grid has zero rows or zer columns.
	 */
	boolean adjustGridSizeDown()
	{
		int newRowMin = rowMin;
		rowMinLoop:
		for (int row = 0; row < nRows; row++, newRowMin++)
			for (int col = 0; col < nCols; col++)
				if (patchPoints[row][col] != null)
					break rowMinLoop;

		int newRowMax = rowMax;
		rowMaxLoop:
		for (int row = nRows - 1; row >= 0; row--, newRowMax--)
			for (int col = 0; col < nCols; col++)
				if (patchPoints[row][col] != null)
					break rowMaxLoop;

		if (newRowMin > newRowMax) return false;

		int newColMin = colMin;
		colMinLoop:
		for (int col = 0; col < nCols; col++, newColMin++)
			for (int row = 0; row < nRows; row++)
				if (patchPoints[row][col] != null)
					break colMinLoop;

		int newColMax = colMax;
		colMaxLoop:
		for (int col = nCols - 1; col >= 0; col--, newColMax--)
			for (int row = 0; row < nRows; row++)
				if (patchPoints[row][col] != null)
					break colMaxLoop;

		if (newColMin > newColMax) return false;

		RowColGrid newRowColGrid = new RowColGrid(newRowMin, newRowMax, newColMin, newColMax);
		PatchPoint[][] newPatchPoints = new PatchPoint[newRowColGrid.nRows][newRowColGrid.nCols];
		copyPatchPointsTo(newPatchPoints, newRowColGrid);
		initializeRowColGrid(newRowColGrid);
		patchPoints = newPatchPoints;
		return true;
	}

	PatchPoint[][] copyPatchPoints(RowColGrid newRowColGrid)
	{
		if (patchPoints == null) return null;
		PatchPoint[][] newPatchPoints = new PatchPoint[newRowColGrid.nRows][newRowColGrid.nCols];
		if (!copyPatchPointsTo(newPatchPoints, newRowColGrid))
			return null;
		else
			return newPatchPoints;
	}

	boolean copyPatchPointsTo(PatchPoint[][] newPatchPoints, RowColGrid newRowColGrid)
	{
		int row = -1, newRow = -1;
		int col = -1, newCol = -1;
		try
		{
			int rowStart = rowMin - newRowColGrid.rowMin;
			int colStart = colMin - newRowColGrid.colMin;
			for (row = 0, newRow = rowStart; row < nRows; row++, newRow++)
				for (col = 0, newCol = colStart; col < nCols; col++, newCol++)
				{
					if (patchPoints[row][col] != null)
					{
						if (newPatchPoints[newRow][newCol] != null)
							StsException.systemError(this, "copyPatchPointsTo", "FAILED COPYING PATCH POINTS from grid: " + id + " " + rowColGrid.toString() +
									" row: " + row + " col: " + col + " to new grid " + newRowColGrid.toString() +
									"row: " + newRow + "col: " + newCol);
						else
							newPatchPoints[newRow][newCol] = patchPoints[row][col];
					}
				}
			return true;
		}
		catch (Exception e)
		{
			StsException.outputWarningException(this, "copyPatchPointsTo", "Failed copying patch " + rowColGrid.toString() + " row: " + row +
					" to new grid " + newRowColGrid.toString() + "row: " + newRow, e);
			return false;
		}
	}

	void resetPatchPoints(RowColGrid newRowColGrid, PatchPoint[][] newPatchPoints)
	{
		initializeRowColGrid(newRowColGrid);
		patchPoints = newPatchPoints;
	}

	boolean contains(RowColGrid newRowColGrid)
	{
		return newRowColGrid.rowMin >= rowMin && newRowColGrid.rowMax <= rowMax &&
				newRowColGrid.colMin >= colMin && newRowColGrid.colMax <= colMax;
	}

	boolean contains(PatchPoint patchPoint)
	{
		int row = patchPoint.getRow();
		int col = patchPoint.getCol();
		return row >= rowMin && row <= rowMax && col >= colMin && col <= colMax;
	}

	private void initializePatchPoints(PatchPoint patchPoint)
	{
		initializeRowColGrid(patchPoint);
		patchPoints = new PatchPoint[nRows][nCols];
	}

	/** debug this point if point row and col are set and they match debug criteria or point.patchGrid.id matches criteria */
	public static final boolean doDebugPoint(PatchPoint patchPoint)
	{
		if (debugPoint)
		{
			if (patchPoint == null || patchPoint.getRow() != debugPointRow || patchPoint.getCol() != debugPointCol)
				return false;
			if (patchPoint.getSlice() == debugPointSlice) return true;
		}
		return debug && debugPatchGrid && patchPoint.patchGrid != null && patchPoint.patchGrid.id == debugPatchID;
	}

	public static final boolean doDebugPoint(PatchPoint patchPoint, int volRow, int volCol)
	{
		if (debugPoint)
		{
			if (patchPoint == null || volRow != debugPointRow || volCol != debugPointCol)
				return false;
			if (patchPoint.getSlice() == debugPointSlice) return true;
		}
		return debug && debugPatchGrid && patchPoint.patchGrid != null && patchPoint.patchGrid.id == debugPatchID;
	}

	public void clear()
	{
		initializeRowColGrid();
		patchPoints = null;
		if (debugPatchID != NO_DEBUG && id == debugPatchID)
			//if(debugPatchGrid())
			StsException.systemDebug(this, "clear", "clearing patch: " + toString());
	}

	private void initializeRowColGrid()
	{
		initializeRowColGrid(largeInt, -largeInt, largeInt, -largeInt);
	}

	private void initializeRowColGrid(int rowMin, int rowMax, int colMin, int colMax)
	{
		initializeRowColGrid(new RowColGrid(rowMin, rowMax, colMin, colMax));
	}

	private void initializeRowColGrid(PatchPoint patchPoint)
	{
		int row = patchPoint.getRow();
		int col = patchPoint.getCol();
		initializeRowColGrid(row, row, col, col);
	}

	private void initializeRowColGrid(RowColGrid newRowColGrid)
	{
		if (debugPatchID != NO_DEBUG && id == debugPatchID)
			//if(debugPatchGrid())
			StsException.systemDebug(this, "initializeRowColGrid", "FOR GRID: " + id + " RESET rowColGrid FROM " + rowColGrid + " TO " + newRowColGrid);
		rowMin = newRowColGrid.rowMin;
		rowMax = newRowColGrid.rowMax;
		colMin = newRowColGrid.colMin;
		colMax = newRowColGrid.colMax;
		nRows = rowMax - rowMin + 1;
		nCols = colMax - colMin + 1;
		this.rowColGrid = newRowColGrid;
	}

	boolean isDisconnected(int row)
	{
		if (debug && StsPatchGrid.debugPatchID == id)
		{
			boolean disconnected = rowMax < row;
			String isOrIsnt = disconnected ? " is " : " isn't ";
			StsException.systemDebug(this, "isDisconnected", "patch " + id + isOrIsnt + " disconnected at row: " + row);
		}
		return rowMax < row;
	}

	boolean isOnePoint()
	{
		return rowMax - rowMin <= 0 && colMax - colMin <= 0;
	}

	boolean isTooSmall(int minNPoints)
	{
		return nPatchPoints <= minNPoints;
	}

	public void resetIndex(int index)
	{
		originalID = id;
		id = index;
		if (debugPatchID != -1 && originalID == debugPatchID)
		{
			StsException.systemDebug(this, "resetIndex", "debugPatch id being reset from " + originalID + " to " + id);
			debugPatchID = id;
		}
	}

	public void finish()
	{
		if (debug && debugPatchGrid && id == debugPatchID)
			StsException.systemDebug(this, "finish", "for patch " + toGridString());

		if (pointsZ != null)
		{
			//	if(debug)
			//		StsException.systemDebug(this, "finish", "PATCH already finished. PATCH: " + toGridString());
			return;
		}
		try
		{
			pointsZ = new float[nRows][nCols];
			gridConnections = new Connection[nRows][nCols][];
			for (int row = 0; row < nRows; row++)
			{
				for (int col = 0; col < nCols; col++)
				{
					PatchPoint patchPoint = patchPoints[row][col];
					if (patchPoint != null)
					{
						float z = patchPoint.z;
						pointsZ[row][col] = z;
						zMin = Math.min(zMin, z);
						zMax = Math.max(zMax, z);
						gridConnections[row][col] = patchPoint.getConnections();
						if (!StsPatchVolume.debug)
							patchPoint.nullTemps();
					}
					else
					{
						pointsZ[row][col] = nullValue;
					}
				}
			}

		}
		catch (Exception e)
		{
			StsException.outputWarningException(this, "finish", e);
		}
	}

	public Connection[][][] getGridConnections() { return gridConnections; }

	/** get nearest patch whose z at this x,y is just above the z slice plane */
	public float getZDistance(int volumeRow, int volumeCol, float z)
	{
		int patchRowMin = Math.max(0, volumeRow - rowMin - 5);
		int patchRowMax = Math.min(nRows - 1, volumeRow - rowMin + 5);
		int patchColMin = Math.max(0, volumeCol - colMin - 5);
		int patchColMax = Math.min(nCols - 1, volumeCol - colMin + 5);
		float dz = StsParameters.largeFloat;
		for (int patchRow = patchRowMin; patchRow <= patchRowMax; patchRow++)
		{
			for (int patchCol = patchColMin; patchCol <= patchColMax; patchCol++)
			{
				float zPatch = getPatchPointZ(patchRow, patchCol);
				if (zPatch == nullValue) continue;
				float dzPatch = z - zPatch;
				if (dzPatch < 0.0f) continue;
				if (dzPatch < dz) dz = dzPatch;
			}
		}
		return dz;
	}

	/** sort first by rowMin and then by colMin. Return 1 if this rowMin&colMin come after other; 0 if equal; -1 otherwise */
	public int compareTo(StsPatchGrid otherGrid)
	{
		if(otherGrid == null)
		{
			StsException.systemError(this, "compareTo", "otherGrid cannot be null.");
			return 0;
		}
		if (sortRowFirst)
		{
			if (rowMin > otherGrid.rowMin) return 1;
			if (rowMin < otherGrid.rowMin) return -1;
			// on the same row
			if (colMin > otherGrid.colMin) return 1;
			if (colMin < otherGrid.colMin) return -1;
			return 0;
		}
		else
		{
			if (colMin > otherGrid.colMin) return 1;
			if (colMin < otherGrid.colMin) return -1;
			// on the same col
			if (rowMin > otherGrid.rowMin) return 1;
			if (rowMin < otherGrid.rowMin) return -1;
			return 0;
		}
	}

	public float[][] getPointsZ()
	{
		return pointsZ;
	}

	public float[][] getNextRowPointsZ()
	{
		float[][] nextRowPointsZ = new float[nRows][nCols];

		for(int row = 0; row < nRows; row++)
		{
			for(int col = 0; col < nCols; col++)
			{
				PatchPoint point = patchPoints[row][col];
				if(point == null)
					nextRowPointsZ[row][col] = nullValue;
				else // point != null
				{
					Connection nextConnection = point.getNextRowConnection();
					if(nextConnection == null)
						nextRowPointsZ[row][col] = nullValue;
					else
						nextRowPointsZ[row][col] = nextConnection.getNextPoint().z;
				}
			}
		}
		return nextRowPointsZ;
	}

	public float[][] getNextColPointsZ()
	{
		float[][] nextColPointsZ = new float[nRows][nCols];

		for(int row = 0; row < nRows; row++)
		{
			for(int col = 0; col < nCols; col++)
			{
				PatchPoint point = patchPoints[row][col];
				if(point == null)
					nextColPointsZ[row][col] = nullValue;
				else // point != null
				{
					Connection nextConnection = point.getNextColConnection();
					if(nextConnection == null)
						nextColPointsZ[row][col] = nullValue;
					else
						nextColPointsZ[row][col] = nextConnection.getNextPoint().z;
				}
			}
		}
		return nextColPointsZ;
	}

	public int getGridSize()
	{
		return nRows * nCols;
	}

	public int[] getGridPointsUsed()
	{
		int nUsed = 0;
		int nActualUsed = 0;
		for (int row = 0; row < nRows; row++)
		{
			int colStart = 0, colEnd = nCols - 1;
			for (int col = 0; col < nCols; col++)
			{
				if (pointsZ[row][col] != nullValue)
				{
					colStart = col;
					break;
				}
			}
			for (int col = nCols - 1; col > 0; col--)
			{
				if (pointsZ[row][col] != nullValue)
				{
					colEnd = col;
					break;
				}
			}

			for (int col = colStart; col <= colEnd; col++)
				if (pointsZ[row][col] != nullValue) nActualUsed++;

			nUsed += colEnd - colStart + 1;
		}
		return new int[]{nUsed, nActualUsed};
	}

	public float getDataMin()
	{
		return dataMin;
	}

	public float getDataMax()
	{
		return dataMax;
	}

	public float getzMax()
	{
		return zMax;
	}

	public float getzMin()
	{
		return zMin;
	}

	public boolean computeCurvature(byte curveType, int filterSize, int minNPoints)
	{
		dataMin = StsPatchVolume.largeFloat;
		dataMax = -StsPatchVolume.largeFloat;

		values = new float[nRows][nCols];
		for (int row = 0; row < nRows; row++)
			Arrays.fill(values[row], nullValue);

		if (nPatchPoints < minNPoints) return false;

		int halfWindow = filterSize / 2;
		// Determine quadratic coefficients for this neighborhood

		float[][] fitPoints = new float[filterSize * filterSize][3];
		for (int volumeRow = rowMin; volumeRow <= rowMax; volumeRow++)
		{
			for (int volumeCol = colMin; volumeCol <= colMax; volumeCol++)
			{
				int nFitPoints = 0;  // number of equations
				int patchPointRow = volumeRow - rowMin;
				int patchPointCol = volumeCol - colMin;
				float zc = getPatchPointZ(patchPointRow, patchPointCol);
				if (zc == StsParameters.nullValue) continue;
				int patchRowMin = Math.max(0, patchPointRow - halfWindow);
				int patchRowMax = Math.min(nRows - 1, patchPointRow + halfWindow);
				int patchColMin = Math.max(0, patchPointCol - halfWindow);
				int patchColMax = Math.min(nCols - 1, patchPointCol + halfWindow);
				float y = (patchRowMin - patchPointRow) * patchVolume.yInc;
				for (int patchRow = patchRowMin; patchRow <= patchRowMax; patchRow++, y += patchVolume.yInc)
				{
					float x = (patchColMin - patchPointCol) * patchVolume.xInc;
					for (int patchCol = patchColMin; patchCol <= patchColMax; patchCol++, x += patchVolume.xInc)
					{
						float z = pointsZ[patchRow][patchCol];
						if (z == StsParameters.nullValue) continue;
						fitPoints[nFitPoints][0] = x;
						fitPoints[nFitPoints][1] = y;
						fitPoints[nFitPoints][2] = z - zc;
						nFitPoints++;
					}
				}
				if (nFitPoints < minNPoints) continue;

				if (!StsQuadraticCurvature.computeSVD(fitPoints, nFitPoints)) continue;

				float val;
				try
				{
					val = StsQuadraticCurvature.getCurvatureComponent(curveType);
				}
				catch (Exception e)
				{
					StsException.systemError(this, "computeCurvature", "getCurvatureComponent failed.");
					continue;
				}

				if (filterType == FILTER_ON_CHI_SQ)
				{
					double chiSqrTest = chiSqrMultiplyer * nFitPoints;
					double chiSqr = StsQuadraticCurvature.computeChiSquared();
					if (chiSqr > chiSqrTest)
					{
						// if(StsPatchVolume.debugPatchGrid) StsException.systemDebug(this, "computeCurvature", "ChiSqr = " + chiSqr + " at volumeRow, volumeCol " + volumeRow + " " + volumeCol);
						//continue;
						if (val > 0) val = badCurvature;
						if (val < 0) val = -badCurvature;
					}
				}

				values[patchPointRow][patchPointCol] = val;

				if (Math.abs(val) > curvatureTest) continue;
				// ChiSqr filtered vals not used for dataMin / dataMax & Statistics
				nValuePatchPoints++;
				sum += val;
				dataMax = Math.max(dataMax, val);
				dataMin = Math.min(dataMin, val);
			}
		}
		return nValuePatchPoints > 0;
	}

	public void drawGridLines(GL gl, StsColorscale colorscale, boolean displayCurvature)
	{
		try
		{
			drawRowGridLines(gl, colorscale, displayCurvature);
			drawColGridLines(gl, colorscale, displayCurvature);
		}
		catch(Exception e)
		{
			StsException.outputWarningException(this, "drawSurfaceFillWithNulls", e);
		}
	}

	private void drawRowGridLines(GL gl, StsColorscale colorscale, boolean displayCurvature)
	{
		float rowY = patchVolume.yMin + rowMin * patchVolume.yInc;
		for (int row = 0; row < nRows; row++, rowY += patchVolume.yInc)
			drawRowGridLine(gl, row, rowY, colorscale, displayCurvature, true);
	}

	/**
	 * @param gl GL state
	 * @param volRow volume row to draw
	 * @param colorscale colorscale used in drawing curvature
	 * @param displayCurvature true if we wish to display curvature
	 */
	public void drawVolRowGridLine(GL gl, int volRow, StsColorscale colorscale, boolean displayCurvature, boolean is3d)
	{
		float rowY = patchVolume.yMin + volRow * patchVolume.yInc;
		drawRowGridLine(gl, volRow - rowMin, rowY, colorscale, displayCurvature, is3d);
	}

	/**
	 *
	 * @param gl GL state
	 * @param row patch row
	 * @param rowY project Y coordinate
	 * @param colorscale colorscale used in drawing curvature
	 * @param displayCurvature true if we wish to display curvature
	 * @param is3d true if we are drawing in 3D otherwise 2D
	 */
	public void drawRowGridLine(GL gl, int row, float rowY, StsColorscale colorscale, boolean displayCurvature, boolean is3d)
	{
		boolean isDrawing = false;
		int col = 0;
		float z;
		try
		{
			float colX = patchVolume.xMin + colMin*patchVolume.xInc;
			for(col = 0; col < nCols; col++, colX += patchVolume.xInc)
			{
				Connection connection = getConnection(row, col, LINK_RIGHT);
				if (connection != null)
				{
					z = pointsZ[row][col];
					if (displayCurvature)
					{
						// StsTraceUtilities.getPointTypeColor(patchType).setGLColor(gl);
						float v = values[row][col];
						if (v == nullValue)
							StsColor.BLACK.setGLColor(gl);
						else
							colorscale.getStsColor(colorscale.getIndexFromValue(v)).setGLColor(gl);
					}

					if(!isDrawing)
					{
						isDrawing = true;
						gl.glBegin(GL.GL_LINE_STRIP);
						if (is3d)
							gl.glVertex3f(colX, rowY, z);
						else
							gl.glVertex2f(colX, z);
					}
					if (is3d)
						gl.glVertex3f(colX+patchVolume.xInc, rowY, connection.getNextZ());
					else
						gl.glVertex2f(colX+patchVolume.xInc, connection.getNextZ());

					if(connection.getNextPatchGrid() != this)
					{
						gl.glEnd();
						isDrawing = false;
					}
				}
				else // link is null
				{
					if(isDrawing)
					{
						gl.glEnd();
						isDrawing = false;
					}
				}
			}
		}
		catch(Exception e)
		{
			StsException.outputWarningException(this, "drawRowGridLine", e);
		}
		finally
		{
			if(isDrawing) gl.glEnd();
		}
	}

	private void drawColGridLines(GL gl, StsColorscale colorscale, boolean displayCurvature)
	{
		float colX = patchVolume.xMin + colMin * patchVolume.xInc;
		for (int col = 0; col < nCols; col++, colX += patchVolume.xInc)
			drawColGridLine(gl, col, colX, colorscale, displayCurvature, true);
	}
	/**
	 * @param gl GL state
	 * @param colorscale colorscale used in drawing curvature
	 * @param displayCurvature true if we wish to display curvature
	 */
	public void drawVolColGridLine(GL gl, int volCol, StsColorscale colorscale, boolean displayCurvature, boolean is3d)
	{
		float colX = patchVolume.xMin + volCol * patchVolume.xInc;
		drawColGridLine(gl, volCol - colMin, colX, colorscale, displayCurvature, is3d);
	}

	public void drawColGridLine(GL gl, int col, float colX, StsColorscale colorscale, boolean displayCurvature, boolean is3d)
	{
		boolean isDrawing = false;
		int row = 0;
		try
		{
			float rowY = patchVolume.yMin + rowMin*patchVolume.yInc;
			for(row = 0; row < nRows; row++, rowY += patchVolume.yInc)
			{
				Connection connection = getConnection(row, col, LINK_UP);
				if (connection != null)
				{
					float z = pointsZ[row][col];
					if (displayCurvature)
					{
						// StsTraceUtilities.getPointTypeColor(patchType).setGLColor(gl);
						float v = values[row][col];
						if (v == nullValue)
							StsColor.BLACK.setGLColor(gl);
						else
							colorscale.getStsColor(colorscale.getIndexFromValue(v)).setGLColor(gl);
					}

					if(!isDrawing)
					{
						isDrawing = true;
						gl.glBegin(GL.GL_LINE_STRIP);
						if (is3d)
							gl.glVertex3f(colX, rowY, z);
						else
							gl.glVertex2f(rowY, z);
					}
					if (is3d)
						gl.glVertex3f(colX, rowY+patchVolume.yInc, connection.getNextZ());
					else
						gl.glVertex2f(rowY+patchVolume.yInc, connection.getNextZ());

					if(connection.getNextPatchGrid() != this)
					{
						gl.glEnd();
						isDrawing = false;
					}
				}
				else // link is null
				{
					if(isDrawing)
					{
						gl.glEnd();
						isDrawing = false;
					}
				}
			}
		}
		catch(Exception e)
		{
			StsException.outputWarningException(this, "drawColGridLine", e);
		}
		finally
		{
			if(isDrawing) gl.glEnd();
		}
	}

	Connection getConnection(int row, int col, int dir)
	{

		if(gridConnections == null) return null;
		if(!isInsideGridRowCol(row, col)) return null;
		if(gridConnections[row][col] == null) return null;
		return gridConnections[row][col][dir];
	}

	Connection[] getConnections(int row, int col)
	{
		if(gridConnections == null) return null;
		if(!isInsideGridRowCol(row, col)) return null;
		return gridConnections[row][col];
	}

	public boolean isPatchGridNearZCursor(float z)
	{
		return z >= zMin && z <= zMax;
	}
/*
	public void drawPatchGrid(GL gl, boolean displayChildPatches, boolean displayCurvature, StsColorscale colorscale)
	{
		draw(gl, displayCurvature, colorscale);
		if (childGrid == null || !displayChildPatches) return;
		childGrid.drawPatchGrid(gl, displayChildPatches, displayCurvature, colorscale);
	}
*/
	public void draw(GL gl, boolean displayCurvature, StsColorscale colorscale)
	{
		if (debugPatchDraw && debugPatchID != NO_DEBUG && id == debugPatchID)
			StsException.systemDebug(this, "draw", "drawing patch " + id);

		if (pointsZ == null) return;
		if (gridCells == null) gridCells = new GridCells();
		if (displayCurvature && values != null)
		{
			setValues(values);
			gridCells.drawSurfaceFillValuesWithNulls(gl, displayCurvature, colorscale);
		}
		else
		{
			gridCells.drawSurfaceFillWithNulls(gl);
		}

		// draw grid lines
		StsColor.BLACK.setGLColor(gl);
		gl.glDisable(GL.GL_LIGHTING);
		StsGLPanel3d glPanel3d = currentModel.getGlPanel3d();
		gl.glLineWidth(StsGraphicParameters.gridLineWidth);
		glPanel3d.setViewShift(gl, StsGraphicParameters.gridShift);
		drawGridLines(gl, colorscale, displayCurvature);
		glPanel3d.resetViewShift(gl);
		gl.glEnable(GL.GL_LIGHTING);
		// TriangleStrip.drawSurfaceFillWithNulls(gl, this, tStrips, pointsZ, tStripNormals, true);
	}


	public void drawOnZCursor2d(GL gl, boolean displayCurvature, StsColorscale colorscale)
	{
		gl.glDisable(GL.GL_LIGHTING);

		if (debugPatchDraw && debugPatchID != NO_DEBUG && id == debugPatchID)
			StsException.systemDebug(this, "draw", "drawing patch " + id);

		if (pointsZ == null) return;
		if (gridCells == null) gridCells = new GridCells();
		if (displayCurvature && values != null)
		{
			setValues(values);
			gridCells.drawSurfaceFillValuesWithNulls(gl, displayCurvature, colorscale);
		}
		else
		{
			gridCells.drawSurfaceFillWithNulls2d(gl);
		}
		gl.glEnable(GL.GL_LIGHTING);

		// draw grid lines
		/*
		StsColor.BLACK.setGLColor(gl);
		gl.glDisable(GL.GL_LIGHTING);
		StsGLPanel3d glPanel3d = currentModel.getGlPanel3d();
		gl.glLineWidth(StsGraphicParameters.gridLineWidth);
		glPanel3d.setViewShift(gl, StsGraphicParameters.gridShift);
		drawGridLines(gl, colorscale, displayCurvature);
		glPanel3d.resetViewShift(gl);
		gl.glEnable(GL.GL_LIGHTING);
		*/
		// TriangleStrip.drawSurfaceFillWithNulls(gl, this, tStrips, pointsZ, tStripNormals, true);
	}

	public void drawRowVox(GL gl, float yMin, float xMin, StsColorscale colorscale)
	{
		float y = yMin + rowMin * patchVolume.yInc;
		for (int row = rowMin; row <= rowMax; row++, y += patchVolume.yInc)
		{
			float[] rowPointsZ = pointsZ[row - rowMin];

			if (values == null) continue;
			float[] rowVals = values[row - rowMin];
			if (rowVals == null) continue;
			gl.glPointSize(3.f);
			gl.glBegin(GL.GL_POINTS);
			float x = xMin + colMin * patchVolume.xInc;
			for (int col = colMin; col <= colMax; col++, x += patchVolume.xInc)
			{
				float z = rowPointsZ[col - colMin];
				Color color = colorscale.getColor(colorscale.getIndexFromValue(rowVals[col - colMin]));
				float colorsf[] = new float[4];
				color.getRGBComponents(colorsf);
				gl.glColor4fv(colorsf, 0);
				if (z != StsParameters.nullValue)
					gl.glVertex3f(x, y, z);
			}
			gl.glEnd();
		}
	}

	public int size()
	{
		return (rowMax - rowMin + 1) * (colMax - colMin + 1);
	}

	public String toString()
	{
		return "id: " + id + " ";
		// int childGridID = (childGrid == null ? -1 : childGrid.id);
		// int parentGridID = (parentGrid == null ? -1 : parentGrid.id);
		// return "id: " + id + " childGrid ID: " + childGridID + " parentGrid ID: " + parentGridID + " originalID: " + originalID + " nPatchPoints " + nPatchPoints;
	}

	public String toGridString()
	{
		String rowColString = super.toString();
		int childGridID = (childGrid == null ? -1 : childGrid.id);
		int parentGridID = (parentGrid == null ? -1 : parentGrid.id);
		if (patchPoints != null)
			return "id: " + id + " childGrid ID: " + childGridID + " parentGrid ID: " + parentGridID + " originalID: " + originalID + " nPatchPoints " + nPatchPoints + " " + rowColString + " zMin: " + zMin + " zMax: " + zMax;
		else
			return "id: " + id + " childGrid ID: " + childGridID + " parentGrid ID: " + parentGridID + " originalID: " + originalID + " nPatchPoints " + nPatchPoints + " " + rowColString;
	}

	public String toFamilyString()
	{
		int childGridID = (childGrid == null ? -1 : childGrid.id);
		int parentGridID = (parentGrid == null ? -1 : parentGrid.id);
		return getFamilyTypeString() + " id: " + id + " child ID: " + childGridID + " parent ID: " + parentGridID + " original ID: " + originalID;
	}

	public StsPatchGrid getParentGrid()
	{
		if (parentGrid != null) return parentGrid;
		else return this;
	}

	public int fillHistogram(float[] data, int nValues)
	{
		for (int row = 0; row < nRows; row++)
		{
			for (int col = 0; col < nCols; col++)
			{
				float value = values[row][col];
				if (value != nullValue)
					data[nValues++] = value;
				if (nValues == data.length)
					return nValues;
			}
		}
		return nValues;
	}

	public float getXMin()
	{
		return patchVolume.xMin;
	}

	public float getXMax()
	{
		return patchVolume.xMax;
	}

	public float getYMin()
	{
		return patchVolume.yMin;
	}

	public float getYMax()
	{
		return patchVolume.yMax;
	}

	public float getXInc()
	{
		return patchVolume.xInc;
	}

	public float getYInc()
	{
		return patchVolume.yInc;
	}

	public float getRowCoor(float[] xyz)
	{
		return patchVolume.getRowCoor(xyz);
	}

	public float getColCoor(float[] xyz)
	{
		return patchVolume.getRowCoor(xyz);
	}

	public double getXOrigin()
	{
		return patchVolume.xOrigin;
	}

	public double getYOrigin()
	{
		return patchVolume.yOrigin;
	}

	public float getXSize()
	{
		return patchVolume.getXSize();
	}

	public float getYSize()
	{
		return patchVolume.getYSize();
	}

	public float getAngle()
	{
		return patchVolume.getAngle();
	}

	public float getXCoor(float rowF, float colF)
	{
		return patchVolume.getXCoor(rowF, colF);
	}

	public float getYCoor(float rowF, float colF)
	{
		return patchVolume.getYCoor(rowF, colF);
	}

	public StsPoint getPoint(int volumeRow, int volumeCol)
	{
		float[] xyz = getXYZorT(volumeRow, volumeCol);
		return new StsPoint(xyz);
	}

	public float[] getXYZorT(int volumeRow, int volumeCol)
	{
		float[] xy = patchVolume.getXYCoors(volumeRow, volumeCol);

		float z = pointsZ[volumeRow - rowMin][volumeCol - colMin];
		return new float[]{xy[0], xy[1], z};
	}

	public float getZMin()
	{
		return zMin;
	}

	public float getZMax()
	{
		return zMax;
	}

	public String getLabel()
	{
		return toString();
	}

	public float interpolateBilinearZ(StsPoint point, boolean computeIfNull, boolean setPoint)
	{
		return 0.0f;
	} // not used:  yet

	public float interpolateBilinearZ(StsGridPoint gridPoint, boolean computeIfNull, boolean setPoint)
	{
		return 0.0f;
	} // not used: yet

	public float getComputePointZ(int row, int col)
	{
		return pointsZ[row][col];
	}

	public boolean toggleSurfacePickingOn()
	{
		return false;
	}

	public void toggleSurfacePickingOff()
	{
	}

	public String getName()
	{
		return "patchGrid-" + originalID;
	}

	public StsGridPoint getSurfacePosition(StsMouse mouse, boolean display, StsGLPanel3d glPanel3d)
	{
		return null;
	}

	public void setIsVisible(boolean isVisible)
	{
		this.isVisible = isVisible;
	}

	public boolean getIsVisible()
	{
		return isVisible;
	}

	public PatchPoint getVolPatchPoint(PatchPoint patchPoint)
	{
		if(patchPoint == null) return null;
		return getVolPatchPoint(patchPoint.getRow(), patchPoint.getCol());
	}

	public boolean isInsidePatchRowCol(int row, int col)
	{
		return row >= 0 && row < nRows && col >= 0 && col < nCols;
	}

	public boolean isInsidePatchRowCol(int[] rowCol)
	{
		if(rowCol == null) return false;
		return rowCol[0] >= 0 && rowCol[0] < nRows && rowCol[1] >= 0 && rowCol[1] < nCols;
	}

	/**
	 * Given global row,col get patchPoint on grid at this point
	 * @param volRow global row
	 * @param volCol global col
	 * @return
	 */
	public PatchPoint getVolPatchPoint(int volRow, int volCol)
	{
		if (patchPoints != null && isInsideRowCol(volRow, volCol))
			return patchPoints[volRow - rowMin][volCol - colMin];
		return null;
	}

	/**
	 * Given local row,col get patchPoint on grid at this point
	 * @param patchRow local row
	 * @param patchCol local col
	 * @return
	 */
	public PatchPoint getGridPatchPoint(int patchRow, int patchCol)
	{
		if (patchPoints == null) return null;
		if (!isInsideGridRowCol(patchRow, patchCol)) return null;
		return patchPoints[patchRow][patchCol];
	}

	public float getVolumePointZ(int volumeRow, int volumeCol)
	{
		return getPatchPointZ(volumeRow - rowMin, volumeCol - colMin);
	}

	public float getPatchPointZ(int patchRow, int patchCol)
	{
		if (pointsZ == null) return nullValue;
		if (!isInsidePatchRowCol(patchRow, patchCol)) return nullValue;
		return pointsZ[patchRow][patchCol];
	}

	public float getCurvature(int volumeRow, int volumeCol, float dataMin, float dataMax)
	{
		if (!isInsideRowCol(volumeRow, volumeCol))
		{
			StsException.systemError(this, "getValue", "volumeRow or volumeCol not inside patch");
			return 0.0f;
		}
		if (values == null) return 0.0f;
		float value = values[volumeRow - rowMin][volumeCol - colMin];
		if (value == nullValue) return 0.0f;
		if (value < dataMin) return dataMin;
		else if (value > dataMax) return dataMax;
		else return value;
	}

	public float[][] getValues()
	{
		return values;
	}

	public void setValues(float[][] values)
	{
		this.values = values;
	}


	/** not used; currently required for interface compatibility */
	public void checkConstructGridNormals()
	{
	}

	public float[][][] computeSmoothGridNormals()
	{
		float[][][] normals = new float[nRows][nCols][];
		for(int row = 0; row < nRows; row++)
		{
			for (int col = 0; col < nCols; col++)
			{
				if(pointsZ[row][col]  != nullValue)
					normals[row][col] = computeSmoothGridNormal(row, col);
			}
		}
		return normals;
	}

	/** Smooth grid normal computes the normal at a point given the connected grid points (NSEW or UP, DOWN, RIGHT, LEFT).
	 *
	 * @param patchRow local row index
	 * @param patchCol local col index
	 * @return normal at this point
	 */
	public float[] computeSmoothGridNormal(int patchRow, int patchCol)
	{
		float z = getPatchPointZ(patchRow, patchCol);
		if(z == nullValue)
		{
			StsException.systemError(this, "computeSmoothGridNormal", "z is null at point for normal!");
			return null;
		}

		float[] idif = new float[3];
		float[] jdif = new float[3];

		idif[1] = patchVolume.yInc;
		jdif[0] = patchVolume.xInc;
		Connection[] connections = getConnections(patchRow, patchCol);
		if(connections == null)
		{
			StsException.systemError(this, "computeSmoothGridNormal", "no gridConnections at point for normal");
			return null;
		}
		idif[2] = getZDif(z, connections[LINK_DOWN], connections[LINK_UP]);
		jdif[2] = getZDif(z, connections[LINK_LEFT], connections[LINK_RIGHT]);
		return StsMath.crossProduct(idif, jdif);
	}

	final private float getZDif(float z, Connection minusConnection, Connection plusConnection)
	{
		float zMinus = Connection.staticGetPrevZ(minusConnection);
		float zPlus = Connection.staticGetNextZ(plusConnection);
		return getZDif(zMinus, z, zPlus);
	}

	final private float getZDif(float zMinus, float z, float zPlus)
	{
		if (zPlus != nullValue)
		{
			if (zMinus != nullValue)
				return zPlus - zMinus;
			else
				return 2*(zPlus - z);
		}
		else // zPlus == nullValue
		{
			if (zMinus != nullValue)
				return 2*(z - zMinus);
			else
				return 0.0f;
		}
	}

	/** return the grid local row & col on otherPatchGrid given the parchRowCol on StsPatchGrid.this grid.
	 *
	 * @param otherPatchGrid grid where we want to define the grid local row and col
	 * @param patchRowCol patchRow and patchCol of the lower-left on this grid (StsPatchGrid.this) which may be different
	 *                    than otherPatchGrid
	 * @return
	 */
	int[] getRelativeGridPatchRowCol(StsPatchGrid otherPatchGrid, int[] patchRowCol)
	{
		if(this != otherPatchGrid)
		{
			int thisVolRow = patchRowCol[0] + rowMin;
			int thisVolCol = patchRowCol[1] + colMin;
			patchRowCol[0] = thisVolRow - otherPatchGrid.rowMin;
			patchRowCol[1] = thisVolCol - otherPatchGrid.colMin;
		}
		if (!otherPatchGrid.isInsidePatchRowCol(patchRowCol))  return null;
		return patchRowCol;
	}

	/** For each grid cell, compute the four triangles with shared vertex at center.
	 *  A triangle exists if both corner points at the link between corner points exists.
	 *  If a corner point doesn't exist, one of the existing adjacent corner points may have
	 *  a link to a point on another grid; if so, use that one.
	 *  After triangles are created, put draw calls into a displayList and delete the gridCells.
	 */
	class GridCells
	{
		/** normals at grid points (null vector if point doesn't exist) */
		float[][][] normals;
		// grid xMin
		// float xMin = getXMin();
		// grid yMin
		// float yMin = getYMin();
		/** min row of this patch on patch volume */
		int rowMin = getRowMin();
		/** min col of this patch on patch volume */
		int colMin = getColMin();
		/** X offsets from lower-left corner of cell to each of the four corner points */
		float[] celldXs = { 0.0f, patchVolume.xInc, patchVolume.xInc, 0.0f };
		/** Y offsets from lower-left corner of cell to each of the four corner points */
		float[] celldYs = { 0.0f, 0.0f, patchVolume.yInc, patchVolume.yInc };
		/** array of grid cells spanning patch with extra border row and col (size is [nRows+1][nCols+1] */
		GridCell[][] gridCells;
		/** number of cell rows to be constructed and drawn */
		int nCellRows;
		/** number of cell cols to be constructed and drawn */
		int nCellCols;


		GridCells()
		{
			normals = computeSmoothGridNormals();
			computeGridCells();
		}

		private void computeGridCells()
		{
			nCellRows = nRows+1;
			nCellCols = nCols+1;
			int rowStart = -1;
			int colStart = -1;
			if(rowMin <= 0)
			{
			 	rowStart = 0;
				nCellRows--;
			}
			if(rowMax >= patchVolume.nRows-1)
				nCellRows--;
			if(colMin <= 0)
			{
				colStart = 0;
				nCellCols--;
			}
			if(colMax >= patchVolume.nCols-1)
				nCellCols--;
			gridCells = new GridCell[nCellRows][nCellCols];

			for (int cr = 0, r = rowStart; cr < nCellRows; cr++, r++ )
				for (int cc = 0, c = colStart; cc < nCellCols; cc++, c++ )
					gridCells[cr][cc] = gridCellConstructor(r, c);
		}


		public void  drawSurfaceFillValuesWithNulls(GL gl, boolean displayCurvature, StsColorscale colorscale)
		{

		}

		public void drawSurfaceFillWithNulls(GL gl)
		{
			try
			{
				drawRowCellsWithNulls(gl);
			}
			catch(Exception e)
			{
				StsException.outputWarningException(this, "drawSurfaceFillWithNulls", e);
			}
		}

		private void drawRowCellsWithNulls(GL gl)
		{
			StsTraceUtilities.getPointTypeColor(patchType).setGLColor(gl);

			for (int row = 0; row < nCellRows; row++)
				for (int col = 0; col < nCellCols; col++)
					if(gridCells[row][col] != null)
						gridCells[row][col].drawCell(gl);
		}

		public void drawSurfaceFillWithNulls2d(GL gl)
		{
			try
			{
				drawRowCellsWithNulls2d(gl);
			}
			catch(Exception e)
			{
				StsException.outputWarningException(this, "drawSurfaceFillWithNulls", e);
			}
		}

		private void drawRowCellsWithNulls2d(GL gl)
		{
			gl.glDisable(GL.GL_LIGHTING);
			StsTraceUtilities.getPointTypeColor(patchType).setGLColor(gl);
			for (int row = 0; row < nCellRows; row++)
				for (int col = 0; col < nCellCols; col++)
					if(gridCells[row][col] != null)
						gridCells[row][col].drawCell2d(gl);
			gl.glEnable(GL.GL_LIGHTING);
		}

		GridCell gridCellConstructor(int row, int col)
		{
			GridCell gridCell = new GridCell(row, col);
			if(gridCell.constructCell())
				return gridCell;
			else
				return null;
		}

		class GridCell
		{
			/** cell lower-left corner row */
			int row;
			/** cell lower-left corner col */
			int col;
			/** cell lower-left corner x coordinate */
			float colX;
			/** cell lower-left corner y coordinate */
			float rowY;
			/** cell center X */
			float centerX;
			/** cell center Y */
			float centerY;
			/** four triangles which form grid cell; some may be null; some may be disconnected from neighbor. */
			LinkTriangle[] linkTriangles = new LinkTriangle[4];
			/** indicates all non-null cell cornerLinkPoint(s) are on the same grud */
			boolean isCornerPointsSameGrid = true;

			/**
			 * Construct the grid cell to be drawn consisting of four triangles with a shared center.
			 * Given the four corner points on this grid, add the other corner-points which
			 * might be on a connected grid.  Compute the z and normal for each non-null corner-point.
			 * Compute the links between adjacent non-null corner-points in CCW order from lower-left:
			 * bottom, right, top, left.  Points in link are from the first to the next CCW.
			 * Compute the center z and normal.
			 * @param row local patch row at lower-left
			 * @param col local patch col at lower-left
			 */
			GridCell(int row, int col)
			{
				this.row = row;
				this.col = col;
				colX = patchVolume.xMin + (colMin + col) * patchVolume.xInc;
				rowY = patchVolume.yMin + (rowMin + row) * patchVolume.yInc;
				centerX = colX + patchVolume.xInc / 2;
				centerY = rowY + patchVolume.yInc / 2;
			}

			/**
			 * Add corner-points which exist at corners of this cell.
			 * Points in the cell are indexed from lower-left in a CCW fashion:
			 * lower-left is 0, lower-rite is 1, upper-right is 2, and upper-left is 3.
			 * Add the forward links: 0->1, 1->2, 0->3, 3->2.
			 * So first find if lower-left exists on this grid; if so, get lower-right and upper-left
			 * from connections (if they exist).  If lower-right is not found, then get it from the grid.
			 * If we found the lower-rite on the grid, then get the upper-rite from its connection.
			 * If the upper-left was found, get the upper-rite from its connection.  If upper-left wasn't
			 * found, then try to get it from the grid.  If it is was found, then get the upper-rite from
			 * its connection.  If the upper-right was found going both CW and CCW around, use it if both
			 * directions yield the same point.  If not the same point, use the one from this grid.  If neither
			 * is from this grid or no upper-rite was found, try to get the point from this grid.
			 * Finally, set the links between the four corner points in a CCW direction.  Link 0 is corners 0 to 1,
			 * link 1 is corners 1 to 2, link 2 is corners 2 to 3, and link 3 is corners 3 to 0.
			 */
			private boolean constructCell()
			{
				// get cell corner points in
				// for each side of cell, see if the first corner-point is linked to the next
				// if so, add both points to the cellPoints[4] array and the link to the cellLinks[4] array

				// Points at cell corners with links to previous and next corner-points in CCW order.
				LinkPoint[] cornerLinkPoints = new LinkPoint[4];
				// local variables
				LinkPoint cornerPoint, prevCornerPoint, nextCornerPoint;

				boolean isOk = false;
				for(int i = 0; i < 4; i++)
				{
					cornerLinkPoints[i] = createCornerLinkPoint(row, col, i);
					if (cornerLinkPoints[i] != null) isOk = true;
				}
				if(!isOk) return false;
				// set nextLinkPoint for each corner, reusing cornerLinkPoints if they are the same
				cornerPoint = cornerLinkPoints[3];
				nextCornerPoint = cornerLinkPoints[0];
				for (int i = 0; i < 4; i++)
				{
					prevCornerPoint = cornerPoint;
					cornerPoint = nextCornerPoint;
					nextCornerPoint = cornerLinkPoints[nextCcwIndex[i]];
					if (cornerPoint == null) continue;
					cornerPoint.setNextLinkPoint(nextCornerPoint);
					cornerPoint.setPrevLinkPoint(prevCornerPoint);
				}
				// eliminate any cornerPoints which don't have a next link
				isOk = false;
				for (int i = 0; i < 4; i++)
				{
					cornerPoint = cornerLinkPoints[i];
					if (cornerPoint == null) continue;
					if(cornerPoint.nextLinkPoint == null)
						cornerLinkPoints[i] = null;
					else
						isOk = true;
				}
				if(!isOk) return false;

				// create up to four triangles for cell with side and connections to center for each
				// also set the isCornerPointsSameGrid flag if all non-null cornerPoints are on same grid
				// if the cornerPoint.nextLinkPoint is off grid, add its nextLinkPoint to it so we have all connected points for center interpolation
				// the cornerPoint.prevLinkPoint has already be set; so we will have a max of four connected points for the center interpolation
				int nTriangles = 0;
				for (int i = 0; i < 4; i++) // i is the index of the first point of the triangle and the side of the triangle
				{
					cornerPoint = cornerLinkPoints[i];
					if(cornerPoint == null) continue;
					LinkPoint nextLinkPoint = cornerPoint.nextLinkPoint;
					if(nextLinkPoint == null) continue;
					if(nextLinkPoint.grid != StsPatchGrid.this)
						isCornerPointsSameGrid = false;
						// nextLinkPoint.setNextLinkPoint(null);
					linkTriangles[nTriangles++] = new LinkTriangle(cornerPoint);
				}
				if(nTriangles == 0) return false;

				LinkTriangle[] newLinkTriangles = new LinkTriangle[nTriangles];
				System.arraycopy(linkTriangles, 0, newLinkTriangles, 0, nTriangles);
				linkTriangles = newLinkTriangles;

				// check set linkTriangle.nextTriangleConnected flag indicating
				// whether triangle is connected to next triangle, i.e., they share the same cornerPoint
				LinkTriangle nextLinkTriangle = linkTriangles[0];
				for (int n = 0; n < nTriangles; n++)
				{
					LinkTriangle linkTriangle = linkTriangles[n];
					nextLinkTriangle = getNextTriangle(linkTriangles, nTriangles, n);
					if(linkTriangle == null || nextLinkTriangle == null) continue;
					linkTriangle.checkSetTriangleIsConnected(nextLinkTriangle);
					// linkTriangle.setPointNormals();
				}
				// Find first triangle which is not connected to prev triangle;
				// If it exists, then reorder triangles from this one and eliminate nulls.
				// With this new order, draw routine can use a triangle fan draw for each
				// series of connected triangles.
				// If no such first triangle is found, simply eliminate null triangles
				int firstTriangle = 0;
				for (int n = 0; n < nTriangles; n++)
				{
					LinkTriangle linkTriangle = linkTriangles[n];
					if(!linkTriangle.nextTriangleConnected)
					{
						firstTriangle = (n+1)%nTriangles;
						break;
					}
				}
				if(firstTriangle != 0)
				{
					LinkTriangle[] reorderedTriangles = new LinkTriangle[nTriangles];
					for(int n = 0; n < nTriangles; n++)
					{
						int nn = (n+firstTriangle)%nTriangles;
						reorderedTriangles[n] = linkTriangles[nn];
					}
					linkTriangles = reorderedTriangles;
				}

				// for each series of connectedTriangles, compute a common center z and normal
				ArrayList<LinkTriangle> connectedTriangles = new ArrayList<>();
				for(int n = 0; n < nTriangles; n++)
				{
					LinkTriangle linkTriangle = linkTriangles[n];
					connectedTriangles.add(linkTriangle);
					while(linkTriangle.nextTriangleConnected && n < nTriangles-1)
					{
						n++;
						linkTriangle = linkTriangles[n];
						connectedTriangles.add(linkTriangle);
					}
					setConnectedTrianglesCenters(connectedTriangles);
					connectedTriangles.clear();
				}
				return true;
			}

			/** given a sequence of triangles ccw from side 0 (which may not adjoin),
			 *  return the next adjoining triangle by comparing side numbers which equal the index of the first point.
			 * @param linkTriangles triangle array
			 * @param nTriangles length of triangle array
			 * @param n index of current triangle
			 * @return next triangle if adjoining or null if none adjoining
			 */
			LinkTriangle getNextTriangle(LinkTriangle[] linkTriangles, int nTriangles, int n)
			{
				LinkTriangle triangle = linkTriangles[n];
				int sideIndex = triangle.point.pointCcwIndex;
				LinkTriangle nextTriangle = linkTriangles[(n+1)%nTriangles];
				int nextSideIndex = nextTriangle.point.pointCcwIndex;
				if(nextSideIndex == (sideIndex+1)%4)
					return nextTriangle;
				else
					return null;
			}
			/** Given a set of connected triangles, iterate through the sequence of points from the point just before
			 *  the first triangle to the point after the last triangle.  Terminate this iteration if and when the
			 *  first point in the sequence is encountered. This sequence of points is used to define the values of
			 *  the common gric-cell centerPoint which is the same for all connected triangles.
			 * @param connectedTriangles ArrayList of connected triangles;
			 */
			void setConnectedTrianglesCenters(ArrayList<LinkTriangle> connectedTriangles)
			{
				CenterPoint centerPoint = new CenterPoint();
				// Set the firstPoint; on subsequent iteration through points we will stop there if firstPoint is reached
				// if this point has a prev point, make prevPoint the firstPoint; otherwise make point the firstPoint
				LinkPoint point = connectedTriangles.get(0).point;
				if(point.prevLinkPoint != null)
					point = point.prevLinkPoint;
				LinkPoint firstPoint = point;
				// now iterate through connected points from point to nextPoint;  terminate early if point is firstPoint set above
				while(point != null)
				{
					centerPoint.add(point);
					point = point.nextLinkPoint;
					if(point == firstPoint) break;
				}
				centerPoint.averagePoints();

				for (LinkTriangle linkTriangle : connectedTriangles)
				{
					linkTriangle.centerZ = centerPoint.z;
					linkTriangle.centerNormal = centerPoint.normal;
				}
			}

			/** CenterPoint is used to sum centerPoint z and normal values and then average them. */
			class CenterPoint
			{
				int nPoints = 0;
				ArrayList<float[]> normalList = new ArrayList<>();
				float z = 0.0f;
				float[] normal;

				CenterPoint() {}

				void add(LinkPoint point)
				{
					z += point.z;
					point.computeRelativeCellPatchPointNormal();
					normalList.add(point.normal);
					nPoints++;
				}

				void averagePoints()
				{
					z /= nPoints;
					normal = averageCellPointsNormal(normalList);
				}
			}

			private float[] averageCellPointsNormal(ArrayList<float[]> gridNormals)
			{
				try
				{
					float[] normal = StsMath.addVectorsNormalize(gridNormals, 1);
					return normal;
				}
				catch(Exception e)
				{
					return verticalNormal;
				}
			}

			LinkPoint createCornerLinkPoint(int row, int col, int ccwIndex)
			{
				row = row + ccwDRow[ccwIndex];
				col = col + ccwDCol[ccwIndex];
				if(!isInsideGridRowCol(row, col)) return null;
				float z = pointsZ[row][col];
				if(z == nullValue) return null;
				return new LinkPoint(StsPatchGrid.this, ccwIndex, row, col, z);
			}

			/** draw all the existing 4 triangles of the grid cell.
			 *  basic draw routine is a triangle fan; if triangles are missing, fan will have to
			 *  be ended if already started and then restarted for the next triangle.
			 * @param gl   gl state/context
			 */
			void drawCell(GL gl)
			{
				boolean isDrawing = false;
				int i = 0;
				try
				{
					for(LinkTriangle triangle : linkTriangles)
					{
						isDrawing = triangle.drawTriangle(gl, isDrawing);
						// no triangle in this quadrant; if drawing: end

					}
				}
				catch(Exception e)
				{
					StsException.outputWarningException(this, "drawCell", "Failed for linkTriangle " + i, e);
					if(isDrawing)
					{
						gl.glEnd();
						isDrawing = false;
					}
				}
				finally
				{
					if(isDrawing) gl.glEnd();
				}
			}

			void drawCell2d(GL gl)
			{
				boolean isDrawing = false;
				int i = 0;
				try
				{
					for(LinkTriangle triangle : linkTriangles)
					{
						// TODO: drawTriangle2d is failing after drawing a line when it draws the next triangle-fan: FIX!
						isDrawing = triangle.drawTriangle2dDebug(gl, isDrawing);
						i++;
					}
				}
				catch(Exception e)
				{
					StsException.outputWarningException(this, "drawCell", "Failed for linkTriangle index " + i, e);
					if(isDrawing)
					{
						gl.glEnd();
						isDrawing = false;
					}
				}
				finally
				{
					if(isDrawing) gl.glEnd();
				}
			}

			public String toString()
			{
				return " cell row " + row + " cell col " + col;
			}

			/** LinkPoint contains the definition of a point as relative row and col on a grid
			 *  and its pointCcwIndex on a cell which is the containing class */
			class LinkPoint
			{
				/** grid this point is on */
				StsPatchGrid grid;
				int pointPatchRow;
				int pointPatchCol;
				int pointCcwIndex;
				LinkPoint prevLinkPoint;
				LinkPoint nextLinkPoint;
				float z;
				float[] normal;

				LinkPoint(StsPatchGrid grid, int[] patchRowCol, int ccwIndex)
				{
					this.grid = grid;
					this.pointPatchRow = patchRowCol[0];
					this.pointPatchCol = patchRowCol[1];
					this.pointCcwIndex = ccwIndex;
					z = grid.getPatchPointZ(pointPatchRow, pointPatchCol);
				}

				LinkPoint(StsPatchGrid grid, int ccwIndex, int patchRow, int patchCol, float z)
				{
					this.grid = grid;
					this.pointPatchRow = patchRow;
					this.pointPatchCol = patchCol;
					this.pointCcwIndex = ccwIndex;
					this.z = z;
				}

				/** From this linkPoint (this), set the nextLinkPoint if it exists */
				void setNextLinkPoint(LinkPoint nextCornerPoint)
				{
					if(nextLinkPoint != null) return;
					Connection nextGridConnection = grid.getConnection(pointPatchRow, pointPatchCol, pointCcwIndex);
					if(nextGridConnection == null) return;

					// get the grid this new LinkPoint is on which may be different than the grid this LinkPoint is on
					StsPatchGrid nextGrid = nextGridConnection.getNextPatchGrid(pointCcwIndex);
					// if the same grid, then set to nextCornerPoint
					if(nextCornerPoint != null && nextGrid == this.grid)
					{
						nextLinkPoint = nextCornerPoint;
						return;
					}
					// nextLinkPoint is on a different grid
					// pointPatchRow & pointPatchCol is the location of this point which must be the nextPoint of the link
					// the point we want is offset from that by prevDRow & prevDCol
					int nextPointPatchRow = pointPatchRow + nextDRow[pointCcwIndex];
					int nextPointPatchCol = pointPatchCol + nextDCol[pointCcwIndex];
					int[] nextPatchRowCol = new int[] {nextPointPatchRow, nextPointPatchCol };
					int nextIndex = nextCcwIndex[pointCcwIndex];
					nextPatchRowCol = grid.getRelativeGridPatchRowCol(nextGrid, nextPatchRowCol);
					if(nextPatchRowCol == null) return;
					nextLinkPoint = new LinkPoint(nextGrid, nextPatchRowCol, nextIndex);
					nextLinkPoint.prevLinkPoint = this;
				}
				/** Given a link point (this) which is the first point of the given link,
				 *  return the point on the previous link just before this point ( the nextPoint of the prev link)
				 * @return
				 */
				void setPrevLinkPoint(LinkPoint prevCornerPoint)
				{
					if(prevLinkPoint != null) return;

					Connection prevGridConnection = grid.getConnection(pointPatchRow, pointPatchCol, nextCcwIndex[pointCcwIndex]);
					if(prevGridConnection == null) return;

					// get the grid this new LinkPoint is on which may be different than the grid this LinkPoint is on
					StsPatchGrid prevGrid = prevGridConnection.getNextPatchGrid(nextCcwIndex[pointCcwIndex]);;
					// if the same grid, then set to nextCornerPoint
					if(prevCornerPoint != null && prevGrid == this.grid)
					{
						prevLinkPoint = prevCornerPoint;
						return;
					}
					// prevLinkPoint is on a different grid
					// pointPatchRow & pointPatchCol is the location of this point which must be the first point of the link
					// the point we want is offset from that by prevDRow & prevDCol
					int prevPointPatchRow = pointPatchRow + prevDRow[pointCcwIndex];
					int prevPointPatchCol = pointPatchCol + prevDCol[pointCcwIndex];
					int[] prevPatchRowCol = new int[] {prevPointPatchRow, prevPointPatchCol };
					int prevIndex = prevCcwIndex[pointCcwIndex];
					prevPatchRowCol = grid.getRelativeGridPatchRowCol(prevGrid, prevPatchRowCol);
					if(prevPatchRowCol == null) return ;
					prevLinkPoint = new LinkPoint(prevGrid, prevPatchRowCol, prevIndex);
					prevLinkPoint.nextLinkPoint = this;
				}

				/** we want a normal at a point on grid
				 * @return normal at the point defined on grid
				 */
				void computeRelativeCellPatchPointNormal()
				{
					if(normal != null) return;
					normal =  grid.computeSmoothGridNormal(pointPatchRow, pointPatchCol);
					if(normal == null) normal = verticalNormal;
					StsMath.normalize(normal);
				}

				/** determines whether this point has orthogonal side links in either direction
				 * @return true if this link has any orthogonal side links in either direction
				 */
				boolean hasSideConnections(int sideCcwIndex)
				{
					Connection[] connections = grid.getConnections(pointPatchRow, pointPatchCol);
					return connections[prevCcwIndex[sideCcwIndex]] != null || connections[nextCcwIndex[sideCcwIndex]] != null;
				}

				public boolean equals(LinkPoint otherPoint)
				{
					if(otherPoint == null) return false;
					if(grid != otherPoint.grid) return false;
					if(pointPatchRow != otherPoint.pointPatchRow) return false;
					if(pointPatchCol != otherPoint.pointPatchCol) return false;
					return true;
				}

				void drawPoint(GL gl)
				{
					if(debug)
					{
						if(z == nullValue)
						{
							StsException.systemDebug(this, "drawPoint",
									"z is null for patch " +  id + " row " + row + " col " + col + " link index " + pointCcwIndex);
							return;
						}
					}
					gl.glVertex3f(colX + celldXs[pointCcwIndex], rowY + celldYs[pointCcwIndex], z);
				}

				void drawPoint2d(GL gl)
				{
					if(debug)
					{
						if(z == nullValue)
						{
							StsException.systemDebug(this, "drawPoint",
									"z is null for patch " +  id + " row " + row + " col " + col + " link index " + pointCcwIndex);
							return;
						}
					}
					gl.glVertex2f(colX + celldXs[pointCcwIndex], rowY + celldYs[pointCcwIndex]);
				}

				void drawNormal(GL gl)
				{
					if(debug)
					{
						if(normal == null)
						{
							StsException.systemDebug(this, "drawNormal",
									"normal is null at patch " + id + " row " + row + " col " + col + " link index " + pointCcwIndex);
							return;
						}
					}
					gl.glNormal3fv(normal, 0);
				}

				private void drawPointAndNormal(GL gl)
				{
					drawNormal(gl);
					drawPoint(gl);
				}

				public String toString()
				{
					return " grid " + grid.id + " pointRow " + pointPatchRow + " pointCol " + pointPatchCol +
							" point ccwIndex " + pointCcwIndex + " volRow " + (pointPatchRow + grid.rowMin) +
							" volCol " + (pointPatchCol + grid.colMin);
				}
			}
			/** Cell to be drawn is composed of four triangles: one side (a ccw link between corner points) and cell center.
			 *  Links were originally all forward links, connected from a point on this grid to the next forward point.
			 *  Sides 2 and 3 were reversed so all are CCW, but connected points are the same.
			 *  Sides do not necessarily form a closed loop and can be split at a corner.  For this reason, triangle
			 *  center z and center normal are computed independently for each triangle.  The center z and normal should
			 *  be identical however if the loop is closed and there is no corner split.
			 */
			class LinkTriangle
			{
				/** first point of triangle on side. nextPoint will be point.nextLinkPoint */
				LinkPoint point;
				/** next point of triangle on side */
				LinkPoint nextPoint;
				/** z at center point: same as other centerZ values for connected group of triangles */
				float centerZ;
				/** normal of center point: same as other centerNormals for connected group of triangles */
				float[] centerNormal;
				/** indicates next triangle is not connected to this one */
				boolean nextTriangleConnected = false;
				/** if this is a single triangle, it has no connected triangles;
				 *  if it has no outside links (links away from the cell corners for point and nextPoint), draw as line  */
				boolean isLine = false;

				/** Construct the LinkTriangle which consists of a point, nextPoint, and centerPoint;
				 *  Point and nextPoint are two adjacent points on side of cell.
				 *  If the nextPoint is also a cornerPoint, then this triangle is connected to the next triangle
				 *  if nextTriangle exists.
				 *  This triangle may be a member of a connected group of triangles which share a
				 *  common center Z and normal.
				 *  If the two end points do not have side links, then we will just draw this triangle as a line (isLine==true)
				 * @param point first linkPoint on side of cell
				 */
				LinkTriangle(LinkPoint point)
				{
					this.point = point;
					nextPoint = point.nextLinkPoint;
					int sideCcwIndex = point.pointCcwIndex;
					isLine = !point.hasSideConnections(sideCcwIndex) && !nextPoint.hasSideConnections(sideCcwIndex);
				}

				void checkSetTriangleIsConnected(LinkTriangle nextTriangle)
				{
					nextTriangleConnected = nextPoint.equals(nextTriangle.point);
				}

				private boolean drawTriangle(GL gl, boolean isDrawing)
				{
					if(!isLine)
					{
						// Cell is drawn with possibly connected triangles using a triangle fan draw
						// If we drew the previous triangle and it wasn't split from this one, just drawn the next point and normal
						// If aren't currently drawing (no previous triangle or it was split from this one at common corner),
						// then draw the complete triangle.
						if (!isDrawing)
						{
							gl.glBegin(GL.GL_TRIANGLE_FAN);
							drawCenterPointAndNormal(gl);
							point.drawPointAndNormal(gl);
							isDrawing = true;
						}
						nextPoint.drawPointAndNormal(gl);
						// if isDrawing and the first point is split  (doesn't share common point with previous side),
						// then terminate drawing
						if(isDrawing && !nextTriangleConnected)
						{
							gl.glEnd();
							isDrawing = false;
						}
					}
					else // link has no side links so draw as a colored line
					{
						if(isDrawing) // may be drawing if previous side is a triangle and not split at first point
						{
							isDrawing = false;
							gl.glEnd();
						}
						if(point.pointCcwIndex < 2) // don't draw line twice (it will be drawn by both cells on side
						{
							gl.glDisable(GL.GL_LIGHTING);
							gl.glLineWidth(StsGraphicParameters.edgeLineWidthHighlighted);
							gl.glBegin(GL.GL_LINES);
							point.drawPoint(gl);
							nextPoint.drawPoint(gl);
							gl.glEnd();
							gl.glEnable(GL.GL_LIGHTING);
						}
					}
					return isDrawing;
				}

				private void drawCenterPointAndNormal(GL gl)
				{
					if(debug)
					{
						if (centerNormal == null)
						{
							StsException.systemDebug(this, "drawCenterPointAndNormal", "normal is null");
							return;
						}
						if (centerZ == nullValue)
						{
							StsException.systemDebug(this, "drawCenterPointAndNormal", "z is null");
							return;
						}
					}
					gl.glNormal3fv(centerNormal, 0);
					gl.glVertex3f(centerX, centerY, centerZ);
				}

				private boolean drawTriangle2dDebug(GL gl, boolean isDrawing)
				{
					if(!isLine)
					{
						// Cell is drawn with possibly connected triangles using a triangle fan draw
						// If we drew the previous triangle and it wasn't split from this one, just drawn the next point and normal
						// If aren't currently drawing (no previous triangle or it was split from this one at common corner),
						// then draw the complete triangle.

						gl.glBegin(GL.GL_TRIANGLES);
						drawCenterPoint2d(gl);
						point.drawPoint2d(gl);
						nextPoint.drawPoint2d(gl);
						gl.glEnd();
					}
					else // link has no side links so draw as a colored line
					{
						if(point.pointCcwIndex < 2) // don't draw line twice (it will be drawn by both cells on side
						{
							gl.glLineWidth(StsGraphicParameters.edgeLineWidthHighlighted);
							gl.glBegin(GL.GL_LINES);
							point.drawPoint2d(gl);
							nextPoint.drawPoint2d(gl);
							gl.glEnd();
						}
					}
					return false;
				}
				private boolean drawTriangle2d(GL gl, boolean isDrawing)
				{
					if(!isLine)
					{
						// Cell is drawn with possibly connected triangles using a triangle fan draw
						// If we drew the previous triangle and it wasn't split from this one, just drawn the next point and normal
						// If aren't currently drawing (no previous triangle or it was split from this one at common corner),
						// then draw the complete triangle.
						if (!isDrawing)
						{
							gl.glBegin(GL.GL_TRIANGLE_FAN);
							drawCenterPoint2d(gl);
							point.drawPoint2d(gl);
							isDrawing = true;
						}
						nextPoint.drawPoint2d(gl);
						// if isDrawing and the first point is split  (doesn't share common point with previous side),
						// then terminate drawing
						if(isDrawing && !nextTriangleConnected)
						{
							gl.glEnd();
							isDrawing = false;
						}
					}
					else // link has no side links so draw as a colored line
					{
						if(isDrawing) // may be drawing if previous side is a triangle and not split at first point
						{
							isDrawing = false;
							gl.glEnd();
						}
						if(point.pointCcwIndex < 2) // don't draw line twice (it will be drawn by both cells on side)
						{
							gl.glDisable(GL.GL_LIGHTING);
							gl.glLineWidth(StsGraphicParameters.edgeLineWidthHighlighted);
							gl.glBegin(GL.GL_LINES);
							point.drawPoint2d(gl);
							nextPoint.drawPoint2d(gl);
							gl.glEnd();
							gl.glEnable(GL.GL_LIGHTING);
						}
					}
					return isDrawing;
				}

				private void drawCenterPoint2d(GL gl)
				{
					if(debug)
					{
						if (centerZ == nullValue)
						{
							StsException.systemDebug(this, "drawCenterPointAndNormal", "z is null");
							return;
						}
					}
					gl.glVertex2f(centerX, centerY);
				}

				public String toString()
				{
					return "Triangle: " + point.toString() +  " to " + nextPoint.toString();
				}
			}
		}
	}
}


