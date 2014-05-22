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
package org.eclipse.oomph.p2.core;

import org.eclipse.oomph.p2.ProfileDefinition;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.equinox.p2.engine.IPhaseSet;
import org.eclipse.equinox.p2.engine.IProvisioningPlan;
import org.eclipse.equinox.p2.engine.PhaseSetFactory;
import org.eclipse.equinox.p2.engine.ProvisioningContext;
import org.eclipse.equinox.p2.metadata.IInstallableUnit;

import java.io.File;

/**
 * @author Eike Stepper
 */
public interface ProfileTransaction
{
  public Profile getProfile();

  public ProfileDefinition getProfileDefinition();

  public File getProfileReferencer();

  public ProfileTransaction setProfileReferencer(File referencer);

  public String getProfileProperty(String key);

  public ProfileTransaction setProfileProperty(String key, String value);

  public ProfileTransaction removeProfileProperty(String key);

  public String getInstallableUnitProperty(IInstallableUnit iu, String key);

  public ProfileTransaction setInstallableUnitProperty(IInstallableUnit iu, String key, String value);

  public ProfileTransaction removeInstallableUnitProperty(IInstallableUnit iu, String key);

  public boolean isRemoveExistingInstallableUnits();

  public ProfileTransaction setRemoveExistingInstallableUnits(boolean removeAll);

  public boolean isDirty();

  public void commit() throws CoreException;

  public void commit(IProgressMonitor monitor) throws CoreException;

  public void commit(CommitContext commitContext, IProgressMonitor monitor) throws CoreException;

  /**
   * @author Eike Stepper
   */
  public class CommitContext
  {
    public ProvisioningContext createProvisioningContext(ProfileTransaction transaction) throws CoreException
    {
      return new ProvisioningContext(transaction.getProfile().getAgent().getProvisioningAgent());
    }

    // public InstallOperation createInstallOperation(ProfileTransaction transaction, Collection<IInstallableUnit> iusToInstall)
    // {
    // ProvisioningSession provisioningSession = transaction.getProfile().getAgent().getProvisioningSession();
    // return new InstallOperation(provisioningSession, iusToInstall);
    // }

    public void handleProvisioningPlan(IProvisioningPlan provisioningPlan) throws CoreException
    {
    }

    public IPhaseSet getPhaseSet(ProfileTransaction transaction) throws CoreException
    {
      return PhaseSetFactory.createDefaultPhaseSet();
    }
  }
}
