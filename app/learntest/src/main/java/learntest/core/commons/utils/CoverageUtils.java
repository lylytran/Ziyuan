/*
 * Copyright (C) 2013 by SUTD (Singapore)
 * All rights reserved.
 *
 * 	Author: SUTD
 *  Version:  $Revision: 1 $
 */

package learntest.core.commons.utils;

import java.util.HashMap;
import java.util.Map;

import cfgcoverage.jacoco.analysis.data.CfgCoverage;
import cfgcoverage.jacoco.analysis.data.CfgNode;
import cfgcoverage.jacoco.analysis.data.NodeCoverage;
import sav.common.core.utils.CollectionUtils;

/**
 * @author LLT
 *
 */
public class CoverageUtils {
	private CoverageUtils() {}

	/**
	 * we only need to check the first node of cfg to know if the cfg is covered.
	 */
	public static boolean notCoverAtAll(CfgCoverage cfgcoverage) {
		NodeCoverage nodeCvg = cfgcoverage.getCoverage(cfgcoverage.getCfg().getStartNode());
		if(nodeCvg.getCoveredTcs().isEmpty()) {
			return true;
		}
		return false;
	}

	public static boolean noDecisionNodeIsCovered(CfgCoverage cfgcoverage) {
		for (CfgNode node : cfgcoverage.getCfg().getDecisionNodes()) {
			if (!cfgcoverage.getCoverage(node).getCoveredTcs().isEmpty()) {
				return false;
			}
		}
		return true;
	}
	
	public static double calculateCoverage(CfgCoverage cfgCoverage) {
		int totalBranches = 0;
		int coveredBranches = 0;
		for (CfgNode node : cfgCoverage.getCfg().getDecisionNodes()) {
			totalBranches += CollectionUtils.getSize(node.getBranches());
			coveredBranches += cfgCoverage.getCoverage(node).getCoveredBranches().size();
		}
		return coveredBranches / (double) totalBranches;
	}
	
	/**
	 * build a coverage map from one single cfg coverage.
	 */
	public static Map<String, CfgCoverage> getCfgCoverageMap(CfgCoverage cfgCoverage) {
		Map<String, CfgCoverage> map = new HashMap<String, CfgCoverage>();
		map.put(cfgCoverage.getCfg().getId(), cfgCoverage);
		return map;
	}
	
}
