## Wvlet Language Grammar

This document describes the grammar of Wvlet language.

```antlrv4
packageDef: 'package' qualifiedId statement*

qualifiedId: identifier ('.' identifier)*

identifier  : IDENTIFIER
            | BACKQUOTED_IDENTIFIER
            | '*'
            | reserved  # Necessary to use reserved words as identifiers
IDENTIFIER  : (LETTER | '_') (LETTER | DIGIT | '_')*
BACKQUOTED_IDENTIFIER: '`' (~'`' | '``')+ '`'
// All reserved keywords (TokenType.Keyword)
reserved   : 'from' | 'select' | 'agg' | 'where' | 'group' | 'by' | ...


statements: statement+
statement : importStatement
          | modelDef
          | query ';'?
          | typeDef
          | funDef
          | showCommand queryOp*

importStatement: 'import' importRef (from str)?
importRef      : qualifiedId ('.' '*')?
               | qualifiedId 'as' identifier

modelDef   : 'model' identifier modelParams? (':' qualifiedId)? '=' modelBody
modelBody  : query 'end'
modelParams: '(' modelParam (',' modelParam)* ')'
modelParam : identifier ':' identifier ('=' expression)?

// top-level query
query      : queryBody
queryBody  : querySingle queryBlock*
// This rule can be used for sub queries
querySingle: 'from' relation (',' relation)* ','? queryBlock*
           | 'select' selectItems queryBlock*
           // For parenthesized query, do not continue queryBlock for disambiguation
           | '(' queryBody ')' 

// relation that can be used after 'from', 'join', 'concat' (set operation), etc.:
relation       : relationPrimary ('as' identifier)?
relationPrimary: qualifiedId ('(' functionArg (',' functionArg)* ')')?
               | querySingle
               | str               // file scan
               | strInterpolation  // embedded raw SQL
               | arrayValue
arrayValue     : '[' arrayValue (',' arrayValue)* ','? ']'

queryBlock: joinExpr
          | 'group' 'by' groupByItemList
          | 'where' booleanExpression
          | 'transform' transformExpr
          | 'select' 'distinct'? selectItems
          | 'agg' selectItems
          | 'pivot' 'on' pivotItem (',' pivotItem)*
               ('group' 'by' groupByItemList)?
               ('agg' selectItems)?
          | 'limit' INTEGER_VALUE
          | 'order' 'by' sortItem (',' sortItem)* ','?)?
          | 'add' selectItems
          | 'exclude' identifier ((',' identifier)* ','?)?
          | 'shift' identifier (',' identifier)* ','?
          | 'test' COLON testExpr*
          | 'show' identifier
          | 'sample' sampleExpr
          | 'concat' relation
          | ('intersect' | 'except') 'all'? relation
          | 'dedup'
          | 'describe'

joinExpr    : joinType? 'join' relation joinCriteria
            | 'cross' 'join' relation
joinType    : 'inner' | 'left' | 'right' | 'full'
joinCriteria: 'on' booleanExpression
            // using equi join keys
            | 'on' identifier (',' identifier)*

groupByItemList: groupByItem (',' groupByItem)* ','?
groupByItem    : expression ('as' identifier (':' identifier)?)?

transformExpr: transformItem (',' transformItem)* ','?
transformItem: qualifiedId '=' expression

selectItems: selectItem (',' selectItem)* ','?
selectItem : (identifier '=')? expression
           | expression ('as' identifier)?

window     : 'over' '(' windowSpec ')'
windowSpec : ('partition' 'by' expression (',' expression)*)?
           | ('order' 'by' sortItem (',' sortItem)*)?

test: 'test' COLON testExpr*
testExpr: booleanExpression

showCommand: 'show' identifier

sampleExpr: sampleSize
          | ('reservoir' | 'system') '(' sampleSize ')'

sampleSize:  ((integerLiteral 'rows'?) | (floatLiteral '%'))

sortItem: expression ('asc' | 'desc')?

pivotKey: identifier ('in' '(' (valueExpression (',' valueExpression)*) ')')?

typeDef    : 'type' identifier typeParams? context? typeExtends? ':' typeElem* 'end'
typeParams : '[' typeParam (',' typeParam)* ']'
typeParam  : identifier ('of' identifier)?
typeExtends: 'extends' qualifiedId (',' qualifiedId)*
typeElem   : valDef | funDef

valDef     : identifier ':' identifier typeParams? ('=' expression)?
funDef:    : 'def' funName defParams? (':' identifier '*'?)? ('=' expression)?
funName    : identifier | symbol
symbol     : '+' | '-' | '*' | '/' | '%' | '&' | '|' | '=' | '==' | '!=' | '<' | '<=' | '>' | '>=' | '&&' | '||'
defParams  : '(' defParam (',' defParam)* ')'
defParam   : identifier ':' identifier ('=' expression)?

context    : '(' 'in' contextItem (',' contextItem)* ')'
contextItem: identifier (':' identifier)?

strInterpolation: identifier
                | '"' stringPart* '"'
                | '"""' stringPart* '"""'  # triple quotes string
stringPart      : stringLiteral | '${' expression '}'


expression        : booleanExpression
booleanExpression : ('!' | 'not') booleanExpression
                  | valueExpression
                  | booleanExpression ('and' | 'or') booleanExpression
valueExpression   : primaryExpression
                  | valueExpression arithmeticOperator valueExpression
                  | valueExpression comparisonOperator valueExpression
                  | valueExpression testOperator valueExpression

arithmeticOperator: '+' | '-' | '*' | '/' | '%'
comparisonOperator: '=' | '==' | 'is' | '!=' | 'is' 'not' | '<' | '<=' | '>' | '>=' | 'like'
testOperator      : 'should' 'not'? ('be' | 'contain')

// Expression that can be chained with '.' operator
primaryExpression : 'this'
                  | '_'
                  | literal
                  | query
                  | '(' querySingle ')'                                                 # subquery
                  | '(' expression ')'                                            # parenthesized expression
                  | '[' expression (',' expression)* ']'                          # array
                  | 'if' booleanExpresssion 'then' expression 'else' expression   # if-then-else
                  | qualifiedId
                  | primaryExpression '.' primaryExpression
                  | primaryExpression '(' functionArg? (',' functionArg)* ')' window? # function call
                  | primaryExpression identifier expression                           # function infix

functionArg       | (identifier '=')? expression

literal           : 'null' | '-'? integerLiteral | '-'? floatLiteral | booleanLiteral | stringLiteral
```