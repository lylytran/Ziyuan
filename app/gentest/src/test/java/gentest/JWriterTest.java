/**
 * Copyright TODO
 */
package gentest;

import gentest.data.Sequence;
import gentest.data.statement.RAssignment;
import gentest.data.statement.Statement;

import japa.parser.ast.CompilationUnit;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import junit.JWriter;

/**
 * @author LLT
 *
 */
public class JWriterTest {

	@Test
	public void testWrite() {
		JWriter writer = new JWriter();
		List<Sequence> methods = new ArrayList<Sequence>();
		Sequence seq = new Sequence();
		Statement stmt1 = new RAssignment(Integer.class, 1);
		seq.getStmts().add(stmt1);
		methods.add(seq );
		CompilationUnit cu = writer.write(methods);
		System.out.println(cu.toString());
	}
}