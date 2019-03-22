import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.ATNConfigSet;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import java.util.BitSet;

public class Main {

    public static void main(String[] args) {
        String code="#include <iostream>\n" +
                "int main(){\n" +
                " int 4val=34.45;\n" +
                "if(200!=34){" +
                "}else{" +
                "}"+
                "return 0;"+
                "}";

        CPP14Lexer lexer=new CPP14Lexer(CharStreams.fromString(code));


        CommonTokenStream tokens = new CommonTokenStream(lexer);

        CPP14Parser parser = new CPP14Parser(tokens);
        parser.addErrorListener(/*new BaseErrorListener(){
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
                super.syntaxError(recognizer, offendingSymbol, line, charPositionInLine, msg, e);
                System.out.println(line);
                System.out.println(msg);
            }

            @Override
            public void reportContextSensitivity(Parser recognizer, DFA dfa, int startIndex, int stopIndex, int prediction, ATNConfigSet configs) {
                super.reportContextSensitivity(recognizer, dfa, startIndex, stopIndex, prediction, configs);

            }

            @Override
            public void reportAttemptingFullContext(Parser recognizer, DFA dfa, int startIndex, int stopIndex, BitSet conflictingAlts, ATNConfigSet configs) {
                super.reportAttemptingFullContext(recognizer, dfa, startIndex, stopIndex, conflictingAlts, configs);
            }

            @Override
            public void reportAmbiguity(Parser recognizer, DFA dfa, int startIndex, int stopIndex, boolean exact, BitSet ambigAlts, ATNConfigSet configs) {
                super.reportAmbiguity(recognizer, dfa, startIndex, stopIndex, exact, ambigAlts, configs);

                System.out.println(startIndex);
            }
        }*/new ANTLRErrorListener() {
            public void syntaxError(Recognizer<?, ?> recognizer, Object o, int i, int i1, String s, RecognitionException e) {
                System.out.println(i+" "+i1+" "+s);
            }

            public void reportAmbiguity(Parser parser, DFA dfa, int i, int i1, boolean b, BitSet bitSet, ATNConfigSet atnConfigSet) {

            }

            public void reportAttemptingFullContext(Parser parser, DFA dfa, int i, int i1, BitSet bitSet, ATNConfigSet atnConfigSet) {

            }

            public void reportContextSensitivity(Parser parser, DFA dfa, int i, int i1, int i2, ATNConfigSet atnConfigSet) {

            }
        });
        // Walk it and attach our listener
        ParseTreeWalker walker = new ParseTreeWalker();
        // Specify our entry point
        ParseTree entryPoint = parser.translationunit();
        walker.walk(new CPP14BaseListener(), entryPoint);

        for(Token token:tokens.getTokens()){
            System.out.println(CPP14Lexer.VOCABULARY.getSymbolicName(token.getType())+" "+token.getText());
        }
    }
}
