/***************************************************************************
 *   Copyright (C) by Fabrizio Montesi                                     *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU Library General Public License as       *
 *   published by the Free Software Foundation; either version 2 of the    *
 *   License, or (at your option) any later version.                       *
 *                                                                         *
 *   This program is distributed in the hope that it will be useful,       *
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of        *
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
 *   GNU General Public License for more details.                          *
 *                                                                         *
 *   You should have received a copy of the GNU Library General Public     *
 *   License along with this program; if not, write to the                 *
 *   Free Software Foundation, Inc.,                                       *
 *   59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.             *
 *                                                                         *
 *   For details about the authors of this software, see the AUTHORS file. *
 ***************************************************************************/

package jolie.lang.parse;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Logger;

import jolie.lang.Constants.OperandType;
import jolie.lang.parse.ast.AndConditionNode;
import jolie.lang.parse.ast.AssignStatement;
import jolie.lang.parse.ast.CompareConditionNode;
import jolie.lang.parse.ast.CompensateStatement;
import jolie.lang.parse.ast.ConstantIntegerExpression;
import jolie.lang.parse.ast.ConstantRealExpression;
import jolie.lang.parse.ast.ConstantStringExpression;
import jolie.lang.parse.ast.CorrelationSetInfo;
import jolie.lang.parse.ast.CurrentHandlerStatement;
import jolie.lang.parse.ast.DeepCopyStatement;
import jolie.lang.parse.ast.EmbeddedServiceNode;
import jolie.lang.parse.ast.ExecutionInfo;
import jolie.lang.parse.ast.ExitStatement;
import jolie.lang.parse.ast.ExpressionConditionNode;
import jolie.lang.parse.ast.ForEachStatement;
import jolie.lang.parse.ast.ForStatement;
import jolie.lang.parse.ast.IfStatement;
import jolie.lang.parse.ast.InstallFixedVariableExpressionNode;
import jolie.lang.parse.ast.InstallStatement;
import jolie.lang.parse.ast.IsTypeExpressionNode;
import jolie.lang.parse.ast.LinkInStatement;
import jolie.lang.parse.ast.LinkOutStatement;
import jolie.lang.parse.ast.NDChoiceStatement;
import jolie.lang.parse.ast.NotConditionNode;
import jolie.lang.parse.ast.NotificationOperationStatement;
import jolie.lang.parse.ast.NullProcessStatement;
import jolie.lang.parse.ast.OLSyntaxNode;
import jolie.lang.parse.ast.OneWayOperationDeclaration;
import jolie.lang.parse.ast.OneWayOperationStatement;
import jolie.lang.parse.ast.OperationDeclaration;
import jolie.lang.parse.ast.OrConditionNode;
import jolie.lang.parse.ast.OutputPortInfo;
import jolie.lang.parse.ast.ParallelStatement;
import jolie.lang.parse.ast.PointerStatement;
import jolie.lang.parse.ast.PostDecrementStatement;
import jolie.lang.parse.ast.PostIncrementStatement;
import jolie.lang.parse.ast.PreDecrementStatement;
import jolie.lang.parse.ast.PreIncrementStatement;
import jolie.lang.parse.ast.ProductExpressionNode;
import jolie.lang.parse.ast.Program;
import jolie.lang.parse.ast.RequestResponseOperationDeclaration;
import jolie.lang.parse.ast.RequestResponseOperationStatement;
import jolie.lang.parse.ast.RunStatement;
import jolie.lang.parse.ast.Scope;
import jolie.lang.parse.ast.SequenceStatement;
import jolie.lang.parse.ast.InputPortInfo;
import jolie.lang.parse.ast.SolicitResponseOperationStatement;
import jolie.lang.parse.ast.DefinitionCallStatement;
import jolie.lang.parse.ast.DefinitionNode;
import jolie.lang.parse.ast.InterfaceDefinition;
import jolie.lang.parse.ast.SpawnStatement;
import jolie.lang.parse.ast.SumExpressionNode;
import jolie.lang.parse.ast.SynchronizedStatement;
import jolie.lang.parse.ast.ThrowStatement;
import jolie.lang.parse.ast.TypeCastExpressionNode;
import jolie.lang.parse.ast.UndefStatement;
import jolie.lang.parse.ast.ValueVectorSizeExpressionNode;
import jolie.lang.parse.ast.VariableExpressionNode;
import jolie.lang.parse.ast.VariablePathNode;
import jolie.lang.parse.ast.WhileStatement;
import jolie.lang.parse.ast.types.TypeDefinition;
import jolie.lang.parse.ast.types.TypeDefinitionLink;
import jolie.lang.parse.ast.types.TypeInlineDefinition;
import jolie.util.Pair;

