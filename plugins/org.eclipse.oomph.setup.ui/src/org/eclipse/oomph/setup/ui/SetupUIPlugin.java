/*
 * Copyright (c) 2014 Eike Stepper (Berlin, Germany) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Eike Stepper - initial API and implementation
 */
package org.eclipse.oomph.setup.ui;

import org.eclipse.oomph.base.Annotation;
import org.eclipse.oomph.base.BaseFactory;
import org.eclipse.oomph.base.BasePackage;
import org.eclipse.oomph.internal.setup.SetupPrompter;
import org.eclipse.oomph.internal.setup.SetupProperties;
import org.eclipse.oomph.preferences.PreferencesFactory;
import org.eclipse.oomph.preferences.util.PreferencesUtil;
import org.eclipse.oomph.preferences.util.PreferencesUtil.PreferenceProperty;
import org.eclipse.oomph.setup.SetupTask;
import org.eclipse.oomph.setup.SetupTaskContainer;
import org.eclipse.oomph.setup.Trigger;
import org.eclipse.oomph.setup.User;
import org.eclipse.oomph.setup.internal.core.SetupContext;
import org.eclipse.oomph.setup.internal.core.SetupTaskPerformer;
import org.eclipse.oomph.setup.internal.core.util.ResourceMirror;
import org.eclipse.oomph.setup.internal.core.util.SetupUtil;
import org.eclipse.oomph.setup.ui.questionnaire.GearAnimator;
import org.eclipse.oomph.setup.ui.questionnaire.GearShell;
import org.eclipse.oomph.setup.ui.recorder.RecorderManager;
import org.eclipse.oomph.setup.ui.recorder.RecorderTransaction;
import org.eclipse.oomph.setup.ui.wizards.SetupWizard;
import org.eclipse.oomph.ui.OomphUIPlugin;
import org.eclipse.oomph.ui.UIUtil;
import org.eclipse.oomph.util.IOUtil;
import org.eclipse.oomph.util.PropertiesUtil;

import org.eclipse.emf.common.ui.EclipseUIPlugin;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.ResourceLocator;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.dynamichelpers.IExtensionTracker;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.PlatformUI;

import org.osgi.framework.BundleContext;

import java.io.File;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * @author Eike Stepper
 */
public final class SetupUIPlugin extends OomphUIPlugin
{
  public static final SetupUIPlugin INSTANCE = new SetupUIPlugin();

  public static final String PLUGIN_ID = INSTANCE.getSymbolicName();

  public static final String INSTALLER_PRODUCT_ID = "org.eclipse.oomph.setup.installer.product";

  public static final String PREF_SKIP_STARTUP_TASKS = "skip.startup.tasks";

  public static final String PREF_ENABLE_PREFERENCE_RECORDER = "enable.preference.recorder";

  private static final String RESTARTING_FILE_NAME = "restarting";

  private static final String ANNOTATION_SOURCE_INITIAL = "initial";

  private static final String ANNOTATION_DETAILS_KEY_OFFLINE = "offline";

  private static final String ANNOTATION_DETAILS_KEY_MIRRORS = "mirrors";

  private static final boolean SETUP_SKIP = PropertiesUtil.isProperty(SetupProperties.PROP_SETUP_SKIP);

  private static Implementation plugin;

  @SuppressWarnings("restriction")
  public SetupUIPlugin()
  {
    super(new ResourceLocator[] { org.eclipse.oomph.internal.ui.UIPlugin.INSTANCE });
  }

  public void refreshCache()
  {
    // compute the setup context again.
    // reset the ECF cache map.
  }

  @Override
  public ResourceLocator getPluginResourceLocator()
  {
    return plugin;
  }

  public static void initialStart(File ws, boolean offline, boolean mirrors)
  {
    Annotation annotation = BaseFactory.eINSTANCE.createAnnotation();
    annotation.setSource(ANNOTATION_SOURCE_INITIAL);
    annotation.getDetails().put(ANNOTATION_DETAILS_KEY_OFFLINE, Boolean.toString(offline));
    annotation.getDetails().put(ANNOTATION_DETAILS_KEY_MIRRORS, Boolean.toString(mirrors));

    File file = new File(ws, ".metadata/.plugins/" + SetupUIPlugin.INSTANCE.getSymbolicName() + "/" + RESTARTING_FILE_NAME);
    saveRestartFile(file, annotation);
  }

  public static void restart(Trigger trigger, EList<SetupTask> setupTasks)
  {
    if (!setupTasks.isEmpty())
    {
      Annotation annotation = BaseFactory.eINSTANCE.createAnnotation();
      annotation.setSource(trigger.toString());
      annotation.getReferences().addAll(setupTasks);

      saveRestartFile(getRestartingFile(), annotation);
    }

    PlatformUI.getWorkbench().restart();
  }

