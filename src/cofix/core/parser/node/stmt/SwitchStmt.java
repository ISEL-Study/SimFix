/**
 * Copyright (C) SEI, PKU, PRC. - All Rights Reserved.
 * Unauthorized copying of this file via any medium is
 * strictly prohibited Proprietary and Confidential.
 * Written by Jiajun Jiang<jiajun.jiang@pku.edu.cn>.
 */
package cofix.core.parser.node.stmt;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Type;

import cofix.core.metric.CondStruct;
import cofix.core.metric.Literal;
import cofix.core.metric.LoopStruct;
import cofix.core.metric.MethodCall;
import cofix.core.metric.NewFVector;
import cofix.core.metric.Operator;
import cofix.core.metric.OtherStruct;
import cofix.core.metric.Variable;
import cofix.core.metric.Variable.USE_TYPE;
import cofix.core.modify.Deletion;
import cofix.core.modify.Insertion;
import cofix.core.modify.Modification;
import cofix.core.parser.NodeUtils;
import cofix.core.parser.node.CodeBlock;
import cofix.core.parser.node.Node;
import cofix.core.parser.node.expr.Expr;

/**
 * @author Jiajun
 * @date Jun 23, 2017
 */
public class SwitchStmt extends Stmt {

	private Expr _expression = null;
	private List<Stmt> _statements = null;
	
	private String _expression_replace = null;
	private String _statements_replace = null;
	
	private int EXPID = 1000;
	
	/**
	 * SwitchStatement:
     *           switch ( Expression )
     *                   { { SwitchCase | Statement } }
 	 * SwitchCase:
     *           case Expression  :
     *           default :
	 */
	public SwitchStmt(int startLine, int endLine, ASTNode node) {
		this(startLine, endLine, node, null);
	}
	
	public SwitchStmt(int startLine, int endLine, ASTNode node, Node parent) {
		super(startLine, endLine, node, parent);
		_nodeType = TYPE.SWSTMT;
	}

	public void setExpression(Expr expression){
		_expression = expression;
	}
	
	public void setStatements(List<Stmt> statements){
		_statements = statements;
	}
	
	@Override
	public boolean match(Node node, Map<String, String> varTrans, Map<String, Type> allUsableVariables, List<Modification> modifications) {
		//두 Node가 match하는 지 확인하고 인자로 주어진 Modification 리스트에 Modification들을 저장, CodeBlockMatcher.match() 내부에서 호출됨
		boolean match = false;
		if(node instanceof SwitchStmt){
			//주어진 Node가 나와 같은 타입인가?
			match = true;
			SwitchStmt other = (SwitchStmt) node; //주어진 Node를 같은 타입으로 형 번환
			modifications.addAll(NodeUtils.listNodeMatching(this, _nodeType, _statements, other._statements, varTrans, allUsableVariables));
			// NodeUtils.listNodeMatching()을 통해 내 하위 statements와 상대의 하위 statements중 match하는 애들이 있는지 본다?
		} else {
			List<Node> children = node.getChildren();
			List<Modification> tmp = new ArrayList<>();
			if(NodeUtils.nodeMatchList(this, children, varTrans, allUsableVariables, tmp)){
				//나 자신과 상대의 자식들 간에 match하는게 있는지 본다?
				match = true;
				modifications.addAll(tmp);
				//상대의 자식들 중 매치하는 모든 자식들과의 modifications를 얻어온다.
			}
			
			if(_statements != null){
				//내 하위의 statements가 있다면
				for(Stmt stmt : _statements){
					tmp = new ArrayList<>();
					if(stmt.match(node, varTrans, allUsableVariables, tmp)){
						//주어진 node와 내 하위 statement가 매치하는지 확인
						match = true;
						modifications.addAll(tmp);
						//모든 modifications 저장
					}
				}
			}
		}
		
		return match;
		//다른 세부 클래스의 Node들(리턴, 심플네임)등은 match안에서 실제로 modification을 만들고 삽입한다.
	}

