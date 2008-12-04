/**
 * 
 */
package cpa.scoperestrictionautomaton.label;

import cfa.objectmodel.CFAEdge;
import cfa.objectmodel.CFAEdgeType;

/**
 * @author holzera
 *
 */
public class FunctionCallLabel implements Label<CFAEdge> {
  private String mFunctionName;
  
  public FunctionCallLabel(String pFunctionName) {
    mFunctionName = pFunctionName;
  }

  @Override
  public boolean matches(CFAEdge pEdge) {
    if (CFAEdgeType.FunctionCallEdge == pEdge.getEdgeType()) {
      return pEdge.getSuccessor().getFunctionName().equals(mFunctionName);
    }    

    return false;
  }
}
