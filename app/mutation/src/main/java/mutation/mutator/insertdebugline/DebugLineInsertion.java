/*
 * Copyright (C) 2013 by SUTD (Singapore)
 * All rights reserved.
 *
 * 	Author: SUTD
 *  Version:  $Revision: 1 $
 */

package mutation.mutator.insertdebugline;

import static mutation.mutator.AstNodeFactory.*;
import japa.parser.ast.CompilationUnit;
import japa.parser.ast.Node;
import japa.parser.ast.body.MethodDeclaration;
import japa.parser.ast.expr.Expression;
import japa.parser.ast.expr.LiteralExpr;
import japa.parser.ast.expr.NameExpr;
import japa.parser.ast.expr.ThisExpr;
import japa.parser.ast.stmt.AssertStmt;
import japa.parser.ast.stmt.ExpressionStmt;
import japa.parser.ast.stmt.ReturnStmt;
import japa.parser.ast.stmt.Statement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import mutation.io.DebugLineFileWriter;
import mutation.mutator.AbstractMutationVisitor;
import mutation.parser.ClassDescriptor;
import sav.common.core.utils.CollectionUtils;

/**
 * @author LLT
 *
 */
public class DebugLineInsertion extends AbstractMutationVisitor {
	private String className;
	private List<Integer> lines;
	private ClassDescriptor clazzDesc;
	private Map<Integer, DebugLineData> returnStmts;
	private Map<Integer, Integer> insertMap;
	private int curPos;
	private DebugLineFileWriter fileWriter;
	private MethodDeclaration curMethod;
	
	public void init(String className, ClassDescriptor classDescriptor,
			List<Integer> lines) {
		this.className = className;
		this.clazzDesc = classDescriptor;
		this.lines = lines;
	}

	public DebugLineInsertionResult insert(CompilationUnit cu) {
		insertMap = new HashMap<Integer, Integer>();
		returnStmts = new HashMap<Integer, DebugLineData>();
		curPos = 0;
		cu.accept(this, true);
		// collect data
		List<DebugLineData> data = new ArrayList<DebugLineData>();
		for (Entry<Integer, Integer> entry : insertMap.entrySet()) {
			Statement newStmt = genDummyStmt(entry.getValue());
			data.add(new AddedLineData(entry.getKey(), newStmt));
		}
		data.addAll(returnStmts.values());
		Collections.sort(data, new Comparator<DebugLineData>() {

			@Override
			public int compare(DebugLineData o1, DebugLineData o2) {
				int val1 = o1.getLineNo();
				int anotherVal = o2.getLineNo();
				return (val1 < anotherVal ? -1 : (val1 == anotherVal ? 0 : 1));
			}

		});
		// add more data into the result
		DebugLineInsertionResult result = new DebugLineInsertionResult(className);
		if (fileWriter != null) {
			result.setMutatedFile(fileWriter.write(data, className));
		}
		
		Map<Integer, DebugLineData> mutatedLines = buildMapMutatedLineNumberToLineData(data);
		
		buildMapOldLineToNewLine(result, mutatedLines);
		
		return result;
	}

	private Map<Integer, DebugLineData> buildMapMutatedLineNumberToLineData(
			List<DebugLineData> data) {
		Map<Integer, DebugLineData> mutatedLines = new HashMap<Integer, DebugLineData>();
		for (DebugLineData debugLine : data) {
			mutatedLines.put(debugLine.getLineNo(), debugLine);
		}
		return mutatedLines;
	}

	private void buildMapOldLineToNewLine(DebugLineInsertionResult result,
			Map<Integer, DebugLineData> mutatedLines) {
		int offset = 0;
		Collections.sort(lines);
		for (Integer oldLine : lines) {
			if (mutatedLines.containsKey(oldLine)) {
				int newLine = mutatedLines.get(oldLine).getDebugLine();
				offset += (newLine - oldLine);
				result.mapDebugLine(oldLine, newLine);
			} else {
				int newLine = oldLine + offset;
				result.mapDebugLine(oldLine, newLine);
			}
		}
	}
	
	private Statement genDummyStmt(int nodeBeginLine) {
		AssertStmt newStmt = assertNotNullStmt(expression(""));
		newStmt.setBeginLine(nodeBeginLine + 1);
		return newStmt;
	}
	
	@Override
	protected boolean beforeVisit(Node node) {
		for (Integer lineNo : lines) {
			if (lineNo >= node.getBeginLine() && lineNo <= node.getEndLine()) {
				return true;
			}
		}
		return false;
	}
	
	@Override
	public void visit(MethodDeclaration n, Boolean arg) {
		this.curMethod = n;
		super.visit(n, arg);
	}
	
	@Override
	protected boolean beforeMutate(Node node) {
		if (!(node instanceof Statement)) {
			return false;
		}
		for (int i = curPos; i < lines.size(); i ++) {
			int loc = getCurrentLocation();
			if (loc == node.getBeginLine()) {
				return true;
			}
		}
		return false;
	}
	
	@Override
	public boolean mutate(ExpressionStmt n) {
		insertMap.put(getCurrentLocation(), n.getEndLine());
		curPos++;
		return false;
	}

	private Integer getCurrentLocation() {
		return lines.get(curPos);
	}

	@Override
	public boolean mutate(ReturnStmt n) {
		if (doesReturnStmtNeedMutate(n.getExpr())){
			return false;
		}
			
		List<Node> newNodes = new ArrayList<Node>();
		String newVarName = generateNewVarName();
		newNodes.add(declarationStmt(curMethod.getType(), newVarName, 
				n.getExpr()));
		newNodes.add(returnStmt(nameExpr(newVarName)));
		Integer curLoc = getCurrentLocation();
		returnStmts.put(curLoc, new ReplacedLineData(curLoc, 
				n, newNodes));
		curPos++;
		return false;
	}

	@SuppressWarnings("unchecked")
	private boolean doesReturnStmtNeedMutate(Expression returnExpr) {
		return !CollectionUtils.existIn(returnExpr.getClass(),
				LiteralExpr.class, NameExpr.class, ThisExpr.class);
	}

	/**
	 * TODO: generate and check if the name already existed in current scope.
	 * it's the job of classDesc
	 */
	private String generateNewVarName() {
		return "tzzzzzzuyu";
	}
	
	public void setFileWriter(DebugLineFileWriter fileWriter) {
		this.fileWriter = fileWriter;
	}
}