	@Override
	public boolean adapt(Modification modification) {
		if(modification.getSourceID() == EXPID){
			_expression_replace = modification.getTargetString();
		} else if(modification instanceof Deletion){
			int index = modification.getSourceID();
			if(index > _statements.size()){
				return false;
			}
			StringBuffer stringBuffer = new StringBuffer();
			for(int i = 0; i < _statements.size(); i++){
				if(i == index){
					continue;
				}
				stringBuffer.append(_statements.get(i).toSrcString());
				stringBuffer.append("\n");
			}
			_statements_replace = stringBuffer.toString();
		} else if(modification instanceof Insertion){
			int index = modification.getSourceID();
			if(index > _statements.size()){
				return false;
			}
			StringBuffer stringBuffer = new StringBuffer();
			for(int i = 0; i < _statements.size(); i++){
				if(i == index){
					stringBuffer.append(modification.getTargetString());
					stringBuffer.append("\n");
				}
				stringBuffer.append(_statements.get(i).toSrcString());
				stringBuffer.append("\n");
			}
			_statements_replace = stringBuffer.toString();
		}
		return false;
	}

	@Override
	public boolean restore(Modification modification) {
		if(modification.getSourceID() == EXPID){
			_expression_replace = null;
		} else {
			_expression_replace = null;
		}
		return false;
	}

	@Override
	public boolean backup(Modification modification) {
		// TODO Auto-generated method stub
		return false;
	}
	
	@Override
	public StringBuffer toSrcString() {
		StringBuffer stringBuffer = new StringBuffer("swtich (");
		if(_expression_replace != null){
			stringBuffer.append(_expression_replace);
		} else {
			stringBuffer.append(_expression.toSrcString());
		}
		stringBuffer.append("){\n");
		if(_statements_replace != null){
			stringBuffer.append(_statements_replace);
			stringBuffer.append("\n");
		}else{
			for(Stmt stmt : _statements){
				stringBuffer.append(stmt.toSrcString());
				stringBuffer.append("\n");
			}
		}
		
		stringBuffer.append("}");
		return stringBuffer;
	}

	@Override
	public List<Literal> getLiterals() {
		List<Literal> list = _expression.getLiterals();
		for(Stmt stmt : _statements){
			list.addAll(stmt.getLiterals());
		}
		return list;
	}

	@Override
	public List<Variable> getVariables() {
		List<Variable> list = _expression.getVariables();
		for(Stmt stmt : _statements){
			list.addAll(stmt.getVariables());
		}
		return list;
	}

	@Override
	public List<LoopStruct> getLoopStruct() {
		List<LoopStruct> list = new LinkedList<>();
		for(Stmt stmt : _statements){
			list.addAll(stmt.getLoopStruct());
		}
		return list;
	}
	
	@Override
	public List<CondStruct> getCondStruct() {
		List<CondStruct> list = new LinkedList<>();
		for(Stmt stmt : _statements){
			list.addAll(stmt.getCondStruct());
		}
		return list;
	}

	@Override
	public List<MethodCall> getMethodCalls() {
		List<MethodCall> list = _expression.getMethodCalls();
		for(Stmt stmt : _statements){
			list.addAll(stmt.getMethodCalls());
		}
		return list;
	}

	@Override
	public List<Operator> getOperators() {
		List<Operator> list = _expression.getOperators();
		for(Stmt stmt : _statements){
			list.addAll(stmt.getOperators());
		}
		return list;
	}

	@Override
	public List<OtherStruct> getOtherStruct() {
		List<OtherStruct> list = new LinkedList<>();
		for(Stmt stmt : _statements){
			list.addAll(stmt.getOtherStruct());
		}
		return list;
	}
	
	@Override
	public void computeFeatureVector() {
		_fVector = new NewFVector();
		_fVector.combineFeature(_expression.getFeatureVector());
		for(Stmt stmt : _statements){
			_fVector.combineFeature(stmt.getFeatureVector());
		}
	}
	

	@Override
	public USE_TYPE getUseType(Node child) {
		return USE_TYPE.USE_SWSTMT;
	}
	
	@Override
	public List<Node> getChildren() {
		List<Node> list = new ArrayList<>();
		for(Stmt stmt : _statements){
			list.add(stmt);
		}
		return list;
	}
	
	@Override
	public String simplify(Map<String, String> varTrans, Map<String, Type> allUsableVariables) {
		StringBuffer stringBuffer = new StringBuffer("swtich (");
		String expr = _expression.simplify(varTrans, allUsableVariables);
		if(expr == null){
			return null;
		}
		stringBuffer.append(expr);
		stringBuffer.append("){\n");
		boolean empty = true;
		for(Stmt stmt : _statements){
			String string = stmt.simplify(varTrans, allUsableVariables);
			if(string != null){
				empty = false;
				stringBuffer.append(string);
				stringBuffer.append("\n");
			}
		}
		if(empty){
			return null;
		}
		
		stringBuffer.append("}");
		return stringBuffer.toString();
	}
}
