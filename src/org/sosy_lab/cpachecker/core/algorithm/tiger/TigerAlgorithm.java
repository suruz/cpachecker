/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2014  Dirk Beyer
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
package org.sosy_lab.cpachecker.core.algorithm.tiger;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.Writer;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;

import javax.annotation.Nullable;
import javax.management.JMException;

import org.sosy_lab.common.Pair;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.FileOption;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.io.Files;
import org.sosy_lab.common.io.Path;
import org.sosy_lab.common.io.PathTemplate;
import org.sosy_lab.common.io.Paths;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.CoreComponentsFactory;
import org.sosy_lab.cpachecker.core.CounterexampleInfo;
import org.sosy_lab.cpachecker.core.MainCPAStatistics;
import org.sosy_lab.cpachecker.core.algorithm.Algorithm;
import org.sosy_lab.cpachecker.core.algorithm.CEGARAlgorithm;
import org.sosy_lab.cpachecker.core.algorithm.testgen.util.StartupConfig;
import org.sosy_lab.cpachecker.core.algorithm.tiger.fql.PredefinedCoverageCriteria;
import org.sosy_lab.cpachecker.core.algorithm.tiger.fql.ast.Edges;
import org.sosy_lab.cpachecker.core.algorithm.tiger.fql.ast.FQLSpecification;
import org.sosy_lab.cpachecker.core.algorithm.tiger.fql.ecp.SingletonECPEdgeSet;
import org.sosy_lab.cpachecker.core.algorithm.tiger.fql.ecp.translators.GuardedEdgeLabel;
import org.sosy_lab.cpachecker.core.algorithm.tiger.fql.ecp.translators.InverseGuardedEdgeLabel;
import org.sosy_lab.cpachecker.core.algorithm.tiger.fql.translators.ecp.CoverageSpecificationTranslator;
import org.sosy_lab.cpachecker.core.algorithm.tiger.goals.Goal;
import org.sosy_lab.cpachecker.core.algorithm.tiger.goals.clustering.ClusteredElementaryCoveragePattern;
import org.sosy_lab.cpachecker.core.algorithm.tiger.goals.clustering.InfeasibilityPropagation;
import org.sosy_lab.cpachecker.core.algorithm.tiger.goals.clustering.InfeasibilityPropagation.Prediction;
import org.sosy_lab.cpachecker.core.algorithm.tiger.util.ARTReuse;
import org.sosy_lab.cpachecker.core.algorithm.tiger.util.BDDUtils;
import org.sosy_lab.cpachecker.core.algorithm.tiger.util.PrecisionCallback;
import org.sosy_lab.cpachecker.core.algorithm.tiger.util.TestCase;
import org.sosy_lab.cpachecker.core.algorithm.tiger.util.TestGoalUtils;
import org.sosy_lab.cpachecker.core.algorithm.tiger.util.TestSuite;
import org.sosy_lab.cpachecker.core.algorithm.tiger.util.ThreeValuedAnswer;
import org.sosy_lab.cpachecker.core.algorithm.tiger.util.WorkerRunnable;
import org.sosy_lab.cpachecker.core.algorithm.tiger.util.WorklistEntryComparator;
import org.sosy_lab.cpachecker.core.algorithm.tiger.util.Wrapper;
import org.sosy_lab.cpachecker.core.algorithm.tiger.util.WrapperUtil;
import org.sosy_lab.cpachecker.core.counterexample.RichModel;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.CPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.Property;
import org.sosy_lab.cpachecker.core.interfaces.Refiner;
import org.sosy_lab.cpachecker.core.interfaces.StateSpacePartition;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.StatisticsProvider;
import org.sosy_lab.cpachecker.core.interfaces.WrapperCPA;
import org.sosy_lab.cpachecker.core.reachedset.LocationMappedReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.core.waitlist.Waitlist;
import org.sosy_lab.cpachecker.cpa.arg.ARGCPA;
import org.sosy_lab.cpachecker.cpa.arg.ARGPath;
import org.sosy_lab.cpachecker.cpa.arg.ARGPath.PathIterator;
import org.sosy_lab.cpachecker.cpa.arg.ARGPathExporter;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.arg.ARGStatistics;
import org.sosy_lab.cpachecker.cpa.arg.ARGUtils;
import org.sosy_lab.cpachecker.cpa.automaton.Automaton;
import org.sosy_lab.cpachecker.cpa.automaton.ControlAutomatonCPA;
import org.sosy_lab.cpachecker.cpa.automaton.InvalidAutomatonException;
import org.sosy_lab.cpachecker.cpa.automaton.PowersetAutomatonCPA;
import org.sosy_lab.cpachecker.cpa.automaton.ReducedAutomatonProduct;
import org.sosy_lab.cpachecker.cpa.bdd.BDDCPA;
import org.sosy_lab.cpachecker.cpa.bdd.BDDTransferRelation;
import org.sosy_lab.cpachecker.cpa.composite.CompositeCPA;
import org.sosy_lab.cpachecker.cpa.predicate.PredicateAbstractionRefinementStrategy;
import org.sosy_lab.cpachecker.cpa.predicate.PredicateCPA;
import org.sosy_lab.cpachecker.cpa.predicate.PredicateCPARefiner;
import org.sosy_lab.cpachecker.cpa.predicate.PredicatePrecision;
import org.sosy_lab.cpachecker.cpa.predicate.RefinementStrategy;
import org.sosy_lab.cpachecker.exceptions.CPAEnabledAnalysisPropertyViolationException;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.Precisions;
import org.sosy_lab.cpachecker.util.automaton.NondeterministicFiniteAutomaton;
import org.sosy_lab.cpachecker.util.predicates.NamedRegionManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.Region;
import org.sosy_lab.cpachecker.util.resources.ProcessCpuTime;
import org.sosy_lab.cpachecker.util.statistics.StatCpuTime;
import org.sosy_lab.cpachecker.util.statistics.StatCpuTime.NoTimeMeasurement;
import org.sosy_lab.solver.AssignableTerm;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