  private static void saveRestartFile(File file, Annotation annotation)
  {
    try
    {
      Resource resource = SetupUtil.createResourceSet().createResource(URI.createFileURI(file.toString()));
      resource.getContents().add(annotation);
      resource.save(null);
    }
    catch (Exception ex)
    {
      // Ignore
    }
  }

  public static boolean isSkipStartupTasks()
  {
    return plugin.getPreferenceStore().getBoolean(PREF_SKIP_STARTUP_TASKS);
  }

  private static File getRestartingFile()
  {
    return new File(INSTANCE.getStateLocation().toString(), RESTARTING_FILE_NAME);
  }

  private static void performStartup()
  {
    final Display display = Display.getDefault();
    display.asyncExec(new Runnable()
    {
      public void run()
      {
        if (!isInstallerProduct())
        {
          StartingPropertyTester.setStarting(true);

          final IWorkbench workbench = PlatformUI.getWorkbench();
          IExtensionTracker extensionTracker = workbench.getExtensionTracker();
          if (extensionTracker == null || workbench.getWorkbenchWindowCount() == 0)
          {
            display.timerExec(1000, this);
          }
          else
          {
            if (SetupTaskPerformer.REMOTE_DEBUG)
            {
              MessageDialog.openInformation(UIUtil.getShell(), "Remote Debug Pause", "The setup tasks are paused to allow you to attach a remote debugger");
            }

            RecorderManager.Lifecycle.start(display);

            if (!SETUP_SKIP && !isSkipStartupTasks())
            {
              new Job("Setup check...")
              {
                @Override
                protected IStatus run(IProgressMonitor monitor)
                {
                  try
                  {
                    monitor.beginTask("Determing tasks to be performed", IProgressMonitor.UNKNOWN);
                    performStartup(workbench, monitor);
                    monitor.done();
                    return Status.OK_STATUS;
                  }
                  finally
                  {
                    StartingPropertyTester.setStarting(false);
                  }
                }
              }.schedule();
            }
            else
            {
              StartingPropertyTester.setStarting(false);

              new Job("Refresh Setup Cache")
              {
                @Override
                protected IStatus run(IProgressMonitor monitor)
                {
                  ResourceMirror resourceMirror = new ResourceMirror();
                  resourceMirror.perform(Arrays.asList(new URI[] { SetupContext.INSTALLATION_SETUP_URI, SetupContext.WORKSPACE_SETUP_URI,
                      SetupContext.USER_SETUP_URI }));

                  ResourceSet resourceSet = resourceMirror.getResourceSet();
                  resourceMirror.dispose();

                  SetupContext.setSelf(SetupContext.createSelf(resourceSet));

                  return Status.OK_STATUS;
                }
              }.schedule();
            }
          }
        }
      }
    });
  }

