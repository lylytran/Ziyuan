/*
 * Copyright (C) 2013 by SUTD (Singapore)
 * All rights reserved.
 *
 * 	Author: SUTD
 *  Version:  $Revision: 1 $
 */

package learntest.core.machinelearning;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jacop.core.Domain;

import gentest.junit.TestsPrinter.PrintOption;
import learntest.core.AbstractLearningComponent;
import learntest.core.LearningMediator;
import learntest.core.commons.data.decision.DecisionProbes;
import learntest.core.commons.data.sampling.SamplingResult;
import learntest.core.commons.utils.DomainUtils;
import learntest.core.machinelearning.iface.ISampleExecutor;
import learntest.main.TestGenerator.GentestResult;
import sav.common.core.ModuleEnum;
import sav.common.core.SavException;
import sav.common.core.formula.Eq;
import sav.common.core.utils.StopTimer;
import sav.strategies.dto.execute.value.ExecVar;

/**
 * @author LLT
 *
 */
public class SampleExecutor extends AbstractLearningComponent implements ISampleExecutor<SamplingResult> {
	/* data object in context */
	private DecisionProbes decisionProbes;
	
	public SampleExecutor(LearningMediator mediator, DecisionProbes decisionProbes) {
		super(mediator);
		this.decisionProbes = decisionProbes;
	}
	
	/**
	 * try to run testcases with new selected input for target method.
	 */
	public SamplingResult runSamples(List<List<Eq<?>>> assignments, List<ExecVar> originVars)
			throws SavException {
		StopTimer timer = new StopTimer("runSample");
		timer.start();
		SamplingResult samples = new SamplingResult(decisionProbes);
		List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
		for (List<Eq<?>> valSet : assignments) {
			if (!valSet.isEmpty()) {
				Map<String, Object> varMap = toInstrVarMap(valSet);
				list.add(varMap);
			}
		}
		logSample(list);
		List<Domain[]> domains = DomainUtils.buildSolutionsFromAssignments(assignments, originVars,
				decisionProbes.getTestInputs());
		try {
			timer.newPoint("gentest");
			GentestResult result = getTestGenerator().genTestAccordingToSolutions(domains, originVars, PrintOption.APPEND);
			timer.newPoint("compile");
			mediator.compile(result.getJunitfiles());
			samples.setNewInputData(result.getTestInputs());
			/* run and update coverage */
			timer.newPoint("coverage");
			runCfgCoverage(samples, result.getJunitClassNames());
			samples.updateNewData();
			timer.newPoint("end");
			timer.stop();
			System.out.println(timer.getResults());
		} catch (ClassNotFoundException e) {
			throw new SavException(ModuleEnum.TESTCASE_GENERATION, e, "");
		} catch (IOException e) {
			throw new SavException(ModuleEnum.TESTCASE_GENERATION, e, "");
		}
		return samples;  
	}

	/**
	 * after running coverage, samples will be updated, the original
	 * decisionProbes will be modified as well. 
	 */
	public void runCfgCoverage(SamplingResult samples, List<String> junitClassNames)
			throws SavException, IOException, ClassNotFoundException {
		/* collect coverage and build cfg */
		StopTimer timer = new StopTimer("cfgCoverage");
		timer.newPoint("start cfg coverage");
		mediator.runCoverageForGeneratedTests(samples.getCfgCoverageMap(), junitClassNames);
		timer.newPoint("end cfg coverage");
	}
	
	private void logSample(List<Map<String, Object>> list) {
		if(list != null){
			int size = list.size();
			System.out.println("Running " + size + " data points...");			
		}
	}
	
	private Map<String, Object> toInstrVarMap(List<Eq<?>> assignments) {
		Map<String, Object> map = new HashMap<String, Object>();
		for (Eq<?> asgt : assignments) {
			map.put(asgt.getVar().getLabel(), asgt.getValue());
		}
		return map;
	}

}