@Options(prefix = "tiger")
public class TigerAlgorithm
    implements Algorithm, PrecisionCallback<PredicatePrecision>, StatisticsProvider, Statistics {

  public static String originalMainFunction = null;

  @Option(secure = true, name = "fqlQuery", description = "Coverage criterion given as an FQL query")
  private String fqlQuery = PredefinedCoverageCriteria.BASIC_BLOCK_COVERAGE; // default is basic block coverage

  @Option(secure = true, name = "optimizeGoalAutomata", description = "Optimize the test goal automata")
  private boolean optimizeGoalAutomata = true;

  @Option(secure = true, name = "printARGperGoal", description = "Print the ARG for each test goal")
  private boolean printARGperGoal = false;

  @Option(secure = true, name = "useAutomataCrossProduct", description = "Compute the cross product of the goal automata?")
  private boolean useAutomataCrossProduct = false;

  @Option(
      secure = true,
      name = "checkCoverage",
      description = "Checks whether a test case for one goal covers another test goal")
  private boolean checkCoverage = true;

  @Option(secure = true, name = "reuseARG", description = "Reuse ARG across test goals")
  private boolean reuseARG = true;

  @Option(secure = true, name = "reusePredicates", description = "Reuse predicates across modifications of an ARG.")
  private boolean reusePredicates = true;

  @Option(secure = true, name = "usePowerset", description = "Construct the powerset of automata states.")
  private boolean usePowerset = true;

  @Option(secure = true, name = "testsuiteFile", description = "Filename for output of generated test suite")
  @FileOption(FileOption.Type.OUTPUT_FILE)
  private Path testsuiteFile = Paths.get("testsuite.txt");

  @Option(secure = true, name = "testcaseGeneartionTimesFile", description = "Filename for output of geneartion times of test cases")
  @FileOption(FileOption.Type.OUTPUT_FILE)
  private Path testcaseGenerationTimesFile = Paths.get("generationTimes.csv");

  @Option(secure=true,
      description="File for saving processed goal automata in DOT format (%s will be replaced with automaton name)")
  @FileOption(FileOption.Type.OUTPUT_FILE)
  private PathTemplate dumpGoalAutomataTo = PathTemplate.ofFormatString("Automaton_%s.dot");

  @Option(
      secure = true,
      name = "useInfeasibilityPropagation",
      description = "Map information on infeasibility of one test goal to other test goals.")
  private boolean useInfeasibilityPropagation = false;

  enum TimeoutStrategy {
    SKIP_AFTER_TIMEOUT,
    RETRY_AFTER_TIMEOUT
  }

  @Option(
      secure = true,
      name = "timeoutStrategy",
      description = "How to proceed with timed-out goals if some time remains after processing all other goals.")
  private TimeoutStrategy timeoutStrategy = TimeoutStrategy.SKIP_AFTER_TIMEOUT;

  @Option(
      secure = true,
      name = "limitsPerGoal.time.cpu.increment",
      description = "Value for which timeout gets incremented if timed-out goals are re-processed.")
  private int timeoutIncrement = 0;

  /*@Option(name = "globalCoverageCheckBeforeTimeout", description = "Perform a coverage check on all remaining coverage goals before the global time out happens.")
  private boolean globalCoverageCheckBeforeTimeout = false;

  @Option(name = "timeForGlobalCoverageCheck", description = "Time budget for coverage check before global time out.")
  private String timeForGlobalCoverageCheck = "0s";*/

  @Option(
      secure = true,
      name = "limitsPerGoal.time.cpu",
      description = "Time limit per test goal in seconds (-1 for infinity).")
  private long cpuTimelimitPerGoal = -1;

  @Option(
      secure = true,
      name = "inverseOrder",
      description = "Inverses the order of test goals each time a new round of re-processing of timed-out goals begins.")
  private boolean inverseOrder = true;

  @Option(
      secure = true,
      name = "useOrder",
      description = "Enforce the original order each time a new round of re-processing of timed-out goals begins.")
  private boolean useOrder = true;

  @Option(
      secure = true,
      name = "algorithmConfigurationFile",
      description = "Configuration file for internal cpa algorithm.")
  @FileOption(FileOption.Type.REQUIRED_INPUT_FILE)
  private Path algorithmConfigurationFile = Paths.get("config/tiger-internal-algorithm.properties");

  @Option(
      secure = true,
      name = "tiger_with_presenceConditions",
      description = "Use Test Input Generator algorithm with an extension using the BDDCPA to model product line presence conditions")
  public boolean useTigerAlgorithm_with_pc = false;

  @Option(
      secure = true,
      name = "numberOfTestGoalsPerRun",
      description = "The number of test goals processed per CPAchecker run (0: all test goals in one run).")
  private int numberOfTestGoalsPerRun = 1;

  @Option(
      secure = true,
      name = "allCoveredGoalsPerTestCase",
      description = "Returns all test goals covered by a test case.")
  private boolean allCoveredGoalsPerTestCase = false;

  @Option(
      secure = true,
      name = "printLabels",
      description = "Prints labels reached with the error path of a test case.")
  private boolean printLabels = false;

  private final Configuration config;
  private final LogManager logger;
  private final CFA cfa;

  private StartupConfig startupConfig;
  private ConfigurableProgramAnalysis cpa;

  private CoverageSpecificationTranslator mCoverageSpecificationTranslator;
  private FQLSpecification fqlSpecification;

  private Wrapper wrapper;
  private GuardedEdgeLabel mAlphaLabel;
  private GuardedEdgeLabel mOmegaLabel;
  private InverseGuardedEdgeLabel mInverseAlphaLabel;

  private TestSuite testsuite;
  private ReachedSet reachedSet = null;
  private ReachedSet outsideReachedSet = null;

  private PredicatePrecision reusedPrecision = null;

  private int statistics_numberOfTestGoals;
  private int statistics_numberOfProcessedTestGoals = 0;
  private StatCpuTime statCpuTime = null;

  private Prediction[] lGoalPrediction;

  private String programDenotation;
  private MainCPAStatistics stats;
  private int testCaseId = 0;

  NamedRegionManager bddCpaNamedRegionManager = null;

  private TestGoalUtils testGoalUtils = null;

  public TigerAlgorithm(Algorithm pAlgorithm, ConfigurableProgramAnalysis pCpa, ShutdownNotifier pShutdownNotifier,
      CFA pCfa, Configuration pConfig, LogManager pLogger, String programDenotation,
      @Nullable final MainCPAStatistics stats) throws InvalidConfigurationException {

    this.programDenotation = programDenotation;
    this.stats = stats;
    this.statCpuTime = new StatCpuTime();
    this.config = pConfig;

    startupConfig = new StartupConfig(pConfig, pLogger, pShutdownNotifier);
    startupConfig.getConfig().inject(this);

    logger = pLogger;

    cpa = pCpa;
    cfa = pCfa;

    // Check if BDD is enabled for variability-aware test-suite generation
    bddCpaNamedRegionManager = BDDUtils.getBddCpaNamedRegionManagerFromCpa(cpa, useTigerAlgorithm_with_pc);

    testsuite = new TestSuite(bddCpaNamedRegionManager, printLabels, useTigerAlgorithm_with_pc);

    assert TigerAlgorithm.originalMainFunction != null;
    mCoverageSpecificationTranslator =
        new CoverageSpecificationTranslator(pCfa.getFunctionHead(TigerAlgorithm.originalMainFunction));

    wrapper = new Wrapper(pCfa, TigerAlgorithm.originalMainFunction);

    mAlphaLabel = new GuardedEdgeLabel(new SingletonECPEdgeSet(wrapper.getAlphaEdge()));
    mInverseAlphaLabel = new InverseGuardedEdgeLabel(mAlphaLabel);
    mOmegaLabel = new GuardedEdgeLabel(new SingletonECPEdgeSet(wrapper.getOmegaEdge()));

    testGoalUtils = new TestGoalUtils(logger, useTigerAlgorithm_with_pc, bddCpaNamedRegionManager, mAlphaLabel,
        mInverseAlphaLabel, mOmegaLabel);

    // get internal representation of FQL query
    fqlSpecification = testGoalUtils.parseFQLQuery(fqlQuery);
  }

  @Override
  public String getName() {
    return "TigerAlgorithm";
  }

  @Override
  public void setPrecision(PredicatePrecision pNewPrec) {
    reusedPrecision = pNewPrec;
  }

  @Override
  public PredicatePrecision getPrecision() {
    return reusedPrecision;
  }

  public long getCpuTime() {
    long cpuTime = -1;
    try {
      long currentCpuTime = (long) (ProcessCpuTime.read() / 1e6);
      long currentWallTime = System.currentTimeMillis();
      statCpuTime.onMeasurementResult(currentCpuTime - statCpuTime.getCpuTimeSumMilliSecs(), currentWallTime - statCpuTime.getWallTimeSumMsec());
      cpuTime = statCpuTime.getCpuTimeSumMilliSecs();
    } catch (NoTimeMeasurement | JMException e) {
      logger.logUserException(Level.WARNING, e, "Could not get CPU time for statistics.");
    }

    return cpuTime;
  }

  @Override
  public AlgorithmStatus run(ReachedSet pReachedSet) throws CPAException, InterruptedException {
    // we empty pReachedSet to stop complaints of an incomplete analysis
    // Problem: pReachedSet does not match the internal CPA structure!
    logger.logf(Level.INFO,
        "We will not use the provided reached set since it violates the internal structure of Tiger's CPAs");
    logger.logf(Level.INFO, "We empty pReachedSet to stop complaints of an incomplete analysis");
    outsideReachedSet = pReachedSet;
    outsideReachedSet.clear();

    statCpuTime.start();
    testsuite.setGenerationStartTime(getCpuTime());

    // Optimization: Infeasibility propagation
    Pair<Boolean, LinkedList<Edges>> lInfeasibilityPropagation = initializeInfisabilityPropagation();

    LinkedList<Goal> pGoalsToCover = testGoalUtils.extractTestGoalPatterns(fqlSpecification, lGoalPrediction,
        lInfeasibilityPropagation, mCoverageSpecificationTranslator, optimizeGoalAutomata);
    statistics_numberOfTestGoals = pGoalsToCover.size();
    logger.logf(Level.INFO, "Number of test goals: %d", statistics_numberOfTestGoals);


    // (iii) do test generation for test goals ...
    boolean wasSound = true;
    try {
      if (!testGeneration(pGoalsToCover, lInfeasibilityPropagation)) {
        logger.logf(Level.WARNING, "Test generation contained unsound reachability analysis runs!");
        wasSound = false;
      }
    } catch (InvalidConfigurationException e1) {
      throw new CPAException("Invalid configuration!", e1);
    }

    assert (pGoalsToCover.isEmpty());

    // write generated test suite and mapping to file system
    try (Writer writer =
        new BufferedWriter(new OutputStreamWriter(new FileOutputStream(testsuiteFile.getAbsolutePath()), "utf-8"))) {
      writer.write(testsuite.toString());
      writer.close();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    if (wasSound) {
      return AlgorithmStatus.SOUND_AND_PRECISE;
    } else {
      return AlgorithmStatus.UNSOUND_AND_PRECISE;
    }
  }

  private Pair<Boolean, LinkedList<Edges>> initializeInfisabilityPropagation() {
    Pair<Boolean, LinkedList<Edges>> lInfeasibilityPropagation;

    if (useInfeasibilityPropagation) {
      lInfeasibilityPropagation = InfeasibilityPropagation.canApplyInfeasibilityPropagation(fqlSpecification);
    } else {
      lInfeasibilityPropagation = Pair.of(Boolean.FALSE, null);
    }

    return lInfeasibilityPropagation;
  }

  private boolean testGeneration(LinkedList<Goal> pGoalsToCover,
      Pair<Boolean, LinkedList<Edges>> pInfeasibilityPropagation)
          throws CPAException, InterruptedException, InvalidConfigurationException {
    boolean wasSound = true;
    int numberOfTestGoals = pGoalsToCover.size();
    testsuite.addGoals(pGoalsToCover);

    NondeterministicFiniteAutomaton<GuardedEdgeLabel> previousAutomaton = null;
    boolean retry = false;

    do {
      if (retry) {
        // retry timed-out goals
        boolean order = true;

        if (timeoutIncrement > 0) {
          long oldCPUTimeLimitPerGoal = cpuTimelimitPerGoal;
          cpuTimelimitPerGoal += timeoutIncrement;
          logger.logf(Level.INFO, "Incremented timeout from %d to %d seconds.", oldCPUTimeLimitPerGoal,
              cpuTimelimitPerGoal);
          Collection<Entry<Integer, Pair<Goal, Region>>> set;
          if (useOrder) {
            if (inverseOrder) {
              order = !order;
            }

            // keep original order of goals (or inverse of it)
            if (order) {
              set = new TreeSet<>(WorklistEntryComparator.ORDER_RESPECTING_COMPARATOR);
            } else {
              set = new TreeSet<>(WorklistEntryComparator.ORDER_INVERTING_COMPARATOR);
            }

            set.addAll(testsuite.getTimedOutGoals().entrySet());
          } else {
            set = new LinkedList<>();
            set.addAll(testsuite.getTimedOutGoals().entrySet());
          }

          pGoalsToCover.clear();
          for (Entry<Integer, Pair<Goal, Region>> entry : set) {
            pGoalsToCover.add(entry.getValue().getFirst());
          }

          statistics_numberOfProcessedTestGoals -= testsuite.getTimedOutGoals().size();
          testsuite.getTimedOutGoals().clear();
        }
      }

      while (!pGoalsToCover.isEmpty()) {
        Set<Goal> goalsToBeProcessed = new HashSet<>();
        int testGoalSetSize;
        if (numberOfTestGoals <= 0) {
          testGoalSetSize = pGoalsToCover.size();
        } else {
          testGoalSetSize =
              (pGoalsToCover.size() > numberOfTestGoalsPerRun) ? numberOfTestGoalsPerRun : pGoalsToCover.size();
        }
        for (int i = 0; i < testGoalSetSize; i++) {
          statistics_numberOfProcessedTestGoals++;
          goalsToBeProcessed.add(pGoalsToCover.poll());
        }

        // TODO: remove covered and infeasible test goals from set?
        // TODO: remove loop? it is not necessary anymore if we call the tiger-algorithm until the complete state space is covered for each goal
        while (!testsuite.areGoalsCoveredOrInfeasible(goalsToBeProcessed)) {
          if (useTigerAlgorithm_with_pc) {
            /* force that a new reachedSet is computed when first starting on a new TestGoal with initial PC TRUE.
             * This enforces that no very constrained ARG is reused when computing a new ARG for a new testgoal with broad pc (TRUE).
             * This strategy allows us to set option tiger.reuseARG=true such that ARG is reused in testgoals (pcs get only more specific).
             * Keyword: overapproximation
             */
            reachedSet = null;
          }

          String logString = "Processing test goals ";
          for (Goal g : goalsToBeProcessed) {
            logString += g.getIndex() + " (" + testsuite.getTestGoalLabel(g) + "), ";
          }
          logString = logString.substring(0, logString.length() - 2);
          if (useTigerAlgorithm_with_pc) {
            Region remainingPresenceCondition =
                BDDUtils.composeRemainingPresenceConditions(goalsToBeProcessed, bddCpaNamedRegionManager);
            logger.logf(Level.INFO, "%s of %d for PC %s.", logString, numberOfTestGoals,
                bddCpaNamedRegionManager.dumpRegion(remainingPresenceCondition));
          } else {
            logger.logf(Level.INFO, "%s of %d.", logString, numberOfTestGoals);
          }

          // TODO: enable tiger techniques for multi-goal generation in one run
          //        if (lGoalPrediction != null && lGoalPrediction[goal.getIndex() - 1] == Prediction.INFEASIBLE) {
          //          // GoalPrediction does not use the target presence condition (remainingPCforGoalCoverage)
          //          // I think this is OK (any infeasible goal will be even more infeasible when restricted with a certain pc)
          //          // TODO: remainingPCforGoalCoverage could perhaps be used to improve precision of the prediction?
          //          logger.logf(Level.INFO, "This goal is predicted as infeasible!");
          //          testsuite.addInfeasibleGoal(goal, goal.getRemainingPresenceCondition(), lGoalPrediction);
          //          continue;
          //        }
          //
          //        NondeterministicFiniteAutomaton<GuardedEdgeLabel> currentAutomaton = goal.getAutomaton();
          //        if (ARTReuse.isDegeneratedAutomaton(currentAutomaton)) {
          //          // current goal is for sure infeasible
          //          logger.logf(Level.INFO, "Test goal infeasible.");
          //          if (useTigerAlgorithm_with_pc) {
          //            logger.logf(Level.WARNING, "Goal %d is infeasible for remaining PC %s !", goal.getIndex(),
          //                bddCpaNamedRegionManager.dumpRegion(goal.getInfeasiblePresenceCondition()));
          //          }
          //          testsuite.addInfeasibleGoal(goal, goal.getRemainingPresenceCondition(), lGoalPrediction);
          //          continue; // we do not want to modify the ARG for the degenerated automaton to keep more reachability information
          //        }
          //
          if (checkCoverage) {
            for (Goal goalToBeChecked : goalsToBeProcessed) {
              if (isCovered(goalToBeChecked)) {
                if (lGoalPrediction != null) {
                  lGoalPrediction[goalToBeChecked.getIndex() - 1] = Prediction.FEASIBLE;
                }
              }
            }
          }

          if (testsuite.areGoalsCoveredOrInfeasible(goalsToBeProcessed)) {
            continue;
          }

          // goal is uncovered so far; run CPAchecker to cover it
          ReachabilityAnalysisResult result =
              runReachabilityAnalysis(goalsToBeProcessed, previousAutomaton, pInfeasibilityPropagation);
          if (result.equals(ReachabilityAnalysisResult.UNSOUND)) {
            logger.logf(Level.WARNING, "Analysis run was unsound!");
            wasSound = false;
          }
          //        previousAutomaton = currentAutomaton;

          if (result.equals(ReachabilityAnalysisResult.TIMEDOUT)) {
            break;
          }
        }
      }

      // reprocess timed-out goals
      if (testsuite.getTimedOutGoals().isEmpty()) {
        logger.logf(Level.INFO, "There were no timed out goals.");
        retry = false;
      } else {
        if (!timeoutStrategy.equals(TimeoutStrategy.RETRY_AFTER_TIMEOUT)) {
          logger.logf(Level.INFO, "There were timed out goals but retry after timeout strategy is disabled.");
        } else {
          retry = true;
        }
      }
    } while (retry);

    if (allCoveredGoalsPerTestCase) {
      for (Goal goal : testsuite.getGoals()) {
        isCovered(goal);
      }
    }

    return wasSound;
  }

  private boolean isCovered(Goal pGoal) {
    boolean isFullyCovered = false;
    for (TestCase testcase : testsuite.getTestCases()) {
      ThreeValuedAnswer isCovered = TigerAlgorithm.accepts(pGoal.getAutomaton(), testcase.getErrorPath());
      if (isCovered.equals(ThreeValuedAnswer.UNKNOWN)) {
        logger.logf(Level.WARNING, "Coverage check for goal %d could not be performed in a precise way!",
            pGoal.getIndex());
        continue;
      } else if (isCovered.equals(ThreeValuedAnswer.REJECT)) {
        continue;
      }

      // test goal is already covered by an existing test case
      if (useTigerAlgorithm_with_pc) {
        boolean goalCoveredByTestCase = pGoal.getCoveringTestCases().containsKey(testcase);

        if (!goalCoveredByTestCase) {
          PathIterator pathIterator = testcase.getArgPath().pathIterator();
          while (pathIterator.hasNext()) {
            ARGState state = pathIterator.getAbstractState();
            if (pathIterator.getIndex() != 0) { // get incoming edge is not allowed if index==0
              if (pathIterator.getIncomingEdge().equals(pGoal.getCriticalEdge())) {
                Region goalCriticalStateRegion = BDDUtils.getRegionFromWrappedBDDstate(state);
                if (goalCriticalStateRegion != null) {
                  if (allCoveredGoalsPerTestCase || !bddCpaNamedRegionManager
                      .makeAnd(pGoal.getRemainingPresenceCondition(), goalCriticalStateRegion)
                      .isFalse()) { // configurations in testGoalPCtoCover and testcase.pc have a non-empty intersection
                    testsuite.addTestCase(testcase, pGoal, goalCriticalStateRegion);
                    logger.logf(Level.WARNING, "Covered some PCs for Goal %d (%s) for PC %s by existing test case!",
                        pGoal.getIndex(), testsuite.getTestGoalLabel(pGoal),
                        bddCpaNamedRegionManager.dumpRegion(goalCriticalStateRegion));
                    logger.logf(Level.WARNING, "Remaining PC %s!",
                        bddCpaNamedRegionManager.dumpRegion(pGoal.getRemainingPresenceCondition()));
                  }
                }
              }
            }
            pathIterator.advance();
          }
        }
      } else {
        testsuite.addTestCase(testcase, pGoal, null);
        logger.logf(Level.WARNING, "Covered Goal %d (%s) by existing test case!", pGoal.getIndex(),
            testsuite.getTestGoalLabel(pGoal));
        if (!allCoveredGoalsPerTestCase) { return true; }
      }
    }

    return isFullyCovered;
  }

  public static ThreeValuedAnswer accepts(NondeterministicFiniteAutomaton<GuardedEdgeLabel> pAutomaton,
      List<CFAEdge> pCFAPath) {
    Set<NondeterministicFiniteAutomaton.State> lCurrentStates = new HashSet<>();
    Set<NondeterministicFiniteAutomaton.State> lNextStates = new HashSet<>();

    lCurrentStates.add(pAutomaton.getInitialState());

    boolean lHasPredicates = false;

    for (CFAEdge lCFAEdge : pCFAPath) {
      for (NondeterministicFiniteAutomaton.State lCurrentState : lCurrentStates) {
        // Automaton accepts as soon as it sees a final state (implicit self-loop)
        if (pAutomaton.getFinalStates().contains(lCurrentState)) { return ThreeValuedAnswer.ACCEPT; }

        for (NondeterministicFiniteAutomaton<GuardedEdgeLabel>.Edge lOutgoingEdge : pAutomaton
            .getOutgoingEdges(lCurrentState)) {
          GuardedEdgeLabel lLabel = lOutgoingEdge.getLabel();

          if (lLabel.hasGuards()) {
            lHasPredicates = true;
          } else {
            if (lLabel.contains(lCFAEdge)) {
              lNextStates.add(lOutgoingEdge.getTarget());
            }
          }
        }
      }

      lCurrentStates.clear();

      Set<NondeterministicFiniteAutomaton.State> lTmp = lCurrentStates;
      lCurrentStates = lNextStates;
      lNextStates = lTmp;
    }

    for (NondeterministicFiniteAutomaton.State lCurrentState : lCurrentStates) {
      // Automaton accepts as soon as it sees a final state (implicit self-loop)
      if (pAutomaton.getFinalStates().contains(lCurrentState)) { return ThreeValuedAnswer.ACCEPT; }
    }

    if (lHasPredicates) {
      return ThreeValuedAnswer.UNKNOWN;
    } else {
      return ThreeValuedAnswer.REJECT;
    }
  }

  enum ReachabilityAnalysisResult {
    SOUND,
    UNSOUND,
    TIMEDOUT
  }

  private ReachabilityAnalysisResult runReachabilityAnalysis(Set<Goal> pTestGoalsToBeProcessed,
      NondeterministicFiniteAutomaton<GuardedEdgeLabel> pPreviousGoalAutomaton,
      Pair<Boolean, LinkedList<Edges>> pInfeasibilityPropagation)
          throws CPAException, InterruptedException, InvalidConfigurationException {

    ARGCPA lARTCPA = composeCPA(pTestGoalsToBeProcessed);

    Preconditions.checkState(lARTCPA.getWrappedCPAs().get(0) instanceof CompositeCPA,
        "CPAcheckers automata should be used! The assumption is that the first component is the automata for the current goal!");
    Preconditions.checkState(
        ((CompositeCPA) lARTCPA.getWrappedCPAs().get(0)).getWrappedCPAs().get(0) instanceof ControlAutomatonCPA
        || ((CompositeCPA) lARTCPA.getWrappedCPAs().get(0)).getWrappedCPAs().get(0) instanceof PowersetAutomatonCPA,
        "CPAcheckers automata should be used! The assumption is that the first component is the automata for the current goal!");

    // TODO: enable tiger techniques for multi-goal generation in one run
    //    if (reuseARG && (reachedSet != null)) {
    //      reuseARG(pTestGoalsToBeProcessed, pPreviousGoalAutomaton, lARTCPA);
    //    } else {
    initializeReachedSet(lARTCPA);
    //    }

    ShutdownNotifier algNotifier = ShutdownNotifier.createWithParent(startupConfig.getShutdownNotifier());
    startupConfig.getConfig();

    Region lRemainingPresenceCondition =
        BDDUtils.composeRemainingPresenceConditions(pTestGoalsToBeProcessed, bddCpaNamedRegionManager);

    Algorithm algorithm = initializeAlgorithm(lRemainingPresenceCondition, lARTCPA, algNotifier);

    ReachabilityAnalysisResult algorithmStatus =
        runReachabilityAnalysis(pTestGoalsToBeProcessed, lARTCPA, pInfeasibilityPropagation,
            lRemainingPresenceCondition, algNotifier, algorithm);

    if (printARGperGoal) {
      for (Goal goal : pTestGoalsToBeProcessed) {
        Path argFile = Paths.get("output", "ARG_goal_" + goal.getIndex() + ".dot");

        try (Writer w = Files.openOutputFile(argFile)) {
          ARGUtils.writeARGAsDot(w, (ARGState) reachedSet.getFirstState());
        } catch (IOException e) {
          logger.logUserException(Level.WARNING, e, "Could not write ARG to file");
        }
      }
    }

    return algorithmStatus;
  }

  private ReachabilityAnalysisResult runReachabilityAnalysis(Set<Goal> pTestGoalsToBeProcessed,
      ARGCPA pARTCPA, Pair<Boolean, LinkedList<Edges>> pInfeasibilityPropagation, Region pRemainingPresenceCondition,
      ShutdownNotifier algNotifier, Algorithm algorithm)
          throws CPAException, InterruptedException, CPAEnabledAnalysisPropertyViolationException {
    ReachabilityAnalysisResult algorithmStatus;

    do {
      if (cpuTimelimitPerGoal < 0) {
        // run algorithm without time limit
        if (algorithm.run(reachedSet).isSound()) {
          algorithmStatus = ReachabilityAnalysisResult.SOUND;
        } else {
          algorithmStatus = ReachabilityAnalysisResult.UNSOUND;
        }
      } else {
        // run algorithm with time limit
        WorkerRunnable workerRunnable = new WorkerRunnable(algorithm, reachedSet, cpuTimelimitPerGoal, algNotifier);

        Thread workerThread = new Thread(workerRunnable);

        workerThread.start();
        workerThread.join();

        if (workerRunnable.throwableWasCaught()) {
          // TODO: handle exception
          algorithmStatus = ReachabilityAnalysisResult.UNSOUND;
          //        throw new RuntimeException(workerRunnable.getCaughtThrowable());
        } else {
          if (workerRunnable.analysisWasSound()) {
            algorithmStatus = ReachabilityAnalysisResult.SOUND;
          } else {
            algorithmStatus = ReachabilityAnalysisResult.UNSOUND;
          }

          if (workerRunnable.hasTimeout()) {
            algorithmStatus = ReachabilityAnalysisResult.TIMEDOUT;
          }
        }
      }

      if (algorithmStatus != ReachabilityAnalysisResult.TIMEDOUT) {
        // TODO: enable tiger techniques for multi-goal generation in one run
        handleCounterexample(pTestGoalsToBeProcessed, pRemainingPresenceCondition, pARTCPA, pInfeasibilityPropagation);

        if (reachedSet.hasWaitingState()) {

          if (useTigerAlgorithm_with_pc) {
            Region remainingPC =
                BDDUtils.composeRemainingPresenceConditions(pTestGoalsToBeProcessed, bddCpaNamedRegionManager);
            restrictBdd(remainingPC);

            // TODO: exclude goals in combination with the presence condition corresponding to the already covered test cases from further exploration

          } else {
            // Exclude covered goals from further exploration
            Set<Property> toBlacklist = Sets.newHashSet();
            for (Goal goal : pTestGoalsToBeProcessed) {
              if (testsuite.isGoalCoveredOrInfeasible(goal)) {
                toBlacklist.add(goal);
              }
            }

            Precisions.disablePropertiesForWaitlist(pARTCPA, reachedSet, toBlacklist);
          }
        }
      }
    } while ((reachedSet.hasWaitingState() && !testsuite.areGoalsCoveredOrInfeasible(pTestGoalsToBeProcessed))
        && (algorithmStatus != ReachabilityAnalysisResult.TIMEDOUT));

    if (algorithmStatus == ReachabilityAnalysisResult.TIMEDOUT) {
      logger.logf(Level.INFO, "Test goal timed out!");
      testsuite.addTimedOutGoals(pTestGoalsToBeProcessed);
    } else {
      // set test goals infeasible
      for (Goal goal : pTestGoalsToBeProcessed) {
        if (!testsuite.isGoalCovered(goal)) {
          handleInfeasibleTestGoal(goal, pInfeasibilityPropagation);
        }
      }
    }

    return algorithmStatus;
  }

  private void restrictBdd(Region pRemainingPresenceCondition) {
    // inject goal Presence Condition in BDDCPA
    BDDCPA bddcpa = null;
    if (cpa instanceof WrapperCPA) {
      // must be non-null, otherwise Exception in constructor of this class
      bddcpa = ((WrapperCPA) cpa).retrieveWrappedCpa(BDDCPA.class);
    } else if (cpa instanceof BDDCPA) {
      bddcpa = (BDDCPA) cpa;
    }
    if (bddcpa.getTransferRelation() instanceof BDDTransferRelation) {
      ((BDDTransferRelation) bddcpa.getTransferRelation()).setGlobalConstraint(pRemainingPresenceCondition);
      logger.logf(Level.INFO, "Restrict BDD to %s.",
          bddCpaNamedRegionManager.dumpRegion(pRemainingPresenceCondition));
    }
  }

  private Algorithm initializeAlgorithm(Region pRemainingPresenceCondition, ARGCPA lARTCPA,
      ShutdownNotifier algNotifier) throws CPAException {
    Algorithm algorithm;
    try {
      Configuration internalConfiguration = Configuration.builder().loadFromFile(algorithmConfigurationFile).build();

      CoreComponentsFactory coreFactory = new CoreComponentsFactory(internalConfiguration, logger, algNotifier);

      ARGPathExporter argPathExporter = new ARGPathExporter(config, logger, cfa.getMachineModel(), cfa.getLanguage());

      algorithm = coreFactory.createAlgorithm(lARTCPA, programDenotation, cfa, stats);

      if (algorithm instanceof CEGARAlgorithm) {
        CEGARAlgorithm cegarAlg = (CEGARAlgorithm) algorithm;

        Refiner refiner = cegarAlg.getRefiner();
        if (refiner instanceof PredicateCPARefiner) {
          PredicateCPARefiner predicateRefiner = (PredicateCPARefiner) refiner;

          if (reusePredicates) {
            RefinementStrategy strategy = predicateRefiner.getStrategy();
            assert (strategy instanceof PredicateAbstractionRefinementStrategy);

            PredicateAbstractionRefinementStrategy refinementStrategy =
                (PredicateAbstractionRefinementStrategy) strategy;
            refinementStrategy.setPrecisionCallback(this);
          }
        }

        ARGStatistics lARTStatistics;
        try {
          lARTStatistics =
              new ARGStatistics(internalConfiguration, logger, lARTCPA,
                  cfa.getMachineModel(), cfa.getLanguage(), null, argPathExporter);
        } catch (InvalidConfigurationException e) {
          throw new RuntimeException(e);
        }
        Set<Statistics> lStatistics = new HashSet<>();
        lStatistics.add(lARTStatistics);
        cegarAlg.collectStatistics(lStatistics);
      }

      if (useTigerAlgorithm_with_pc) {
        restrictBdd(pRemainingPresenceCondition);
      }
    } catch (IOException | InvalidConfigurationException e) {
      throw new RuntimeException(e);
    }
    return algorithm;
  }

  private void initializeReachedSet(ARGCPA lARTCPA) {
    reachedSet = new LocationMappedReachedSet(Waitlist.TraversalMethod.BFS); // TODO why does TOPSORT not exist anymore?

    AbstractState lInitialElement =
        lARTCPA.getInitialState(cfa.getMainFunction(), StateSpacePartition.getDefaultPartition());
    Precision lInitialPrecision =
        lARTCPA.getInitialPrecision(cfa.getMainFunction(), StateSpacePartition.getDefaultPartition());

    reachedSet.add(lInitialElement, lInitialPrecision);

    outsideReachedSet.add(lInitialElement, lInitialPrecision);

    if (reusePredicates) {
      // initialize reused predicate precision
      PredicateCPA predicateCPA = lARTCPA.retrieveWrappedCpa(PredicateCPA.class);

      if (predicateCPA != null) {
        reusedPrecision = (PredicatePrecision) predicateCPA.getInitialPrecision(cfa.getMainFunction(),
            StateSpacePartition.getDefaultPartition());
      } else {
        logger.logf(Level.INFO, "No predicate CPA available to reuse predicates!");
      }
    }
  }

  private void reuseARG(Goal pGoal, NondeterministicFiniteAutomaton<GuardedEdgeLabel> pPreviousGoalAutomaton,
      ARGCPA lARTCPA) {
    ARTReuse.modifyReachedSet(reachedSet, cfa.getMainFunction(), lARTCPA, 0, pPreviousGoalAutomaton,
        pGoal.getAutomaton());

    // reusedPrecision == null indicates that there is no PredicateCPA
    if (reusePredicates && reusedPrecision != null) {
      for (AbstractState lWaitlistElement : reachedSet.getWaitlist()) {
        Precision lOldPrecision = reachedSet.getPrecision(lWaitlistElement);
        Precision lNewPrecision =
            Precisions.replaceByType(lOldPrecision, reusedPrecision, Predicates.instanceOf(PredicatePrecision.class));

        reachedSet.updatePrecision(lWaitlistElement, lNewPrecision);
      }
    }
  }

  private void handleCounterexample(Set<Goal> pTestGoalsToBeProcessed, Region pRemainingPresenceCondition,
      ARGCPA lARTCPA, Pair<Boolean, LinkedList<Edges>> pInfeasibilityPropagation) {

    if (!reachedSet.hasWaitingState() || reachedSet.getLastState() == null) {
      return;
      // TODO: Infeasible goal
      //  lastState might be null if a new iteration with the same set reached added found no new states
    }

    for (Goal goal : pTestGoalsToBeProcessed) {
      if (testsuite.isGoalCovered(goal) || testsuite.isGoalInfeasible(goal)) {
        continue;
      }

      // TODO check whether a last state might remain from an earlier run and a reuse of the ARG
      AbstractState lastState = reachedSet.getLastState();

      if (lastState == null) { throw new RuntimeException(
          "We need a last state to determine the feasibility of the test goal!"); }

      // TODO: Last state is not the target state anymore. AbstractStates.containsTargetState(reachedSet)?????
      //    if (AbstractStates.isTargetState(lastState)) {
      if (AbstractStates.containsTargetState(reachedSet)) {
        // TODO add missing soundness checks!

        // can we obtain a counterexample to check coverage for other test goals?
        Map<ARGState, CounterexampleInfo> counterexamples = lARTCPA.getCounterexamples();

        if (counterexamples.isEmpty()) {
          // TODO: handle empty counter example with presence conditions
          logger.logf(Level.INFO, "Counterexample is not available.");

          handleFeasibleTestGoalWithoutCounterExample(goal, lastState);
        } else {
          // test goal is feasible
          handleFeasibleTestGoalWithCounterExample(goal, counterexamples, pInfeasibilityPropagation);
        }
      } else {
        // TODO: is it possible to say something about infeasibility at this point of the analysis?
        // we consider the test goal is infeasible
//        logger.logf(Level.INFO, "Test goal infeasible.");
//        handleInfeasibleTestGoal(pGoal, pRemainingPresenceCondition, pInfeasibilityPropagation);
      }
    }
  }

  private void handleFeasibleTestGoalWithCounterExample(Goal pGoal,
      Map<ARGState, CounterexampleInfo> counterexamples,
      Pair<Boolean, LinkedList<Edges>> pInfeasibilityPropagation) {
    logger.logf(Level.INFO, "Counterexample is available.");

    for (Map.Entry<ARGState, CounterexampleInfo> lEntry : counterexamples.entrySet()) {
      CounterexampleInfo cex = lEntry.getValue();

      if (cex.isSpurious()) {
        logger.logf(Level.WARNING, "Counterexample is spurious!");
      } else {
        RichModel model = cex.getTargetPathModel();
        List<BigInteger> inputValues = calculateInputValues(model);

        if (useTigerAlgorithm_with_pc) {
          /* We could determine regions for coverage goals reached earlier during execution of the test case.
          * Now we can't because cex */
          Region testCaseFinalRegion = BDDUtils.getRegionFromWrappedBDDstate(reachedSet.getLastState());
          logger.logf(Level.INFO,
              "New test case (goal: " + pGoal.getIndex() + ") with " + (testCaseFinalRegion == null ? "(final)" : "(critical)")
                  + " PC "
                  + bddCpaNamedRegionManager.dumpRegion((testCaseFinalRegion == null
                      ? testCaseFinalRegion : testCaseFinalRegion)));

          TestCase testcase = new TestCase(testCaseId++, inputValues, cex.getTargetPath(), cex.getTargetPath().getInnerEdges(),
              (testCaseFinalRegion == null ? testCaseFinalRegion : testCaseFinalRegion), bddCpaNamedRegionManager,
              getCpuTime());

          PathIterator pathIterator = cex.getTargetPath().pathIterator();
          while (pathIterator.hasNext()) {
            ARGState state = pathIterator.getAbstractState();
            if (pathIterator.getIndex() != 0) { // get incoming edge is not allowed if index==0
              if (pathIterator.getIncomingEdge().equals(pGoal.getCriticalEdge())) {
                Region goalCriticalStateRegion = BDDUtils.getRegionFromWrappedBDDstate(state);
                if (goalCriticalStateRegion != null) {
                  testsuite.addTestCase(testcase, pGoal, goalCriticalStateRegion);
                  logger.logf(Level.WARNING, "Covered some PCs for Goal %d (%s) for PC %s!",
                      pGoal.getIndex(), testsuite.getTestGoalLabel(pGoal),
                      bddCpaNamedRegionManager.dumpRegion(goalCriticalStateRegion));
                  logger.logf(Level.WARNING, "Remaining PC %s!",
                      bddCpaNamedRegionManager.dumpRegion(pGoal.getRemainingPresenceCondition()));
                  break;
                }
              }
            }
            pathIterator.advance();
          }
        } else {
          for (CFAEdge lCFAEdge : cex.getTargetPath().getInnerEdges()) {
            if (lCFAEdge != null && lCFAEdge.equals(pGoal.getCriticalEdge())) {
              // we consider the test goal as feasible
              logger.logf(Level.INFO, "Test goal is feasible.");
              logger.logf(Level.WARNING, "Covered Goal %d (%s)!",
                  pGoal.getIndex(), testsuite.getTestGoalLabel(pGoal));

              logger.logf(Level.INFO, "*********************** extract abstract state ***********************");

              TestCase testcase = new TestCase(testCaseId++, inputValues, cex.getTargetPath(), cex.getTargetPath().getInnerEdges(),
                  null, null, getCpuTime());
              testsuite.addTestCase(testcase, pGoal, null);

              if (lGoalPrediction != null) {
                lGoalPrediction[pGoal.getIndex() - 1] = Prediction.FEASIBLE;
              }
              break;
            }
          }
        }
      }
    }
  }

  private void handleFeasibleTestGoalWithoutCounterExample(Goal pGoal, AbstractState lastState) {
    LinkedList<CFAEdge> trace = new LinkedList<>();

    // Try to reconstruct a trace in the ARG and shrink it
    ARGState argState = AbstractStates.extractStateByType(lastState, ARGState.class);
    ARGPath path = ARGUtils.getOnePathTo(argState);

    Collection<ARGState> parents;
    parents = argState.getParents();

    Region testCaseCriticalStateRegion = null;
    while (!parents.isEmpty()) {
      //assert (parents.size() == 1);
      /*if (parents.size() != 1) {
        throw new RuntimeException();
      }*/

      ARGState parent = null;

      for (ARGState tmp_parent : parents) {
        parent = tmp_parent;
        break; // we just choose some parent
      }

      CFAEdge edge = parent.getEdgeToChild(argState);
      trace.addFirst(edge);

      // TODO Alex?
      if (edge.equals(pGoal.getCriticalEdge())) {
        if (useTigerAlgorithm_with_pc) {
          testCaseCriticalStateRegion = BDDUtils.getRegionFromWrappedBDDstate(argState);
        }
        logger.logf(Level.INFO, "*********************** extract abstract state ***********************");
      }

      argState = parent;
      parents = argState.getParents();
    }

    // TODO we need a different way to obtain input values
    List<BigInteger> inputValues = new ArrayList<>();

    Region testCaseFinalRegion = null;
    if (useTigerAlgorithm_with_pc) {
      testCaseFinalRegion = BDDUtils.getRegionFromWrappedBDDstate(lastState);
      logger.logf(
          Level.INFO,
          " generated test case with "
              + (testCaseCriticalStateRegion == null ? "(final)" : "(critical)")
              + " PC "
              + bddCpaNamedRegionManager.dumpRegion((testCaseCriticalStateRegion == null ? testCaseFinalRegion
                  : testCaseCriticalStateRegion)));
    }

    TestCase testcase =
        new TestCase(testCaseId++, inputValues, path, trace, (testCaseCriticalStateRegion == null
            ? testCaseFinalRegion : testCaseCriticalStateRegion), bddCpaNamedRegionManager, getCpuTime());
    testsuite.addTestCase(testcase, pGoal, testCaseCriticalStateRegion);
  }

  private void handleInfeasibleTestGoal(Goal pGoal, Pair<Boolean, LinkedList<Edges>> pInfeasibilityPropagation) {
    if (lGoalPrediction != null) {
      lGoalPrediction[pGoal.getIndex() - 1] = Prediction.INFEASIBLE;
    }

    if (useTigerAlgorithm_with_pc) {
      testsuite.addInfeasibleGoal(pGoal, pGoal.getRemainingPresenceCondition(), lGoalPrediction);
      pGoal.setInfeasiblePresenceCondition(pGoal.getRemainingPresenceCondition());
      logger.logf(Level.WARNING, "Goal %d is infeasible for remaining PC %s !", pGoal.getIndex(),
          bddCpaNamedRegionManager.dumpRegion(pGoal.getRemainingPresenceCondition()));
    } else {
      logger.logf(Level.WARNING, "Goal %d is infeasible!", pGoal.getIndex());
      testsuite.addInfeasibleGoal(pGoal, null, lGoalPrediction);
    }

    // TODO add missing soundness checks!
    if (pInfeasibilityPropagation.getFirst()) {
      logger.logf(Level.INFO, "Do infeasibility propagation!");
      HashSet<CFAEdge> lTargetEdges = new HashSet<>();
      ClusteredElementaryCoveragePattern lClusteredPattern =
          (ClusteredElementaryCoveragePattern) pGoal.getPattern();
      ListIterator<ClusteredElementaryCoveragePattern> lRemainingPatterns =
          lClusteredPattern.getRemainingElementsInCluster();
      int lTmpIndex = pGoal.getIndex() - 1; // caution lIndex starts at 0
      while (lRemainingPatterns.hasNext()) {
        Prediction lPrediction = lGoalPrediction[lTmpIndex];
        ClusteredElementaryCoveragePattern lRemainingPattern = lRemainingPatterns.next();
        if (lPrediction.equals(Prediction.UNKNOWN)) {
          lTargetEdges.add(lRemainingPattern.getLastSingletonCFAEdge());
        }

        lTmpIndex++;
      }
      Collection<CFAEdge> lFoundEdges =
          InfeasibilityPropagation.dfs2(lClusteredPattern.getCFANode(),
              lClusteredPattern.getLastSingletonCFAEdge(), lTargetEdges);
      lRemainingPatterns = lClusteredPattern.getRemainingElementsInCluster();
      lTmpIndex = pGoal.getIndex() - 1;
      while (lRemainingPatterns.hasNext()) {
        Prediction lPrediction = lGoalPrediction[lTmpIndex];
        ClusteredElementaryCoveragePattern lRemainingPattern = lRemainingPatterns.next();
        if (lPrediction.equals(Prediction.UNKNOWN)) {
          if (!lFoundEdges.contains(lRemainingPattern.getLastSingletonCFAEdge())) {
            //mFeasibilityInformation.setStatus(lTmpIndex+1, FeasibilityInformation.FeasibilityStatus.INFEASIBLE);
            // TODO remove ???
            lGoalPrediction[lTmpIndex] = Prediction.INFEASIBLE;
          }
        }
        lTmpIndex++;
      }
    }
  }

  private List<BigInteger> calculateInputValues(RichModel model) {
    Comparator<Map.Entry<AssignableTerm, Object>> comp =
        new Comparator<Map.Entry<AssignableTerm, Object>>() {

          @Override
          public int compare(Entry<AssignableTerm, Object> pArg0, Entry<AssignableTerm, Object> pArg1) {
            assert pArg0.getKey().getName().equals(pArg1.getKey().getName());
            assert pArg0.getKey() instanceof AssignableTerm.Variable;
            assert pArg1.getKey() instanceof AssignableTerm.Variable;

            AssignableTerm.Variable v0 = (AssignableTerm.Variable) pArg0.getKey();
            AssignableTerm.Variable v1 = (AssignableTerm.Variable) pArg1.getKey();

            int v0ssa = new Integer((v0.getName().substring(v0.getName().indexOf("@") + 1)));
            int v1ssa = new Integer((v1.getName().substring(v1.getName().indexOf("@") + 1)));
            return v0ssa - v1ssa;
          }

        };

    TreeSet<Map.Entry<AssignableTerm, Object>> inputs = new TreeSet<>(comp);

    for (Entry<AssignableTerm, Object> e : model.entrySet()) {
      if (e.getKey() instanceof AssignableTerm.Variable) {
        AssignableTerm.Variable v = (AssignableTerm.Variable) e.getKey();

        if (v.getName().startsWith(WrapperUtil.CPAtiger_INPUT + "::__retval__")) {
          inputs.add(e);
        }
      }
    }

    List<BigInteger> inputValues = new ArrayList<>(inputs.size());

    for (Entry<AssignableTerm, Object> e : inputs) {
      //assert e.getValue() instanceof BigInteger;
      //inputValues.add((BigInteger)e.getValue());
      inputValues.add(new BigInteger(e.getValue().toString()));
    }
    return inputValues;
  }

  private void dumpAutomaton(Automaton pA) {
    try (Writer w = Files.openOutputFile(dumpGoalAutomataTo.getPath(pA.getName()))) {

      pA.writeDotFile(w);

    } catch (IOException e) {
      logger.logUserException(Level.WARNING, e, "Could not write the automaton to DOT file");
    }
  }

  private ARGCPA composeCPA(Set<Goal> pGoalsToBeProcessed) throws CPAException, InvalidConfigurationException {

    Preconditions.checkArgument(cpa instanceof ARGCPA,
        "Tiger: Only support for ARGCPA implemented for CPA composition!");
    ARGCPA oldArgCPA = (ARGCPA) cpa;

    List<Automaton> componentAutomata = Lists.newArrayList();
    {
      List<Automaton> goalAutomata = Lists.newArrayList();

      for (Goal goal : pGoalsToBeProcessed) {
        final Automaton a = goal.createControlAutomaton();
        goalAutomata.add(a);
        dumpAutomaton(a);
      }

      if (useAutomataCrossProduct) {
        final Automaton productAutomaton;
        try {
          logger.logf(Level.INFO, "Computing the cross product of %d automata.", pGoalsToBeProcessed.size());
          productAutomaton = ReducedAutomatonProduct.productOf(goalAutomata, "GOAL_PRODUCT");
          logger.logf(Level.INFO, "Cross product with %d states.", productAutomaton.getStates().size());
        } catch (InvalidAutomatonException e) {
          throw new CPAException("One of the automata is invalid!", e);
        }

        dumpAutomaton(productAutomaton);
        componentAutomata.add(productAutomaton);
      } else {
        componentAutomata.addAll(goalAutomata);
      }
    }

    logger.logf(Level.INFO, "Analyzing %d test goals with %d observer automata.", pGoalsToBeProcessed.size(), componentAutomata.size());

    Collection<ConfigurableProgramAnalysis> automataCPAs = Lists.newArrayList();

    for (Automaton componentAutomaton: componentAutomata) {

      final CPAFactory automataFactory = usePowerset
          ? PowersetAutomatonCPA.factory()
              : ControlAutomatonCPA.factory();

      automataFactory.setConfiguration(Configuration.copyWithNewPrefix(config, componentAutomaton.getName()));
      automataFactory.setLogger(logger.withComponentName(componentAutomaton.getName()));
      automataFactory.set(cfa, CFA.class);
      automataFactory.set(componentAutomaton, Automaton.class);

      automataCPAs.add(automataFactory.createInstance());
    }

    // Add one automata CPA for each goal
    LinkedList<ConfigurableProgramAnalysis> lComponentAnalyses = new LinkedList<>();
    lComponentAnalyses.addAll(automataCPAs);
    lComponentAnalyses.addAll(oldArgCPA.getWrappedCPAs());

    final ARGCPA result;

    try {
      // create composite CPA
      CPAFactory lCPAFactory = CompositeCPA.factory();
      lCPAFactory.setChildren(lComponentAnalyses);
      lCPAFactory.setConfiguration(startupConfig.getConfig());
      lCPAFactory.setLogger(logger);
      lCPAFactory.set(cfa, CFA.class);

      ConfigurableProgramAnalysis lCPA = lCPAFactory.createInstance();

      // create ARG CPA
      CPAFactory lARTCPAFactory = ARGCPA.factory();
      lARTCPAFactory.set(cfa, CFA.class);
      lARTCPAFactory.setChild(lCPA);
      lARTCPAFactory.setConfiguration(startupConfig.getConfig());
      lARTCPAFactory.setLogger(logger);

      result = (ARGCPA) lARTCPAFactory.createInstance();

    } catch (InvalidConfigurationException | CPAException e) {
      throw new RuntimeException(e);
    }

    return result;
  }

  @Override
  public void collectStatistics(Collection<Statistics> pStatsCollection) {
    pStatsCollection.add(this);
  }

  @Override
  public void printStatistics(PrintStream pOut, Result pResult, ReachedSet pReached) {

    pOut.println("Number of test cases:                              " + testsuite.getNumberOfTestCases());
    pOut.println("Number of test goals:                              " + statistics_numberOfTestGoals);
    pOut.println("Number of processed test goals:                    " + statistics_numberOfProcessedTestGoals);

    Set<Goal> feasibleGoals = null;
    Set<Goal> partiallyFeasibleGoals = null;
    Set<Goal> infeasibleGoals = null;
    Set<Goal> partiallyInfeasibleGoals = null;
    Set<Goal> timedoutGoals = null;
    Set<Goal> partiallyTimedoutGoals = null;

    if (useTigerAlgorithm_with_pc) {
      feasibleGoals = new HashSet<>();
      partiallyFeasibleGoals = new HashSet<>();
      infeasibleGoals = new HashSet<>();
      partiallyInfeasibleGoals = new HashSet<>();
      timedoutGoals = new HashSet<>();
      partiallyTimedoutGoals = new HashSet<>();

      for (Goal goal : testsuite.getGoals()) {
        if (goal.getCoveringTestCases().size() > 0) {
          // goal is feasible
          boolean partiallyFeasible = false;
          if (testsuite.isGoalInfeasible(goal)) {
            // goal is partially feasible
            partiallyInfeasibleGoals.add(goal);
            partiallyFeasible = true;
          }
          if (testsuite.isGoalTimedout(goal)) {
            // goal is partially timedout
            partiallyTimedoutGoals.add(goal);
            partiallyFeasible = true;
          }
          if (partiallyFeasible) {
            // goal is partially feasible
            partiallyFeasibleGoals.add(goal);
          } else {
            // goal feasible
            feasibleGoals.add(goal);
          }
        } else if (testsuite.isGoalInfeasible(goal)) {
          // goal is infeasible
          if (testsuite.isGoalTimedout(goal)) {
            // goal is partially timed out
            partiallyInfeasibleGoals.add(goal);
            partiallyInfeasibleGoals.add(goal);;
          } else {
            // goal is infeasible
            infeasibleGoals.add(goal);
          }
        } else {
          // goal is timedout
          timedoutGoals.add(goal);
        }
      }

      pOut.println("Number of feasible test goals:                     " + feasibleGoals.size());
      pOut.println("Number of partially feasible test goals:           " + partiallyFeasibleGoals.size());
      pOut.println("Number of infeasible test goals:                   " + infeasibleGoals.size());
      pOut.println("Number of partially infeasible test goals:         " + partiallyInfeasibleGoals.size());
      pOut.println("Number of timedout test goals:                     " + timedoutGoals.size());
      pOut.println("Number of partially timedout test goals:           " + partiallyTimedoutGoals.size());

      if (timedoutGoals.size() > 0 || partiallyTimedoutGoals.size() > 0) {
        pOut.println("Timeout occured during processing of a test goal!");
      }
    } else {
      pOut.println("Number of feasible test goals:                     " + testsuite.getNumberOfFeasibleTestGoals());
      pOut.println("Number of infeasible test goals:                   " + testsuite.getNumberOfInfeasibleTestGoals());
      pOut.println("Number of timedout test goals:                     " + testsuite.getNumberOfTimedoutTestGoals());

      if (testsuite.getNumberOfTimedoutTestGoals() > 0) {
        pOut.println("Timeout occured during processing of a test goal!");
      }
    }

    // write generated test suite and mapping to file system
    try (Writer writer = new BufferedWriter(
        new OutputStreamWriter(new FileOutputStream(testsuiteFile.getAbsolutePath()), "utf-8"))) {

      writer.write(testsuite.toString());

    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    // write test case generation times to file system
    try (Writer writer = new BufferedWriter(
        new OutputStreamWriter(new FileOutputStream(testcaseGenerationTimesFile.getAbsolutePath()), "utf-8"))) {

      List<TestCase> testcases = new ArrayList<>(testsuite.getTestCases());
      testcases.sort(new Comparator<TestCase>() {

        @Override
        public int compare(TestCase pTestCase1, TestCase pTestCase2) {
          if (pTestCase1.getGenerationTime() > pTestCase2.getGenerationTime()) {
            return 1;
          } else if (pTestCase1.getGenerationTime() < pTestCase2.getGenerationTime()) { return -1; }
          return 0;
        }
      });

      if (useTigerAlgorithm_with_pc) {
        Set<Goal> feasible = new HashSet<>();
        feasible.addAll(feasibleGoals);
        feasible.addAll(partiallyFeasibleGoals);
        feasible.removeAll(partiallyTimedoutGoals);
        for (Goal goal : feasible) {
          Map<TestCase, Region> tests = goal.getCoveringTestCases();
          TestCase lastTestCase = getLastTestCase(tests.keySet());
          lastTestCase.incrementNumberOfNewlyCoveredGoals();
        }
        Set<Goal> partially = new HashSet<>();
        partially.addAll(feasibleGoals);
        partially.addAll(partiallyFeasibleGoals);
        partially.removeAll(partiallyInfeasibleGoals);
        for (Goal goal : partially) {
          Map<TestCase, Region> tests = goal.getCoveringTestCases();
          TestCase lastTestCase = getLastTestCase(tests.keySet());
          lastTestCase.incrementNumberOfNewlyPartiallyCoveredGoals();
        }

        writer.write("Test Case;Generation Time;Covered Goals After Generation;Completely Covered Goals After Generation;Partially Covered Goals After Generation\n");
        int completelyCoveredGoals = 0;
        int partiallyCoveredGoals = 0;
        for (TestCase testCase : testcases) {
          int newCoveredGoals = testCase.getNumberOfNewlyCoveredGoals();
          int newPartiallyCoveredGoals = testCase.getNumberOfNewlyPartiallyCoveredGoals();
          completelyCoveredGoals += newCoveredGoals;
          partiallyCoveredGoals += newPartiallyCoveredGoals;

          writer.write(testCase.getId() + ";" + testCase.getGenerationTime() + ";"
              + (completelyCoveredGoals + partiallyCoveredGoals) + ";" + completelyCoveredGoals + ";"
              + partiallyCoveredGoals + "\n");
        }
      } else {
        Set<Goal> coveredGoals = new HashSet<>();
        writer.write("Test Case;Generation Time;Covered Goals After Generation\n");
        for (TestCase testCase : testcases) {
          coveredGoals.addAll(testsuite.getTestGoalsCoveredByTestCase(testCase));
          writer.write(testCase.getId() + ";" + testCase.getGenerationTime() + ";" + coveredGoals.size() + "\n");
        }
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private TestCase getLastTestCase(Set<TestCase> pTestCases) {
    TestCase lastTestCase = null;
    for (TestCase testCase : pTestCases) {
      if (lastTestCase == null || testCase.getGenerationTime() < lastTestCase.getGenerationTime()) {
        lastTestCase = testCase;
      }
    }
    return lastTestCase;
  }

}
