/***************************************************************************
 * Bytecode Viewer (BCV) - Java & Android Reverse Engineering Suite        *
 * Copyright (C) 2014 Konloch - Konloch.com / BytecodeViewer.com           *
 *                                                                         *
 * This program is free software: you can redistribute it and/or modify    *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation, either version 3 of the License, or     *
 *   (at your option) any later version.                                   *
 *                                                                         *
 *   This program is distributed in the hope that it will be useful,       *
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of        *
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
 *   GNU General Public License for more details.                          *
 *                                                                         *
 *   You should have received a copy of the GNU General Public License     *
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>. *
 ***************************************************************************/

package the.bytecode.club.bytecodeviewer.plugin;

import com.google.common.io.Files;
import me.konloch.kontainer.io.DiskReader;
import me.konloch.kontainer.io.DiskWriter;
import org.apache.commons.compress.utils.FileNameUtils;
import the.bytecode.club.bytecodeviewer.BytecodeViewer;
import the.bytecode.club.bytecodeviewer.Configuration;
import the.bytecode.club.bytecodeviewer.gui.components.FileChooser;
import the.bytecode.club.bytecodeviewer.gui.components.SearchableRSyntaxTextArea;
import the.bytecode.club.bytecodeviewer.gui.resourceviewer.viewer.ComponentViewer;
import the.bytecode.club.bytecodeviewer.resources.IconResources;
import the.bytecode.club.bytecodeviewer.translation.TranslatedComponents;
import the.bytecode.club.bytecodeviewer.translation.components.TranslatedJMenu;
import the.bytecode.club.bytecodeviewer.translation.components.TranslatedJMenuItem;
import the.bytecode.club.bytecodeviewer.util.DialogUtils;
import the.bytecode.club.bytecodeviewer.util.MiscUtils;
import the.bytecode.club.bytecodeviewer.util.SyntaxLanguage;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static the.bytecode.club.bytecodeviewer.Constants.FS;
import static the.bytecode.club.bytecodeviewer.Constants.TEMP_DIRECTORY;
import static the.bytecode.club.bytecodeviewer.Settings.addRecentPlugin;

/**
 * @author Konloch
 * @since 7/1/2021
 */

public class PluginWriter extends JFrame
{
    private SearchableRSyntaxTextArea area;
    private JMenuItem menuSaveAs;
    private JMenuItem menuSave;
    private String content;
    private String pluginName;
    private File savePath;
    private long lastModifiedPluginWriterPane = 0;

    public PluginWriter(PluginTemplate template) throws IOException
    {
        this.content = template.getContents();
        this.pluginName = "Template." + template.getExtension();
        buildGUI();
    }

    public PluginWriter(String content, String pluginName)
    {
        this.content = content;
        this.pluginName = pluginName;
        buildGUI();
    }

    public void buildGUI()
    {
        setTitle("Editing BCV Plugin: " + pluginName);
        setIconImages(IconResources.iconList);
        setSize(new Dimension(542, 316));

        area = (SearchableRSyntaxTextArea) Configuration.rstaTheme.apply(new SearchableRSyntaxTextArea());
        area.setOnCtrlS(this::save);
        area.setText(content);
        area.setCaretPosition(0);
        SyntaxLanguage.setLanguage(area, pluginName);
        content = null;

        area.addKeyListener(new KeyAdapter()
        {
            @Override
            public void keyTyped(KeyEvent e)
            {
                lastModifiedPluginWriterPane = System.currentTimeMillis();
            }
        });

        JButton run = new JButton("Run");

        JMenuBar menuBar = new JMenuBar();
        JMenu menu = new TranslatedJMenu("File", TranslatedComponents.FILE);
        JMenuItem menuOpen = new TranslatedJMenuItem("Open...", TranslatedComponents.OPEN);
        JMenuItem menuRun = new TranslatedJMenuItem("Run", TranslatedComponents.RUN);
        menuSaveAs = new TranslatedJMenuItem("Save As...", TranslatedComponents.SAVE_AS);
        menuSave = new TranslatedJMenuItem("Save...", TranslatedComponents.SAVE);
        menuSave.setVisible(false);

        menuBar.add(menu);
        menu.add(menuOpen);
        menu.add(menuSaveAs);
        menu.add(menuSave);
        menu.add(menuRun);

        setJMenuBar(menuBar);
        add(area.getScrollPane());
        add(run, BorderLayout.SOUTH);

        menuOpen.addActionListener((l) -> openPlugin());
        run.addActionListener((l) -> runPlugin());
        menuRun.addActionListener((l) -> runPlugin());
        menuSaveAs.addActionListener((l) -> save());
        menuSave.addActionListener((l) -> save());

        this.setLocationRelativeTo(null);
    }

