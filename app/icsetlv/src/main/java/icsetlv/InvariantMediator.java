/*
 * Copyright (C) 2013 by SUTD (Singapore)
 * All rights reserved.
 *
 * 	Author: SUTD
 *  Version:  $Revision: 1 $
 */

package icsetlv;

import icsetlv.common.dto.BkpInvariantResult;
import icsetlv.common.dto.BreakpointData;
import icsetlv.common.dto.ExecVar;
import icsetlv.sampling.SelectiveSampling;
import icsetlv.variable.DebugValueInstExtractor;
import icsetlv.variable.JunitDebugger;
import icsetlv.variable.TestResultVerifier;
import icsetlv.variable.TestcasesExecutor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import libsvm.core.Machine;
import sav.common.core.SavException;
import sav.common.core.utils.Assert;
import sav.common.core.utils.CollectionUtils;
import sav.common.core.utils.FileUtils;
import sav.common.core.utils.StopTimer;
import sav.common.core.utils.StringUtils;
import sav.strategies.dto.BreakPoint;
import sav.strategies.vm.VMConfiguration;

/**
 * @author LLT
 *
 */
public class InvariantMediator {
	private static boolean LOG_BKP_DATA = true;
	private TestcasesExecutor tcExecutor;
	private Machine machine;
	private SelectiveSampling selectiveSampling;
	private VMConfiguration vmConfig;
	private TestResultVerifier testVerifier;

	public List<BkpInvariantResult> learn(VMConfiguration config, List<String> allTests,
			List<BreakPoint> bkps) throws SavException {
		Assert.assertNotNull(tcExecutor, "TestcasesExecutor cannot be null!");
		Assert.assertNotNull(machine, "machine cannot be null!");
		this.vmConfig = config;
		List<BreakpointData> bkpsData = debugTestAndCollectData(config, allTests, bkps);
		/* prepare before learning */
		vmConfig.setEnableVmLog(false);
		tcExecutor.setjResultFileDeleteOnExit(true);
		testVerifier = new TestResultVerifier(tcExecutor.getjResult());
		tcExecutor.setTestResultVerifier(testVerifier);
		/* learning */
		/*
		 * set time out for tcExecutor (to make sure the process will stop if we
		 * have infinitive loop after instrument)
		 */
		tcExecutor.setTimeout(getMaxExecutionTime(tcExecutor.getTimer()), TimeUnit.MILLISECONDS);
		InvariantLearner learner = new InvariantLearner(this);
		List<BkpInvariantResult> learningResult = learner.learn(bkpsData);
		// reset tcExecutor
		tcExecutor.setValueExtractor(null);
		tcExecutor.setjResultFileDeleteOnExit(false);
		vmConfig.setEnableVmLog(true);
		tcExecutor.setTestResultVerifier(null);
		tcExecutor.setTimeout(JunitDebugger.DEFAULT_TIMEOUT, TimeUnit.SECONDS);
		return learningResult;
	}

	private long getMaxExecutionTime(StopTimer timer) {
		long max = JunitDebugger.DEFAULT_TIMEOUT;
		for (Long execTime : tcExecutor.getTimer().getTimeResults().values()) {
			if (execTime > max) {
				max = execTime;
			}
		}
		return max;
	}

	public List<BreakpointData> debugTestAndCollectData(VMConfiguration config,
			List<String> allTests, List<BreakPoint> bkps) throws SavException {
		ensureTcExecutor();
		tcExecutor.setup(config, allTests);
		return debugTestAndCollectData(bkps);
	}
	
	public List<BreakpointData> debugTestAndCollectData(List<BreakPoint> bkps)
			throws SavException {
		tcExecutor.run(bkps);
		return tcExecutor.getResult();
	}
	
	public List<BreakpointData> instDebugAndCollectData(
			List<BreakPoint> bkps, Map<String, Object> instrVarMap) throws SavException {
		ensureTcExecutor();
		tcExecutor.setValueExtractor(new DebugValueInstExtractor(tcExecutor.getValRetrieveLevel(), instrVarMap));
		List<BreakpointData> result = debugTestAndCollectData(bkps);
		return result;
	}
	
	public void logBkpData(BreakpointData bkpData, List<ExecVar> allVars, String... msg) {
		if (!LOG_BKP_DATA) {
			return;
		}
		BreakPoint bkp = bkpData.getBkp();
		List<Integer> orgLines = bkp.getOrgLineNos();
		StringBuilder sb = new StringBuilder();
		sb.append("\n");
		sb.append(String.format("***********LINE %s (debugLine: %s)*************", 
											orgLines, bkp.getLineNo())); sb.append("\n");
											
		if (CollectionUtils.isNotEmpty(msg)) {
			sb.append(StringUtils.spaceJoin((Object[])msg)).append("\n");
		}
		sb.append("varLabels");sb.append("\n");
		sb.append(allVars);sb.append("\n");
		sb.append("passValues");sb.append("\n");
		sb.append(sav.common.core.utils.StringUtils.join(bkpData.getPassValues(), "\n"));sb.append("\n");
		sb.append("failValues");sb.append("\n");
		sb.append(sav.common.core.utils.StringUtils.join(bkpData.getFailValues(), "\n"));sb.append("\n");
		sb.append("************************");sb.append("\n");
		FileUtils.appendFile("D:/testData.txt", sb.toString());
	}
	
	public void ensureSelectiveSampling() {
		if (selectiveSampling == null) {
			selectiveSampling = new SelectiveSampling(this);
		}
	}
	
	public void ensureTcExecutor() {
		if (tcExecutor == null) {
			tcExecutor = new TestcasesExecutor(DefaultValues.DEBUG_VALUE_RETRIEVE_LEVEL); 
		}
	}
	
	public void setTcExecutor(TestcasesExecutor tcExecutor) {
		this.tcExecutor = tcExecutor;
	}

	public void setMachine(Machine machine) {
		this.machine = machine;
	}
	
	public Machine getMachine() {
		return machine;
	}
}
