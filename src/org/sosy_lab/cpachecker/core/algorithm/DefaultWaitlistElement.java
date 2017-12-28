/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2017  Dirk Beyer
 *  All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 *  CPAchecker web page:
 *    http://cpachecker.sosy-lab.org
 */
package org.sosy_lab.cpachecker.core.algorithm;

import com.google.common.base.Preconditions;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.WaitlistElementWithAbstractState;


public class DefaultWaitlistElement implements WaitlistElementWithAbstractState {

  private final AbstractState state;
  private final Precision precision;

  public DefaultWaitlistElement(AbstractState pState, Precision pPrec) {
    Preconditions.checkNotNull(pState);
    Preconditions.checkNotNull(pPrec);
    state = pState;
    precision = pPrec;
  }

  @Override
  public boolean contains(AbstractState pState) {
    return state.equals(pState);
  }

  @Override
  public Collection<AbstractState> getAbstractStates() {
    return Collections.singleton(state);
  }

  @Override
  public AbstractState getAbstractState() {
    return state;
  }

  @Override
  public Precision getPrecision() {
    return precision;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + Objects.hashCode(precision);
    result = prime * result + Objects.hashCode(state);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    DefaultWaitlistElement other = (DefaultWaitlistElement) obj;
    return Objects.equals(state, other.state)
        && Objects.equals(precision, precision);
  }
}