/**
 * Checks the well-formedness and validity of a JOLIE program.
 * @see Program
 * @author Fabrizio Montesi
 */
public class SemanticVerifier implements OLVisitor
{
	final private Program program;
	private boolean valid = true;
	
	final private Map< String, InputPortInfo > inputPorts = new HashMap< String, InputPortInfo >();
	final private Map< String, OutputPortInfo > outputPorts = new HashMap< String, OutputPortInfo >();
	
	final private Set< String > subroutineNames = new HashSet< String > ();
	final private Map< String, OneWayOperationDeclaration > oneWayOperations =
						new HashMap< String, OneWayOperationDeclaration >();
	final private Map< String, RequestResponseOperationDeclaration > requestResponseOperations =
						new HashMap< String, RequestResponseOperationDeclaration >();

	private boolean insideInputPort = false;
	private boolean mainDefined = false;
	
	final private Logger logger = Logger.getLogger( "JOLIE" );
	
	final private Map< String, TypeDefinition > definedTypes = OLParser.createTypeDeclarationMap();
	//private TypeDefinition rootType; // the type representing the whole session state
	
	public SemanticVerifier( Program program )
	{
		this.program = program;
		/*rootType = new TypeInlineDefinition(
			new ParsingContext(),
			"#RootType",
			NativeType.VOID,
			jolie.lang.Constants.RANGE_ONE_TO_ONE
		);*/
	}

	private void warning( OLSyntaxNode node, String message )
	{
		if ( node == null ) {
			logger.warning( message );
		} else {
			logger.warning( node.context().sourceName() + ":" + node.context().line() + ": " + message );
		}
	}
	
	private void error( OLSyntaxNode node, String message )
	{
		valid = false;
		if ( node != null ) {
			ParsingContext context = node.context();
			logger.severe( context.sourceName() + ":" + context.line() + ": " + message );
		} else {
			logger.severe( message );
		}
	}
	
	public boolean validate()
	{
		program.accept( this );

		if ( mainDefined == false ) {
			error( null, "Main procedure not defined" );
		}
		
		if ( !valid  ) {
			logger.severe( "Aborting: input file semantically invalid." );
			return false;
		}

		return valid;
	}

	private boolean isTopLevelType = true;

	public void visit( TypeInlineDefinition n )
	{
		checkCardinality( n );
		boolean backupRootType = isTopLevelType;
		if ( isTopLevelType ) {
			// Check if the type has already been defined
			if ( definedTypes.containsKey( n.id() ) ) {
				error( n, "type " + n.id() + " has already been defined" );
			}
		}

		isTopLevelType = false;

		if ( n.hasSubTypes() ) {
			for( Entry< String, TypeDefinition > entry : n.subTypes() ) {
				entry.getValue().accept( this );
			}
		}

		isTopLevelType = backupRootType;

		if ( isTopLevelType ) {
			definedTypes.put( n.id(), n );
		}
	}
	
	public void visit( TypeDefinitionLink n )
	{
		checkCardinality( n );
		if ( n.isValid() == false ) {
			error( n, "type " + n.id() + " points to an undefined type" );
		}
	}

	private void checkCardinality( TypeDefinition type )
	{
		if ( type.cardinality().min() < 0 ) {
			error( type, "type " + type.id() + " specifies an invalid minimum range value (must be positive)" );
		}
		if ( type.cardinality().max() < 0 ) {
			error( type, "type " + type.id() + " specifies an invalid maximum range value (must be positive)" );
		}
	}

	public void visit( SpawnStatement n )
	{
		n.body().accept( this );
	}
	
	public void visit( Program n )
	{
		for( OLSyntaxNode node : n.children() ) {
			node.accept( this );
		}
	}

	public void visit( VariablePathNode n )
	{}

	public void visit( InputPortInfo n )
	{
		if ( inputPorts.get( n.id() ) != null ) {
			error( n, "input port " + n.id() + " has been already defined" );
		}
		inputPorts.put( n.id(), n );

		insideInputPort = true;

		for( OperationDeclaration op : n.operations() ) {
			op.accept( this );
		}

		insideInputPort = false;
	}
	
	public void visit( OutputPortInfo n )
	{
		if ( outputPorts.get( n.id() ) != null )
			error( n, "output port " + n.id() + " has been already defined" );
		outputPorts.put( n.id(), n );

		for( OperationDeclaration op : n.operations() ) {
			op.accept( this );
		}
	}
		
	public void visit( OneWayOperationDeclaration n )
	{
		if ( definedTypes.get( n.requestType().id() ) == null ) {
			error( n, "unknown type: " + n.requestType().id() );
		}
		if ( insideInputPort ) { // Input operation
			if ( oneWayOperations.containsKey( n.id() ) ) {
				OneWayOperationDeclaration other = oneWayOperations.get( n.id() );
				if ( n.requestType() != null && n.requestType().equals( other.requestType() ) == false ) {
					error( n, "input operations sharing the same name cannot declare different types (One-Way operation " + n.id() + ")" );
				}
			} else {
				oneWayOperations.put( n.id(), n );
			}
		}
	}
		
