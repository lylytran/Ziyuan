/*
 * Copyright (C) 2013 by SUTD (Singapore)
 * All rights reserved.
 *
 * 	Author: SUTD
 *  Version:  $Revision: 1 $
 */

package gentest.core.execution;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gentest.core.data.statement.RArrayAssignment;
import gentest.core.data.statement.RArrayConstructor;
import gentest.core.data.statement.RAssignment;
import gentest.core.data.statement.RConstructor;
import gentest.core.data.statement.REvaluationMethod;
import gentest.core.data.statement.Rmethod;
import gentest.core.data.statement.Statement;
import gentest.core.data.statement.StatementVisitor;
import gentest.core.data.variable.ISelectedVariable;
import sav.common.core.utils.Assert;

/**
 * @author LLT
 *
 */
public class VariableRuntimeExecutor implements StatementVisitor {
	protected static Logger log = LoggerFactory.getLogger(VariableRuntimeExecutor.class);
	protected RuntimeData data;
	protected Boolean successful;
	
	public VariableRuntimeExecutor() {
		data = new RuntimeData();
	}
	
	public VariableRuntimeExecutor(int firstVarId) {
		data = new RuntimeData(firstVarId);
	}
	
	public void reset() {
		reset(0);
	} 
	
	
	public void reset(int firstVarId) {
		data.reset();
		data.setFirstVarId(firstVarId);
		successful = true;
	} 
	
	public boolean start(ISelectedVariable receiver) {
		reset();
		if (receiver != null) {
			execute(receiver);
		}
		return successful;
	}

	public boolean execute(ISelectedVariable param) {
		return execute(param.getStmts());
	}
	
	public boolean execute(List<Statement> stmts) {
		for (Statement stmt : stmts) {
			execute(stmt);
		}
		return successful;
	}

	public boolean execute(Statement stmt) {
		try {
			stmt.accept(this);
		} catch(Throwable ex) {
			log.debug(ex.getMessage());
			onFail();
		}
		return successful;
	}

	private void onFail() {
		successful = false;
	}
	
	protected void addExecData(int varId, Object value) {
		data.addExecData(varId, value);
	}
	
	protected Object getExecData(int var) {
		return data.getExecData(var);
	}
	
	/* visitor part */
	@Override
	public boolean visit(RAssignment stmt) {
		addExecData(stmt.getOutVarId(), stmt.getValue());
		return successful;
	}

	@Override
	public boolean visit(RConstructor stmt) {
		List<Object> inputs = new ArrayList<Object>(stmt.getInVarIds().length);
		for (int var : stmt.getInVarIds()) {
			inputs.add(getExecData(var));
		}
		Object newInstance;
		try {
			newInstance = stmt.getConstructor().newInstance(
					(Object[]) inputs.toArray());
			// update data
			for (int i = 0; i < stmt.getInVarIds().length; i++) {
				addExecData(stmt.getInVarIds()[i], inputs.get(i));
			}
			addExecData(stmt.getOutVarId(), newInstance);
		} catch (Throwable e) {
			log.debug(e.getMessage());
			onFail();
		}
		return successful;
	}

	class ReturnValue{
		Object returnedValue = null;
	}
	
	@Override
	public boolean visitRmethod(Rmethod stmt) {
		final List<Object> inputs = new ArrayList<Object>(stmt.getInVarIds().length);
		for (int var : stmt.getInVarIds()) {
			inputs.add(getExecData(var));
		}
		
		final ReturnValue value = new ReturnValue();
		try {
			final Object obj = getExecData(stmt.getReceiverVarId());
			final Method method = stmt.getMethod();
//			System.out.println(method);
			
//			returnedValue = method.invoke(obj, (Object[]) inputs.toArray());
			
			FutureTask<?> theTask = null;
			try{
				theTask = new FutureTask<Object>(new Runnable(){
					@Override
					public void run() {
						try {
							Object returnedValue = method.invoke(obj, (Object[]) inputs.toArray());
							value.returnedValue = returnedValue;
						} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
							e.printStackTrace();
						}
					}
					
				}, null);
				
				new Thread(theTask).start();
				
				/**according to jdk document, the get methods will block if the computation has not yet completed*/
				theTask.get(10L, TimeUnit.SECONDS);
			}
			catch(TimeoutException e){
				e.printStackTrace();
			}
			
			// update data
			for (int i = 0; i < stmt.getInVarIds().length; i++) {
				addExecData(stmt.getInVarIds()[i], inputs.get(i));
			}
			if (stmt.getOutVarId() != Statement.INVALID_VAR_ID) {
				addExecData(stmt.getOutVarId(), value.returnedValue);
			}
		} catch (Throwable e) {
			log.debug(e.getMessage());
			onFail();
		}
		return successful;
	}
	
	@Override
	public boolean visit(REvaluationMethod stmt) {
		visitRmethod(stmt);
		successful = (Boolean) getExecData(stmt.getOutVarId());
		return successful;
	}

	@Override
	public boolean visit(RArrayConstructor arrayConstructor) {
		Class<?> arrayContentType = arrayConstructor.getContentType();
		Object array = Array.newInstance(arrayContentType, arrayConstructor.getSizes());
		
		addExecData(arrayConstructor.getOutVarId(), array);
		return true;
	}

	@Override
	public boolean visit(RArrayAssignment rArrayAssignment) {
		final Object array = getExecData(rArrayAssignment.getArrayVarID());
		final Object element = getExecData(rArrayAssignment.getLocalVariableID());

		int[] location = rArrayAssignment.getIndex();
		Object innerArray = array;
		for (int i = 0; i < location.length - 1; i++) {
			innerArray = Array.get(innerArray, location[i]);
		}
		Array.set(innerArray, location[location.length - 1], element);
		// No need to update exec data
		return true;
	}

	/*----------*/
	public boolean isSuccessful() {
		Assert.assertTrue(successful != null);
		return successful;
	}

}
