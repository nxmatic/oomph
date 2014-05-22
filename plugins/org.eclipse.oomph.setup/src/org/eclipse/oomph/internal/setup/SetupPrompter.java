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
package org.eclipse.oomph.internal.setup;

import org.eclipse.oomph.setup.SetupTaskContext;
import org.eclipse.oomph.setup.VariableTask;
import org.eclipse.oomph.util.UserCallback;

import java.util.List;

/**
 * @author Eike Stepper
 */
public interface SetupPrompter
{
  public static final SetupPrompter CANCEL = new SetupPrompter()
  {
    public UserCallback getUserCallback()
    {
      return null;
    }

    public String getValue(VariableTask variable)
    {
      return null;
    }

    public boolean promptVariables(List<? extends SetupTaskContext> performers)
    {
      return false;
    }
  };

  public UserCallback getUserCallback();

  public String getValue(VariableTask variable);

  public boolean promptVariables(List<? extends SetupTaskContext> performers);
}
