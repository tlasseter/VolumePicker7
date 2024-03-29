package com.Sts.Actions.Wizards.CombinationVolume;

import com.Sts.Actions.Wizards.*;
import com.Sts.MVC.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author unascribed
 * @version 1.0
 */

public class StsDefineCombinationVolumePanel extends JPanel implements ActionListener
{
    private StsWizard wizard;
    private StsWizardStep wizardStep;

    private StsModel model = null;
    JPanel jPanel1 = new JPanel();
    JComboBox typesCombo = new JComboBox();
    JLabel jLabel1 = new JLabel();
    JLabel jLabel2 = new JLabel();
    JTextField cvName = new JTextField();
    JTextArea typeDescription = new JTextArea();
    GridBagLayout gridBagLayout2 = new GridBagLayout();
  JCheckBox inclusiveChk = new JCheckBox();
  GridBagLayout gridBagLayout1 = new GridBagLayout();

    public StsDefineCombinationVolumePanel(StsWizard wizard, StsWizardStep wizardStep)
    {
        this.wizard = wizard;
        this.wizardStep = wizardStep;
        try
        {
            initialize();
            jbInit();
            typesCombo.setSelectedIndex(0);
        }
        catch(Exception ex)
        {
            ex.printStackTrace();
        }
    }

    public void initialize()
    {
        model = wizard.getModel();
        for(int i=0; i<((StsCombinationVolumeWizard)wizard).mvTypes.length; i++)
        {
            typesCombo.addItem(((StsCombinationVolumeWizard)wizard).mvTypes[i]);
        }
    }

    void jbInit() throws Exception
    {
        jPanel1.setBorder(BorderFactory.createEtchedBorder());
        jPanel1.setLayout(gridBagLayout1);
        this.setLayout(gridBagLayout2);
        jLabel1.setHorizontalAlignment(SwingConstants.RIGHT);
        jLabel1.setText("Types:");
        jLabel2.setHorizontalAlignment(SwingConstants.RIGHT);
        jLabel2.setText("Name:");
        cvName.setText("CombinationVolumeName");
        typesCombo.addActionListener(this);
        typeDescription.setBackground(Color.lightGray);
        typeDescription.setFont(new java.awt.Font("SansSerif", 0, 10));
        typeDescription.setBorder(BorderFactory.createEtchedBorder());
        typeDescription.setLineWrap(true);
        typeDescription.setWrapStyleWord(true);

        inclusiveChk.setEnabled(true);
        inclusiveChk.setSelected(true);
        inclusiveChk.setText("Inclusive");
        inclusiveChk.addActionListener(this);
        jPanel1.add(jLabel2,  new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0
            ,GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(3, 7, 0, 0), 20, 7));
        jPanel1.add(jLabel1,  new GridBagConstraints(0, 1, 1, 1, 0.0, 0.0
            ,GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(9, 7, 0, 0), 15, 3));
        jPanel1.add(cvName,  new GridBagConstraints(1, 0, 1, 1, 1.0, 0.0
            ,GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(3, 0, 0, 6), 198, 4));
        jPanel1.add(typesCombo,  new GridBagConstraints(1, 1, 1, 1, 1.0, 0.0
            ,GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 6), 299, 4));
        jPanel1.add(typeDescription,  new GridBagConstraints(1, 2, 1, 1, 1.0, 1.0
            ,GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 6), 0, 29));
        jPanel1.add(inclusiveChk,  new GridBagConstraints(1, 3, 1, 1, 0.0, 0.0
            ,GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(0, 0, 158, 211), 53, -3));
        this.add(jPanel1,  new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0
            ,GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(6, 5, 5, 3), 3, 7));

    }

    public void actionPerformed(ActionEvent e)
    {
        Object source = e.getSource();
        if(source == typesCombo)
        {
            if(typesCombo.getSelectedIndex() == ((StsCombinationVolumeWizard)wizard).BOX_SET)
            {
                typeDescription.setText("Graphically select the microseismic events from each volume " +
                    "and combine all the data from around the selected events into " +
                    " a single cube.");
                inclusiveChk.setEnabled(true);
            }
            else if(typesCombo.getSelectedIndex() == ((StsCombinationVolumeWizard)wizard).SV_CUMMULATIVE)
            {
                typeDescription.setText("Select a set of pre-defined sub-volumes and combine them into " +
                                        " a single cube.");
                inclusiveChk.setEnabled(false);
            }
            else if(typesCombo.getSelectedIndex() == ((StsCombinationVolumeWizard)wizard).CUMMULATIVE)
            {
                typeDescription.setText("Select a set of volumes difference and create a virtual volume " +
                    "which is generated by summing the differences of all the selected " +
                    "volumes in order.");
                inclusiveChk.setEnabled(false);
            }
            else if(typesCombo.getSelectedIndex() == ((StsCombinationVolumeWizard)wizard).DIFFERENCE)
            {
                typeDescription.setText("Select two volumes and create a virtual volume of the difference of" +
                    " the two volumes.");
                inclusiveChk.setEnabled(false);
            }
            else if(typesCombo.getSelectedIndex() == ((StsCombinationVolumeWizard)wizard).MATH)
            {
                typeDescription.setText("Select a set of volumes and a math operator describing how to " +
                                        "merge them into a single volume" );
                inclusiveChk.setEnabled(false);
            }
            else
                ;
        }
    }

    public boolean isInclusive()
    {
        return inclusiveChk.isSelected();
    }

    public byte getType()
    {
        return (byte)typesCombo.getSelectedIndex();
    }

    public String getVolumeName()
    {
        return cvName.getText();
    }
}
