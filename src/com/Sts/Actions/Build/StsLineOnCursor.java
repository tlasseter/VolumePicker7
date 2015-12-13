
//Title:        S2S: Seismic-to-simulation
//Version:
//Copyright:    Copyright (c) 2001
//Author:       Tom Lasseter
//Company:      4D Systems LLC
//Description:  Web-enabled Integrated Interpretation System

package com.Sts.Actions.Build;

import com.Sts.Actions.*;
import com.Sts.DBTypes.*;
import com.Sts.MVC.*;
import com.Sts.MVC.View3d.*;
import com.Sts.Types.*;
import com.Sts.Utilities.*;

public class StsLineOnCursor extends StsAction
{
    StsLine line;
    StsCursor3d cursor3d;

    /** construct a action handler for building a StsLine on a cursor. */
	public StsLineOnCursor(StsActionManager actionManager)
    {
        super(actionManager);
		addEndAllButton();
		addEndButton();
	}

    public boolean start()
    {
        try
        {
            line = StsLine.buildLine();
            line.setDrawVertices(true);
            model.addDisplayableInstance(line);
             model.win3d.cursorPickSetup();
            cursor3d = model.win3d.getCursor3d();
            statusArea.setTitle("Build " + line.getLabel() + ":");
            logMessage("Pick vertices on X or Y 3-D cursor plane, then press Done button.");
        }
        catch(Exception e)
        {
            if (line != null) line.setDrawVertices(false);
         	StsException.outputException(e, StsException.WARNING);
            return false;
        }
        return true;
    }

    /** mouse action for 3d window */
   	public boolean performMouseAction(StsMouse mouse, StsGLPanel glPanel)
	{
 		StsSurfaceVertex vertex;
        StsPoint pickPoint;

    	int leftButtonState = mouse.getButtonStateCheckClear(StsMouse.LEFT);

        int currentDirNo = cursor3d.getCurrentDirNo();

        if(currentDirNo == StsParameters.NO_MATCH)  // do we have a 3-d cursor?
        {
            logMessage("Use the 3-D Cursor Tool to define an active CURSOR plane");
        }
        StsGLPanel3d glPanel3d = (StsGLPanel3d)glPanel;
        // get the current cursor location as a point
        pickPoint = cursor3d.getPointInCursorPlane(glPanel3d, currentDirNo, mouse);
        if( pickPoint == null ) return true;

       	if(leftButtonState == StsMouse.PRESSED || leftButtonState == StsMouse.DRAGGED)
        {
            // display x-y-z location
			logMessage("X: " + pickPoint.v[0] + " Y: "
            						+ pickPoint.v[1] + " Z: " + pickPoint.v[2]);
        }
       	else if(StsMouse.isButtonStateReleasedOrClicked(leftButtonState))  // add a point
       	{

            vertex = line.addLineVertex(pickPoint, true, false);

            if (vertex != null)
            {
                logMessage("Added vertex: " + vertex.getIndex() + " to current "
                                   + line.getLabel() + ".");

                model.win3d.win3dDisplay();
            }
        }

        return true;
	}

    /** add a line when the mouse action finishes */
  	public boolean end()
    {
        statusArea.textOnly();
        line.setDrawVertices(false);
        logMessage("Added " + line.getName() + " with " + line.getLineVertices().getSize() + " vertices.");
        logMessage(line.getLabel() + " built successfully.");
        line.addToModel();
        model.win3d.win3dDisplay();
        return true;
    }
}


