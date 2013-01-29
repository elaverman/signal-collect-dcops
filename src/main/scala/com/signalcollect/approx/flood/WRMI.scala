/*
 *  @author Philip Stutz, Mihaela Verman
 *  
 *  Copyright 2012 University of Zurich
 *      
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *  
 *         http://www.apache.org/licenses/LICENSE-2.0
 *  
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *  
 */

/*
 * Implementation of Weighted Regret Monitoring with Inertia
 * ( Arslan, Marden, Shamma, 2007. "Autonomous vehicle-target assignment: a game theoretical formulation". 
 *  ASME Journal of Dynamic Systems, Measurement and Control 129, 584-596 )
 */

package com.signalcollect.approx.flood

import com.signalcollect._
import com.signalcollect.configuration._
import com.signalcollect.configuration.LoggingLevel._
import scala.util._
import scala.math
import com.signalcollect.interfaces.MessageBus

class WRMIVertexBuilder(algorithmDescription: String, fadingMemory: Double = 0.03, inertia: Double = 0.5) extends ConstraintVertexBuilder {
  def apply(id: Int, constraints: Iterable[Constraint], domain: Array[Int]): Vertex[Any, _] = {
    val r = new Random
    val v = new WRMIVertex(id, domain(r.nextInt(domain.size)), constraints, domain, fadingMemory, inertia)

    for (ctr <- constraints) {
      for (variable <- ctr.variablesList) {
        if (variable != id) {
          v.addEdge(new StateForwarderEdge(variable), null.asInstanceOf[GraphEditor[Any, Any]])
        }
      }
    }

    v
  }

  override def toString = "WRMI - " + algorithmDescription
}


/**
 * Represents an Agent
 *
 *  @param id: the identifier of this vertex
 *  @param constraints: the set of constraints in which it is involved
 *  @param possibleValues: which values can the state take
 */
class WRMIVertex(
  id: Int,
  initialState: Int,
  var constraints: Iterable[Constraint],
  val possibleValues: Array[Int],
  fadingMemory: Double, //0.03 //constant rho for fading memory  - is 1 if we do not take into account memory and only current utility
  inertia: Double,
  eps: Double = 0.0001
  )
  extends DataGraphVertex(id, 0)
  with ApproxBestResponseVertex[Int, Int] {

  type Signal = Int

  var utility: Double = 0
  var neighbourConfig: Map[Any, Int] = _
  var existsBetterStateUtility: Boolean = false

  var weightedAvgDiff: Array[Double] = Array.fill[Double](possibleValues.size)(0)
  var stateRegret: Array[Double] = Array.fill[Double](possibleValues.size)(0)
  var numberSatisfied: Int = 0 //number of satisfied constraints
  var normFactor: Double = 0

  def computeUtility(ownConfig: Int): Double = {
    //Calculate utility and number of satisfied constraints for the current value
    val config = neighbourConfig + (id -> ownConfig)
    constraints.foldLeft(0.0)((a, b) => a + b.utility(config))
  }

  def randomlyPickCandidateStateIndex(distribution: Array[Double], normFactor: Double): Int = {
    val r = new Random()
    val probabilitySel: Double = r.nextDouble()
    var candidateStateIndex: Int = -1

    if (normFactor < eps)
      return state
    var partialSum: Double = 0
    for (i <- 0 to (possibleValues.size - 1)) {
      if (candidateStateIndex == -1) {
        partialSum += distribution(i)
        if (probabilitySel * normFactor <= partialSum) {
          candidateStateIndex = i
        }
      }
    }
    possibleValues(candidateStateIndex)
  }

  /**
   * The collect function chooses a new random state and chooses it if it improves over the old state,
   * or, if it doesn't it still chooses it (for exploring purposes) with probability decreasing with time
   */
  def collect: Int = {

    neighbourConfig = mostRecentSignalMap.map(x => (x._1, x._2)).toMap //neighbourConfigs must be immutable and mostRecentSignalMap is mutable, so we convert
    utility = computeUtility(state)

    //Update the weighted average Diff and state regrets for each action 
    normFactor = 0

    for (i <- 0 to (possibleValues.size - 1)) {
      val regret = computeUtility(possibleValues(i)) - utility
      weightedAvgDiff(i) = fadingMemory * regret + (1 - fadingMemory) * weightedAvgDiff(i)
      stateRegret(i) = if (weightedAvgDiff(i) > eps) weightedAvgDiff(i) else 0
      normFactor += stateRegret(i)
    }

    //Select one of the actions with maximum weighted average utilities as candidate State

   // val candidateStateIndex = 
    val candidateState = randomlyPickCandidateStateIndex(stateRegret, normFactor)

    //only for calculating exists BetterStateUtility to determine NE:
    val maxUtility = computeUtility(computeMaxUtilityState())

    val acceptanceProbability: Double = (new Random).nextDouble()

    if ((acceptanceProbability > inertia) && (candidateState != state)) { // we adopt the new maximum state, else we do not change state
    //  if (id==50)
      println("Vertex: " + id + "; changed to state: " + 
          candidateState + " of regret/utility " + stateRegret(candidateState) + "/" + computeUtility(candidateState) + 
          " instead of old state " + state + " with utility " + utility + "; prob = " + acceptanceProbability + " > inertia =  " + inertia
          +"\n "+weightedAvgDiff.mkString("; ")+"\n"+stateRegret.mkString("; "))
      utility = computeUtility(candidateState)
      existsBetterStateUtility = maxUtility > utility //||((maxUtility==utility)&&(maximumsCount != 1)) //for strict NE. only STRICT NE are absorbing!
      return candidateState
    }

    existsBetterStateUtility = maxUtility > utility //||((maxUtility==utility)&&(maximumsCount != 1)) //for strict NE. only STRICT NE are absorbing!
    return state

  } //end collect function

  
 // override def scoreSignal: Double = 1
  
  override def scoreSignal: Double = {
    lastSignalState match {
      case Some(oldState) =>
        if ((oldState == state) && ((utility == constraints.size)||(normFactor < eps))) { //computation is allowed to stop only if state has not changed and the utility is maximized
          0
        } else {
          1
        }
      case other => 1

    }

  } //end scoreSignal

} //end WRMIVertex class

