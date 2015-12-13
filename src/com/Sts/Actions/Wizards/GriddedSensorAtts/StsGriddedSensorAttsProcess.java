package com.Sts.Actions.Wizards.GriddedSensorAtts;

import com.Sts.Actions.Wizards.*;
import com.Sts.Actions.Wizards.WizardHeaders.*;
import com.Sts.DBTypes.StsSensorVirtualVolume;
import com.Sts.MVC.*;
import com.Sts.UI.Progress.*;
import com.Sts.Utilities.*;

public class StsGriddedSensorAttsProcess extends StsWizardStep implements Runnable
{
	public StsProgressPanel panel;
    private StsHeaderPanel header;
	private boolean canceled = false;
	
    public StsGriddedSensorAttsProcess(StsGriddedSensorAttsWizard wizard)
    {
		super(wizard);
		this.wizard = wizard;
		panel = StsProgressPanel.constructor(false,10,40);
		header = new StsHeaderPanel();
		setPanels(panel, header);
		header.setTitle("Virtual Volume Definition");
		header.setSubtitle("Construct Float Volume");
        header.setLink("http://www.s2ssystems.com/Protected/Marketing/AppLinks.html#VirtualVolume");
		header.setInfoText(wizardDialog, "(1) Once complete, press the Finish Button to dismiss the screen");		
    }
    
	public boolean start()
	{
		run();
		return true;
	}
	
    public void run()
    {
		if(canceled)
		{
			success = false;
			return;
		}
		StsModel model = wizard.getModel();
		StsProject project = model.getProject();
		try
		{
			disablePrevious();
			model.disableDisplay();
			panel.appendLine("Creating virtual volume:" + wizard.getName());
			((StsGriddedSensorAttsWizard)wizard).buildVolume(panel);
            statusArea.setText("Processing successfully completed for:" + wizard.getName());
            panel.appendLine("Virtual volume " + wizard.getName() + " successfully created.\n");
            panel.finished();
		}
		catch(Exception e)
		{
            StsException.outputWarningException(this, "run", e);
            statusArea.setText("Processing failed for:" + wizard.getName());
			success = false;
			return;
		}
		try
		{
            project.runCompleteLoading();
            StsSensorVirtualVolume sensorVolume = ((StsGriddedSensorAttsWizard)wizard).sensorVolume;
            model.win3d.cursor3dPanel.setGridCheckboxState(false);
            model.win3d.cursor3dPanel.getSliderZ().setValue(sensorVolume.getSensorZMin() + (sensorVolume.getSensorZMax()-sensorVolume.getSensorZMin())/2);            
			success = true;
			return;
		}
		catch(Exception e)
		{
			StsException.outputException("StsVolumeLoad.start() failed.", e, StsException.WARNING);
			panel.setDescriptionAndLevel("StsVolumeLoad.start() failed.", StsProgressBar.ERROR);
			success = false;
			return;
		}
        finally
        {
            wizard.completeLoading(success);
        }
    }
    
	public boolean end()
	{
		return true;
	}    
}