  private static void performStartup(IWorkbench workbench, IProgressMonitor monitor)
  {
    Trigger trigger = Trigger.STARTUP;
    boolean restarting = false;
    Set<URI> neededRestartTasks = new HashSet<URI>();

    try
    {
      File restartingFile = getRestartingFile();
      if (restartingFile.exists())
      {
        monitor.subTask("Reading restart tasks");
        Resource resource = SetupUtil.createResourceSet().getResource(URI.createFileURI(restartingFile.toString()), true);

        Annotation annotation = (Annotation)EcoreUtil.getObjectByType(resource.getContents(), BasePackage.Literals.ANNOTATION);
        resource.getContents().remove(annotation);

        if (ANNOTATION_SOURCE_INITIAL.equals(annotation.getSource()))
        {
          if ("true".equals(annotation.getDetails().get(ANNOTATION_DETAILS_KEY_OFFLINE)))
          {
            System.setProperty(SetupProperties.PROP_SETUP_OFFLINE_STARTUP, "true");
          }

          if ("true".equals(annotation.getDetails().get(ANNOTATION_DETAILS_KEY_MIRRORS)))
          {
            System.setProperty(SetupProperties.PROP_SETUP_MIRRORS_STARTUP, "true");
          }
        }
        else
        {
          for (EObject eObject : annotation.getReferences())
          {
            neededRestartTasks.add(EcoreUtil.getURI(eObject));
          }

          trigger = Trigger.get(annotation.getSource());
          restarting = true;
        }

        IOUtil.deleteBestEffort(restartingFile);
        System.setProperty(SetupProperties.PROP_SETUP_CONFIRM_SKIP, "true");
      }
    }
    catch (Exception ex)
    {
      // Ignore
    }

    performQuestionnaire(UIUtil.getShell(), false);

    monitor.subTask("Creating a task performer");

    // This performer is only used to detect a need to update or to open the setup wizard.
    SetupTaskPerformer performer = null;
    final ResourceSet resourceSet = SetupUtil.createResourceSet();

    try
    {
      performer = SetupTaskPerformer.createForIDE(resourceSet, SetupPrompter.CANCEL, trigger);
    }
    catch (OperationCanceledException ex)
    {
      //$FALL-THROUGH$
    }
    catch (Throwable ex)
    {
      INSTANCE.log(ex);
      return;
    }
    finally
    {
      SetupContext.setSelf(SetupContext.createSelf(resourceSet));
    }

    if (performer != null)
    {
      try
      {
        // At this point we know that no prompt was needed.
        EList<SetupTask> neededTasks = performer.initNeededSetupTasks();
        if (restarting)
        {
          for (Iterator<SetupTask> it = neededTasks.iterator(); it.hasNext();)
          {
            SetupTask setupTask = it.next();
            if (setupTask.getPriority() == SetupTask.PRIORITY_INSTALLATION || !neededRestartTasks.contains(EcoreUtil.getURI(setupTask)))
            {
              it.remove();
            }
          }
        }

        if (neededTasks.isEmpty())
        {
          // No tasks are needed, either. Nothing to do.
          System.clearProperty(SetupProperties.PROP_SETUP_CONFIRM_SKIP);
          return;
        }
      }
      catch (Throwable ex)
      {
        INSTANCE.log(ex);
        return;
      }
    }
    else
    {
      System.clearProperty(SetupProperties.PROP_SETUP_CONFIRM_SKIP);
    }

    if (performer == null)
    {
      monitor.setTaskName("Performing tasks that need prompted variables");
    }
    else
    {
      monitor.setTaskName("Performing " + performer.getTriggeredSetupTasks().size() + " tasks");
    }

    final SetupTaskPerformer finalPerfomer = performer;
    UIUtil.syncExec(new Runnable()
    {
      public void run()
      {
        if (finalPerfomer != null)
        {
          resourceSet.getResources().add(finalPerfomer.getUser().eResource());
        }

        SetupWizard updater = new SetupWizard.Updater(finalPerfomer);
        updater.openDialog(UIUtil.getShell());
      }
    });
  }

  public static void performQuestionnaire(final Shell parentShell, boolean force)
  {
    RecorderTransaction transaction = RecorderTransaction.open();

    try
    {
      SetupTaskContainer rootObject = transaction.getRootObject();
      if (rootObject instanceof User)
      {
        User user = (User)rootObject;
        if (user.getQuestionnaireDate() == null || force)
        {
          final Map<URI, String> preferences = new HashMap<URI, String>();
          UIUtil.syncExec(new Runnable()
          {
            public void run()
            {
              GearShell shell = new GearShell(parentShell);
              Map<URI, String> result = shell.openModal();
              if (result != null)
              {
                preferences.putAll(result);
              }
            }
          });

          URI uri = PreferencesFactory.eINSTANCE.createURI(GearAnimator.RECORDER_PREFERENCE_KEY);
          if (preferences.containsKey(uri))
          {
            boolean enabled = Boolean.parseBoolean(preferences.remove(uri));
            user.setPreferenceRecorderDefault(enabled);
          }

          if (!preferences.isEmpty())
          {
            boolean inIDE = !isInstallerProduct();
            for (Entry<URI, String> entry : preferences.entrySet())
            {
              String path = PreferencesFactory.eINSTANCE.convertURI(entry.getKey());
              transaction.setPolicy(path, true);

              if (inIDE)
              {
                PreferenceProperty property = new PreferencesUtil.PreferenceProperty(path);
                property.set(entry.getValue());
              }
            }

            transaction.setPreferences(preferences);
          }

          user.setQuestionnaireDate(new Date());
          transaction.setForceDirty(true);
          transaction.commit();
        }
      }
    }
    finally
    {
      transaction.close();
    }
  }

  private static boolean isInstallerProduct()
  {
    String productID = PropertiesUtil.getProperty("eclipse.product");
    return INSTALLER_PRODUCT_ID.equals(productID);
  }

  /**
   * @author Eike Stepper
   */
  public static class Implementation extends EclipseUIPlugin
  {
    public Implementation()
    {
      plugin = this;
    }

    @Override
    public void start(BundleContext context) throws Exception
    {
      super.start(context);
      performStartup();
    }

    @Override
    public void stop(BundleContext context) throws Exception
    {
      if (!isInstallerProduct())
      {
        RecorderManager.Lifecycle.stop();
      }

      super.stop(context);
    }
  }
}