	public void visit( RequestResponseOperationDeclaration n )
	{
		if ( n.requestType() != null && definedTypes.get( n.requestType().id() ) == null ) {
			error( n, "unknown type: " + n.requestType().id() );
		}
		if ( n.responseType() != null && definedTypes.get( n.responseType().id() ) == null ) {
			error( n, "unknown type: " + n.requestType().id() );
		}
		for( Entry< String, TypeDefinition > fault : n.faults().entrySet() ) {
			if ( fault.getValue() != null && !definedTypes.containsKey( fault.getValue().id() ) ) {
				error( n, "unknown type " + fault.getValue().id() + " for fault " + fault.getKey() );
			}
		}

		if ( insideInputPort ) { // Input operation
			if ( requestResponseOperations.containsKey( n.id() ) ) {
				RequestResponseOperationDeclaration other = requestResponseOperations.get( n.id() );
				checkEqualness( n, other );
			} else {
				requestResponseOperations.put( n.id(), n );
			}
		}
	}
	
	private void checkEqualness( RequestResponseOperationDeclaration n, RequestResponseOperationDeclaration other )
	{
		if ( n.requestType() == null ) {
			if ( other.requestType() != null ) {
				error( n, "input operations sharing the same name cannot declare different request types (Request-Response operation " + n.id() + ")" );
			}
		} else if ( n.requestType().equals( other.requestType() ) == false ) {
			error( n, "input operations sharing the same name cannot declare different request types (Request-Response operation " + n.id() + ")" );
		}

		if ( n.responseType() == null ) {
			if ( other.responseType() != null ) {
				error( n, "input operations sharing the same name cannot declare different response types (Request-Response operation " + n.id() + ")" );
			}
		} else if ( n.responseType().equals( other.responseType() ) == false ) {
			error( n, "input operations sharing the same name cannot declare different response types (Request-Response operation " + n.id() + ")" );
		}

		if ( n.faults().size() != other.faults().size() ) {
			error( n, "input operations sharing the same name cannot declared different fault types (Request-Response operation " + n.id() );
		}

		for( Entry< String, TypeDefinition > fault : n.faults().entrySet() ) {
			if ( fault.getValue() != null ) {
				if ( !other.faults().containsKey( fault.getKey() ) || !other.faults().get( fault.getKey() ).equals( fault.getValue() ) ) {
					error( n, "input operations sharing the same name cannot declared different fault types (Request-Response operation " + n.id() );
				}
			}
		}
	}

	public void visit( DefinitionNode n )
	{
		if ( subroutineNames.contains( n.id() ) ) {
			error( n, "Procedure " + n.id() + " uses an already defined identifier" );
		} else {
			subroutineNames.add( n.id() );
		}
		
		if ( "main".equals( n.id() ) ) {
			mainDefined = true;
		}
		n.body().accept( this );
	}
		
	public void visit( ParallelStatement stm )
	{
		for( OLSyntaxNode node : stm.children() ) {
			node.accept( this );
		}
	}
		
	public void visit( SequenceStatement stm )
	{
		for( OLSyntaxNode node : stm.children() ) {
			node.accept( this );
		}
	}
		
	public void visit( NDChoiceStatement stm )
	{
		for( Pair< OLSyntaxNode, OLSyntaxNode > pair : stm.children() ) {
			pair.key().accept( this );
			pair.value().accept( this );
		}
	}
	
	public void visit( NotificationOperationStatement n )
	{
		OutputPortInfo p = outputPorts.get( n.outputPortId() );
		if ( p == null ) {
			error( n, n.outputPortId() + " is not a valid output port" );
		} else {
			OperationDeclaration decl = p.operationsMap().get( n.id() );
			if ( decl == null )
				error( n, "Operation " + n.id() + " has not been declared in output port type " + p.id() );
			else if ( !( decl instanceof OneWayOperationDeclaration ) )
				error( n, "Operation " + n.id() + " is not a valid one-way operation in output port " + p.id() );
		} 
	}
	
	public void visit( SolicitResponseOperationStatement n )
	{
		OutputPortInfo p = outputPorts.get( n.outputPortId() );
		if ( p == null )
			error( n, n.outputPortId() + " is not a valid output port" );
		else {
			OperationDeclaration decl = p.operationsMap().get( n.id() );
			if ( decl == null )
				error( n, "Operation " + n.id() + " has not been declared in output port " + p.id() );
			else if ( !( decl instanceof RequestResponseOperationDeclaration ) )
				error( n, "Operation " + n.id() + " is not a valid request-response operation in output port " + p.id() );
		} 
	}
	