    @Override
    public void setVisible(boolean b)
    {
        if (Configuration.pluginWriterAsNewTab)
        {
            Component component = getComponent(0);

            JPanel p = new JPanel(new BorderLayout());
            JPanel p2 = new JPanel(new BorderLayout());
            p.add(p2, BorderLayout.NORTH);
            p.add(component, BorderLayout.CENTER);

            p2.add(getJMenuBar(), BorderLayout.CENTER);

            ComponentViewer.addComponentAsTab(pluginName, p);
        }
        else
        {
            super.setVisible(b);
        }
    }

    public void setPluginName(String name)
    {
        this.pluginName = name;
        setTitle("Editing BCV Plugin: " + name);
    }

    public void openPlugin()
    {
        final File file = DialogUtils.fileChooser("Select External Plugin", "External Plugin", Configuration.getLastPluginDirectory(), PluginManager.fileFilter(), Configuration::setLastPluginDirectory, FileChooser.EVERYTHING);

        if (file == null || !file.exists())
            return;

        try
        {
            area.setText(DiskReader.loadAsString(file.getAbsolutePath()));
            area.setCaretPosition(0);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        setSourceFile(file);
    }

    public void runPlugin()
    {
        File tempFile = new File(TEMP_DIRECTORY + FS + "temp" + MiscUtils.randomString(32) + FS + pluginName);
        tempFile.getParentFile().mkdirs();

        try
        {
            if(savePath != null) //opened a plugin from (Plugins>Open Plugin or Plugins>Recent Plugins)
            {
                //original save path should be overwritten
                if(savePath.lastModified() <= lastModifiedPluginWriterPane)
                {
                    Files.write(area.getText().getBytes(StandardCharsets.UTF_8), savePath); //overwrite original plugin location with new data
                    Files.write(area.getText().getBytes(StandardCharsets.UTF_8), tempFile); //write to temporary file location
                }
                else
                {
                    Files.copy(savePath, tempFile); //write to temporary file location

                    //update content from latest disk data
                    content = DiskReader.loadAsString(savePath.getAbsolutePath());

                    //update plugin writer UI on disk update
                    SwingUtilities.invokeLater(()->
                    {
                        try
                        {
                            int caretPosition = area.getCaretPosition();

                            area.setText(content);
                            area.setCaretPosition(caretPosition);
                        }
                        catch (Exception e)
                        {
                            e.printStackTrace();
                        }
                    });
                }
            }
            else //temp plugin editing (Plugins>New Java Plugin>Run)
            {
                Files.write(area.getText().getBytes(StandardCharsets.UTF_8), tempFile); //write to temporary file location
            }

            //run plugin from that location
            PluginManager.runPlugin(tempFile);
        }
        catch (Exception e)
        {
            BytecodeViewer.handleException(e);
        }
        catch (Throwable t)
        {
            t.printStackTrace();
        }
        finally
        {
            tempFile.getParentFile().delete();
        }
    }

    public void save()
    {
        Thread exportThread = new Thread(() ->
        {
            if (!BytecodeViewer.autoCompileSuccessful())
                return;

            if (savePath == null)
            {
                final String ext = FileNameUtils.getExtension(pluginName);
                JFileChooser fc = new FileChooser(Configuration.getLastPluginDirectory(), "Save Plugin", "BCV Plugin", ext);

                int returnVal = fc.showSaveDialog(BytecodeViewer.viewer);
                if (returnVal == JFileChooser.APPROVE_OPTION)
                {
                    Configuration.setLastPluginDirectory(fc.getSelectedFile());

                    File file = fc.getSelectedFile();
                    String path = file.getAbsolutePath();

                    //auto append extension
                    if (!path.endsWith("." + ext))
                        path += "." + ext;

                    if (!DialogUtils.canOverwriteFile(path))
                        return;

                    //swap from save-as to having a defined path each save
                    setSourceFile(new File(path));
                }
                else
                {
                    return;
                }
            }

            DiskWriter.replaceFile(savePath.getAbsolutePath(), area.getText(), false);
            addRecentPlugin(savePath);
        }, "Plugin Editor Save");

        exportThread.start();
    }

    public void setSourceFile(File file)
    {
        menuSaveAs.setVisible(false);
        menuSave.setVisible(true);
        menuSaveAs.updateUI();
        menuSave.updateUI();
        savePath = file;
        setPluginName(file.getName());
    }
}
