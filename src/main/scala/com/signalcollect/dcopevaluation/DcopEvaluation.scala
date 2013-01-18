/*
 *  @author Daniel Strebel
 *  @author Philip Stutz
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

package com.signalcollect.dcopevaluation

import com.signalcollect.evaluation.jobsubmission._
import com.signalcollect.nodeprovisioning.torque._
import com.signalcollect.evaluation.resulthandling._
import com.signalcollect.configuration._
import com.signalcollect._
import com.signalcollect.nodeprovisioning.torque.TorqueNodeProvisioner
import com.signalcollect.graphproviders.synthetic._
import com.signalcollect.nodeprovisioning.local.LocalNodeProvisioner
import com.signalcollect.nodeprovisioning.Node
import com.signalcollect.nodeprovisioning.local.LocalNode
import com.signalcollect.evaluation.algorithms.ChineseWhispersEvaluationRun
import com.signalcollect.evaluation.util.ParallelFileGraphLoader
import com.signalcollect.evaluation.util.GoogleGraphLoader
import com.typesafe.config.Config
import com.signalcollect.approx.flood._
import com.signalcollect.dcopgraphproviders._

/**
 * Runs a PageRank algorithm on a graph of a fixed size
 * for different numbers of worker threads.
 *
 * Evaluation is set to execute on a 'Kraken'-node.
 */
object DcopEvaluation extends App {

  val evalName = "only ela-special 9mil nodes"
  val jvmParameters = "-Xmx64000m -XX:+UseNUMA -XX:+UseCondCardMark -XX:+UseParallelGC"

  val fastEval = new EvaluationSuiteCreator(evaluationName = evalName,
    executionHost =
      //new LocalHost 
      new TorqueHost(torqueHostname = "kraken.ifi.uzh.ch", localJarPath = "./target/signal-collect-dcops-assembly-2.0.0-SNAPSHOT.jar", torqueUsername = System.getProperty("user.name"), priority = TorquePriority.fast)
      )
  val out = new java.io.FileWriter("results.txt")
  val outTime = new java.io.FileWriter("resultsTime.txt")
  var startTime = System.nanoTime()
  val terminationCondition = new DSANGlobalTerminationCondition(out, outTime, startTime)

  val executionConfigAsync = ExecutionConfiguration(ExecutionMode.PureAsynchronous).withSignalThreshold(0.01)/*.withGlobalTerminationCondition(terminationCondition)*/.withTimeLimit(36000000)//1200000)
  val executionConfigSync = ExecutionConfiguration(ExecutionMode.Synchronous).withSignalThreshold(0.01).withTimeLimit(36000000)//1200000)

  
  
  
  val repetitions = 2
  val executionConfigurations = List(executionConfigAsync, executionConfigSync)
  val graphSizes =  //List(3, 4)
    				List(5000)//(10, 100, 1000, 10000)
  
  for (i <- 0 until repetitions) {
    for (executionConfig <- executionConfigurations) {
      for (numberOfColors <- List(50, 20, 12, 10, 8, 6)){//Range(12, 4, -1)) {
        for (graphSize <- graphSizes) {
           for (algorithm <- List(/*new DSANVertexBuilder(" - 0.001 exploration", (time, delta) => 0.001), */ new DSANVertexBuilder("ela-special", ((time, delta) => if ( delta <= 0.01) 0.001 else math.exp(delta * time * time / 1000))))){
            val graphBuilder = new GraphBuilder[Any, Any]()
            fastEval.addJobForEvaluationAlgorithm(new DSANEvaluationRun(algorithm.toString, graphBuilder = graphBuilder, vertexBuilder = algorithm, graphProvider = new ConstraintGridProvider(height = graphSize, width = graphSize, numberOfColors = numberOfColors), executionConfiguration = executionConfig, jvmParams = jvmParameters, reportMemoryStats = true))
          }
        }
      }
    }
  }

  fastEval.setResultHandlers(List(new ConsoleResultHandler(true), new GoogleDocsResultHandler(args(0), args(1), "evaluation_ela", "data")))
  fastEval.runEvaluation
}