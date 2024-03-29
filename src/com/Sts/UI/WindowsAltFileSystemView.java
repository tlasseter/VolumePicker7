package com.Sts.UI;

/**
 * <p>Title: S2S Development</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: S2S Systems LLC</p>
 * @author T.Lasseter
 * @version 1.1
 */

// This class is necessary due to an annoying bug on Windows NT where
// instantiating a JFileChooser with the default FileSystemView will
// cause a "drive A: not ready" error every time. I grabbed the
// Windows FileSystemView impl from the 1.3 SDK and modified it so
// as to not use java.io.File.listRoots() to get fileSystem roots.
// java.io.File.listRoots() does a SecurityManager.checkRead() which
// causes the OS to try to access drive A: even when there is no disk,
// causing an annoying "abort, retry, ignore" popup message every time
// we instantiate a JFileChooser!
//
// Instead of calling listRoots() we use a straightforward alternate
// method of getting file system roots.
// Create fileChooser with: new JFileChooser(currentDirectory, new WindowsAltFileSystemView())

import javax.swing.filechooser.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;


class WindowsAltFileSystemView extends FileSystemView
{
    private static final Object[] noArgs = {};
    private static final Class[] noArgTypes = {};

    private static Method listRootsMethod = null;
    private static boolean listRootsMethodChecked = false;

    /**
     * Returns true if the given file is a root.
     */
    public boolean isRoot(File f)
    {
        if(!f.isAbsolute())  return false;

        String parentPath = f.getParent();
        if(parentPath == null)
            return true;
        else
        {
            File parent = new File(parentPath);
            return parent.equals(f);
        }
    }

    /**
     * creates a new folder with a default folder name.
     */
    public File createNewFolder(File containingDir) throws IOException
    {
        if(containingDir == null) throw new IOException("Containing directory is null:");

        File newFolder = null;

        // Using NT's default folder name
        newFolder = createFileObject(containingDir, "New Folder");
        int i = 2;
        while (newFolder.exists() && (i < 100))
        {
            newFolder = createFileObject(containingDir, "New Folder (" + i + ")");
            i++;
        }

        if(newFolder.exists())
            throw new IOException("Directory already exists:" + newFolder.getAbsolutePath());
        else
            newFolder.mkdirs();

        return newFolder;
    }

    /**
     * Returns whether a file is hidden or not. On Windows
     * there is currently no way to get this information from
     * io.File, therefore always return false.
     */
    public boolean isHiddenFile(File f) { return false; }

    /**
     * Returns all root partitians on this system. On Windows, this
     * will be the A: through Z: drives.
     */
    public File[] getRoots()
    {
        Vector rootsVector = new Vector();

        // Create the A: drive whether it is mounted or not
        FileSystemRoot floppy = new FileSystemRoot("A" + ":"  + File.separator);
        rootsVector.addElement(floppy);

        // Run through all possible mount points and check
        // for their existance.
        for (char c = 'C'; c <= 'Z'; c++)
        {
            char device[] = {c, ':', '\\'};
            String deviceName = new String(device);
            File deviceFile = new FileSystemRoot(deviceName);
            if (deviceFile != null && deviceFile.exists())
                rootsVector.addElement(deviceFile);
        }
        File[] roots = new File[rootsVector.size()];
        rootsVector.copyInto(roots);
        return roots;
    }

    class FileSystemRoot extends File
    {
        public FileSystemRoot(File f)
        {
            super(f, "");
        }

        public FileSystemRoot(String s)
        {
            super(s);
        }

        public boolean isDirectory()
        {
            return true;
        }
    }
}
