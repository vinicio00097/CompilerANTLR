import compiler.generated.Parser;
import java_cup.runtime.Symbol;
import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.ATNConfigSet;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.tree.*;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.reactfx.Subscription;

import java.io.*;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main extends Application {

    private static final String[] KEYWORDS = new String[] {
            "abstract", "assert", "boolean", "break", "byte",
            "case", "catch", "char", "class", "const",
            "continue", "default", "do", "double", "else",
            "enum", "extends", "final", "finally", "float",
            "for", "goto", "if", "implements", "import",
            "instanceof", "int", "interface", "long", "native",
            "new", "package", "private", "protected", "public",
            "return", "short", "static", "strictfp", "super",
            "switch", "synchronized", "this", "throw", "throws",
            "transient", "try", "void", "volatile", "while","printf"
    };

    private static final String KEYWORD_PATTERN = "\\b(" + String.join("|", KEYWORDS) + ")\\b";
    private static final String IDENTIFIERS="[a-z|A-Z]+[0-9]*";
    private static final String CONSTANTS="[\\d]{1,12}|[\\d+\\.\\d+]";
    private static final String ARITHMETIC_OPERATORS="(\\+|\\-|\\*|\\/|\\%|\\+\\+|\\-\\-)";
    private static final String RELATIONAL_OPERATORS="((\\=|\\!)\\=)|(\\>(\\=)?)|(\\<(\\=)?)";
    private static final String LOGICAL_OPERATORS="(\\&\\&|\\|\\||\\!)";
    private static final String BITWISE_OPERATORS="(\\&|\\|\\^|\\<\\<|\\>\\>)";
    private static final String ASSIGNMENT_OPERATORS="((\\+|\\-|\\*|\\/|\\%)?\\=)";
    private static final String PAREN_PATTERN = "\\(|\\)";
    private static final String BRACE_PATTERN = "\\{|\\}";
    private static final String BRACKET_PATTERN = "\\[|\\]";
    private static final String SEMICOLON_PATTERN = "\\;";
    private static final String STRING_PATTERN = "\"([^\"\\\\]|\\\\.)*\"";
    private static final String COMMENT_PATTERN = "//[^\n]*" + "|" + "/\\*(.|\\R)*?\\*/";

    private static final Pattern PATTERN = Pattern.compile(
            "(?<KEYWORD>" + KEYWORD_PATTERN + ")"
                    + "|(?<IDENTIFIERS>"+IDENTIFIERS+")"
                    + "|(?<CONSTANTS>"+CONSTANTS+")"
                    + "|(?<ARITHMETICOPERATORS>"+ARITHMETIC_OPERATORS+")"
                    + "|(?<RELATIONALOPERATORS>"+RELATIONAL_OPERATORS+")"
                    + "|(?<LOGICALOPERATORS>"+LOGICAL_OPERATORS+")"
                    + "|(?<BITWISEOPERATORS>"+BITWISE_OPERATORS+")"
                    + "|(?<ASSIGNMENTOPERATORS>"+ASSIGNMENT_OPERATORS+")"
                    + "|(?<PAREN>" + PAREN_PATTERN + ")"
                    + "|(?<BRACE>" + BRACE_PATTERN + ")"
                    + "|(?<BRACKET>" + BRACKET_PATTERN + ")"
                    + "|(?<SEMICOLON>" + SEMICOLON_PATTERN + ")"
                    + "|(?<STRING>" + STRING_PATTERN + ")"
                    + "|(?<COMMENT>" + COMMENT_PATTERN + ")",Pattern.CASE_INSENSITIVE
    );

    private CodeArea codeArea;
    private ExecutorService executor;
    private File fileOpened;
    private TextArea errores=new TextArea();
    private MenuBar menuBar=new MenuBar();
    private VBox vbox=new VBox(menuBar);
    private ToolBar toolBar=new ToolBar();
    private HBox layoutH=new HBox();
    private boolean isGood=true;

    @Override
    public void start(Stage primaryStage) throws Exception{

        executor = Executors.newSingleThreadExecutor();
        codeArea = new CodeArea();
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        Subscription cleanupWhenDone = codeArea.multiPlainChanges()
                .successionEnds(Duration.ofMillis(500))
                .supplyTask(this::computeHighlightingAsync)
                .awaitLatest(codeArea.multiPlainChanges())
                .filterMap(t -> {
                    if(t.isSuccess()) {
                        return Optional.of(t.get());
                    } else {
                        t.getFailure().printStackTrace();
                        return Optional.empty();
                    }
                })
                .subscribe(this::applyHighlighting);

        // call when no longer need it: `cleanupWhenFinished.unsubscribe();`

        menuBar.getMenus().addAll(
            generateFileMenu(),
            generateActionsMenu()
        );


        errores.setEditable(false);

        layoutH.setAlignment(Pos.TOP_RIGHT);
        layoutH.getChildren().addAll(
            new Button("Execute"),
            new Separator(Orientation.VERTICAL),
            new Button("Compile")
        );
        //toolBar.getItems().addAll(layoutH);
        toolBar.setPrefHeight(30.0);

        ToolBar toolBarResult=new ToolBar();
        toolBarResult.setPrefHeight(30.0);



        vbox.getChildren().addAll(
            toolBar,
            new VirtualizedScrollPane<>(codeArea),
            toolBarResult,
            errores
        );
        VBox.setVgrow(codeArea.getParent(),Priority.ALWAYS);

        Scene scene = new Scene(vbox, 800, 600);
        scene.getStylesheets().add(getClass().getClassLoader().getResource("java-keywords.css").toString());
        primaryStage.setScene(scene);
        primaryStage.setTitle("Compiler");
        primaryStage.show();
    }


    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void stop() {
        executor.shutdown();
    }

    private Task<StyleSpans<Collection<String>>> computeHighlightingAsync() {
        String text = codeArea.getText();
        Task<StyleSpans<Collection<String>>> task = new Task<StyleSpans<Collection<String>>>() {
            @Override
            protected StyleSpans<Collection<String>> call() throws Exception {
                return computeHighlighting(text);
            }
        };
        executor.execute(task);
        return task;
    }

    private void applyHighlighting(StyleSpans<Collection<String>> highlighting) {
        codeArea.setStyleSpans(0, highlighting);
    }

    private static StyleSpans<Collection<String>> computeHighlighting(String text) {
        Matcher matcher = PATTERN.matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder
                = new StyleSpansBuilder<>();
        while(matcher.find()) {
            String styleClass =
                    matcher.group("KEYWORD") != null ? "keyword" :
                            matcher.group("PAREN") != null ? "paren" :
                                    matcher.group("BRACE") != null ? "brace" :
                                            matcher.group("BRACKET") != null ? "bracket" :
                                                    matcher.group("SEMICOLON") != null ? "semicolon" :
                                                            matcher.group("STRING") != null ? "string" :
                                                                    matcher.group("COMMENT") != null ? "comment" :
                                                                            null; /* never happens */ assert styleClass != null;
            spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
            spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
            lastKwEnd = matcher.end();
        }
        spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
        return spansBuilder.create();
    }

    private Menu generateFileMenu(){
        String[] opciones={"Open","Save as...","Save"};
        Menu ArchivoMenu=new Menu("File");

        for(String opcion:opciones){
            MenuItem opcionWidget=new MenuItem(opcion);
            opcionWidget.setOnAction(new EventHandler<ActionEvent>() {
                @Override
                public void handle(ActionEvent event) {
                    switch (opcionWidget.getText()){
                        case "Open": {
                            FileChooser openFile = new FileChooser();
                            openFile.getExtensionFilters().add(new FileChooser.ExtensionFilter("TXT files(*.txt)", "*.txt"));
                            fileOpened = openFile.showOpenDialog(codeArea.getScene().getWindow());

                            if(fileOpened!=null){
                                try {
                                    codeArea.replaceText(readFile(fileOpened));
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        }break;
                        case "Open as...":{
                            FileChooser openFile = new FileChooser();
                            openFile.getExtensionFilters().add(new FileChooser.ExtensionFilter("TXT files(*.txt)", "*.txt"));
                            fileOpened = openFile.showSaveDialog(codeArea.getScene().getWindow());

                            if(fileOpened!=null){
                                try {
                                    writeFile(fileOpened);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        }break;
                        case "Save":{
                            if(fileOpened==null){
                                FileChooser openFile = new FileChooser();
                                openFile.getExtensionFilters().add(new FileChooser.ExtensionFilter("TXT files(*.txt)", "*.txt"));
                                fileOpened = openFile.showSaveDialog(codeArea.getScene().getWindow());

                                try {
                                    writeFile(fileOpened);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }

                            }else{
                                if(!fileOpened.exists()){
                                    FileChooser openFile = new FileChooser();
                                    openFile.getExtensionFilters().add(new FileChooser.ExtensionFilter("TXT files(*.txt)", "*.txt"));
                                    fileOpened = openFile.showSaveDialog(codeArea.getScene().getWindow());

                                    try {
                                        writeFile(fileOpened);
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }else{
                                    try {
                                        writeFile(fileOpened);
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        }break;
                    }
                }
            });

            ArchivoMenu.getItems().add(opcionWidget);
        }

        return  ArchivoMenu;
    }

    private Menu generateActionsMenu(){
        String[] actions={"Compile","Execute"};
        Menu ActionsMenu=new Menu("Actions");

        for(String action:actions){
            MenuItem actionItem=new MenuItem(action);
            ActionsMenu.getItems().add(actionItem);

            actionItem.setOnAction(new EventHandler<ActionEvent>() {
                @Override
                public void handle(ActionEvent event) {
                    switch (actionItem.getText()){
                        case "Compile":{
                            isGood=true;
                            errores.clear();

                            CPP14Lexer lexer=new CPP14Lexer(CharStreams.fromString(codeArea.getText()));

                            CommonTokenStream tokens = new CommonTokenStream(lexer);

                            CPP14Parser parser = new CPP14Parser(tokens);


                            try {
                                compiler.generated.Lexer scanner = new compiler.generated.Lexer(new BufferedReader(new StringReader(codeArea.getText())));

                                compiler.generated.Parser parserCup = new Parser(scanner);
                                Symbol s = parserCup.parse();

                                if (s.toString().equals("#0"))
                                    errores.setText("SUCCESSFULL COMPILATION ");
                                else
                                    errores.setText(s.toString());

                            } catch (Exception e) {
                                if(e.getMessage()!=null){
                                    errores.appendText(e.getMessage());
                                }
                                e.getMessage();
                            }

                            // Walk it and attach our listener
                            ParseTreeWalker walker = new ParseTreeWalker();
                            // Specify our entry point
                            ParseTree entryPoint = parser.translationunit();
                            walker.walk(new CPP14BaseListener(), entryPoint);

                            for(Token token:tokens.getTokens()){
                                System.out.println(CPP14Lexer.VOCABULARY.getSymbolicName(token.getType())+" "+token.getText());
                            }
                        }break;
                        case "Execute":{

                        }break;
                    }
                }
            });
        }

        return  ActionsMenu;
    }

    private String readFile(File file) throws IOException {
        StringBuilder contents = new StringBuilder();
        BufferedReader reader = null;

        reader = new BufferedReader(new FileReader(file));
        String text = null;

        // repeat until all lines is read
        while ((text = reader.readLine()) != null) {
            contents.append(text).append(System.getProperty("line.separator"));
        }
        reader.close();

        return contents.toString();
    }

    private boolean writeFile(File file) throws  IOException{
        try{
            // Create file
            FileWriter fstream = new FileWriter(file.getAbsolutePath());
            BufferedWriter out = new BufferedWriter(fstream);
            out.write(codeArea.getText());
            out.close();
            return true;
        }catch (Exception e){//Catch exception if any
            e.printStackTrace();
            return false;
        }
    }
}
