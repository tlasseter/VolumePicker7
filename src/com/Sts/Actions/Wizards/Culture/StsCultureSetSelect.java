package com.Sts.Actions.Wizards.Culture;

import com.Sts.Actions.Wizards.*;
import com.Sts.Actions.Wizards.WizardHeaders.*;

import java.awt.*;


/**
 * <p>Title: </p>
 *
 * <p>Description: </p>
 *
 * <p>Copyright: Copyright (c) 2002</p>
 *
 * <p>Company: </p>
 *
 * @author not attributable
 * @version 1.0
 */
public class StsCultureSetSelect extends StsWizardStep
{
    StsCultureSetSelectPanel panel;
    StsHeaderPanel header;

    public StsCultureSetSelect(StsWizard wizard)
    {
        super(wizard);
        panel = new StsCultureSetSelectPanel(wizard, this);
        header = new StsHeaderPanel();
        setPanels(panel, header);
        panel.setPreferredSize(new Dimension(500, 400));
        header.setTitle("Culture Set Selection");
        header.setSubtitle("Selecting one or more culture objects for set");
        header.setLink("http://www.s2ssystems.com/Protected/Marketing/AppLinks.html#Culture");
        header.addInfoLine(wizardDialog, "(1) Navigate to the desired file directory using the Dir button.");
        header.addInfoLine(wizardDialog, "     ***** All available objects in the selected directory will be placed in the left list.");
        header.addInfoLine(wizardDialog, "     ***** The default location is the project directory but any directory can be selected");
        header.addInfoLine(wizardDialog, "(2) Select the culture objects (*.xml & *.dxf files) and move them to the right list.");
        header.addInfoLine(wizardDialog, "     ***** Supported XML & DXF Object Types: Line, Point and Text");
        header.addInfoLine(wizardDialog, "     *****      XML Format: Object Type,Format,Width,Color");
        header.addInfoLine(wizardDialog, "     *****              XY, X Position, Y Position, Text (if applicable)");
        header.addInfoLine(wizardDialog, "     *****     Example: TEXT,STD,1,RED");
        header.addInfoLine(wizardDialog, "     *****              XY, 523600.0, 5434000.0, Well#1");
        header.addInfoLine(wizardDialog, "(3) Select the desired objects from the left list and place them in the right list using the provided controls between the lists.");
        header.addInfoLine(wizardDialog, "(4) Once all object selections are complete, press the Next>> Button.");
    }

    public String[] getSelectedObjects() {  return panel.getSelectedObjectNames(); }
    public String getSelectedDirectory() {  return panel.getCurrentDirectory(); }

    public boolean start()
    {
        panel.initialize();
        return true;
    }

    public boolean end()
    {
        return true;
    }
}
