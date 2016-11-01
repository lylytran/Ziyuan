/*
 * Copyright (C) 2013 by SUTD (Singapore)
 * All rights reserved.
 *
 * 	Author: SUTD
 *  Version:  $Revision: 1 $
 */

package icsetlv.variable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.IntegerValue;
import com.sun.jdi.Location;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.Value;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.ClassPrepareEvent;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventQueue;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.MethodExitEvent;
import com.sun.jdi.event.VMDeathEvent;
import com.sun.jdi.event.VMDisconnectEvent;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.MethodExitRequest;

import sav.common.core.ModuleEnum;
import sav.common.core.SavException;
import sav.common.core.utils.BreakpointUtils;
import sav.common.core.utils.CollectionUtils;
import sav.strategies.dto.BreakPoint;
import sav.strategies.vm.SimpleDebugger;
import sav.strategies.vm.VMConfiguration;

/**
 * @author LLT
 * This class allows to start a program in debug mode, and add breakpoints
 * in order to collect data.
 */
public abstract class BreakpointDebugger {
	protected static Logger log = LoggerFactory.getLogger(BreakpointDebugger.class);
	private VMConfiguration config;
	protected SimpleDebugger debugger = new SimpleDebugger();
	// map of classes and their breakpoints
	private Map<String, List<BreakPoint>> brkpsMap;
	protected List<BreakPoint> bkps;
	protected boolean buggy;

	public void setup(VMConfiguration config) {
		this.config = config;
	}

	public final void run(List<BreakPoint> brkps) throws SavException {
		this.bkps = brkps;
		this.brkpsMap = BreakpointUtils.initBrkpsMap(brkps);
		/* before debugging */
		beforeDebugging();
		this.config.setDebug(true);
		
		/* start debugger */
		VirtualMachine vm = debugger.run(config);
		if (vm == null) {
			throw new SavException(ModuleEnum.JVM, "cannot start jvm!");
		}
		
		/* add class watch */
		addClassWatch(vm.eventRequestManager());

		/* process debug events */
		EventQueue eventQueue = vm.eventQueue();
		boolean stop = false;
		boolean eventTimeout = false;
		Map<String, BreakPoint> locBrpMap = new HashMap<String, BreakPoint>();
		while (!stop && !eventTimeout) {
			EventSet eventSet;
			try {
				eventSet = eventQueue.remove(3000);
			} catch (InterruptedException e) {
				// do nothing, just return.
				break;
			}
			if (eventSet == null) {
				log.warn("Time out! Cannot get event set!");
				eventTimeout = true;
				break;
			}
			for (Event event : eventSet) {
				if (event instanceof VMDeathEvent
						|| event instanceof VMDisconnectEvent) {
					stop = true;
					break;
				} else if (event instanceof MethodExitEvent) {
					if (((MethodExitEvent) event).location().lineNumber() != 165) {
						continue;
					}
					Value returnValue = ((MethodExitEvent) event).returnValue();					
					//if (returnValue.type().name().equals("org.junit.runner.Result")) {
						ObjectReference result = (ObjectReference) returnValue;
						ObjectReference failList = (ObjectReference) result.getValue(result.referenceType().fieldByName("fFailures"));
						ObjectReference innerList = (ObjectReference) failList.getValue(failList.referenceType().fieldByName("list"));
						if (((IntegerValue)innerList.getValue(innerList.referenceType().fieldByName("size"))).value() != 0) {
							buggy = true;
							stop = true; 
							break;
						}
					//}
				} else if (event instanceof ClassPrepareEvent) {
					// add breakpoint watch on loaded class
					ClassPrepareEvent classPrepEvent = (ClassPrepareEvent) event;
					handleClassPrepareEvent(vm, classPrepEvent);
					/* add breakpoint request */
					ReferenceType refType = classPrepEvent.referenceType();
					// breakpoints
					addBreakpointWatch(vm, refType, locBrpMap);					
				} else if (event instanceof BreakpointEvent) {
					BreakpointEvent bkpEvent = (BreakpointEvent) event;
					BreakPoint bkp = locBrpMap.get(bkpEvent.location()
							.toString());
					handleBreakpointEvent(bkp, vm, bkpEvent);
				}
			}
			eventSet.resume();
		}
		if (!eventTimeout) {
			vm.resume();
			/* wait until the process completes */
			// wait for will cause the process to wait forever, comment it seems works, but may cause some problems.
			//debugger.waitProcessUntilStop();
		}
		/* end of debug */
		afterDebugging();
	}

	/** abstract methods */
	protected abstract void beforeDebugging() throws SavException;
	protected abstract void handleClassPrepareEvent(VirtualMachine vm, ClassPrepareEvent event);
	protected abstract void handleBreakpointEvent(BreakPoint bkp, VirtualMachine vm, BreakpointEvent bkpEvent) throws SavException;
	protected abstract void afterDebugging() throws SavException ;

	/** add watch requests **/
	protected void addClassWatch(EventRequestManager erm) {
		/*MethodExitRequest methodExitRequest = erm.createMethodExitRequest();
		methodExitRequest.addClassFilter("org.junit.runner.JUnitCore");
		methodExitRequest.setSuspendPolicy(EventRequest.SUSPEND_NONE);
		methodExitRequest.enable();*/
		/* add class watch for breakpoints */
		for (String className : brkpsMap.keySet()) {
			addClassWatch(erm, className);
		}
	}

	protected final void addClassWatch(EventRequestManager erm, String className) {
		ClassPrepareRequest classPrepareRequest = erm.createClassPrepareRequest();
		classPrepareRequest.addClassFilter(className);
		classPrepareRequest.setEnabled(true);
		MethodExitRequest methodExitRequest = erm.createMethodExitRequest();
		methodExitRequest.addClassFilter(className);
		methodExitRequest.enable();
	}
	
	private void addBreakpointWatch(VirtualMachine vm, ReferenceType refType,
			Map<String, BreakPoint> locBrpMap) {
		for (BreakPoint brkp : CollectionUtils.initIfEmpty(brkpsMap.get(refType.name()))) {
			Location location = addBreakpointWatch(vm, refType, brkp.getLineNo());
			if (location != null) {
				locBrpMap.put(location.toString(), brkp);
			} else {
				log.warn("Cannot add break point " + brkp);
			}
		}
	}
	
	protected final Location addBreakpointWatch(VirtualMachine vm,
			ReferenceType refType, int lineNumber) {
		List<Location> locations;
		try {
			locations = refType.locationsOfLine(lineNumber);
		} catch (AbsentInformationException e) {
			log.warn(e.getMessage());
			return null;
		}
		if (!locations.isEmpty()) {
			Location location = locations.get(0);
			BreakpointRequest breakpointRequest = vm.eventRequestManager()
					.createBreakpointRequest(location);
			breakpointRequest.setEnabled(true);
			return location;
		} 
		return null;
	}
	
	public String getProccessError() {
		return debugger.getProccessError();
	}
	
	protected VMConfiguration getVmConfig() {
		return config;
	}

	public boolean isBuggy() {
		return buggy;
	}
	
}