	public void visit( ThrowStatement n )
	{
		verify( n.expression() );
	}

	public void visit( CompensateStatement n ) {}
	
	public void visit( InstallStatement n )
	{
		for( Pair< String, OLSyntaxNode > pair : n.handlersFunction().pairs() ) {
			pair.value().accept( this );
		}
	}

	public void visit( Scope n )
	{
		n.body().accept( this );
	}
	
	public void visit( OneWayOperationStatement n )
	{
		verify( n.inputVarPath() );
	}

	public void visit( RequestResponseOperationStatement n )
	{
		verify( n.inputVarPath() );
		verify( n.process() );
	}

	public void visit( LinkInStatement n ) {}
	public void visit( LinkOutStatement n ) {}
	public void visit( SynchronizedStatement n )
	{
		n.body().accept( this );
	}
		
	public void visit( AssignStatement n )
	{
		n.variablePath().accept( this );
		n.expression().accept( this );
	}

	private void verify( OLSyntaxNode n )
	{
		if ( n != null ) {
			n.accept( this );
		}
	}

	public void visit( PointerStatement n ) {}
	public void visit( DeepCopyStatement n ) {}

	public void visit( IfStatement n )
	{
		for( Pair< OLSyntaxNode, OLSyntaxNode > choice : n.children() ) {
			choice.key().accept(  this );
			choice.value().accept(  this );
		}
		verify( n.elseProcess() );
	}

	public void visit( DefinitionCallStatement n ) {}

	public void visit( WhileStatement n )
	{
		n.condition().accept( this );
		n.body().accept( this );
	}

	public void visit( OrConditionNode n )
	{
		for( OLSyntaxNode node : n.children() ) {
			node.accept( this );
		}
	}

	public void visit( AndConditionNode n )
	{
		for( OLSyntaxNode node : n.children() ) {
			node.accept( this );
		}
	}

	public void visit( NotConditionNode n )
	{
		n.condition().accept( this );
	}

	public void visit( CompareConditionNode n )
	{
		n.leftExpression().accept( this );
		n.rightExpression().accept( this );
	}

	public void visit( ExpressionConditionNode n ) 
	{
		n.expression().accept( this );
	}

	public void visit( ConstantIntegerExpression n ) {}
	public void visit( ConstantRealExpression n ) {}
	public void visit( ConstantStringExpression n ) {}

	public void visit( ProductExpressionNode n )
	{
		for( Pair< OperandType, OLSyntaxNode > pair : n.operands() ) {
			pair.value().accept( this );
		}
	}

	public void visit( SumExpressionNode n )
	{
		for( Pair< OperandType, OLSyntaxNode > pair : n.operands() ) {
			pair.value().accept( this );
		}
	}

	public void visit( VariableExpressionNode n )
	{
		n.variablePath().accept( this );
	}

	public void visit( InstallFixedVariableExpressionNode n )
	{
		n.variablePath().accept(  this );
	}

	public void visit( NullProcessStatement n ) {}

	public void visit( ExitStatement n ) {}

	public void visit( ExecutionInfo n ) {}

	public void visit( CorrelationSetInfo n ) {}

	public void visit( RunStatement n )
	{
		warning( n, "Run statement is not a stable feature yet." );
	}

	public void visit( ValueVectorSizeExpressionNode n )
	{
		n.variablePath().accept( this );
	}

	public void visit( PreIncrementStatement n )
	{
		n.variablePath().accept( this );
	}

	public void visit( PostIncrementStatement n )
	{
		n.variablePath().accept( this );
	}

	public void visit( PreDecrementStatement n )
	{
		n.variablePath().accept( this );
	}

	public void visit( PostDecrementStatement n )
	{
		n.variablePath().accept( this );
	}

	public void visit( UndefStatement n )
	{
		n.variablePath().accept( this );
	}

	
	public void visit( ForStatement n )
	{
		n.init().accept( this );
		n.condition().accept( this );
		n.post().accept( this );
		n.body().accept( this );
	}

	public void visit( ForEachStatement n )
	{
		n.keyPath().accept( this );
		n.targetPath().accept( this );
		n.body().accept( this );
	}

	public void visit( IsTypeExpressionNode n )
	{
		n.variablePath().accept( this );
	}

	public void visit( TypeCastExpressionNode n )
	{
		n.expression().accept( this );
	}

	public void visit( EmbeddedServiceNode n )
	{}
	
	/**
	 * @todo Must check if it's inside an install function
	 */
	public void visit( CurrentHandlerStatement n )
	{}

	public void visit( InterfaceDefinition n )
	{}
}