/** Builds an agents graph and executes the computation */
object WRMI extends App {

  val graph = GraphBuilder.withLoggingLevel(LoggingLevel.Debug).build

  println("From client: Graph built")

  //Simple graph with 2 vertices

  //    val c12:  Constraint = Variable(1) != Variable(2)
  //    
  //    graph.addVertex(new WRMIVertex(1, Array(c12), Array(0, 1)))
  //   	graph.addVertex(new WRMIVertex(2, Array(c12), Array(0, 1)))
  //   
  //   	graph.addEdge(new StateForwarderEdge(1, 2))
  //   	graph.addEdge(new StateForwarderEdge(2, 1))

  //Graph with 6 nodes 

  var constraints: List[Constraint] = List()

  constraints = (Variable(1) != Variable(2)) :: constraints
  constraints = (Variable(1) != Variable(3)) :: constraints
  constraints = (Variable(3) != Variable(2)) :: constraints
  constraints = (Variable(3) != Variable(4)) :: constraints
  constraints = (Variable(5) != Variable(4)) :: constraints
  constraints = (Variable(5) != Variable(3)) :: constraints
  constraints = (Variable(5) != Variable(6)) :: constraints
  constraints = (Variable(6) != Variable(2)) :: constraints

  for (i <- 1 to 6) {
    graph.addVertex(new WRMIVertex(i, 0, constraints.filter(s => s.variablesList().contains(i)).toArray: Array[Constraint], Array(0, 1, 2), 0.03, 0.5))
  }

  for (ctr <- constraints) {
    for (i <- ctr.variablesList()) {
      for (j <- ctr.variablesList()) {
        if (i != j)
          graph.addEdge(i, new StateForwarderEdge(j))
      }
    }
  }

  println("Begin")

  val stats = graph.execute(ExecutionConfiguration().withExecutionMode(ExecutionMode.Synchronous))
  println(stats)
  graph.foreachVertex(println(_))
  graph.shutdown
}
